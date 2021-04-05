package tfb.status.testlib;

import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.mail.MessagingException;
import java.time.Duration;
import java.util.List;
import tfb.status.service.EmailSender;

/**
 * Estimates how long it will take to send emails.
 *
 * <p>This class may be useful in tests that (1) send email
 * <strong>asynchronously</strong>, (2) sleep, then (3) verify that the email
 * was received.  Specifically, {@link #timeToSendOneEmail()} may be used to
 * calculate how long to sleep.
 *
 * <p>In tests where the call to {@link EmailSender#sendEmail(String, String,
 * List)} is made on the current thread, there is no reason to use this class.
 * That call will block the current thread until the email is sent, so there is
 * no reason to sleep before verifying that the email was received.
 */
@Singleton
public final class MailDelay {
  private final Duration delay;

  @Inject
  public MailDelay(EmailSender emailSender)
      throws MessagingException {

    Stopwatch stopwatch =
        Stopwatch.createStarted(Ticker.systemTicker());

    //
    // On a healthy host, sending email should take milliseconds.  However,
    // we've seen this take 5 seconds on a Windows host due to the last method
    // call in this chain of calls:
    //
    //   emailSender.sendEmail(...)
    //   --> javax.mail.Transport#send(javax.mail.Message)
    //   --> javax.mail.internet.MimeMessage#saveChanges()
    //   --> java.net.InetAddress#getLocalHost()
    //   --> java.net.InetAddress#getCanonicalHostName()
    //
    // The issue was reproducible with this standalone piece of code:
    //
    //   java.net.InetAddress
    //       .getLocalHost()
    //       .getCanonicalHostName(); // <-- this method takes 5 seconds!
    //
    // For that host, the issue was fixable by modifying the priority of one
    // network adapter.  We expect the issue may also arise in other Windows
    // hosts that have Docker for Windows installed -- or, if not Docker for
    // Windows, then any other application that came with its own network
    // adapter.
    //
    emailSender.sendEmail(
        "mail delay test subject",
        "mail delay test content",
        List.of());

    delay = stopwatch.elapsed().plusMillis(100); // add buffer time
  }

  /**
   * Returns the amount of time that {@linkplain EmailSender#sendEmail(String,
   * String, List) sending one email} is expected to take.
   */
  public Duration timeToSendOneEmail() {
    return delay;
  }
}
