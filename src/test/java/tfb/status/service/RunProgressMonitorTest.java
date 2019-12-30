package tfb.status.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.MoreFiles;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import javax.mail.MessagingException;
import org.glassfish.hk2.api.messaging.Topic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.config.RunProgressMonitorConfig;
import tfb.status.testlib.MailDelay;
import tfb.status.testlib.MailServer;
import tfb.status.testlib.TestServicesInjector;
import tfb.status.util.ZipFiles;
import tfb.status.view.Results;
import tfb.status.view.UpdatedResultsEvent;

/**
 * Tests for {@link RunProgressMonitor}.
 */
@ExtendWith(TestServicesInjector.class)
public final class RunProgressMonitorTest {
  /**
   * Verifies that {@link UpdatedResultsEvent} causes an email to be sent when
   * we expect more progress in a benchmarking environment but we don't see that
   * progress within the expected amount of time.
   */
  @Test
  public void testRecordProgress(Topic<UpdatedResultsEvent> updatedResultsTopic,
                                 RunProgressMonitorConfig config,
                                 MailServer mailServer,
                                 MailDelay mailDelay,
                                 ObjectMapper objectMapper,
                                 FileSystem fileSystem,
                                 FileStore fileStore)
      throws IOException, MessagingException, InterruptedException {

    // -------------------------------------------------------------------------
    // Diversion: write a new set of results to disk by copying and modifying
    //            some existing results.
    //
    Path originalJsonFile =
        fileStore.resultsDirectory()
                 .resolve("results.2019-12-11-13-21-02-404.json");

    Results originalResults;
    try (InputStream inputStream = Files.newInputStream(originalJsonFile)) {
      originalResults = objectMapper.readValue(inputStream, Results.class);
    }

    String uuid = UUID.randomUUID().toString();
    String environment = "test_environment_" + uuid;

    Path newJsonFile =
        originalJsonFile.resolveSibling(
            MoreFiles.getNameWithoutExtension(originalJsonFile)
                + "_" + uuid + ".json");

    Results newResults =
        new Results(
            /* uuid= */ uuid,
            /* name= */ originalResults.name,
            /* environmentDescription= */ environment,
            /* startTime= */ originalResults.startTime,
            /* completionTime= */ originalResults.completionTime,
            /* duration= */ originalResults.duration,
            /* frameworks= */ originalResults.frameworks,
            /* completed= */ originalResults.completed,
            /* succeeded= */ originalResults.succeeded,
            /* failed= */ originalResults.failed,
            /* rawData= */ originalResults.rawData,
            /* queryIntervals= */ originalResults.queryIntervals,
            /* concurrencyLevels= */ originalResults.concurrencyLevels,
            /* git= */ originalResults.git);

    try (BufferedWriter writer = Files.newBufferedWriter(newJsonFile)) {
      objectMapper.writeValue(writer, newResults);
    }
    //
    // End of diversion.
    // -------------------------------------------------------------------------

    // The amount of time after the final update for an environment is recorded
    // and before we receive an email telling us the environment has timed out.
    Duration expectedDelay =
        Duration.ofSeconds(config.environmentTimeoutSeconds)
                .plus(mailDelay.timeToSendOneEmail());

    String subject =
        RunProgressMonitor.environmentCrashedEmailSubject(environment);

    // Counts the number of emails received about our environment.
    class EmailCounter {
      int count() throws IOException, MessagingException {
        return mailServer.getMessages(m -> m.getSubject().equals(subject))
                         .size();
      }
    }

    var emails = new EmailCounter();

    // If we keep sending updates before the timeout can occur, then no emails
    // are sent.

    updatedResultsTopic.publish(new UpdatedResultsEvent(uuid));

    Thread.sleep(expectedDelay.dividedBy(2).toMillis());

    updatedResultsTopic.publish(new UpdatedResultsEvent(uuid));

    Thread.sleep(expectedDelay.dividedBy(2).toMillis());

    assertEquals(0, emails.count());

    // If we wait for too long between updates, then an email is sent.

    Thread.sleep(expectedDelay.toMillis());

    assertEquals(1, emails.count());

    // If there is no more progress, there are no more emails.

    Thread.sleep(expectedDelay.toMillis());

    assertEquals(1, emails.count());

    // If there is more progress and then too long of a delay, then another
    // email is sent.

    updatedResultsTopic.publish(new UpdatedResultsEvent(uuid));

    Thread.sleep(expectedDelay.toMillis());

    assertEquals(2, emails.count());

    // Once the zip file for the run appears, since this is not a continuous
    // benchmarking environment, there are no more emails.

    // -------------------------------------------------------------------------
    // Diversion: write the results.zip file for this run to disk.
    //
    Path originalZipFile =
        fileStore.resultsDirectory()
                 .resolve("results.2019-12-16-03-22-48-407.zip");

    // Avoid putting this in the results directory until it's done.
    Path tempZipFile = fileSystem.getPath("temp_results_" + uuid + ".zip");

    Files.copy(originalZipFile, tempZipFile);

    ZipFiles.findZipEntry(
        /* zipFile= */
        tempZipFile,

        /* entryPath= */
        "results.json",

        /* ifPresent= */
        (Path zipEntry) -> {
          try (BufferedWriter writer = Files.newBufferedWriter(zipEntry)) {
            objectMapper.writeValue(writer, newResults);
          }
        },

        /* ifAbsent= */
        () -> fail("The results.zip file should include a results.json"));

    Path newZipFile =
        originalJsonFile.resolveSibling(
            MoreFiles.getNameWithoutExtension(originalZipFile)
                + "_" + uuid + ".zip");

    Files.move(tempZipFile, newZipFile);
    //
    // End of diversion.
    // -------------------------------------------------------------------------

    updatedResultsTopic.publish(new UpdatedResultsEvent(uuid));

    Thread.sleep(expectedDelay.toMillis());

    assertEquals(2, emails.count());
  }
}
