package tfb.status.service;

import static com.google.common.net.MediaType.JSON_UTF_8;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static tfb.status.util.MoreAssertions.assertInstanceOf;
import static tfb.status.util.MoreAssertions.assertLinesEqual;
import static tfb.status.util.MoreAssertions.assertMediaType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.ByteSource;
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
import tfb.status.bootstrap.TestServices;

/**
 * Tests for {@link EmailSender}.
 */
public final class EmailSenderTest {
  private static TestServices services;
  private static EmailSender emailSender;
  private static ObjectMapper objectMapper;

  @BeforeAll
  public static void beforeAll() {
    services = new TestServices();
    emailSender = services.serviceLocator().getService(EmailSender.class);
    objectMapper = services.serviceLocator().getService(ObjectMapper.class);
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

    MimeMessage message = services.onlyEmailMessage();

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
    var point = new Point(1, 2);

    byte[] pointBytes = objectMapper.writeValueAsBytes(point);

    DataSource attachment =
        emailSender.createAttachment(
            /* fileName= */ "point.json",
            /* mediaType= */ JSON_UTF_8,
            /* fileBytes= */ ByteSource.wrap(pointBytes));

    emailSender.sendEmail("subject", "textContent", List.of(attachment));

    MimeMessage message = services.onlyEmailMessage();

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

    Point deserializedPoint;
    try (InputStream inputStream = secondPart.getInputStream()) {
      deserializedPoint = objectMapper.readValue(inputStream, Point.class);
    }

    assertEquals(point, deserializedPoint);
  }

  public static final class Point {
    public final int x;
    public final int y;

    @JsonCreator
    public Point(@JsonProperty("x") int x,
                 @JsonProperty("y") int y) {
      this.x = x;
      this.y = y;
    }

    @Override
    public boolean equals(Object object) {
      if (object == this) {
        return true;
      } else if (!(object instanceof Point)) {
        return false;
      } else {
        Point that = (Point) object;
        return this.x == that.x
            && this.y == that.y;
      }
    }

    @Override
    public int hashCode() {
      return x ^ y;
    }
  }
}
