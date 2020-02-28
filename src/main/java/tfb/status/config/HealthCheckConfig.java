package tfb.status.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;
import javax.inject.Singleton;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * The configuration for this application's health checks.
 */
@Immutable
@Singleton
public final class HealthCheckConfig {
  /**
   * The number of seconds between the end of a health check and the start of
   * the next health check.
   */
  public final int intervalSeconds;

  public HealthCheckConfig(int intervalSeconds) {
    this.intervalSeconds = intervalSeconds;
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (object == this) {
      return true;
    } else if (!(object instanceof HealthCheckConfig)) {
      return false;
    } else {
      HealthCheckConfig that = (HealthCheckConfig) object;
      return this.intervalSeconds == that.intervalSeconds;
    }
  }

  @Override
  public int hashCode() {
    int hash = 1;
    hash = 31 * hash + Integer.hashCode(intervalSeconds);
    return hash;
  }

  @JsonCreator
  public static HealthCheckConfig create(
      @JsonProperty(value = "intervalSeconds", required = false)
      @Nullable Integer intervalSeconds) {

    return new HealthCheckConfig(
        /* intervalSeconds= */
        Objects.requireNonNullElse(
            intervalSeconds,
            DEFAULT_INTERVAL_SECONDS));
  }

  public static HealthCheckConfig defaultConfig() {
    return create(null);
  }

  // TODO: Is this too large?  It seems large.
  private static final int DEFAULT_INTERVAL_SECONDS = 30;
}
