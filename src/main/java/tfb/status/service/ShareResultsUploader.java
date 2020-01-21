package tfb.status.service;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.ByteStreams;
import com.google.common.io.MoreFiles;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.config.FileStoreConfig;
import tfb.status.config.UrlsConfig;
import tfb.status.util.ZipFiles;
import tfb.status.view.Results;
import tfb.status.view.ShareResultsJsonView;

import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.undertow.util.Headers.CONTENT_TYPE;
import static io.undertow.util.StatusCodes.NOT_FOUND;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.regex.Pattern.CASE_INSENSITIVE;

/**
 * A service responsible for handling public uploads of results.json files.
 * This class provides several ways of creating new shared results files in
 * the {@link FileStore#shareDirectory()}. All access to that directory
 * should be done through this service.
 */
@Singleton
public class ShareResultsUploader {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final FileStoreConfig fileStoreConfig;
  private final UrlsConfig urlsConfig;
  private final FileStore fileStore;
  private final ObjectMapper objectMapper;

  @Inject
  public ShareResultsUploader(FileStoreConfig fileStoreConfig,
                              UrlsConfig urlsConfig,
                              FileStore fileStore,
                              ObjectMapper objectMapper) {
    this.fileStoreConfig = Objects.requireNonNull(fileStoreConfig);
    this.urlsConfig = Objects.requireNonNull(urlsConfig);
    this.fileStore = Objects.requireNonNull(fileStore);
    this.objectMapper = Objects.requireNonNull(objectMapper);
  }

  /**
   * Upload the given raw results JSON string. Creates a {@link ReadableByteChannel}
   * from the string and passes it to {@link #upload(ReadableByteChannel)}.
   *
   * @param resultsJson The raw results JSON.
   * @see #upload(ReadableByteChannel)
   */
  public ShareResultsJsonView upload(String resultsJson)
          throws IOException, ShareResultsUploadException {
    Objects.requireNonNull(resultsJson);

    try (InputStream is = new ByteArrayInputStream(resultsJson.getBytes(UTF_8));
         ReadableByteChannel in = Channels.newChannel(is)) {
      return upload(in);
    }
  }

  /**
   * Upload the given byte channel containing the raw results JSON. Creates
   * a temporary file from the bytes and passes it to {@link #upload(Path)}.
   *
   * @param in The byte channel containing the raw results JSON.
   * @see #upload(Path)
   */
  public ShareResultsJsonView upload(ReadableByteChannel in)
      throws IOException, ShareResultsUploadException {
    Objects.requireNonNull(in);

    // Copy the input to a temporary file.
    Path tempFile = Files.createTempFile(/* prefix= */ "TFB_Share_Upload",
        /* suffix= */ ".json");

    try (WritableByteChannel out =
             Files.newByteChannel(tempFile, WRITE, APPEND)) {
      ByteStreams.copy(in, out);
    }

    return upload(tempFile);
  }

  /**
   * Upload the given file containing raw results JSON. This first validates the
   * size requirements: that the given file isn't too large, and that the share
   * directory is not full. This method then validates the contents of the file,
   * ensuring that it de-serializes to a {@link Results} object successfully,
   * and that it contains a non-empty {@link Results#testMetadata}. If
   * validation fails a {@link ShareResultsUploadException} is thrown. Otherwise
   * this will create a new zip file in the {@link FileStore#shareDirectory()}
   * containing the JSON file and return information about it.
   *
   * @param tempFile The file containing raw results JSON. After this method returns,
   *                 this file is guaranteed to be deleted.
   * @return A view containing information about the new file if validation passes
   * and the new zip file was successfully created.
   * @throws IOException If any network errors occur.
   * @throws ShareResultsUploadException If validation of the JSON fails.
   */
  public ShareResultsJsonView upload(Path tempFile)
      throws IOException, ShareResultsUploadException {
    Objects.requireNonNull(tempFile);

    try {
      validateUploadSize(tempFile.toFile().length());
      String validationError = validateNewFile(tempFile);
      if (validationError != null) {
        throw new ShareResultsUploadException(validationError);
      }

      String shareId = UUID.randomUUID().toString();
      String fileName = shareId + ".json";
      String zipFileName = shareId + ".zip";

      Path permanentFile = fileStore.shareDirectory().resolve(zipFileName);
      MoreFiles.createParentDirectories(permanentFile);

      try (FileOutputStream fos = new FileOutputStream(permanentFile.toFile());
           ZipOutputStream zos = new ZipOutputStream(fos);
           InputStream in = Files.newInputStream(tempFile)) {
        // Create a single entry in the zip file for the json file.
        ZipEntry zipEntry = new ZipEntry(fileName);
        zos.putNextEntry(zipEntry);
        ByteStreams.copy(in, zos);
        zos.closeEntry();
      }

      String resultsUrl =
          urlsConfig.tfbStatus
              + "/share-results/"
              + URLEncoder.encode(fileName, UTF_8);
      String visualizeResultsUrl =
          urlsConfig.teWeb
              + "/benchmarks/#section=test&shareid="
              + URLEncoder.encode(shareId, UTF_8);

      return new ShareResultsJsonView(
          /* fileName= */ fileName,
          /* resultsUrl= */ resultsUrl,
          /* visualizeResultsUrl= */ visualizeResultsUrl);
    } finally {
      Files.delete(tempFile);
    }
  }

  /**
   * Return null if the JSON file successfully de-serializes to {@link Results}
   * and has non-empty {@link Results#testMetadata}. Otherwise returns a relevant
   * error message.
   */
  private @Nullable String validateNewFile(Path newJsonFile) {
    Objects.requireNonNull(newJsonFile);

    try (InputStream inputStream = Files.newInputStream(newJsonFile)) {
      Results results = objectMapper.readValue(inputStream, Results.class);

      if (results.testMetadata == null || results.testMetadata.isEmpty()) {
        return "Results must contain non-empty test metadata";
      }
    } catch (JsonProcessingException e) {
      logger.warn("Exception processing json file {}", newJsonFile, e);
      return "Invalid results JSON";
    } catch (IOException e) {
      logger.warn("Exception reading json file {}", newJsonFile, e);
      return "Error reading results.";
    }

    return null;
  }

  /**
   * Ensure that a results file of the given size isn't too big, and
   * that the share directory is not full.
   *
   * @param resultsJsonSizeBytes The size of the results file to be uploaded,
   *                             in bytes.
   * @throws ShareResultsUploadException If the file is too large. or the
   * share directory is at capacity.
   */
  private void validateUploadSize(long resultsJsonSizeBytes)
      throws ShareResultsUploadException {
    if (resultsJsonSizeBytes > fileStoreConfig.maxShareFileSizeBytes) {
      throw new ShareResultsUploadException("Share uploads cannot exceed "
          + fileStoreConfig.maxShareFileSizeBytes + " bytes.");
    }

    // We are only checking if the share directory is currently under its max
    // size, without the addition of the new file. This reduces the complexity
    // and potentially wasted time of compressing the json file to a zip file
    // before checking if it can fit in the share directory. This is a fine
    // compromise because it means that at most the share directory will exceed
    // the max size by just one large results json file, and we will not accept
    // further uploads after that.
    long shareDirectorySize = FileStore.directorySizeBytes(
        fileStore.shareDirectory().toFile());
    if (shareDirectorySize >= fileStoreConfig.maxShareDirectorySizeBytes) {
      throw new ShareResultsUploadException("Share uploads has reached max capacity.");
    }
  }

  /**
   * Read an uploaded results file from the share directory of the specified name.
   * This is intended to read files created through one of this class's upload
   * methods. Results file uploads are stored in zip files of the same name, and
   * should always be modified or accessed through this class.
   *
   * @param jsonFileName The requested json file name, of the form
   *                     "47f93e49-2ffe-4b8e-828a-25513b7d160e.json".
   * @param ifPresent A consumer to be called with the path to the zip file entry
   *                  for the json file. If this is invoked, the given path is
   *                  guaranteed to exist and point to the requested json file,
   *                  meaning it can be read without further checking.
   * @param ifAbsent A runnable that is invoked if the upload cannot be found for
   *                 any reason.
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

    Matcher matcher = UUID_JSON_FILE.matcher(jsonFileName);
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

  private static final Pattern UUID_JSON_FILE = Pattern.compile("^([^./]+)(\\.json)");

  /**
   * An exception indicating that an error occurred during share file upload.
   * The {@link #getMessage()} should be a human-readable message indicating
   * the reason.
   */
  public static class ShareResultsUploadException extends Exception {
    ShareResultsUploadException(String message) {
      super(message);
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
