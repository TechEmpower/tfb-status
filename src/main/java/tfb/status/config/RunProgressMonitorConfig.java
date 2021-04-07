package tfb.status.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * The configuration for the service that monitors progress of benchmarking
 * environments.
 *
 * @param environmentTimeoutSeconds The number of seconds to wait before
 *        assuming that a benchmarking environment has crashed.
 * @param maxEnvironments The maximum number of benchmarking environments that
 *        are expected to report progress.
 */
@Immutable
@Singleton
public record RunProgressMonitorConfig(long environmentTimeoutSeconds,
                                       int maxEnvironments) {

  @JsonCreator
  public static RunProgressMonitorConfig create(
      @JsonProperty(value = "environmentTimeoutSeconds", required = false)
      @Nullable Long environmentTimeoutSeconds,

      @JsonProperty(value = "maxEnvironments", required = false)
      @Nullable Integer maxEnvironments) {

    return new RunProgressMonitorConfig(
        /* environmentTimeoutSeconds= */
        Objects.requireNonNullElse(
            environmentTimeoutSeconds,
            DEFAULT_ENVIRONMENT_TIMEOUT_SECONDS),

        /* maxEnvironments= */
        Objects.requireNonNullElse(
            maxEnvironments,
            DEFAULT_MAX_ENVIRONMENTS));
  }

  public static RunProgressMonitorConfig defaultConfig() {
    return create(null, null);
  }

  private static final long DEFAULT_ENVIRONMENT_TIMEOUT_SECONDS =
      Duration.ofHours(6).toSeconds();

  private static final int DEFAULT_MAX_ENVIRONMENTS = 5;
}
