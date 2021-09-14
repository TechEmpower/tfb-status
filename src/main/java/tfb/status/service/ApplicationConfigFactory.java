package tfb.status.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glassfish.hk2.extras.provides.Provides;
import org.jvnet.hk2.annotations.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.config.ApplicationConfig;

/**
 * Provides the {@link ApplicationConfig} used by this application.
 */
@Singleton
public final class ApplicationConfigFactory {
  private final FileSystem fileSystem;
  private final ObjectMapper objectMapper;
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Inject
  public ApplicationConfigFactory(FileSystem fileSystem,
                                  ObjectMapper objectMapper) {

    this.fileSystem = Objects.requireNonNull(fileSystem);
    this.objectMapper = Objects.requireNonNull(objectMapper);
  }

  @Provides
  public ApplicationConfig readConfigFile(@Optional
                                          @Named(CONFIG_FILE_PATH)
                                          @Nullable String path)
      throws IOException {

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
    }

    if (tree.isEmpty()) {
      logger.info("Empty configuration file; using default configuration");
      return ApplicationConfig.defaultConfig();
    }

    return objectMapper.treeToValue(tree, ApplicationConfig.class);
  }

  /**
   * The {@link Named#value()} of the {@link Optional} string that indicates the
   * path to this application's YAML configuration file.  If absent, a default
   * configuration will be used.
   */
  public static final String CONFIG_FILE_PATH = "tfb.status.configFilePath";
}
