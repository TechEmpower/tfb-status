package tfb.status.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import jakarta.inject.Singleton;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * The configuration for static assets served by this application.
 *
 * @param mode Indicates whether static assets will be loaded from the file
 *        system or from the class path.  See {@link ResourceMode}.
 */
@Immutable
@Singleton
public record AssetsConfig(ResourceMode mode) {

  public AssetsConfig {
    Objects.requireNonNull(mode);
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
