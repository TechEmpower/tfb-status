package tfb.status.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.mail.MessagingException;
import java.io.IOException;
import org.glassfish.hk2.api.messaging.Topic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.testlib.MailDelay;
import tfb.status.testlib.MailServer;
import tfb.status.testlib.ResultsTester;
import tfb.status.testlib.TestServicesInjector;
import tfb.status.view.Results;
import tfb.status.view.UpdatedResultsEvent;

/**
 * Tests for {@link RunCompleteMailer}
 */
@ExtendWith(TestServicesInjector.class)
public final class RunCompleteMailerTest {
  /**
   * Verifies that {@link UpdatedResultsEvent} causes an email to be sent when a
   * run is complete.
   */
  @Test
  public void testRunCompleteEmail(Topic<UpdatedResultsEvent> updatedResultsTopic,
                                   MailServer mailServer,
                                   MailDelay mailDelay,
                                   ResultsTester resultsTester)
      throws InterruptedException, IOException, MessagingException {

    Results results = resultsTester.newResults();
    assertNotNull(results.uuid());

    resultsTester.saveJsonToResultsDirectory(results);
    resultsTester.saveZipToResultsDirectory(results);

    updatedResultsTopic.publish(new UpdatedResultsEvent(results.uuid()));

    Thread.sleep(mailDelay.timeToSendOneEmail().toMillis());

    String subject = RunCompleteMailer.runCompleteEmailSubject(results);

    assertEquals(
        1,
        mailServer.getMessages(m -> m.getSubject().equals(subject))
                  .size());
  }
}
