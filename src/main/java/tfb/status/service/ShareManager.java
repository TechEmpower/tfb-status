package tfb.status.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE_NEW;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import com.google.common.io.MoreFiles;
import com.google.errorprone.annotations.Immutable;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.mail.MessagingException;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.config.ShareConfig;
import tfb.status.util.FileUtils;
import tfb.status.view.Results;

/**
 * Accepts uploads of results.json files from users for sharing.
 */
@Singleton
public final class ShareManager {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final ShareConfig config;
  private final FileStore fileStore;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final EmailSender emailSender;

  @GuardedBy("emailTimeLock")
  private volatile @Nullable Instant previousEmailTime;
  private final Object emailTimeLock = new Object();

  @Inject
  public ShareManager(ShareConfig config,
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

  /**
   * Accepts a new results.json file to be shared.
   *
   * <p>This method first validates the size requirements: that the given file
   * isn't too large, and that the share directory is not full.  This method
   * then validates the contents of the file, ensuring that it de-serializes to
   * a {@link Results} object successfully, and that it contains a non-empty
   * {@link Results#testMetadata}.
   *
   * @param resultsBytes the bytes of the results.json file to be shared
   * @return an object describing the success or failure of the call
   */
  public ShareOutcome shareResults(InputStream resultsBytes)
      throws IOException {

    Objects.requireNonNull(resultsBytes);

    // We are only checking if the share directory is currently under its max
    // size, without the addition of the new file.  This reduces the complexity
    // and potentially wasted time of zipping the json file before checking if
    // it can fit in the share directory.  This is a fine compromise because it
    // means that at most the share directory will exceed the max size by just
    // one large results json file, and we will not accept further uploads after
    // that.
    long shareDirectorySize =
        FileUtils.directorySizeInBytes(fileStore.shareDirectory());

    if (shareDirectorySize >= config.maxDirectorySizeInBytes) {
      onShareDirectoryFull(
          config.maxDirectorySizeInBytes,
          shareDirectorySize);

      return new ShareOutcome(
          new ShareOutcome.Failure(
              ShareOutcome.Failure.Kind.SHARE_DIRECTORY_FULL,
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
        return new ShareOutcome(
            new ShareOutcome.Failure(
                ShareOutcome.Failure.Kind.FILE_TOO_LARGE,
                "Share uploads cannot exceed "
                    + config.maxFileSizeInBytes
                    + " bytes."));

      Results results;
      try (InputStream inputStream = Files.newInputStream(tempFile)) {
        results = objectMapper.readValue(inputStream, Results.class);
      } catch (JsonProcessingException e) {
        logger.info("Exception processing json file {}", tempFile, e);
        return new ShareOutcome(
            new ShareOutcome.Failure(
                ShareOutcome.Failure.Kind.INVALID_JSON,
                "Invalid results JSON"));
      }

      if (results.testMetadata == null || results.testMetadata.isEmpty())
        return new ShareOutcome(
            new ShareOutcome.Failure(
                ShareOutcome.Failure.Kind.MISSING_TEST_METADATA,
                "Results must contain non-empty test metadata"));

      String shareId = UUID.randomUUID().toString();
      Path sharedFile = getSharedFile(shareId);
      if (sharedFile == null)
        throw new AssertionError("This must be a valid share id: " + shareId);

      MoreFiles.createParentDirectories(sharedFile);

      try (OutputStream outputStream =
               Files.newOutputStream(sharedFile, CREATE_NEW);

           GZIPOutputStream gzipOutputStream =
               new GZIPOutputStream(outputStream)) {

        Files.copy(tempFile, gzipOutputStream);
      }

      String resultsUrl =
          config.tfbStatusOrigin
              + "/share/download/"
              + URLEncoder.encode(shareId + ".json", UTF_8);

      String visualizeResultsUrl =
          config.tfbWebsiteOrigin
              + "/benchmarks/#section=test&shareid="
              + URLEncoder.encode(shareId, UTF_8);

      return new ShareOutcome(
          new ShareOutcome.Success(
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
   * @throws IllegalArgumentException if the specified string could never be the
   *         share id for any results
   */
  public @Nullable ByteSource findSharedResults(String shareId) {
    Objects.requireNonNull(shareId);

    Path sharedFile = getSharedFile(shareId);
    if (sharedFile == null)
      throw new IllegalArgumentException("Invalid share id: " + shareId);

    if (!Files.isRegularFile(sharedFile))
      return null;

    return new ByteSource() {
      @Override
      public InputStream openStream() throws IOException {
        return new GZIPInputStream(Files.newInputStream(sharedFile));
      }
    };
  }

  /**
   * Returns the path to the results.json file with the specified share id, or
   * {@code null} if the id is not a possible share id.  The returned file
   * exists if those shared results exist.
   *
   * @param shareId the id of the shared results
   * @return the file containing the shared results hash, or {@code null} if the
   *         share id is invalid
   */
  private @Nullable Path getSharedFile(String shareId) {
    return resolveChildPath(fileStore.shareDirectory(), shareId + ".json.gz");
  }

  private static @Nullable Path resolveChildPath(Path directory,
                                                 String fileName) {
    Objects.requireNonNull(directory);
    Objects.requireNonNull(fileName);

    Path child;
    try {
      child = directory.resolve(fileName);
    } catch (InvalidPathException ignored) {
      return null;
    }

    if (!child.equals(child.normalize()))
      return null;

    if (!directory.equals(child.getParent()))
      return null;

    return child;
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
   * Describes whether or not an attempt to share a results.json file succeeded.
   * Use {@link #isFailure()} to determine whether this attempt failed.  If this
   * was a failure, use {@link #getFailure()} for a message appropriate to
   * display to the user.  Otherwise, {@link #getSuccess()} describes the newly
   * shared results.json file.
   */
  @Immutable
  public static final class ShareOutcome {
    private final @Nullable Success success;
    private final @Nullable Failure failure;

    /**
     * Constructs a successful outcome.
     */
    ShareOutcome(Success success) {
      this.success = Objects.requireNonNull(success);
      this.failure = null;
    }

    /**
     * Constructs a failed outcome.
     */
    ShareOutcome(Failure failure) {
      this.success = null;
      this.failure = Objects.requireNonNull(failure);
    }

    /**
     * Returns {@code true} if this attempt to share a results.json file failed.
     */
    public boolean isFailure() {
      return failure != null;
    }

    /**
     * Returns information about why this attempt to share a results.json file
     * failed.
     *
     * @throws NoSuchElementException if this share succeeded
     */
    public Failure getFailure() {
      if (failure == null)
        throw new NoSuchElementException("This share attempt succeeded");

      return failure;
    }

    /**
     * Returns information about the successfully shared results.json file.
     *
     * @throws NoSuchElementException if this share failed
     */
    public Success getSuccess() {
      if (success == null)
        throw new NoSuchElementException("This share attempt failed");

      return success;
    }

    /**
     * A view of a results.json file that was successfully shared by a user.
     */
    @Immutable
    public static final class Success {
      /**
       * The unique id for these shared results.
       */
      public final String shareId;

      /**
       * The absolute URL for viewing these shared results as JSON on this
       * website.
       */
      public final String resultsUrl;

      /**
       * The absolute URL for visualizing these shared results on the TFB
       * website.
       */
      public final String visualizeResultsUrl;

      @JsonCreator
      public Success(
          @JsonProperty(value = "shareId", required = true)
          String shareId,

          @JsonProperty(value = "resultsUrl", required = true)
          String resultsUrl,

          @JsonProperty(value = "visualizeResultsUrl", required = true)
          String visualizeResultsUrl) {

        this.shareId = Objects.requireNonNull(shareId);
        this.resultsUrl = Objects.requireNonNull(resultsUrl);
        this.visualizeResultsUrl = Objects.requireNonNull(visualizeResultsUrl);
      }
    }

    /**
     * A view of a failed attempt by a user to share a results.json file.
     */
    @Immutable
    public static final class Failure {
      /**
       * The kind of failure that occurred.
       */
      public final Kind kind;

      /**
       * A message describing why the results could not be shared, which may be
       * displayed directly to the user.
       */
      public final String message;

      @JsonCreator
      public Failure(
          @JsonProperty(value = "kind", required = true)
          Kind kind,

          @JsonProperty(value = "message", required = true)
          String message) {

        this.kind = Objects.requireNonNull(kind);
        this.message = Objects.requireNonNull(message);
      }

      /**
       * A kind of failure that prevents results from being shared.
       */
      public enum Kind {
        /**
         * The results cannot be shared because the share directory has reached
         * its {@linkplain ShareConfig#maxDirectorySizeInBytes maximum size}.
         */
        SHARE_DIRECTORY_FULL,

        /**
         * The results cannot be shared because the results.json file exceeds
         * the {@linkplain ShareConfig#maxFileSizeInBytes maximum size} for
         * individual files.
         */
        FILE_TOO_LARGE,

        /**
         * The results cannot be shared because its {@link Results#testMetadata}
         * is {@code null} or empty.
         */
        MISSING_TEST_METADATA,

        /**
         * The results cannot be shared because the results.json file isn't a
         * valid JSON encoding of {@link Results}.
         */
        INVALID_JSON
      }
    }
  }
}
