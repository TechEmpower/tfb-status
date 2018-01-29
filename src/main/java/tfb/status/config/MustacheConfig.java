package tfb.status.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * The configuration for Mustache templates rendered by the application.
 */
@Immutable
public final class MustacheConfig {
  public final ResourceMode mode;
  public final String root;

  @JsonCreator
  public MustacheConfig(

      @Nullable
      @JsonProperty(value = "mode", required = false)
      ResourceMode mode,

      @Nullable
      @JsonProperty(value = "root", required = false)
      String root) {

    this.mode = Objects.requireNonNullElseGet(mode, ResourceMode::defaultMode);
    this.root = Objects.requireNonNullElseGet(root, () -> defaultRoot(this.mode));
  }

  private static String defaultRoot(ResourceMode mode) {
    switch (mode) {
      case CLASS_PATH:  return "mustache";
      case FILE_SYSTEM: return "src/main/resources/mustache";
    }
    throw new AssertionError("Unknown resource mode: " + mode);
  }
}
