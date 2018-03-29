package tfb.status.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * The parent configuration object for the entire application, containing all
 * other component-specific configuration objects.
 */
@Immutable
public final class ApplicationConfig {
  public final HttpServerConfig http;
  public final AssetsConfig assets;
  public final MustacheConfig mustache;
  public final FileStoreConfig fileStore;

  /**
   * The configuration for outbound emails, or {@code null} if outbound emails
   * are disabled.
   */
  @Nullable public final EmailConfig email;

  @JsonCreator
  public ApplicationConfig(

      @Nullable
      @JsonProperty(value = "http", required = false)
      HttpServerConfig http,

      @Nullable
      @JsonProperty(value = "assets", required = false)
      AssetsConfig assets,

      @Nullable
      @JsonProperty(value = "mustache", required = false)
      MustacheConfig mustache,

      @Nullable
      @JsonProperty(value = "fileStore", required = false)
      FileStoreConfig fileStore,

      @Nullable
      @JsonProperty(value = "email", required = false)
      EmailConfig email) {

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
            () -> new FileStoreConfig(null, null, null, null));

    this.email = email;
  }
}
