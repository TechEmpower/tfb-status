package tfb.status.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import jakarta.inject.Singleton;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * The configuration for Mustache templates rendered by this application.
 *
 * @param mode Indicates whether Mustache templates will be loaded from the file
 *        system or from the class path.  See {@link ResourceMode}.
 */
@Immutable
@Singleton
public record MustacheConfig(ResourceMode mode) {

  public MustacheConfig {
    Objects.requireNonNull(mode);
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
