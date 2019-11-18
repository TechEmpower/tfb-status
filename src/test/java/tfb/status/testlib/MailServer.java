package tfb.status.testlib;

import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.icegreen.greenmail.store.FolderException;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.mail.internet.MimeMessage;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glassfish.hk2.api.PreDestroy;
import tfb.status.config.EmailConfig;
import tfb.status.service.EmailSender;

/**
 * A locally-hosted mail server that receives emails from {@link EmailSender}
 * during tests.
 *
 * <p>This server does not start automatically.  Call {@link #start()} to begin
 * listening for incoming mail.
 */
@Singleton
public final class MailServer implements PreDestroy {
  @GuardedBy("this")
  private final @Nullable GreenMail server;

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

  @Override
  public void preDestroy() {
    stop();
  }

  /**
   * Starts this server.
   */
  public synchronized void start() {
    if (server != null)
      server.start();
  }

  /**
   * Stops this server.
   */
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
