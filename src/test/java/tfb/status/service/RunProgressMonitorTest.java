package tfb.status.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.config.RunProgressMonitorConfig;
import tfb.status.testlib.MailDelay;
import tfb.status.testlib.MailServer;
import tfb.status.testlib.TestServicesInjector;

/**
 * Tests for {@link RunProgressMonitor}.
 */
@ExtendWith(TestServicesInjector.class)
public final class RunProgressMonitorTest {
  /**
   * Verifies that {@link RunProgressMonitor#recordProgress(String, boolean)}
   * causes an email to be sent when we expect more progress in a benchmarking
   * environment but we don't see that progress within the expected amount of
   * time.
   */
  @Test
  public void testRecordProgress(RunProgressMonitor runProgressMonitor,
                                 RunProgressMonitorConfig config,
                                 MailServer mailServer,
                                 MailDelay mailDelay)
      throws IOException, MessagingException, InterruptedException {

    // The amount of time after the final update for an environment is recorded
    // and before we receive an email telling us the environment has timed out.
    Duration expectedDelay =
        Duration.ofSeconds(config.environmentTimeoutSeconds)
                .plus(mailDelay.timeToSendOneEmail());

    String environment = "test_environment_" + UUID.randomUUID();

    String subject =
        RunProgressMonitor.environmentCrashedEmailSubject(environment);

    runProgressMonitor.recordProgress(environment, false);
    Thread.sleep(expectedDelay.toMillis());

    ImmutableList<MimeMessage> messagesAfterOneTimeRun =
        mailServer.getMessages(m -> m.getSubject().equals(subject));

    assertEquals(
        0,
        messagesAfterOneTimeRun.size());

    runProgressMonitor.recordProgress("other_environment", true);
    Thread.sleep(expectedDelay.toMillis());

    ImmutableList<MimeMessage> messagesAfterOtherEnvironment =
        mailServer.getMessages(m -> m.getSubject().equals(subject));

    assertEquals(0, messagesAfterOtherEnvironment.size());

    runProgressMonitor.recordProgress(environment, true);
    Thread.sleep(expectedDelay.dividedBy(2).toMillis());
    runProgressMonitor.recordProgress(environment, true);
    Thread.sleep(expectedDelay.dividedBy(2).toMillis());

    ImmutableList<MimeMessage> messagesAfterProgressReceived =
        mailServer.getMessages(m -> m.getSubject().equals(subject));

    assertEquals(0, messagesAfterProgressReceived.size());

    Thread.sleep(expectedDelay.toMillis());

    ImmutableList<MimeMessage> messagesAfterNoProgress =
        mailServer.getMessages(m -> m.getSubject().equals(subject));

    assertEquals(1, messagesAfterNoProgress.size());

    MimeMessage message =
        Iterables.getOnlyElement(messagesAfterNoProgress);

    assertEquals(subject, message.getSubject());
  }
}
