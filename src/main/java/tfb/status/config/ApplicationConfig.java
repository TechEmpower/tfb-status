package tfb.status.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.errorprone.annotations.Immutable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.inject.Singleton;

/**
 * The parent configuration object for this entire application, containing all
 * other component-specific configuration objects.
 *
 * <p>This class provides factory methods for obtaining a default configuration
 * and for reading a configuration from a YAML file.  See {@link
 * #defaultConfig()} and {@link #readYamlFile(Path)}.
 */
@Immutable
@Singleton
public final class ApplicationConfig {
  public final HttpServerConfig http;
  public final AssetsConfig assets;
  public final MustacheConfig mustache;
  public final FileStoreConfig fileStore;

  /**
   * The configuration for outbound emails, or {@code null} if outbound emails
   * are disabled.
   */
  @Nullable public final EmailConfig email;

  @JsonCreator
  public ApplicationConfig(

      @Nullable
      @JsonProperty(value = "http", required = false)
      HttpServerConfig http,

      @Nullable
      @JsonProperty(value = "assets", required = false)
      AssetsConfig assets,

      @Nullable
      @JsonProperty(value = "mustache", required = false)
      MustacheConfig mustache,

      @Nullable
      @JsonProperty(value = "fileStore", required = false)
      FileStoreConfig fileStore,

      @Nullable
      @JsonProperty(value = "email", required = false)
      EmailConfig email) {

    this.http =
        Objects.requireNonNullElseGet(
            http,
            () -> new HttpServerConfig(null, null, null));

    this.assets =
        Objects.requireNonNullElseGet(
            assets,
            () -> new AssetsConfig(null, null));

    this.mustache =
        Objects.requireNonNullElseGet(
            mustache,
            () -> new MustacheConfig(null, null));

    this.fileStore =
        Objects.requireNonNullElseGet(
            fileStore,
            () -> new FileStoreConfig(null));

    this.email = email;
  }

  /**
   * Returns a default configuration for this application.
   */
  public static ApplicationConfig defaultConfig() {
    return new ApplicationConfig(null, null, null, null, null);
  }

  /**
   * Reads this application's configuration from a YAML file on disk.
   *
   * @param yamlFile the YAML configuration file for this application
   * @return the configuration object for this application
   * @throws IllegalArgumentException if the file cannot be deserialized into a
   *         configuration object
   */
  public static ApplicationConfig readYamlFile(Path yamlFile) {
    Objects.requireNonNull(yamlFile);
    var yamlMapper = new ObjectMapper(new YAMLFactory());
    try (InputStream inputStream = Files.newInputStream(yamlFile)) {
      return yamlMapper.readValue(inputStream, ApplicationConfig.class);
    } catch (IOException e) {
      throw new IllegalArgumentException(
          "Unable to read configuration file \"" + yamlFile + "\"",
          e);
    }
  }
}
