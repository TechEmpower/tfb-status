package tfb.status.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.mail.MessagingException;
import java.io.IOException;
import java.time.Duration;
import org.glassfish.hk2.api.messaging.Topic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.config.RunProgressMonitorConfig;
import tfb.status.testlib.MailDelay;
import tfb.status.testlib.MailServer;
import tfb.status.testlib.ResultsTester;
import tfb.status.testlib.TestServicesInjector;
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
                                 ResultsTester resultsTester)
      throws IOException, MessagingException, InterruptedException {

    Results newResults = resultsTester.newResults();
    assertNotNull(newResults.uuid());
    assertNotNull(newResults.environmentDescription());

    resultsTester.saveJsonToResultsDirectory(newResults);

    String uuid = newResults.uuid();
    String environment = newResults.environmentDescription();

    // The amount of time after the final update for an environment is recorded
    // and before we receive an email telling us the environment has timed out.
    Duration expectedDelay =
        Duration.ofSeconds(config.environmentTimeoutSeconds())
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

    resultsTester.saveZipToResultsDirectory(newResults);

    updatedResultsTopic.publish(new UpdatedResultsEvent(uuid));

    Thread.sleep(expectedDelay.toMillis());

    assertEquals(2, emails.count());
  }
}
