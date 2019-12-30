package tfb.status.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import java.time.Duration;
import java.util.Objects;
import javax.inject.Singleton;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * The configuration for the service that sends an email whenever a run
 * completes.
 */
@Immutable
@Singleton
public final class RunCompleteMailerConfig {
  /**
   * The minimum number of seconds that must pass after sending one email before
   * sending another.  This is used to prevent accidental self-spam when TFB has
   * a problem that causes it to "complete" many runs very quickly.
   */
  public final long minSecondsBetweenEmails;

  public RunCompleteMailerConfig(long minSecondsBetweenEmails) {
    this.minSecondsBetweenEmails = minSecondsBetweenEmails;
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (object == this) {
      return true;
    } else if (!(object instanceof RunCompleteMailerConfig)) {
      return false;
    } else {
      RunCompleteMailerConfig that = (RunCompleteMailerConfig) object;
      return this.minSecondsBetweenEmails == that.minSecondsBetweenEmails;
    }
  }

  @Override
  public int hashCode() {
    int hash = 1;
    hash = 31 * hash + Long.hashCode(minSecondsBetweenEmails);
    return hash;
  }

  @JsonCreator
  public static RunCompleteMailerConfig create(
      @JsonProperty(value = "minSecondsBetweenEmails", required = false)
      @Nullable Long minSecondsBetweenEmails) {

    return new RunCompleteMailerConfig(
        /* minSecondsBetweenEmails= */
        Objects.requireNonNullElse(
            minSecondsBetweenEmails,
            DEFAULT_MIN_SECONDS_BETWEEN_EMAILS));
  }

  public static RunCompleteMailerConfig defaultConfig() {
    return create(null);
  }

  private static final long DEFAULT_MIN_SECONDS_BETWEEN_EMAILS =
      Duration.ofDays(1).toSeconds();
}
