package tfb.status.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glassfish.hk2.api.Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.config.ApplicationConfig;

/**
 * Provides the {@link ApplicationConfig} used by this application.
 */
@Singleton
final class ApplicationConfigFactory implements Factory<ApplicationConfig> {
  private final FileSystem fileSystem;
  private final @Nullable String path;
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Inject
  public ApplicationConfigFactory(
      FileSystem fileSystem,
      @Named(CONFIG_FILE_PATH) Optional<String> optionalPath) {

    this.fileSystem = Objects.requireNonNull(fileSystem);
    this.path = optionalPath.orElse(null);
  }

  @Override
  @Singleton
  public ApplicationConfig provide() {
    if (path == null) {
      logger.info("Using default configuration");
      return new ApplicationConfig(null, null, null, null, null, null);
    }

    Path yamlFile = fileSystem.getPath(path);
    logger.info("Using custom configuration file \"" + yamlFile + "\"");

    var yamlMapper = new ObjectMapper(new YAMLFactory());

    try (InputStream inputStream = Files.newInputStream(yamlFile)) {
      return yamlMapper.readValue(inputStream, ApplicationConfig.class);
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Unable to read configuration file \"" + yamlFile + "\"",
          e);
    }
  }

  @Override
  public void dispose(ApplicationConfig instance) {
    // No cleanup required.
  }

  static final String CONFIG_FILE_PATH = "tfb.status.configFilePath";
}
