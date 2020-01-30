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
import tfb.status.view.ShareResultsJsonView;

/**
 * A service responsible for handling public uploads of results.json files.
 * This class provides several ways of creating new shared results files in
 * the {@link FileStore#shareDirectory()}. All access to that directory
 * should be done through this service.
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
   * Upload the given input stream containing the raw results JSON. Creates a
   * temporary file from the bytes and passes it to {@link #upload(Path)}.
   *
   * @param in The input stream containing the raw results JSON.
   * @see #upload(Path)
   */
  public ShareResultsUploadReport upload(InputStream in) throws IOException {
    Objects.requireNonNull(in);

    // Copy the input to a temporary file.
    Path tempFile = Files.createTempFile(/* prefix= */ "TFB_Share_Upload",
        /* suffix= */ ".json");

    try (OutputStream out =
             Files.newOutputStream(tempFile, WRITE, APPEND)) {
      ByteStreams.copy(in, out);
    }

    return upload(tempFile);
  }

  /**
   * Upload the given file containing raw results JSON. This first validates the
   * size requirements: that the given file isn't too large, and that the share
   * directory is not full. This method then validates the contents of the file,
   * ensuring that it de-serializes to a {@link Results} object successfully,
   * and that it contains a non-empty {@link Results#testMetadata}.
   *
   * @param tempFile The file containing raw results JSON. After this method
   *                 returns, this file is guaranteed to be deleted.
   * @return A new {@link ShareResultsUploadReport} instance containing either
   * error or success information.
   * @throws IOException If any network errors occur.
   */
  public ShareResultsUploadReport upload(Path tempFile) throws IOException {
    Objects.requireNonNull(tempFile);

    try {
      String sizeError = validateUploadSize(Files.size(tempFile));
      if (sizeError != null) {
        return new ShareResultsUploadReport(sizeError);
      }
      String validationError = validateNewFile(tempFile);
      if (validationError != null) {
        return new ShareResultsUploadReport(validationError);
      }

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
        try (InputStream in = Files.newInputStream(tempFile);
             OutputStream out = Files.newOutputStream(entry, CREATE_NEW)) {
          in.transferTo(out);
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

      ShareResultsJsonView success = new ShareResultsJsonView(
          /* fileName= */ fileName,
          /* resultsUrl= */ resultsUrl,
          /* visualizeResultsUrl= */ visualizeResultsUrl);
      return new ShareResultsUploadReport(success);
    } finally {
      Files.delete(tempFile);
    }
  }

  /**
   * Returns null if the JSON file successfully de-serializes to {@link Results}
   * and has non-empty {@link Results#testMetadata}. Otherwise returns a
   * relevant error message.
   */
  private @Nullable String validateNewFile(Path newJsonFile)
      throws IOException {
    Objects.requireNonNull(newJsonFile);

    try (InputStream inputStream = Files.newInputStream(newJsonFile)) {
      Results results = objectMapper.readValue(inputStream, Results.class);

      if (results.testMetadata == null || results.testMetadata.isEmpty()) {
        return "Results must contain non-empty test metadata";
      }
    } catch (JsonProcessingException e) {
      logger.warn("Exception processing json file {}", newJsonFile, e);
      return "Invalid results JSON";
    }

    return null;
  }

  /**
   * Ensure that a results file of the given size isn't too big, and that the
   * share directory is not full. Returns null upon success, or a relevant error
   * message.
   */
  private @Nullable String validateUploadSize(long resultsJsonSizeBytes)
      throws IOException {
    if (resultsJsonSizeBytes > fileStoreConfig.maxShareFileSizeBytes) {
      return "Share uploads cannot exceed "
          + fileStoreConfig.maxShareFileSizeBytes + " bytes.";
    }

    // We are only checking if the share directory is currently under its max
    // size, without the addition of the new file. This reduces the complexity
    // and potentially wasted time of compressing the json file to a zip file
    // before checking if it can fit in the share directory. This is a fine
    // compromise because it means that at most the share directory will exceed
    // the max size by just one large results json file, and we will not accept
    // further uploads after that.
    long shareDirectorySize = FileUtils.directorySizeBytes(
        fileStore.shareDirectory());
    if (shareDirectorySize >= fileStoreConfig.maxShareDirectorySizeBytes) {
      shareResultsMailer.onShareDirectoryFull(
          fileStoreConfig.maxShareDirectorySizeBytes, shareDirectorySize);
      return "Share uploads has reached max capacity.";
    }

    return null;
  }

  /**
   * Read an uploaded results file from the share directory of the specified
   * name. This is intended to read files created through one of this class's
   * upload methods. Results file uploads are stored in zip files of the same
   * name, and should always be modified or accessed through this class.
   *
   * @param jsonFileName The requested json file name, of the form
   *                     "47f93e49-2ffe-4b8e-828a-25513b7d160e.json".
   * @param ifPresent A consumer to be called with the path to the zip file
   *                  entry for the json file. If this is invoked, the given
   *                  path is guaranteed to exist and point to the requested
   *                  json file, meaning it can be read without further
   *                  checking.
   * @param ifAbsent A runnable that is invoked if the upload cannot be found
   *                 for any reason.
   * @throws IOException If an error occurs reading or consuming the zip file.
   * @see #upload(Path)
   */
  public void getUpload(String jsonFileName,
                        ShareResultsConsumer ifPresent,
                        Runnable ifAbsent)
      throws IOException {

    Objects.requireNonNull(jsonFileName);
    Objects.requireNonNull(ifPresent);
    Objects.requireNonNull(ifAbsent);

    Matcher matcher = JSON_FILE.matcher(jsonFileName);
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
        /* zipFile= */ zipFile,
        /* entryPath= */ jsonFileName,
        /* ifPresent= */ (Path zipEntry) -> {
          if (Files.isRegularFile(zipEntry)) {
            ifPresent.accept(zipEntry);
          } else {
            ifAbsent.run();
          }
        },
        /* ifAbsent= */ ifAbsent);
  }

  private static final Pattern JSON_FILE =
      Pattern.compile("^([^./]+)(\\.json)");

  /**
   * Holds information about whether or not an upload was successful. Use
   * {@link #isError()} to determine whether it was a success. If there was an
   * error, use {@link #getErrorMessage()} for a message appropriate for showing
   * to the user. Otherwise, {@link #getSuccess()} gives information about the
   * newly uploaded results file.
   */
  @Immutable
  public static final class ShareResultsUploadReport {
    private final @Nullable ShareResultsJsonView success;
    private final @Nullable String errorMessage;

    /**
     * Create a successful result with the specified json view.
     */
    ShareResultsUploadReport(ShareResultsJsonView success) {
      this.success = Objects.requireNonNull(success);
      this.errorMessage = null;
    }

    /**
     * Create an error result with the specified error message.
     */
    ShareResultsUploadReport(String errorMessage) {
      this.errorMessage = Objects.requireNonNull(errorMessage);
      this.success = null;
    }

    /**
     * @return {@code true} if the upload failed and you should call
     * {@link #getErrorMessage()}.
     */
    public boolean isError() {
      return errorMessage != null;
    }

    /**
     * @return The error message if there was an error, intended to be shown
     * to the user.
     */
    public String getErrorMessage() {
      if (errorMessage == null) {
        throw new IllegalStateException(
            "Cannot get error message from successful upload result.");
      }
      return errorMessage;
    }

    /**
     * @return Information about the newly uploaded results file if the upload
     * was successful.
     */
    public ShareResultsJsonView getSuccess() {
      if (success == null) {
        throw new IllegalStateException(
            "Cannot get success info from unsuccessful upload result.");
      }
      return success;
    }
  }

  /**
   * A consumer to be called with the path to the zip file entry for the json
   * file. If this is invoked, the given path is guaranteed to exist and
   * point to the requested json file, meaning it can be read without further
   * checking.
   */
  @FunctionalInterface
  public interface ShareResultsConsumer {
    void accept(Path zipEntry) throws IOException;
  }
}
