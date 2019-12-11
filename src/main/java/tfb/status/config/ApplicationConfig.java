package tfb.status.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;
import javax.inject.Singleton;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * The parent configuration object for this entire application, containing all
 * other component-specific configuration objects.
 */
@Immutable
@Singleton
public final class ApplicationConfig {
  public final HttpServerConfig http;
  public final AssetsConfig assets;
  public final MustacheConfig mustache;
  public final FileStoreConfig fileStore;
  public final RunProgressMonitorConfig runProgressMonitor;

  /**
   * The configuration for outbound emails, or {@code null} if outbound emails
   * are disabled.
   */
  public final @Nullable EmailConfig email;

  @JsonCreator
  public ApplicationConfig(

      @JsonProperty(value = "http", required = false)
      @Nullable HttpServerConfig http,

      @JsonProperty(value = "assets", required = false)
      @Nullable AssetsConfig assets,

      @JsonProperty(value = "mustache", required = false)
      @Nullable MustacheConfig mustache,

      @JsonProperty(value = "fileStore", required = false)
      @Nullable FileStoreConfig fileStore,

      @JsonProperty(value = "email", required = false)
      @Nullable EmailConfig email,

      @JsonProperty(value = "runProgressMonitor", required = false)
      @Nullable RunProgressMonitorConfig runProgressMonitor) {

    this.http =
        Objects.requireNonNullElseGet(
            http,
            () -> new HttpServerConfig(null, null, null));

    this.assets =
        Objects.requireNonNullElseGet(
            assets,
            () -> new AssetsConfig(null, null));

    this.mustache =
        Objects.requireNonNullElseGet(
            mustache,
            () -> new MustacheConfig(null, null));

    this.fileStore =
        Objects.requireNonNullElseGet(
            fileStore,
            () -> new FileStoreConfig(null));

    this.email = email;

    this.runProgressMonitor =
        Objects.requireNonNullElseGet(
            runProgressMonitor,
            () -> new RunProgressMonitorConfig(null, null));
  }
}
