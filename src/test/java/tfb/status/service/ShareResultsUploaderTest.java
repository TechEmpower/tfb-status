package tfb.status.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.CharSource;
import com.google.common.io.MoreFiles;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.config.FileStoreConfig;
import tfb.status.testlib.ResultsTester;
import tfb.status.testlib.TestServicesInjector;
import tfb.status.view.Results;
import tfb.status.view.ShareResultsJsonView;

/**
 * Tests for {@link ShareResultsUploader}.
 */
@ExtendWith(TestServicesInjector.class)
public final class ShareResultsUploaderTest {
  /**
   * Ensure that uploading a valid results json file succeeds. This validates
   * the returned data, ensuring that the information is as expected based on
   * the test_config. This also ensures that the uploader created a zip file
   * with the expected name in the expected directory. It then uses the uploader
   * to read the uploaded file and ensures that it exactly equals the original
   * file.
   */
  @Test
  public void shareResultsUploader_upload(
      ShareResultsUploader shareResultsUploader,
      FileStore fileStore) throws IOException {

    Path resultsJson =
        fileStore.resultsDirectory().resolve(
            "results.2019-12-11-13-21-02-404.json");

    ShareResultsUploader.ShareResultsUploadReport report;
    try (InputStream in = Files.newInputStream(resultsJson)) {
      report = shareResultsUploader.upload(in);
    }

    // The upload should have succeeded.
    assertFalse(report.isError());
    assertThrows(
        IllegalStateException.class,
        () -> report.getErrorMessage());

    ShareResultsJsonView jsonView = report.getSuccess();

    // Validate the basics of the info returned.
    assertTrue(jsonView.fileName.endsWith(".json"));
    assertTrue(
        jsonView.resultsUrl.startsWith(
            "https://test.tfb-status.techempower.com/share-results/view/"));
    assertTrue(
        jsonView.visualizeResultsUrl.startsWith(
            "https://www.test.techempower.com/benchmarks/"));

    // Ensure the uploader created a zip file in the expected directory with the
    // expected name.
    String shareId = MoreFiles.getNameWithoutExtension(
        Paths.get(jsonView.fileName));
    assertTrue(
        Files.exists(fileStore.shareDirectory().resolve(shareId + ".zip")));

    AtomicBoolean zipEntryPresent = new AtomicBoolean(false);
    shareResultsUploader.getUpload(
        /* jsonFileName= */ jsonView.fileName,
        /* ifPresent= */ (Path zipEntry) -> {
          zipEntryPresent.set(true);
          // The uploaded file should contain the same exact bytes as was given.
          assertTrue(MoreFiles.equal(resultsJson, zipEntry));
        },
        /* ifAbsent= */ () -> {});

    assertTrue(zipEntryPresent.get());
  }

  /**
   * Ensure that the uploader returns the correct error message when given
   * invalid json.
   */
  @Test
  public void shareResultsUploader_rejectInvalidJson(
      ShareResultsUploader shareResultsUploader) throws IOException {

    ShareResultsUploader.ShareResultsUploadReport report;
    try (InputStream in =
             CharSource.wrap("foo").asByteSource(UTF_8).openStream()) {
      report = shareResultsUploader.upload(in);
    }

    assertTrue(report.isError());

    assertThrows(
        IllegalStateException.class,
        () -> report.getSuccess());

    assertEquals("Invalid results JSON", report.getErrorMessage());
  }

  /**
   * Ensure that the uploader returns the correct error message when given
   * results that don't have test metadata.
   */
  @Test
  public void shareResultsUploader_rejectMissingTestMetadata(
      ShareResultsUploader shareResultsUploader,
      FileStore fileStore,
      FileSystem fileSystem,
      ResultsTester resultsTester,
      ObjectMapper objectMapper) throws IOException {

    Path oldJsonFile =
        fileStore.resultsDirectory()
            .resolve("results.2019-12-11-13-21-02-404.json");

    Results oldResults;
    try (InputStream inputStream = Files.newInputStream(oldJsonFile)) {
      oldResults = objectMapper.readValue(inputStream, Results.class);
    }

    Results results = new Results(
        /* uuid= */ oldResults.uuid,
        /* name= */ oldResults.name,
        /* environmentDescription= */ oldResults.environmentDescription,
        /* startTime= */ oldResults.startTime,
        /* completionTime= */ oldResults.completionTime,
        /* duration= */ oldResults.duration,
        /* frameworks= */ oldResults.frameworks,
        /* completed= */ oldResults.completed,
        /* succeeded= */ oldResults.succeeded,
        /* failed= */ oldResults.failed,
        /* rawData= */ oldResults.rawData,
        /* queryIntervals= */ oldResults.queryIntervals,
        /* concurrencyLevels= */ oldResults.concurrencyLevels,
        /* git= */ oldResults.git,
        /* testMetadata= */ null);

    Path jsonFile = fileSystem.getPath("results_to_share.json");
    resultsTester.saveJsonToFile(results, jsonFile);

    ShareResultsUploader.ShareResultsUploadReport report =
        shareResultsUploader.upload(jsonFile);

    assertTrue(report.isError());

    assertEquals(
        "Results must contain non-empty test metadata",
        report.getErrorMessage());
  }

  /**
   * Ensure that the uploader returns the correct error message when the given
   * results file is too large.
   */
  @Test
  public void shareResultsUploader_uploadRejectLargeFileSize(
      FileStoreConfig fileStoreConfig,
      ShareResultsUploader shareResultsUploader) throws IOException {

    // Create an input stream that will generate 1 more byte than the configured
    // max file size. It outputs the letter "a" until it has given the right
    // number of bytes without creating a large and unnecessary backing array in
    // memory.
    InputStream is = new FixedLengthStaticInputStream(
        fileStoreConfig.maxShareFileSizeBytes + 1);

    ShareResultsUploader.ShareResultsUploadReport report =
        shareResultsUploader.upload(is);

    assertTrue(report.isError());

    assertEquals(
        "Share uploads cannot exceed "
            + fileStoreConfig.maxShareFileSizeBytes
            + " bytes.",
        report.getErrorMessage());
  }

  /**
   * Ensure that the uploader returns the correct error message when the share
   * directory is at capacity.
   */
  @Test
  public void shareResultsUploader_uploadRejectWhenShareDirectoryFull(
      FileStoreConfig fileStoreConfig,
      FileStore fileStore,
      ShareResultsUploader shareResultsUploader) throws IOException {

    // Make a new large directory in the share directory so that it is above
    // its maximum configured size.
    Path directory = fileStore.shareDirectory().resolve("full_directory");
    Files.createDirectory(directory);

    try {
      long fileSize = fileStoreConfig.maxShareFileSizeBytes;
      long directorySize = 0;

      while (directorySize < fileStoreConfig.maxShareDirectorySizeBytes) {
        Path file = directory.resolve(UUID.randomUUID().toString() + ".txt");

        try (InputStream in = new FixedLengthStaticInputStream(fileSize);
             OutputStream out =
                 Files.newOutputStream(file, CREATE_NEW, WRITE)) {
          in.transferTo(out);
        }

        directorySize += fileSize;
      }

      ShareResultsUploader.ShareResultsUploadReport report =
          shareResultsUploader.upload(new FixedLengthStaticInputStream(1000));

      assertTrue(report.isError());

      assertEquals(
          "Share uploads has reached max capacity.", report.getErrorMessage());
    } finally {
      MoreFiles.deleteDirectoryContents(directory);
      Files.delete(directory);
    }
  }

  /**
   * Ensure that the ifAbsent callback is invoked when getting an upload that
   * does not exist.
   */
  @Test
  public void shareResultsUploader_getUploadRejectMissingFile(
      ShareResultsUploader shareResultsUploader,
      FileStore fileStore) throws IOException {

    String shareId;
    do {
      shareId = UUID.randomUUID().toString();
    } while (
        Files.exists(fileStore.shareDirectory().resolve(shareId + ".zip")));

    AtomicBoolean ifAbsentCalled = new AtomicBoolean(false);

    shareResultsUploader.getUpload(
        /* jsonFileName= */ shareId + ".json",
        /* ifPresent= */ (Path zipEntry) -> {},
        /* ifAbsent= */ () -> ifAbsentCalled.set(true));

    assertTrue(ifAbsentCalled.get());
  }

  /**
   * Ensure that the ifAbsent callback is invoked when getting an upload for an
   * invalid file name.
   */
  @Test
  public void shareResultUploader_getUploadInvalidFile(
      ShareResultsUploader shareResultsUploader) throws IOException {
    AtomicBoolean ifAbsentCalled = new AtomicBoolean(false);

    shareResultsUploader.getUpload(
        /* jsonFileName= */ "not-a-json-file.txt",
        /* ifPresent= */ (Path zipEntry) -> {},
        /* ifAbsent= */ () -> ifAbsentCalled.set(true));

    assertTrue(ifAbsentCalled.get());
  }

  /**
   * An input stream that will generate the configured number of bytes. It
   * outputs the letter "a" until it has given the right number of bytes without
   * creating a large and unnecessary backing array in memory.
   */
  private static final class FixedLengthStaticInputStream extends InputStream {
    private final long count;
    private int data = ("a".getBytes(UTF_8)[0] & 0xff);
    private int pos = -1;

    /**
     * @param count The number of bytes to output via {@link #read()}.
     */
    FixedLengthStaticInputStream(long count) {
      this.count = count;
    }

    @Override
    public int read() {
      pos++;
      return (pos < count) ? data : -1;
    }
  }
}
