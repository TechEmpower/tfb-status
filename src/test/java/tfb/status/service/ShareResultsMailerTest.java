package tfb.status.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import tfb.status.testlib.MailServer;
import tfb.status.testlib.TestServicesInjector;

/**
 * Tests for {@link ShareResultsMailer}.
 */
@Execution(ExecutionMode.SAME_THREAD) // currently not parallelizable
@ExtendWith(TestServicesInjector.class)
public final class ShareResultsMailerTest {
  /**
   * Verifies that {@link ShareResultsMailer#onShareDirectoryFull(long, long)}
   * causes an email with the expected content to be sent.
   */
  @Test
  public void testOnShareDirectoryFull(MailServer mailServer,
                                       ShareResultsMailer shareResultsMailer)
      throws IOException, MessagingException {

    // The mailer is a singleton and may have been called by other tests
    // indirectly.  We just need to make sure it sent an email as a result of
    // this test.
    List<MimeMessage> messages = getShareDirectoryFullEmails(mailServer);
    int initialMessagesSize = messages.size();

    shareResultsMailer.onShareDirectoryFull(1234, 5678);

    messages = getShareDirectoryFullEmails(mailServer);

    // TODO: Ensure an email was actually sent by this test. Otherwise, this is
    //       not friendly to parallelism as another test could also cause an
    //       email to be sent during this test.
    assertEquals(initialMessagesSize + 1, messages.size());
  }

  private static ImmutableList<MimeMessage>
  getShareDirectoryFullEmails(MailServer mailServer)
      throws IOException, MessagingException {

    return mailServer.getMessages(
        m ->
            m.getSubject().equals(
                ShareResultsMailer.SHARE_DIRECTORY_FULL_SUBJECT));
  }
}
