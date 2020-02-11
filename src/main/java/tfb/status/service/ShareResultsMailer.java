package tfb.status.service;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.mail.MessagingException;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.config.SharingConfig;

/**
 * Sends an email when the {@link FileStore#shareDirectory()} is full.
 */
@Singleton
public final class ShareResultsMailer {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final Clock clock;
  private final EmailSender emailSender;
  private final SharingConfig config;

  @GuardedBy("emailTimeLock")
  private volatile @Nullable Instant previousEmailTime;
  private final Object emailTimeLock = new Object();

  @Inject
  public ShareResultsMailer(Clock clock,
                            EmailSender emailSender,
                            SharingConfig config) {

    this.clock = Objects.requireNonNull(clock);
    this.emailSender = Objects.requireNonNull(emailSender);
    this.config = Objects.requireNonNull(config);
  }

  /**
   * Notifies this service that the share directory is full.  This service may
   * respond by sending an email, assuming it has not sent a similar email too
   * recently.
   *
   * @param capacityBytes the maximum size of the share directory
   * @param sizeBytes the current size of the share directory
   */
  public void onShareDirectoryFull(long capacityBytes, long sizeBytes) {
    synchronized (emailTimeLock) {
      Instant now = clock.instant();
      Instant previous = this.previousEmailTime;

      if (previous != null) {
        Instant nextEmailTime =
            previous.plusSeconds(config.minSecondsBetweenEmails);

        if (now.isBefore(nextEmailTime)) {
          logger.warn(
              "Suppressing email for full share directory, "
                  + "another email was sent for that account too recently, "
                  + "previous email time = {}, next possible email time = {}",
              previous,
              nextEmailTime);
          return;
        }
      }

      this.previousEmailTime = now;
    }

    String textContent =
        "Hello,"
            + "\n"
            + "\n"
            + "The share directory used for storing public uploads of results "
            + "files has reached capacity.  Please audit the directory and "
            + "delete old uploads or expand the configured capacity."
            + "\n"
            + "\n"
            + "Share directory capacity: " + capacityBytes + " bytes"
            + "\n"
            + "Share directory size:     " + sizeBytes + " bytes"
            + "\n"
            + "\n"
            + "-a robot";

    try {
      emailSender.sendEmail(
          /* subject= */ SHARE_DIRECTORY_FULL_SUBJECT,
          /* textContent= */ textContent,
          /* attachments= */ ImmutableList.of());

    } catch (MessagingException e) {
      logger.warn("Error sending email for share directory full", e);
    }
  }

  @VisibleForTesting
  static final String SHARE_DIRECTORY_FULL_SUBJECT =
      "<tfb> <auto> Share directory full";
}
