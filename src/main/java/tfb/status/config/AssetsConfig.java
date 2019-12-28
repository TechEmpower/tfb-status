package tfb.status.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;
import javax.inject.Singleton;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * The configuration for static assets served by this application.
 */
@Immutable
@Singleton
public final class AssetsConfig {
  /**
   * Indicates whether static assets will be loaded from the file system or from
   * the class path.  See {@link ResourceMode}.
   */
  public final ResourceMode mode;

  public AssetsConfig(ResourceMode mode) {
    this.mode = Objects.requireNonNull(mode);
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (object == this) {
      return true;
    } else if (!(object instanceof AssetsConfig)) {
      return false;
    } else {
      AssetsConfig that = (AssetsConfig) object;
      return this.mode == that.mode;
    }
  }

  @Override
  public int hashCode() {
    int hash = 1;
    hash = 31 * hash + mode.hashCode();
    return hash;
  }

  @JsonCreator
  public static AssetsConfig create(
      @JsonProperty(value = "mode", required = false)
      @Nullable ResourceMode mode) {

    return new AssetsConfig(
        /* mode= */
        Objects.requireNonNullElseGet(
            mode,
            () -> ResourceMode.defaultMode()));
  }

  public static AssetsConfig defaultConfig() {
    return create(null);
  }
}
