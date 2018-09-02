package tfb.status.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.inject.Singleton;

/**
 * The configuration for static assets served by this application.
 */
@Immutable
@Singleton
public final class AssetsConfig {
  public final ResourceMode mode;
  public final String root;

  @JsonCreator
  public AssetsConfig(

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
      case CLASS_PATH:  return "assets";
      case FILE_SYSTEM: return "src/main/resources/assets";
    }
    throw new AssertionError("Unknown resource mode: " + mode);
  }
}
