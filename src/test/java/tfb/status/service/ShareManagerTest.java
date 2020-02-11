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
import tfb.status.config.ShareConfig;
import tfb.status.service.ShareManager.ShareOutcome;
import tfb.status.testlib.MailDelay;
import tfb.status.testlib.MailServer;
import tfb.status.testlib.ResultsTester;
import tfb.status.testlib.TestServicesInjector;
import tfb.status.view.Results;

/**
 * Tests for {@link ShareManager}.
 */
@Execution(ExecutionMode.SAME_THREAD) // currently not parallelizable
@ExtendWith(TestServicesInjector.class)
public final class ShareManagerTest {
  /**
   * Verifies that {@link ShareManager#shareResults(InputStream)} succeeds for
   * a valid results.json file.
   */
  @Test
  public void testShareResults(ShareManager shareManager,
                               ShareConfig shareConfig,
                               ResultsTester resultsTester,
                               FileStore fileStore)
      throws IOException {

    Results results = resultsTester.newResults();
    ByteSource resultsBytes = resultsTester.asByteSource(results);

    ShareOutcome outcome;
    try (InputStream inputStream = resultsBytes.openStream()) {
      outcome = shareManager.shareResults(inputStream);
    }

    // The upload should have succeeded.
    assertFalse(outcome.isFailure());

    assertThrows(
        NoSuchElementException.class,
        () -> outcome.getFailure());

    ShareOutcome.Success success = outcome.getSuccess();

    assertStartsWith(
        shareConfig.tfbStatusOrigin + "/share/download/",
        success.resultsUrl);

    assertStartsWith(
        shareConfig.tfbWebsiteOrigin + "/benchmarks/",
        success.visualizeResultsUrl);

    ByteSource sharedBytes = shareManager.findSharedResults(success.shareId);

    assertNotNull(sharedBytes);

    assertTrue(sharedBytes.contentEquals(resultsBytes));
  }

  /**
   * Verifies that {@link ShareManager#shareResults(InputStream)} responds
   * with an error message when the specified results.json file contains
   * invalid JSON.
   */
  @Test
  public void testShareResults_invalidJson(ShareManager shareManager)
      throws IOException {

    ByteSource invalidJsonBytes = CharSource.wrap("foo").asByteSource(UTF_8);

    ShareOutcome outcome;
    try (InputStream inputStream = invalidJsonBytes.openStream()) {
      outcome = shareManager.shareResults(inputStream);
    }

    assertTrue(outcome.isFailure());

    assertThrows(
        NoSuchElementException.class,
        () -> outcome.getSuccess());

    assertEquals(
        ShareOutcome.Failure.Kind.INVALID_JSON,
        outcome.getFailure().kind);
  }

  /**
   * Verifies that {@link ShareManager#shareResults(InputStream)} responds
   * with an error message when the specified results.json file contains no
   * {@link Results#testMetadata}.
   */
  @Test
  public void testShareResults_noTestMetadata(ShareManager shareManager,
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

    ShareOutcome outcome;
    try (InputStream inputStream = resultsBytes.openStream()) {
      outcome = shareManager.shareResults(inputStream);
    }

    assertTrue(outcome.isFailure());

    assertEquals(
        ShareOutcome.Failure.Kind.MISSING_TEST_METADATA,
        outcome.getFailure().kind);
  }

  /**
   * Verifies that {@link ShareManager#shareResults(InputStream)} responds
   * with an error message when the specified results.json file is too large.
   */
  @Test
  public void testShareResults_fileTooLarge(ShareConfig shareConfig,
                                            ShareManager shareManager,
                                            ResultsTester resultsTester)
      throws IOException {

    // Start with a valid results.json file and append spaces to the end to make
    // it too large (but valid otherwise).
    Results results = resultsTester.newResults();
    ByteSource resultsBytes = resultsTester.asByteSource(results);

    // Make the uploaded file exactly too large.
    long paddingNeeded =
        shareConfig.maxFileSizeInBytes + 1 - resultsBytes.size();

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

    ShareOutcome outcome;
    try (InputStream inputStream = paddedResultsBytes.openStream()) {
      outcome = shareManager.shareResults(inputStream);
    }

    assertTrue(outcome.isFailure());

    assertEquals(
        ShareOutcome.Failure.Kind.FILE_TOO_LARGE,
        outcome.getFailure().kind);
  }

  /**
   * Verifies that {@link ShareManager#shareResults(InputStream)} responds
   * with an error message when the the share directory is full.
   */
  @Test
  public void testShareResults_shareDirectoryFull(ShareConfig shareConfig,
                                                  FileStore fileStore,
                                                  ResultsTester resultsTester,
                                                  MailServer mailServer,
                                                  MailDelay mailDelay,
                                                  ShareManager shareManager)
      throws IOException, MessagingException, InterruptedException {

    Results results = resultsTester.newResults();
    ByteSource resultsBytes = resultsTester.asByteSource(results);

    // Counts the number of emails received regarding the share directory being
    // full.
    class EmailCounter {
      final String subject = ShareManager.SHARE_DIRECTORY_FULL_SUBJECT;

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
    ShareOutcome outcome;
    Path junk = fileStore.shareDirectory().resolve("junk.txt");
    try {
      try (FileChannel fileChannel =
               FileChannel.open(junk, CREATE_NEW, WRITE)) {
        fileChannel.write(
            ByteBuffer.wrap(new byte[0]),
            shareConfig.maxDirectorySizeInBytes);
      }

      try (InputStream inputStream = resultsBytes.openStream()) {
        outcome = shareManager.shareResults(inputStream);
      }

      assertTrue(outcome.isFailure());

      assertEquals(
          ShareOutcome.Failure.Kind.SHARE_DIRECTORY_FULL,
          outcome.getFailure().kind);

      Thread.sleep(mailDelay.timeToSendOneEmail().toMillis());

      assertEquals(1, emails.count());

      // Assert that we don't receive a second email.

      try (InputStream inputStream = resultsBytes.openStream()) {
        outcome = shareManager.shareResults(inputStream);
      }

      assertTrue(outcome.isFailure());

      assertEquals(
          ShareOutcome.Failure.Kind.SHARE_DIRECTORY_FULL,
          outcome.getFailure().kind);

      Thread.sleep(mailDelay.timeToSendOneEmail().toMillis());

      assertEquals(1, emails.count());

    } finally {
      Files.delete(junk);
    }
  }

  /**
   * Verifies that {@link ShareManager#findSharedResults(String)} returns {@code
   * null} when the specified shared file does not exist.
   */
  @Test
  public void testFindSharedResults_notFound(ShareManager shareManager,
                                             FileStore fileStore) {

    assertNull(shareManager.findSharedResults(UUID.randomUUID().toString()));
  }

  /**
   * Verifies that {@link ShareManager#findSharedResults(String)} throws {@link
   * IllegalArgumentException} when the specified share id is invalid.
   */
  @Test
  public void testFindSharedResults_invalidShareId(ShareManager shareManager,
                                                   FileStore fileStore) {

    assertThrows(
        IllegalArgumentException.class,
        () -> shareManager.findSharedResults("../."));
  }
}
