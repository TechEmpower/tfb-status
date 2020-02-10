package tfb.status.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.ByteStreams;
import com.google.common.io.MoreFiles;
import com.google.errorprone.annotations.Immutable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.config.FileStoreConfig;
import tfb.status.config.UrlsConfig;
import tfb.status.util.FileUtils;
import tfb.status.util.ZipFiles;
import tfb.status.view.Results;
import tfb.status.view.ShareResultsErrorJsonView;
import tfb.status.view.ShareResultsJsonView;

/**
 * Accepts uploads of results.json files from users for sharing.  This class
 * provides several ways of creating new shared results files in the {@link
 * FileStore#shareDirectory()}.  All access to that directory should be done
 * through this service.
 */
@Singleton
public final class ShareResultsUploader {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final FileStoreConfig fileStoreConfig;
  private final UrlsConfig urlsConfig;
  private final FileStore fileStore;
  private final ObjectMapper objectMapper;
  private final ShareResultsMailer shareResultsMailer;

  @Inject
  public ShareResultsUploader(FileStoreConfig fileStoreConfig,
                              UrlsConfig urlsConfig,
                              FileStore fileStore,
                              ObjectMapper objectMapper,
                              ShareResultsMailer shareResultsMailer) {

    this.fileStoreConfig = Objects.requireNonNull(fileStoreConfig);
    this.urlsConfig = Objects.requireNonNull(urlsConfig);
    this.fileStore = Objects.requireNonNull(fileStore);
    this.objectMapper = Objects.requireNonNull(objectMapper);
    this.shareResultsMailer = Objects.requireNonNull(shareResultsMailer);
  }

  /**
   * Accepts an upload of a results.json file containing the specified bytes.
   *
   * <p>This method first validates the size requirements: that the given file
   * isn't too large, and that the share directory is not full.  This method
   * then validates the contents of the file, ensuring that it de-serializes to
   * a {@link Results} object successfully, and that it contains a non-empty
   * {@link Results#testMetadata}.
   *
   * @param fileBytes the raw bytes of the results.json file
   * @return an object describing the success or failure of the upload
   */
  public ShareResultsUploadReport upload(InputStream fileBytes)
      throws IOException {

    Objects.requireNonNull(fileBytes);

    // We are only checking if the share directory is currently under its max
    // size, without the addition of the new file.  This reduces the complexity
    // and potentially wasted time of compressing the json file to a zip file
    // before checking if it can fit in the share directory.  This is a fine
    // compromise because it means that at most the share directory will exceed
    // the max size by just one large results json file, and we will not accept
    // further uploads after that.
    long shareDirectorySize =
        FileUtils.directorySizeInBytes(fileStore.shareDirectory());

    if (shareDirectorySize >= fileStoreConfig.maxShareDirectorySizeBytes) {
      shareResultsMailer.onShareDirectoryFull(
          fileStoreConfig.maxShareDirectorySizeBytes,
          shareDirectorySize);

      return new ShareResultsUploadReport(
          new ShareResultsErrorJsonView(
              "Share uploads has reached max capacity."));
    }

    Path tempFile =
        Files.createTempFile(
            /* prefix= */ "TFB_Share_Upload",
            /* suffix= */ ".json");

    try {
      InputStream limitedBytes =
          ByteStreams.limit(
              fileBytes,
              fileStoreConfig.maxShareFileSizeBytes + 1);

      long fileSize;
      try (OutputStream outputStream =
               Files.newOutputStream(tempFile, WRITE, APPEND)) {
        fileSize = limitedBytes.transferTo(outputStream);
      }

      if (fileSize > fileStoreConfig.maxShareFileSizeBytes)
        return new ShareResultsUploadReport(
            new ShareResultsErrorJsonView(
                "Share uploads cannot exceed "
                    + fileStoreConfig.maxShareFileSizeBytes
                    + " bytes."));

      Results results;
      try (InputStream inputStream = Files.newInputStream(tempFile)) {
        results = objectMapper.readValue(inputStream, Results.class);
      } catch (JsonProcessingException e) {
        logger.info("Exception processing json file {}", tempFile, e);
        return new ShareResultsUploadReport(
            new ShareResultsErrorJsonView("Invalid results JSON"));
      }

      if (results.testMetadata == null || results.testMetadata.isEmpty())
        return new ShareResultsUploadReport(
            new ShareResultsErrorJsonView(
                "Results must contain non-empty test metadata"));

      String shareId = UUID.randomUUID().toString();
      String fileName = shareId + ".json";
      String zipFileName = shareId + ".zip";

      Path permanentFile = fileStore.shareDirectory().resolve(zipFileName);
      MoreFiles.createParentDirectories(permanentFile);

      try (FileSystem zipFs =
               FileSystems.newFileSystem(
                   permanentFile,
                   Map.of("create", "true"))) {

        // Create a single entry in the zip file for the json file.
        Path entry = zipFs.getPath(fileName);

        try (InputStream inputStream = Files.newInputStream(tempFile);
             OutputStream outputStream =
                 Files.newOutputStream(entry, CREATE_NEW)) {
          inputStream.transferTo(outputStream);
        }
      }

      String resultsUrl =
          urlsConfig.tfbStatus
              + "/share-results/view/"
              + URLEncoder.encode(fileName, UTF_8);

      String visualizeResultsUrl =
          urlsConfig.teWeb
              + "/benchmarks/#section=test&shareid="
              + URLEncoder.encode(shareId, UTF_8);

      return new ShareResultsUploadReport(
          new ShareResultsJsonView(
              /* fileName= */ fileName,
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
   * @param jsonFileName the requested json file name, of the form
   *        "47f93e49-2ffe-4b8e-828a-25513b7d160e.json"
   * @param ifPresent a consumer to be called with the path to the zip file
   *        entry for the json file
   * @param ifAbsent a runnable that is invoked if the upload is not found
   * @throws IOException if an error occurs reading or consuming the zip file
   */
  public void getUpload(String jsonFileName,
                        ShareResultsConsumer ifPresent,
                        Runnable ifAbsent)
      throws IOException {

    Objects.requireNonNull(jsonFileName);
    Objects.requireNonNull(ifPresent);
    Objects.requireNonNull(ifAbsent);

    Matcher matcher = JSON_FILE_PATTERN.matcher(jsonFileName);
    if (!matcher.matches()) {
      ifAbsent.run();
      return;
    }

    String shareId = matcher.group(1);
    String zipFileName = shareId + ".zip";
    Path zipFile = fileStore.shareDirectory().resolve(zipFileName);

    if (!Files.isRegularFile(zipFile)) {
      ifAbsent.run();
      return;
    }

    ZipFiles.findZipEntry(
        /* zipFile= */
        zipFile,

        /* entryPath= */
        jsonFileName,

        /* ifPresent= */
        (Path zipEntry) -> {
          if (Files.isRegularFile(zipEntry))
            ifPresent.accept(zipEntry);
          else
            ifAbsent.run();
        },

        /* ifAbsent= */
        ifAbsent);
  }

  private static final Pattern JSON_FILE_PATTERN =
      Pattern.compile("^([^./]+)(\\.json)");

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

  /**
   * A consumer to be called with the path to the zip file entry for the json
   * file.  If this is invoked, the specified path is guaranteed to exist and
   * to refer to the requested json file, meaning it can be read without further
   * checking.
   *
   * <p>This interface should only be used by callers of {@link
   * #getUpload(String, ShareResultsConsumer, Runnable)}.
   */
  @FunctionalInterface
  public interface ShareResultsConsumer {
    void accept(Path zipEntry) throws IOException;
  }
}
