package tfb.status.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import jakarta.inject.Singleton;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glassfish.hk2.api.PerLookup;
import org.glassfish.hk2.extras.provides.Provides;

/**
 * The parent configuration object for this entire application, containing all
 * other component-specific configuration objects.
 *
 * @param http See {@link HttpServerConfig}.
 * @param assets See {@link AssetsConfig}.
 * @param mustache See {@link MustacheConfig}.
 * @param fileStore See {@link FileStoreConfig}.
 * @param runProgressMonitor See {@link RunProgressMonitorConfig}.
 * @param runCompleteMailer See {@link RunCompleteMailerConfig}.
 * @param share See {@link ShareConfig}.
 * @param healthCheck See {@link HealthCheckConfig}.
 * @param email The configuration for outbound emails, or {@code null} if
 *              outbound emails are disabled.  See {@link EmailConfig}.
 */
@Immutable
@Singleton
public record ApplicationConfig(@Provides HttpServerConfig http,
                                @Provides AssetsConfig assets,
                                @Provides MustacheConfig mustache,
                                @Provides FileStoreConfig fileStore,
                                @Provides RunProgressMonitorConfig runProgressMonitor,
                                @Provides RunCompleteMailerConfig runCompleteMailer,
                                @Provides ShareConfig share,
                                @Provides HealthCheckConfig healthCheck,
                                @Provides @PerLookup @Nullable EmailConfig email) {

  public ApplicationConfig {
    Objects.requireNonNull(http);
    Objects.requireNonNull(assets);
    Objects.requireNonNull(mustache);
    Objects.requireNonNull(fileStore);
    Objects.requireNonNull(runProgressMonitor);
    Objects.requireNonNull(runCompleteMailer);
    Objects.requireNonNull(share);
    Objects.requireNonNull(healthCheck);
  }

  @JsonCreator
  public static ApplicationConfig create(
      @JsonProperty(value = "http", required = false)
      @Nullable HttpServerConfig http,

      @JsonProperty(value = "assets", required = false)
      @Nullable AssetsConfig assets,

      @JsonProperty(value = "mustache", required = false)
      @Nullable MustacheConfig mustache,

      @JsonProperty(value = "fileStore", required = false)
      @Nullable FileStoreConfig fileStore,

      @JsonProperty(value = "runProgressMonitor", required = false)
      @Nullable RunProgressMonitorConfig runProgressMonitor,

      @JsonProperty(value = "runCompleteMailer", required = false)
      @Nullable RunCompleteMailerConfig runCompleteMailer,

      @JsonProperty(value = "share", required = false)
      @Nullable ShareConfig share,

      @JsonProperty(value = "healthCheck", required = false)
      @Nullable HealthCheckConfig healthCheck,

      @JsonProperty(value = "email", required = false)
      @Nullable EmailConfig email) {

    return new ApplicationConfig(
        /* http= */
        Objects.requireNonNullElseGet(
            http,
            () -> HttpServerConfig.defaultConfig()),

        /* assets= */
        Objects.requireNonNullElseGet(
            assets,
            () -> AssetsConfig.defaultConfig()),

        /* mustache= */
        Objects.requireNonNullElseGet(
            mustache,
            () -> MustacheConfig.defaultConfig()),

        /* fileStore= */
        Objects.requireNonNullElseGet(
            fileStore,
            () -> FileStoreConfig.defaultConfig()),

        /* runProgressMonitor= */
        Objects.requireNonNullElseGet(
            runProgressMonitor,
            () -> RunProgressMonitorConfig.defaultConfig()),

        /* runCompleteMailer= */
        Objects.requireNonNullElseGet(
            runCompleteMailer,
            () -> RunCompleteMailerConfig.defaultConfig()),

        /* share= */
        Objects.requireNonNullElseGet(
            share,
            () -> ShareConfig.defaultConfig()),

        /* healthCheck= */
        Objects.requireNonNullElseGet(
            healthCheck,
            () -> HealthCheckConfig.defaultConfig()),

        /* email= */
        email);
  }

  public static ApplicationConfig defaultConfig() {
    return create(null, null, null, null, null, null, null, null, null);
  }
}
