package tfb.status.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;
import javax.inject.Singleton;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * The configuration for Mustache templates rendered by this application.
 */
@Immutable
@Singleton
public final class MustacheConfig {
  /**
   * Indicates whether Mustache templates will be loaded from the file system or
   * from the class path.
   *
   * @see ResourceMode
   */
  public final ResourceMode mode;
  /**
   * The root directory for Mustache templates rendered by this application.
   */
  public final String root;

  @JsonCreator
  public MustacheConfig(

      @JsonProperty(value = "mode", required = false)
      @Nullable ResourceMode mode,

      @JsonProperty(value = "root", required = false)
      @Nullable String root) {

    this.mode = Objects.requireNonNullElseGet(mode, () -> ResourceMode.defaultMode());
    this.root = Objects.requireNonNullElseGet(root, () -> defaultRoot(this.mode));
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) {
      return true;
    } else if (!(object instanceof MustacheConfig)) {
      return false;
    } else {
      MustacheConfig that = (MustacheConfig) object;
      return this.mode == that.mode
          && this.root.equals(that.root);
    }
  }

  @Override
  public int hashCode() {
    int hash = 1;
    hash = 31 * hash + mode.hashCode();
    hash = 31 * hash + root.hashCode();
    return hash;
  }

  private static String defaultRoot(ResourceMode mode) {
    switch (mode) {
      case CLASS_PATH:  return "mustache";
      case FILE_SYSTEM: return "src/main/resources/mustache";
    }
    throw new AssertionError("Unknown resource mode: " + mode);
  }
}
