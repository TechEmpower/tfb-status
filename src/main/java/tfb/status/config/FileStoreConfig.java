package tfb.status.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.inject.Singleton;

/**
 * The configuration for miscellaneous files managed by this application.
 */
@Immutable
@Singleton
public final class FileStoreConfig {
  /**
   * The root directory for miscellaneous files managed by this application.
   */
  public final String root;

  @JsonCreator
  public FileStoreConfig(

      @Nullable
      @JsonProperty(value = "root", required = false)
      String root) {

    this.root =
        Objects.requireNonNullElse(
            root,
            DEFAULT_ROOT);
  }

  private static final String DEFAULT_ROOT = "managed_files";
}
