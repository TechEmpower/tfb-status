package tfb.status.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glassfish.hk2.api.Factory;
import org.jvnet.hk2.annotations.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.config.ApplicationConfig;

/**
 * Provides the {@link ApplicationConfig} used by this application.
 */
@Singleton
final class ApplicationConfigFactory implements Factory<ApplicationConfig> {
  private final FileSystem fileSystem;
  private final ObjectMapper objectMapper;
  private final @Nullable String path;
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Inject
  public ApplicationConfigFactory(
      FileSystem fileSystem,
      ObjectMapper objectMapper,
      @Optional @Named(CONFIG_FILE_PATH) @Nullable String path) {

    this.fileSystem = Objects.requireNonNull(fileSystem);
    this.objectMapper = Objects.requireNonNull(objectMapper);
    this.path = path;
  }

  @Override
  @Singleton
  public ApplicationConfig provide() {
    if (path == null) {
      logger.info("No configuration file; using default configuration");
      return ApplicationConfig.defaultConfig();
    }

    Path yamlFile = fileSystem.getPath(path);
    logger.info("Using custom configuration file \"" + yamlFile + "\"");

    var yamlMapper = new YAMLMapper();

    JsonNode tree;
    try (InputStream inputStream = Files.newInputStream(yamlFile)) {
      tree = yamlMapper.readTree(inputStream);
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Unable to read configuration file \"" + yamlFile + "\"",
          e);
    }

    if (tree.isEmpty()) {
      logger.info("Empty configuration file; using default configuration");
      return ApplicationConfig.defaultConfig();
    }

    try {
      return objectMapper.treeToValue(tree, ApplicationConfig.class);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void dispose(ApplicationConfig instance) {
    // No cleanup required.
  }

  static final String CONFIG_FILE_PATH = "tfb.status.configFilePath";
}
