package tfb.status.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import jakarta.inject.Singleton;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * The configuration for miscellaneous files managed by this application.
 *
 * @param root The root directory for miscellaneous files managed by this
 *        application.
 */
@Immutable
@Singleton
public record FileStoreConfig(String root) {

  public FileStoreConfig {
    Objects.requireNonNull(root);
  }

  @JsonCreator
  public static FileStoreConfig create(
      @JsonProperty(value = "root", required = false)
      @Nullable String root) {

    return new FileStoreConfig(
        /* root= */
        Objects.requireNonNullElse(
            root,
            DEFAULT_ROOT));
  }

  public static FileStoreConfig defaultConfig() {
    return create(null);
  }

  private static final String DEFAULT_ROOT = "managed_files";
}
