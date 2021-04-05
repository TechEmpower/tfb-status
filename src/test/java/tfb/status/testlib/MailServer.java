package tfb.status.testlib;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.icegreen.greenmail.smtp.SmtpServer;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glassfish.hk2.api.PerLookup;
import org.glassfish.hk2.api.PreDestroy;
import tfb.status.config.EmailConfig;
import tfb.status.hk2.extensions.Provides;
import tfb.status.service.EmailSender;
import tfb.status.service.EmailSender.OverridePort;

/**
 * A locally-hosted mail server that receives emails from {@link EmailSender}
 * during tests.
 *
 * <p>This server does not start automatically.  Call {@link #start()} to begin
 * listening for incoming mail.
 */
@Singleton
public final class MailServer implements PreDestroy {
  @GuardedBy("this") private final @Nullable GreenMail server;
  @GuardedBy("this") private boolean isRunning;

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
   * Starts this server if it is currently stopped.
   */
  public synchronized void start() {
    if (isRunning) return;

    if (server != null)
      server.start();

    isRunning = true;
  }

  /**
   * Stops this server if it is currently running.
   */
  public synchronized void stop() {
    if (!isRunning) return;

    if (server != null)
      server.stop();

    isRunning = false;
  }

  /**
   * Returns the port number that has been assigned to this server.
   *
   * <p>When the {@linkplain EmailConfig#port configured port number} is
   * non-zero, the assigned port number will equal the configured port number.
   * Otherwise, when the configured port number is zero, the host system will
   * dynamically assign an ephemeral port for this server, and this method
   * returns that dynamically assigned port number.
   *
   * @throws IllegalStateException if this server is not running
   */
  public synchronized int assignedPort() {
    if (server == null)
      throw new IllegalStateException(
          "Email is disabled in this application's config");

    if (!isRunning)
      throw new IllegalStateException("This server is not running");

    SmtpServer smtp = Objects.requireNonNull(server.getSmtp());
    return smtp.getPort();
  }

  /**
   * Provides a service that reveals the {@link #assignedPort()} of this mail
   * server to the {@link EmailSender}.  This allows the server and client to
   * communicate over an ephemeral port.  Returns {@code null} when email is
   * disabled.
   */
  @Provides
  @PerLookup
  public synchronized @Nullable OverridePort overridePort() {
    if (server == null)
      return null;

    return () -> {
      // If we're using an ephemeral port, we won't know which port number we're
      // using until the server is started.
      start();
      return assignedPort();
    };
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
