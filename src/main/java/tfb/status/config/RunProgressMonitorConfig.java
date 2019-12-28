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

  public RunProgressMonitorConfig(long environmentTimeoutSeconds,
                                  int maxEnvironments) {

    this.environmentTimeoutSeconds = environmentTimeoutSeconds;
    this.maxEnvironments = maxEnvironments;
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) {
      return true;
    } else if (!(object instanceof RunProgressMonitorConfig)) {
      return false;
    } else {
      RunProgressMonitorConfig that = (RunProgressMonitorConfig) object;
      return this.environmentTimeoutSeconds == that.environmentTimeoutSeconds
          && this.maxEnvironments == that.maxEnvironments;
    }
  }

  @Override
  public int hashCode() {
    int hash = 1;
    hash = 31 * hash + Long.hashCode(environmentTimeoutSeconds);
    hash = 31 * hash + Integer.hashCode(maxEnvironments);
    return hash;
  }

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
