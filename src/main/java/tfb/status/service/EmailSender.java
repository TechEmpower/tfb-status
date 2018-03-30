package tfb.status.service;

import com.google.common.io.ByteSource;
import com.google.common.net.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import org.jvnet.hk2.annotations.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.config.EmailConfig;

/**
 * Sends emails to a fixed recipient.
 */
@Singleton
public final class EmailSender {
  @Nullable private final EmailConfig config;
  private final Logger logger = LoggerFactory.getLogger(getClass());

  /**
   * Constructs a new email sender that uses the provided settings for all the
   * emails it sends.
   *
   * @param config the configuration for emails, or {@code null} if outbound
   *        emails should be quietly discarded
   */
  @Inject
  public EmailSender(@Optional @Nullable EmailConfig config) {
    this.config = config;
  }

  /**
   * Sends a new email.
   *
   * @param subject the subject of the email
   * @param textContent the text content of the email
   * @param attachments files to attach to the email
   * @throws MessagingException if the email is not delivered successfully
   */
  public void sendEmail(String subject,
                        String textContent,
                        List<DataSource> attachments)
      throws MessagingException {

    Objects.requireNonNull(subject);
    Objects.requireNonNull(textContent);
    Objects.requireNonNull(attachments);

    if (config == null) {
      logger.info(
          "Email is not enabled, discarding email with subject \"{}\"",
          subject);
      return;
    }

    InternetAddress from = new InternetAddress(config.from);
    InternetAddress to = new InternetAddress(config.to);

    Properties environment = new Properties();
    environment.setProperty("mail.smtp.host", config.host);
    environment.setProperty("mail.smtp.port", String.valueOf(config.port));
    environment.setProperty("mail.smtp.starttls.enable", "true");

    Session session = Session.getInstance(environment);

    MimeMessage message = new MimeMessage(session);
    message.setFrom(from);
    message.setRecipient(Message.RecipientType.TO, to);
    message.setSubject(subject);

    if (attachments.isEmpty()) {
      message.setText(textContent, "utf-8");
    } else {
      MimeMultipart multipart = new MimeMultipart();
      MimeBodyPart textBodyPart = new MimeBodyPart();
      textBodyPart.setText(textContent, "utf-8");
      multipart.addBodyPart(textBodyPart);
      for (DataSource attachment : attachments) {
        MimeBodyPart fileBodyPart = new MimeBodyPart();
        fileBodyPart.setFileName(attachment.getName());
        fileBodyPart.setDataHandler(new DataHandler(attachment));
        multipart.addBodyPart(fileBodyPart);
      }
      message.setContent(multipart);
    }

    Transport.send(message);
  }

  /**
   * Constructs a representation of an email attachment suitable for use with
   * {@link #sendEmail(String, String, List)}.
   *
   * @param fileName the name of the attached file
   * @param mediaType the content type of the attached file
   * @param fileBytes the body of the attached file
   */
  public DataSource createAttachment(String fileName,
                                     MediaType mediaType,
                                     ByteSource fileBytes) {
    return new ByteSourceAttachment(fileName, mediaType, fileBytes);
  }

  private static final class ByteSourceAttachment implements DataSource {
    private final String fileName;
    private final MediaType mediaType;
    private final ByteSource fileBytes;

    ByteSourceAttachment(String fileName,
                         MediaType mediaType,
                         ByteSource fileBytes) {
      this.fileName = Objects.requireNonNull(fileName);
      this.mediaType = Objects.requireNonNull(mediaType);
      this.fileBytes = Objects.requireNonNull(fileBytes);
    }

    @Override
    public InputStream getInputStream() throws IOException {
      return fileBytes.openStream();
    }

    @Override
    public OutputStream getOutputStream() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getContentType() {
      return mediaType.toString();
    }

    @Override
    public String getName() {
      return fileName;
    }
  }
}
