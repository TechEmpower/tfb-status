package tfb.status.service;

import static com.google.common.net.MediaType.JSON_UTF_8;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static tfb.status.testlib.MoreAssertions.assertInstanceOf;
import static tfb.status.testlib.MoreAssertions.assertLinesEqual;
import static tfb.status.testlib.MoreAssertions.assertMediaType;

import com.google.common.io.CharSource;
import com.google.common.net.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.activation.DataSource;
import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tfb.status.testlib.MailServer;
import tfb.status.testlib.TestServices;

/**
 * Tests for {@link EmailSender}.
 */
public final class EmailSenderTest {
  private static TestServices services;
  private static MailServer mailServer;
  private static EmailSender emailSender;

  @BeforeAll
  public static void beforeAll() {
    services = new TestServices();
    mailServer = services.mailServer();
    emailSender = services.getService(EmailSender.class);
  }

  @AfterAll
  public static void afterAll() {
    services.shutdown();
  }

  /**
   * Verifies that {@link EmailSender#sendEmail(String, String, List)} sends the
   * expected email when there are no attachments.
   */
  @Test
  public void testSendEmailTextOnly() throws MessagingException, IOException {
    emailSender.sendEmail("subject", "textContent", List.of());

    MimeMessage message = mailServer.onlyEmailMessage();

    assertEquals("subject", message.getSubject());

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
  public void testSendEmailWithAttachments() throws MessagingException, IOException {
    String attachedJson = "{\"foo\":\"bar\"}";

    DataSource attachment =
        emailSender.createAttachment(
            /* fileName= */ "foo.json",
            /* mediaType= */ JSON_UTF_8,
            /* fileBytes= */ CharSource.wrap(attachedJson).asByteSource(UTF_8));

    emailSender.sendEmail("subject", "textContent", List.of(attachment));

    MimeMessage message = mailServer.onlyEmailMessage();

    assertMediaType(
        MediaType.create("multipart", "mixed"),
        message.getContentType());

    assertEquals("subject", message.getSubject());

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
        JSON_UTF_8,
        secondPart.getContentType());

    String receivedJson;
    try (InputStream inputStream = secondPart.getInputStream()) {
      receivedJson = new String(inputStream.readAllBytes(), UTF_8);
    }

    assertEquals(attachedJson, receivedJson);
  }
}
