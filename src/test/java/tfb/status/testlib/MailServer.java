package tfb.status.testlib;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import java.io.IOException;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.mail.MessagingException;
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
   * Returns all email messages that have been received by this mail server
   * matching the specified filter.
   *
   * @param filter the filter to apply to the messages
   * @throws IllegalStateException if email is disabled
   * @throws IOException if the filter throws an {@link IOException}
   * @throws MessagingException if the filter throws a {@link MessagingException}
   */
  public synchronized ImmutableList<MimeMessage> getMessages(MessageFilter filter)
      throws IOException, MessagingException {

    if (server == null)
      throw new IllegalStateException(
          "Email is disabled in this application's config");

    var messages = new ImmutableList.Builder<MimeMessage>();

    for (MimeMessage message : server.getReceivedMessages()) {
      if (filter.test(message)) {
        messages.add(message);
      }
    }

    return messages.build();
  }

  /**
   * A function that accepts an email message and returns {@code true} if that
   * message satisfies some condition.
   *
   * <p>This interface should only be used by callers of {@link
   * #getMessages(MailServer.MessageFilter)}.
   */
  @FunctionalInterface
  public interface MessageFilter {
    /**
     * Returns {@code true} if the specified message matches this filter.
     */
    boolean test(MimeMessage message) throws IOException, MessagingException;
  }
}
