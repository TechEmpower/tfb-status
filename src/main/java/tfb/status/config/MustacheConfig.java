package tfb.status.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import jakarta.inject.Singleton;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * The configuration for Mustache templates rendered by this application.
 */
@Immutable
@Singleton
public final class MustacheConfig {
  /**
   * Indicates whether Mustache templates will be loaded from the file system or
   * from the class path.  See {@link ResourceMode}.
   */
  public final ResourceMode mode;

  public MustacheConfig(ResourceMode mode) {
    this.mode = Objects.requireNonNull(mode);
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (object == this) {
      return true;
    } else if (!(object instanceof MustacheConfig)) {
      return false;
    } else {
      MustacheConfig that = (MustacheConfig) object;
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
  public static MustacheConfig create(
      @JsonProperty(value = "mode", required = false)
      @Nullable ResourceMode mode) {

    return new MustacheConfig(
        /* mode= */
        Objects.requireNonNullElseGet(
            mode,
            () -> ResourceMode.defaultMode()));
  }

  public static MustacheConfig defaultConfig() {
    return create(null);
  }
}
