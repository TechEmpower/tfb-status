package tfb.status.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * The configuration for the service that sends an email whenever a run
 * completes.
 *
 * @param minSecondsBetweenEmails The minimum number of seconds that must pass
 *        after sending one email before sending another.  This is used to
 *        prevent accidental self-spam when TFB has a problem that causes it to
 *        "complete" many runs very quickly.
 */
@Immutable
@Singleton
public record RunCompleteMailerConfig(long minSecondsBetweenEmails) {

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
