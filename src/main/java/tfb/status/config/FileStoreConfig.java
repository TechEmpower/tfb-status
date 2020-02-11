package tfb.status.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;
import javax.inject.Singleton;
import org.checkerframework.checker.nullness.qual.Nullable;

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

  public FileStoreConfig(String root) {
    this.root = Objects.requireNonNull(root);
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (object == this) {
      return true;
    } else if (!(object instanceof FileStoreConfig)) {
      return false;
    } else {
      FileStoreConfig that = (FileStoreConfig) object;
      return this.root.equals(that.root);
    }
  }

  @Override
  public int hashCode() {
    int hash = 1;
    hash = 31 * hash + root.hashCode();
    return hash;
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
