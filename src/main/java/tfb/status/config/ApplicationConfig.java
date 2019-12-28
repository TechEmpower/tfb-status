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
  /**
   * See {@link HttpServerConfig}.
   */
  public final HttpServerConfig http;

  /**
   * See {@link AssetsConfig}.
   */
  public final AssetsConfig assets;

  /**
   * See {@link MustacheConfig}.
   */
  public final MustacheConfig mustache;

  /**
   * See {@link FileStoreConfig}.
   */
  public final FileStoreConfig fileStore;

  /**
   * See {@link RunProgressMonitorConfig}.
   */
  public final RunProgressMonitorConfig runProgressMonitor;

  /**
   * The configuration for outbound emails, or {@code null} if outbound emails
   * are disabled.  See {@link EmailConfig}.
   */
  public final @Nullable EmailConfig email;

  public ApplicationConfig(HttpServerConfig http,
                           AssetsConfig assets,
                           MustacheConfig mustache,
                           FileStoreConfig fileStore,
                           RunProgressMonitorConfig runProgressMonitor,
                           @Nullable EmailConfig email) {

    this.http = Objects.requireNonNull(http);
    this.assets = Objects.requireNonNull(assets);
    this.mustache = Objects.requireNonNull(mustache);
    this.fileStore = Objects.requireNonNull(fileStore);
    this.runProgressMonitor = Objects.requireNonNull(runProgressMonitor);
    this.email = email;
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (object == this) {
      return true;
    } else if (!(object instanceof ApplicationConfig)) {
      return false;
    } else {
      ApplicationConfig that = (ApplicationConfig) object;
      return this.http.equals(that.http)
          && this.assets.equals(that.assets)
          && this.mustache.equals(that.mustache)
          && this.fileStore.equals(that.fileStore)
          && this.runProgressMonitor.equals(that.runProgressMonitor)
          && Objects.equals(this.email, that.email);
    }
  }

  @Override
  public int hashCode() {
    int hash = 1;
    hash = 31 * hash + http.hashCode();
    hash = 31 * hash + assets.hashCode();
    hash = 31 * hash + mustache.hashCode();
    hash = 31 * hash + fileStore.hashCode();
    hash = 31 * hash + runProgressMonitor.hashCode();
    hash = 31 * hash + Objects.hashCode(email);
    return hash;
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

        /* email= */
        email);
  }

  public static ApplicationConfig defaultConfig() {
    return create(null, null, null, null, null, null);
  }
}
