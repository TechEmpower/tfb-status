package tfb.status.testlib;

import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.icegreen.greenmail.store.FolderException;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.mail.internet.MimeMessage;
import tfb.status.config.EmailConfig;
import tfb.status.service.EmailSender;

/**
 * A locally-hosted mail server that receives emails from {@link EmailSender}
 * during tests.
 */
public final class MailServer {
  @GuardedBy("this") @Nullable private final GreenMail server;

  @Inject
  public MailServer(Optional<EmailConfig> optionalConfig) {
    EmailConfig config = optionalConfig.orElse(null);

    if (config == null)
      server = null;

    else
      server =
          new GreenMail(
              new ServerSetup(
                  /* port= */ config.port,
                  /* bindAddress= */ "localhost",
                  /* protocol= */ "smtp"));
  }

  /**
   * Starts this server.
   */
  @PostConstruct
  public synchronized void start() {
    if (server != null)
      server.start();
  }

  /**
   * Stops this server.
   */
  @PreDestroy
  public synchronized void stop() {
    if (server != null)
      server.stop();
  }

  /**
   * Returns the only email message that has been received by the email server
   * since the last time this method was invoked.
   *
   * @throws IllegalStateException if there is not exactly one email message
   */
  public synchronized MimeMessage onlyEmailMessage() {
    if (server == null)
      throw new IllegalStateException(
          "Email was disabled in this application's config");

    MimeMessage[] messages = server.getReceivedMessages();
    try {
      server.purgeEmailFromAllMailboxes();
    } catch (FolderException e) {
      throw new RuntimeException(e);
    }

    if (messages.length != 1)
      throw new IllegalStateException("There is not exactly one email");

    return messages[0];
  }
}
