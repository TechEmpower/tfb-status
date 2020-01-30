package tfb.status.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;
import javax.inject.Singleton;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glassfish.hk2.api.PerLookup;
import tfb.status.hk2.extensions.Provides;

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
  @Provides
  public final HttpServerConfig http;

  /**
   * See {@link AssetsConfig}.
   */
  @Provides
  public final AssetsConfig assets;

  /**
   * See {@link MustacheConfig}.
   */
  @Provides
  public final MustacheConfig mustache;

  /**
   * See {@link FileStoreConfig}.
   */
  @Provides
  public final FileStoreConfig fileStore;

  /**
   * See {@link RunProgressMonitorConfig}.
   */
  @Provides
  public final RunProgressMonitorConfig runProgressMonitor;

  /**
   * See {@link RunCompleteMailerConfig}.
   */
  @Provides
  public final RunCompleteMailerConfig runCompleteMailer;

  /**
   * See {@link UrlsConfig}.
   */
  @Provides
  public final UrlsConfig urlsConfig;

  /**
   * See {@link ShareResultsMailerConfig}
   */
  @Provides
  public final ShareResultsMailerConfig shareResultsMailer;

  /**
   * The configuration for outbound emails, or {@code null} if outbound emails
   * are disabled.  See {@link EmailConfig}.
   */
  public final @Nullable EmailConfig email;

  // We can't annotate the `email` field directly with @Provides.  The scope it
  // would inherit from this class is @Singleton, and @Singleton doesn't support
  // null values.  We want the scope of the field to be @PerLookup, which does
  // support null values, but @PerLookup can't target fields.
  @Provides
  @PerLookup
  public @Nullable EmailConfig email() {
    return email;
  }

  public ApplicationConfig(HttpServerConfig http,
                           AssetsConfig assets,
                           MustacheConfig mustache,
                           FileStoreConfig fileStore,
                           RunProgressMonitorConfig runProgressMonitor,
                           RunCompleteMailerConfig runCompleteMailer,
                           @Nullable EmailConfig email,
                           UrlsConfig urlsConfig,
                           ShareResultsMailerConfig shareResultsMailer) {

    this.http = Objects.requireNonNull(http);
    this.assets = Objects.requireNonNull(assets);
    this.mustache = Objects.requireNonNull(mustache);
    this.fileStore = Objects.requireNonNull(fileStore);
    this.runProgressMonitor = Objects.requireNonNull(runProgressMonitor);
    this.runCompleteMailer = Objects.requireNonNull(runCompleteMailer);
    this.email = email;
    this.urlsConfig = Objects.requireNonNull(urlsConfig);
    this.shareResultsMailer = Objects.requireNonNull(shareResultsMailer);
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
          && this.runCompleteMailer.equals(that.runCompleteMailer)
          && Objects.equals(this.email, that.email)
          && this.urlsConfig.equals(that.urlsConfig);
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
    hash = 31 * hash + runCompleteMailer.hashCode();
    hash = 31 * hash + Objects.hashCode(email);
    hash = 31 * hash + urlsConfig.hashCode();
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

      @JsonProperty(value = "runCompleteMailer", required = false)
      @Nullable RunCompleteMailerConfig runCompleteMailer,

      @JsonProperty(value = "email", required = false)
      @Nullable EmailConfig email,

      @JsonProperty(value = "urls", required = false)
      @Nullable UrlsConfig urls,

      @JsonProperty(value = "shareResultsMailer", required = false)
      @Nullable ShareResultsMailerConfig shareResultsMailer) {

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

        /* email= */
        email,

        /* urlsConfig= */
        Objects.requireNonNullElseGet(
            urls,
            () -> UrlsConfig.defaultConfig()),

        /* shareResultsMailer= */
        Objects.requireNonNullElseGet(
            shareResultsMailer,
            () -> ShareResultsMailerConfig.defaultConfig()));
  }

  public static ApplicationConfig defaultConfig() {
    return create(null, null, null, null, null, null, null, null, null);
  }
}
