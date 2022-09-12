package tfb.status.service;

import com.google.common.io.ByteSource;
import com.google.common.net.HostAndPort;
import com.google.common.net.MediaType;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jvnet.hk2.annotations.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.config.EmailConfig;

/**
 * Sends emails to a fixed recipient.
 */
@Singleton
public final class EmailSender {
  private final @Nullable EmailConfig config;
  private final @Nullable OverridePort overridePort;
  private final Logger logger = LoggerFactory.getLogger(getClass());

  /**
   * Constructs a new email sender with the provided configuration.
   *
   * @param config the configuration for emails, or {@code null} if outbound
   *        emails should be quietly discarded
   * @param overridePort a service that produces the port number of the mail
   *        server on demand, which may be useful when the mail server listens
   *        on a dynamically assigned ephemeral port, or {@code null} if the
   *        {@linkplain EmailConfig#port() configured port number} will be used
   * @throws IllegalArgumentException if the configuration is invalid
   */
  @Inject
  public EmailSender(@Nullable EmailConfig config,
                     @Optional @Nullable OverridePort overridePort) {

    if (config != null) {
      verifyHostAndPort(config.host(), config.port());
      verifyEmailAddress(config.from());
      verifyEmailAddress(config.to());
    }

    this.config = config;
    this.overridePort = overridePort;
  }

  /**
   * A service that produces the port number of the mail server on demand.  If
   * this service exists, then the value returned by {@link #getPort()} is
   * considered to override the {@linkplain EmailConfig#port() configured port
   * number}.
   */
  @FunctionalInterface
  public interface OverridePort {
    /**
     * Returns the port number of the mail server.
     */
    int getPort();
  }

  private static void verifyHostAndPort(String host, int port) {
    Objects.requireNonNull(host);
    HostAndPort.fromParts(host, port);
  }

  private static void verifyEmailAddress(String emailAddress) {
    Objects.requireNonNull(emailAddress);
    try {
      new InternetAddress(emailAddress);
    } catch (AddressException e) {
      throw new IllegalArgumentException(
          "Invalid email address " + emailAddress,
          e);
    }
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

    int port =
        (overridePort == null)
            ? config.port()
            : overridePort.getPort();

    var from = new InternetAddress(config.from());
    var to = new InternetAddress(config.to());

    var environment = new Properties();
    environment.setProperty("mail.smtp.host", config.host());
    environment.setProperty("mail.smtp.port", String.valueOf(port));
    environment.setProperty("mail.smtp.auth", String.valueOf(true));
    environment.setProperty("mail.smtp.starttls.enable", "true");

    var authenticator =
        new PasswordAuthenticator(
            /* username= */ config.username(),
            /* password= */ config.password());

    Session session = Session.getInstance(environment, authenticator);

    var message = new MimeMessage(session);
    message.setFrom(from);
    message.setRecipient(Message.RecipientType.TO, to);
    message.setSubject(subject);

    if (attachments.isEmpty()) {
      message.setText(textContent, "utf-8");
    } else {
      var multipart = new MimeMultipart();
      var textBodyPart = new MimeBodyPart();
      textBodyPart.setText(textContent, "utf-8");
      multipart.addBodyPart(textBodyPart);
      for (DataSource attachment : attachments) {
        var fileBodyPart = new MimeBodyPart();
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

  private static final class PasswordAuthenticator extends Authenticator {
    private final String username;
    private final String password;

    PasswordAuthenticator(String username, String password) {
      this.username = Objects.requireNonNull(username);
      this.password = Objects.requireNonNull(password);
    }

    @Override
    protected PasswordAuthentication getPasswordAuthentication() {
      return new PasswordAuthentication(username, password);
    }
  }
}
