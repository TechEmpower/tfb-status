package tfb.status.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import com.google.common.io.MoreFiles;
import com.google.errorprone.annotations.Immutable;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.mail.MessagingException;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.config.SharingConfig;
import tfb.status.util.FileUtils;
import tfb.status.view.Results;
import tfb.status.view.ShareResultsErrorJsonView;
import tfb.status.view.ShareResultsJsonView;

/**
 * Accepts uploads of results.json files from users for sharing.
 */
@Singleton
public final class ShareResultsUploader {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final SharingConfig config;
  private final FileStore fileStore;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final EmailSender emailSender;

  @GuardedBy("emailTimeLock")
  private volatile @Nullable Instant previousEmailTime;
  private final Object emailTimeLock = new Object();

  @Inject
  public ShareResultsUploader(SharingConfig config,
                              FileStore fileStore,
                              ObjectMapper objectMapper,
                              Clock clock,
                              EmailSender emailSender) {

    this.config = Objects.requireNonNull(config);
    this.fileStore = Objects.requireNonNull(fileStore);
    this.objectMapper = Objects.requireNonNull(objectMapper);
    this.clock = Objects.requireNonNull(clock);
    this.emailSender = Objects.requireNonNull(emailSender);
  }

  // TODO: Save results as .json.gz instead of as .zip files.

  /**
   * Accepts an upload of a results.json file containing the specified bytes.
   *
   * <p>This method first validates the size requirements: that the given file
   * isn't too large, and that the share directory is not full.  This method
   * then validates the contents of the file, ensuring that it de-serializes to
   * a {@link Results} object successfully, and that it contains a non-empty
   * {@link Results#testMetadata}.
   *
   * @param resultsBytes the raw bytes of the results.json file
   * @return an object describing the success or failure of the upload
   */
  public ShareResultsUploadReport upload(InputStream resultsBytes)
      throws IOException {

    Objects.requireNonNull(resultsBytes);

    // We are only checking if the share directory is currently under its max
    // size, without the addition of the new file.  This reduces the complexity
    // and potentially wasted time of compressing the json file to a zip file
    // before checking if it can fit in the share directory.  This is a fine
    // compromise because it means that at most the share directory will exceed
    // the max size by just one large results json file, and we will not accept
    // further uploads after that.
    long shareDirectorySize =
        FileUtils.directorySizeInBytes(fileStore.shareDirectory());

    if (shareDirectorySize >= config.maxDirectorySizeInBytes) {
      onShareDirectoryFull(
          config.maxDirectorySizeInBytes,
          shareDirectorySize);

      return new ShareResultsUploadReport(
          new ShareResultsErrorJsonView(
              ShareResultsErrorJsonView.ErrorKind.SHARE_DIRECTORY_FULL,
              "Share uploads has reached max capacity."));
    }

    Path tempFile =
        Files.createTempFile(
            /* prefix= */ "TFB_Share_Upload",
            /* suffix= */ ".json");

    try {
      InputStream limitedBytes =
          ByteStreams.limit(
              resultsBytes,
              config.maxFileSizeInBytes + 1);

      long fileSize = Files.copy(limitedBytes, tempFile, REPLACE_EXISTING);

      if (fileSize > config.maxFileSizeInBytes)
        return new ShareResultsUploadReport(
            new ShareResultsErrorJsonView(
                ShareResultsErrorJsonView.ErrorKind.FILE_TOO_LARGE,
                "Share uploads cannot exceed "
                    + config.maxFileSizeInBytes
                    + " bytes."));

      Results results;
      try (InputStream inputStream = Files.newInputStream(tempFile)) {
        results = objectMapper.readValue(inputStream, Results.class);
      } catch (JsonProcessingException e) {
        logger.info("Exception processing json file {}", tempFile, e);
        return new ShareResultsUploadReport(
            new ShareResultsErrorJsonView(
                ShareResultsErrorJsonView.ErrorKind.INVALID_JSON,
                "Invalid results JSON"));
      }

      if (results.testMetadata == null || results.testMetadata.isEmpty())
        return new ShareResultsUploadReport(
            new ShareResultsErrorJsonView(
                ShareResultsErrorJsonView.ErrorKind.MISSING_TEST_METADATA,
                "Results must contain non-empty test metadata"));

      String shareId = UUID.randomUUID().toString();
      Path zipFile = fileStore.shareDirectory().resolve(shareId + ".zip");
      MoreFiles.createParentDirectories(zipFile);

      try (FileSystem zipFs =
               FileSystems.newFileSystem(
                   zipFile,
                   Map.of("create", "true"))) {

        // Create a single entry in the zip file for the json file.
        Path entry = zipFs.getPath(shareId + ".json");
        Files.copy(tempFile, entry);
      }

      String resultsUrl =
          config.tfbStatusOrigin
              + "/share-results/view/"
              + URLEncoder.encode(shareId + ".json", UTF_8);

      String visualizeResultsUrl =
          config.tfbWebsiteOrigin
              + "/benchmarks/#section=test&shareid="
              + URLEncoder.encode(shareId, UTF_8);

      return new ShareResultsUploadReport(
          new ShareResultsJsonView(
              /* shareId= */ shareId,
              /* resultsUrl= */ resultsUrl,
              /* visualizeResultsUrl= */ visualizeResultsUrl));

    } finally {
      Files.delete(tempFile);
    }
  }

  /**
   * Reads an uploaded results file from the share directory.  This is intended
   * to read files created through one of this class's upload methods.  Results
   * file uploads are stored in zip files of the same name, and should always be
   * modified or accessed through this class.
   *
   * @param shareId the share id for the results that were previously shared
   * @return the bytes of the shared results.json file, or {@code null} if no
   *         shared results.json file with the specified id is found
   */
  public @Nullable ByteSource getUpload(String shareId) {
    Objects.requireNonNull(shareId);

    Path zipFile = fileStore.shareDirectory().resolve(shareId + ".zip");
    if (!zipFile.equals(zipFile.normalize())
        || !zipFile.startsWith(fileStore.shareDirectory())
        || !fileStore.shareDirectory().equals(zipFile.getParent())
        || !Files.isRegularFile(zipFile)) {
      return null;
    }

    return new ByteSource() {
      @Override
      public InputStream openStream() throws IOException {
        // Return an input stream that closes the zip file when closed.
        InputStream closingStream = null;

        FileSystem zipFs = FileSystems.newFileSystem(zipFile);
        try {
          Path entry = zipFs.getPath(shareId + ".json");
          InputStream rawStream = Files.newInputStream(entry);
          closingStream =
              new FilterInputStream(rawStream) {
                @Override
                public void close() throws IOException {
                  try {
                    super.close();
                  } finally {
                    zipFs.close();
                  }
                }
              };
        } finally {
          if (closingStream == null)
            zipFs.close();
        }

        // If we're here, meaning the preceding code did not throw, then this
        // stream is guaranteed to be non-null.
        return Objects.requireNonNull(closingStream);
      }
    };
  }

  /**
   * Notifies the maintainers of this application that the share directory is
   * full.  This method may return without sending the email if a similar email
   * was sent too recently.
   *
   * @param capacityBytes the maximum size of the share directory
   * @param sizeBytes the current size of the share directory
   */
  private void onShareDirectoryFull(long capacityBytes, long sizeBytes) {
    synchronized (emailTimeLock) {
      Instant now = clock.instant();
      Instant previous = this.previousEmailTime;

      if (previous != null) {
        Instant nextEmailTime =
            previous.plusSeconds(config.minSecondsBetweenEmails);

        if (now.isBefore(nextEmailTime)) {
          logger.warn(
              "Suppressing email for full share directory, "
                  + "another email was sent for that account too recently, "
                  + "previous email time = {}, next possible email time = {}",
              previous,
              nextEmailTime);
          return;
        }
      }

      this.previousEmailTime = now;
    }

    String textContent =
        "Hello,"
            + "\n"
            + "\n"
            + "The share directory used for storing public uploads of results "
            + "files has reached capacity.  Please audit the directory and "
            + "delete old uploads or expand the configured capacity."
            + "\n"
            + "\n"
            + "Share directory capacity: " + capacityBytes + " bytes"
            + "\n"
            + "Share directory size:     " + sizeBytes + " bytes"
            + "\n"
            + "\n"
            + "-a robot";

    try {
      emailSender.sendEmail(
          /* subject= */ SHARE_DIRECTORY_FULL_SUBJECT,
          /* textContent= */ textContent,
          /* attachments= */ List.of());

    } catch (MessagingException e) {
      logger.warn("Error sending email for share directory full", e);
    }
  }

  @VisibleForTesting
  static final String SHARE_DIRECTORY_FULL_SUBJECT =
      "<tfb> <auto> Share directory full";

  /**
   * Describes whether or not an upload was successful.  Use {@link #isError()}
   * to determine whether it was a success.  If there was an error, use {@link
   * #getError()} for a message appropriate to display to the user.  Otherwise,
   * {@link #getSuccess()} describes the newly uploaded results file.
   */
  @Immutable
  public static final class ShareResultsUploadReport {
    private final @Nullable ShareResultsJsonView success;
    private final @Nullable ShareResultsErrorJsonView error;

    /**
     * Create a successful result with the specified json view.
     */
    ShareResultsUploadReport(ShareResultsJsonView success) {
      this.success = Objects.requireNonNull(success);
      this.error = null;
    }

    /**
     * Create an error result with the specified error message.
     */
    ShareResultsUploadReport(ShareResultsErrorJsonView error) {
      this.success = null;
      this.error = Objects.requireNonNull(error);
    }

    /**
     * Returns {@code true} if the upload failed and it is acceptable to call
     * {@link #getError()}.
     */
    public boolean isError() {
      return error != null;
    }

    /**
     * Returns the error message if there was an error.  This error message may
     * be displayed to the user.
     *
     * @throws NoSuchElementException if this upload was successful
     */
    public ShareResultsErrorJsonView getError() {
      if (error == null)
        throw new NoSuchElementException(
            "Cannot get error message from successful upload result.");

      return error;
    }

    /**
     * Returns information about the newly uploaded results file if the upload
     * was successful.
     *
     * @throws NoSuchElementException if this upload was not successful
     */
    public ShareResultsJsonView getSuccess() {
      if (success == null)
        throw new NoSuchElementException(
            "Cannot get success info from unsuccessful upload result.");

      return success;
    }
  }
}
