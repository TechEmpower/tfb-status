package tfb.status.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import jakarta.inject.Singleton;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * The configuration for this application's health checks.
 *
 * @param intervalSeconds The number of seconds between the end of a health
 *        check and the start of the next health check.
 */
@Immutable
@Singleton
public record HealthCheckConfig(int intervalSeconds) {

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
