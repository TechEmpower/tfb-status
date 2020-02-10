package tfb.status.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static tfb.status.testlib.MoreAssertions.assertStartsWith;

import com.google.common.base.Ascii;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharSource;
import com.google.common.io.MoreFiles;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import tfb.status.config.FileStoreConfig;
import tfb.status.service.ShareResultsUploader.ShareResultsUploadReport;
import tfb.status.testlib.ResultsTester;
import tfb.status.testlib.TestServicesInjector;
import tfb.status.view.Results;
import tfb.status.view.ShareResultsJsonView;

/**
 * Tests for {@link ShareResultsUploader}.
 */
@Execution(ExecutionMode.SAME_THREAD) // currently not parallelizable
@ExtendWith(TestServicesInjector.class)
public final class ShareResultsUploaderTest {
  /**
   * Verifies that {@link ShareResultsUploader#upload(InputStream)} succeeds for
   * a valid results.json file.
   *
   * <p>This validates the returned data, ensuring that the information is as
   * expected based on the test_config.  This also ensures that the uploader
   * created a zip file with the expected name in the expected directory.  It
   * then uses the uploader to read the uploaded file and ensures that it
   * exactly equals the original file.
   */
  @Test
  public void testUpload(ShareResultsUploader shareResultsUploader,
                         ResultsTester resultsTester,
                         FileStore fileStore)
      throws IOException {

    Results results = resultsTester.newResults();
    ByteSource resultsBytes = resultsTester.asByteSource(results);

    ShareResultsUploadReport report;
    try (InputStream inputStream = resultsBytes.openStream()) {
      report = shareResultsUploader.upload(inputStream);
    }

    // The upload should have succeeded.
    assertFalse(report.isError());

    assertThrows(
        NoSuchElementException.class,
        () -> report.getError());

    ShareResultsJsonView shareView = report.getSuccess();

    assertStartsWith(
        "https://test.tfb-status.techempower.com/share-results/view/",
        shareView.resultsUrl);

    assertStartsWith(
        "https://www.test.techempower.com/benchmarks/",
        shareView.visualizeResultsUrl);

    // Ensure the uploader created a zip file in the expected directory with the
    // expected name.
    Path zipFile =
        fileStore.shareDirectory().resolve(shareView.shareId + ".zip");

    assertTrue(Files.exists(zipFile));

    AtomicBoolean zipEntryPresent = new AtomicBoolean(false);

    shareResultsUploader.getUpload(
        /* shareId= */
        shareView.shareId,

        /* ifPresent= */
        (Path zipEntry) -> {
          zipEntryPresent.set(true);
          // The uploaded file should contain the same exact bytes as was given.
          ByteSource zipEntryBytes = MoreFiles.asByteSource(zipEntry);
          assertTrue(zipEntryBytes.contentEquals(resultsBytes));
        },

        /* ifAbsent= */
        () -> fail());

    assertTrue(zipEntryPresent.get());
  }

  /**
   * Verifies that {@link ShareResultsUploader#upload(InputStream)} responds
   * with an error message when the specified results.json file contains
   * invalid JSON.
   */
  @Test
  public void testUpload_invalidJson(ShareResultsUploader shareResultsUploader)
      throws IOException {

    ByteSource invalidJsonBytes = CharSource.wrap("foo").asByteSource(UTF_8);

    ShareResultsUploadReport report;
    try (InputStream inputStream = invalidJsonBytes.openStream()) {
      report = shareResultsUploader.upload(inputStream);
    }

    assertTrue(report.isError());

    assertThrows(
        NoSuchElementException.class,
        () -> report.getSuccess());

    // TODO: Avoid verifying specific error text.
    assertEquals(
        "Invalid results JSON",
        report.getError().message);
  }

  /**
   * Verifies that {@link ShareResultsUploader#upload(InputStream)} responds
   * with an error message when the specified results.json file contains no
   * {@link Results#testMetadata}.
   */
  @Test
  public void testUpload_noTestMetadata(ShareResultsUploader shareResultsUploader,
                                        ResultsTester resultsTester)
      throws IOException {

    Results template = resultsTester.newResults();

    Results results =
        new Results(
            /* uuid= */ template.uuid,
            /* name= */ template.name,
            /* environmentDescription= */ template.environmentDescription,
            /* startTime= */ template.startTime,
            /* completionTime= */ template.completionTime,
            /* duration= */ template.duration,
            /* frameworks= */ template.frameworks,
            /* completed= */ template.completed,
            /* succeeded= */ template.succeeded,
            /* failed= */ template.failed,
            /* rawData= */ template.rawData,
            /* queryIntervals= */ template.queryIntervals,
            /* concurrencyLevels= */ template.concurrencyLevels,
            /* git= */ template.git,
            /* testMetadata= */ null);

    ByteSource resultsBytes = resultsTester.asByteSource(results);

    ShareResultsUploadReport report;
    try (InputStream inputStream = resultsBytes.openStream()) {
      report = shareResultsUploader.upload(inputStream);
    }

    assertTrue(report.isError());

    // TODO: Avoid verifying specific error text.
    assertEquals(
        "Results must contain non-empty test metadata",
        report.getError().message);
  }

  /**
   * Verifies that {@link ShareResultsUploader#upload(InputStream)} responds
   * with an error message when the specified results.json file is too large.
   */
  @Test
  public void testUpload_fileTooLarge(FileStoreConfig fileStoreConfig,
                                      ShareResultsUploader shareResultsUploader,
                                      ResultsTester resultsTester)
      throws IOException {

    // Start with a valid results.json file and append spaces to the end to make
    // it too large (but valid otherwise).
    Results results = resultsTester.newResults();
    ByteSource resultsBytes = resultsTester.asByteSource(results);

    // Make the uploaded file exactly too large.
    long paddingNeeded =
        fileStoreConfig.maxShareFileSizeBytes + 1 - resultsBytes.size();

    ByteSource padding =
        new ByteSource() {
          @Override
          public InputStream openStream() {
            @SuppressWarnings("InputStreamSlowMultibyteRead")
            InputStream spaces =
                new InputStream() {
                  @Override
                  public int read() {
                    return Ascii.SPACE;
                  }
                };

            return ByteStreams.limit(spaces, paddingNeeded);
          }
        };

    ByteSource paddedResultsBytes =
        ByteSource.concat(resultsBytes, padding);

    ShareResultsUploadReport report;
    try (InputStream inputStream = paddedResultsBytes.openStream()) {
      report = shareResultsUploader.upload(inputStream);
    }

    assertTrue(report.isError());

    // TODO: Avoid verifying specific error text.
    assertEquals(
        "Share uploads cannot exceed "
            + fileStoreConfig.maxShareFileSizeBytes
            + " bytes.",
        report.getError().message);
  }

  /**
   * Verifies that {@link ShareResultsUploader#upload(InputStream)} responds
   * with an error message when the the share directory is full.
   */
  @Test
  public void testUpload_shareDirectoryFull(FileStoreConfig fileStoreConfig,
                                            FileStore fileStore,
                                            ResultsTester resultsTester,
                                            ShareResultsUploader shareResultsUploader)
      throws IOException {

    Results results = resultsTester.newResults();
    ByteSource resultsBytes = resultsTester.asByteSource(results);

    // Create a new large file in the share directory so that the directory
    // exceeds its maximum configured size.
    //
    // TODO: This is unfriendly to other tests running in parallel, since it
    //       temporarily prevents the share functionality from working for
    //       anyone.
    ShareResultsUploadReport report;
    Path junk = fileStore.shareDirectory().resolve("junk.txt");
    try {
      try (FileChannel fileChannel =
               FileChannel.open(junk, CREATE_NEW, WRITE)) {
        fileChannel.write(
            ByteBuffer.wrap(new byte[0]),
            fileStoreConfig.maxShareDirectorySizeBytes);
      }
      try (InputStream inputStream = resultsBytes.openStream()) {
        report = shareResultsUploader.upload(inputStream);
      }
    } finally {
      Files.delete(junk);
    }

    assertTrue(report.isError());

    // TODO: Avoid verifying specific error text.
    assertEquals(
        "Share uploads has reached max capacity.",
        report.getError().message);
  }

  /**
   * Verifies that {@link ShareResultsUploader#getUpload(String,
   * ShareResultsUploader.ShareResultsConsumer, Runnable)} invokes the specified
   * "if absent" callback when the specified shared file does not exist.
   */
  @Test
  public void testGetUpload_invalidShareId(ShareResultsUploader shareResultsUploader,
                                           FileStore fileStore)
      throws IOException {

    String shareId;
    do shareId = UUID.randomUUID().toString();
    while (Files.exists(fileStore.shareDirectory().resolve(shareId + ".zip")));

    AtomicBoolean ifAbsentCalled = new AtomicBoolean(false);

    shareResultsUploader.getUpload(
        /* shareId= */
        shareId,

        /* ifPresent= */
        zipEntry -> fail(),

        /* ifAbsent= */
        () -> ifAbsentCalled.set(true));

    assertTrue(ifAbsentCalled.get());
  }

  /**
   * Verifies that {@link ShareResultsUploader#getUpload(String,
   * ShareResultsUploader.ShareResultsConsumer, Runnable)} invokes the specified
   * "if absent" callback when the specified share id does not conform to the
   * scheme used for share ids.
   */
  @Test
  public void testGetUpload_invalidFileName(ShareResultsUploader shareResultsUploader)
      throws IOException {

    AtomicBoolean ifAbsentCalled = new AtomicBoolean(false);

    shareResultsUploader.getUpload(
        /* shareId= */ "../abc",
        /* ifPresent= */ zipEntry -> fail(),
        /* ifAbsent= */ () -> ifAbsentCalled.set(true));

    assertTrue(ifAbsentCalled.get());
  }
}
