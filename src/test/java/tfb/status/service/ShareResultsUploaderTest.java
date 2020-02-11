package tfb.status.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tfb.status.testlib.MoreAssertions.assertStartsWith;

import com.google.common.base.Ascii;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.UUID;
import javax.mail.MessagingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import tfb.status.config.SharingConfig;
import tfb.status.service.ShareResultsUploader.ShareResultsUploadReport;
import tfb.status.testlib.MailDelay;
import tfb.status.testlib.MailServer;
import tfb.status.testlib.ResultsTester;
import tfb.status.testlib.TestServicesInjector;
import tfb.status.view.Results;
import tfb.status.view.ShareResultsErrorJsonView;
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
                         SharingConfig sharingConfig,
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
        sharingConfig.tfbStatusOrigin + "/share-results/view/",
        shareView.resultsUrl);

    assertStartsWith(
        sharingConfig.tfbWebsiteOrigin + "/benchmarks/",
        shareView.visualizeResultsUrl);

    ByteSource sharedBytes = shareResultsUploader.getUpload(shareView.shareId);

    assertNotNull(sharedBytes);

    assertTrue(sharedBytes.contentEquals(resultsBytes));
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

    assertEquals(
        ShareResultsErrorJsonView.ErrorKind.INVALID_JSON,
        report.getError().errorKind);
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

    assertEquals(
        ShareResultsErrorJsonView.ErrorKind.MISSING_TEST_METADATA,
        report.getError().errorKind);
  }

  /**
   * Verifies that {@link ShareResultsUploader#upload(InputStream)} responds
   * with an error message when the specified results.json file is too large.
   */
  @Test
  public void testUpload_fileTooLarge(SharingConfig sharingConfig,
                                      ShareResultsUploader shareResultsUploader,
                                      ResultsTester resultsTester)
      throws IOException {

    // Start with a valid results.json file and append spaces to the end to make
    // it too large (but valid otherwise).
    Results results = resultsTester.newResults();
    ByteSource resultsBytes = resultsTester.asByteSource(results);

    // Make the uploaded file exactly too large.
    long paddingNeeded =
        sharingConfig.maxFileSizeInBytes + 1 - resultsBytes.size();

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

    assertEquals(
        ShareResultsErrorJsonView.ErrorKind.FILE_TOO_LARGE,
        report.getError().errorKind);
  }

  /**
   * Verifies that {@link ShareResultsUploader#upload(InputStream)} responds
   * with an error message when the the share directory is full.
   */
  @Test
  public void testUpload_shareDirectoryFull(SharingConfig sharingConfig,
                                            FileStore fileStore,
                                            ResultsTester resultsTester,
                                            MailServer mailServer,
                                            MailDelay mailDelay,
                                            ShareResultsUploader shareResultsUploader)
      throws IOException, MessagingException, InterruptedException {

    Results results = resultsTester.newResults();
    ByteSource resultsBytes = resultsTester.asByteSource(results);

    // Counts the number of emails received regarding the share directory being
    // full.
    class EmailCounter {
      final String subject = ShareResultsUploader.SHARE_DIRECTORY_FULL_SUBJECT;

      int count() throws IOException, MessagingException {
        return mailServer.getMessages(m -> m.getSubject().equals(subject))
                         .size();
      }
    }

    var emails = new EmailCounter();

    assertEquals(0, emails.count());

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
            sharingConfig.maxDirectorySizeInBytes);
      }

      try (InputStream inputStream = resultsBytes.openStream()) {
        report = shareResultsUploader.upload(inputStream);
      }

      assertTrue(report.isError());

      assertEquals(
          ShareResultsErrorJsonView.ErrorKind.SHARE_DIRECTORY_FULL,
          report.getError().errorKind);

      Thread.sleep(mailDelay.timeToSendOneEmail().toMillis());

      assertEquals(1, emails.count());

      // Assert that we don't receive a second email.

      try (InputStream inputStream = resultsBytes.openStream()) {
        report = shareResultsUploader.upload(inputStream);
      }

      assertTrue(report.isError());

      assertEquals(
          ShareResultsErrorJsonView.ErrorKind.SHARE_DIRECTORY_FULL,
          report.getError().errorKind);

      Thread.sleep(mailDelay.timeToSendOneEmail().toMillis());

      assertEquals(1, emails.count());

    } finally {
      Files.delete(junk);
    }
  }

  /**
   * Verifies that {@link ShareResultsUploader#getUpload(String)} returns {@code
   * null} when the specified shared file does not exist.
   */
  @Test
  public void testGetUpload_notFound(ShareResultsUploader shareResultsUploader,
                                     FileStore fileStore) {

    assertNull(shareResultsUploader.getUpload(UUID.randomUUID().toString()));
  }

  /**
   * Verifies that {@link ShareResultsUploader#getUpload(String)} throws {@link
   * IllegalArgumentException} when the specified share id is invalid.
   */
  @Test
  public void testGetUpload_invalidShareId(ShareResultsUploader shareResultsUploader,
                                           FileStore fileStore) {

    assertThrows(
        IllegalArgumentException.class,
        () -> shareResultsUploader.getUpload("../."));
  }
}
