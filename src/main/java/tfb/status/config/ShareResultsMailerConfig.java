package tfb.status.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import java.time.Duration;
import java.util.Objects;
import javax.inject.Singleton;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * The configuration for the service that sends an emails related to sharing.
 */
@Immutable
@Singleton
public class ShareResultsMailerConfig {
  /**
   * The minimum number of seconds that must pass after sending one email before
   * sending another. This is used to prevent accidental self-spam if there are
   * a lot of requests to upload results when the share directory is full.
   */
  public final long minSecondsBetweenDirectoryFullEmails;

  public ShareResultsMailerConfig(long minSecondsBetweenDirectoryFullEmails) {
    this.minSecondsBetweenDirectoryFullEmails =
        minSecondsBetweenDirectoryFullEmails;
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (object == this) {
      return true;
    } else if (!(object instanceof ShareResultsMailerConfig)) {
      return false;
    } else {
      ShareResultsMailerConfig that = (ShareResultsMailerConfig) object;
      return this.minSecondsBetweenDirectoryFullEmails ==
          that.minSecondsBetweenDirectoryFullEmails;
    }
  }

  @Override
  public int hashCode() {
    int hash = 1;
    hash = 31 * hash + Long.hashCode(minSecondsBetweenDirectoryFullEmails);
    return hash;
  }

  @JsonCreator
  public static ShareResultsMailerConfig create(
      @JsonProperty(
          value = "minSecondsBetweenDirectoryFullEmails",
          required = false)
      @Nullable Long minSecondsBetweenDirectoryFullEmails) {

    return new ShareResultsMailerConfig(
        /* minSecondsBetweenDirectoryFullEmails= */
        Objects.requireNonNullElse(
            minSecondsBetweenDirectoryFullEmails,
            DEFAULT_MIN_SECONDS_BETWEEN_DIRECTORY_FULL_EMAILS));
  }

  public static ShareResultsMailerConfig defaultConfig() {
    return create(null);
  }

  private static final long DEFAULT_MIN_SECONDS_BETWEEN_DIRECTORY_FULL_EMAILS =
      Duration.ofDays(1).toSeconds();
}
