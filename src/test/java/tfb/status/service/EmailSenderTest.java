package tfb.status.service;

import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static tfb.status.testlib.MoreAssertions.assertInstanceOf;
import static tfb.status.testlib.MoreAssertions.assertLinesEqual;
import static tfb.status.testlib.MoreAssertions.assertMediaType;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.CharSource;
import com.google.common.net.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import javax.activation.DataSource;
import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.testlib.MailServer;
import tfb.status.testlib.TestServicesInjector;

/**
 * Tests for {@link EmailSender}.
 */
@ExtendWith(TestServicesInjector.class)
public final class EmailSenderTest {
  /**
   * Verifies that {@link EmailSender#sendEmail(String, String, List)} sends the
   * expected email when there are no attachments.
   */
  @Test
  public void testSendEmailTextOnly(EmailSender emailSender,
                                    MailServer mailServer)
      throws MessagingException, IOException {

    String subject = getClass().getName() + " " + UUID.randomUUID().toString();

    emailSender.sendEmail(subject, "textContent", List.of());

    ImmutableList<MimeMessage> messages =
        mailServer.getMessages(m -> m.getSubject().equals(subject));

    assertEquals(1, messages.size());

    MimeMessage message = Iterables.getOnlyElement(messages);

    assertEquals(subject, message.getSubject());

    assertMediaType(
        PLAIN_TEXT_UTF_8,
        message.getContentType());

    String content =
        assertInstanceOf(
            String.class,
            message.getContent());

    assertLinesEqual(
        List.of("textContent"),
        content);
  }

  /**
   * Verifies that {@link EmailSender#sendEmail(String, String, List)} sends the
   * expected email when there is at least one attachment.
   */
  @Test
  public void testSendEmailWithAttachments(EmailSender emailSender,
                                           MailServer mailServer)
      throws MessagingException, IOException {

    String subject = getClass().getName() + " " + UUID.randomUUID().toString();

    String attachedJson = "{\"foo\":\"bar\"}";

    DataSource attachment =
        emailSender.createAttachment(
            /* fileName= */ "foo.json",
            /* mediaType= */ MediaType.create("application", "json"),
            /* fileBytes= */ CharSource.wrap(attachedJson).asByteSource(UTF_8));

    emailSender.sendEmail(subject, "textContent", List.of(attachment));

    ImmutableList<MimeMessage> messages =
        mailServer.getMessages(m -> m.getSubject().equals(subject));

    assertEquals(1, messages.size());

    MimeMessage message = Iterables.getOnlyElement(messages);

    assertEquals(subject, message.getSubject());

    assertMediaType(
        MediaType.create("multipart", "mixed"),
        message.getContentType());

    MimeMultipart multipart =
        assertInstanceOf(
            MimeMultipart.class,
            message.getContent());

    assertEquals(2, multipart.getCount());

    BodyPart firstPart = multipart.getBodyPart(0);

    assertMediaType(
        PLAIN_TEXT_UTF_8,
        firstPart.getContentType());

    String firstContent =
        assertInstanceOf(
            String.class,
            firstPart.getContent());

    assertLinesEqual(
        List.of("textContent"),
        firstContent);

    BodyPart secondPart = multipart.getBodyPart(1);

    assertEquals(
        attachment.getName(),
        secondPart.getFileName());

    assertMediaType(
        "application/json",
        secondPart.getContentType());

    String receivedJson;
    try (InputStream inputStream = secondPart.getInputStream()) {
      receivedJson = new String(inputStream.readAllBytes(), UTF_8);
    }

    assertEquals(attachedJson, receivedJson);
  }
}
