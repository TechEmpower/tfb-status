package tfb.status.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import java.time.Duration;
import java.util.Objects;
import javax.inject.Singleton;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * The configuration for the service that monitors progress of benchmarking
 * environments.
 */
@Immutable
@Singleton
public final class RunProgressMonitorConfig {
  /**
   * The number of minutes to wait before assuming that a benchmarking
   * environment has crashed.
   */
  public final long environmentTimeoutSeconds;

  /**
   * The maximum number of benchmarking environments that are expected to report
   * progress.
   */
  public final int maxEnvironments;

  @JsonCreator
  public RunProgressMonitorConfig(

      @JsonProperty(value = "environmentTimeoutSeconds", required = false)
      @Nullable Long environmentTimeoutSeconds,

      @JsonProperty(value = "maxEnvironments", required = false)
      @Nullable Integer maxEnvironments) {

    this.environmentTimeoutSeconds =
        Objects.requireNonNullElse(
            environmentTimeoutSeconds,
            DEFAULT_ENVIRONMENT_TIMEOUT_SECONDS);

    this.maxEnvironments =
        Objects.requireNonNullElse(
            maxEnvironments,
            DEFAULT_MAX_ENVIRONMENTS);
  }

  private static final long DEFAULT_ENVIRONMENT_TIMEOUT_SECONDS =
      Duration.ofHours(6).toSeconds();

  private static final int DEFAULT_MAX_ENVIRONMENTS = 5;
}