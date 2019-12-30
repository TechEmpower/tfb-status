package tfb.status.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javax.inject.Provider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.config.ApplicationConfig;
import tfb.status.config.AssetsConfig;
import tfb.status.config.EmailConfig;
import tfb.status.config.FileStoreConfig;
import tfb.status.config.HttpServerConfig;
import tfb.status.config.MustacheConfig;
import tfb.status.config.RunCompleteMailerConfig;
import tfb.status.config.RunProgressMonitorConfig;
import tfb.status.testlib.TestServicesInjector;

/**
 * Tests for all the {@code *ConfigFactory} classes.
 *
 * @see ApplicationConfigFactory
 * @see HttpServerConfigFactory
 * @see AssetsConfigFactory
 * @see MustacheConfigFactory
 * @see FileStoreConfigFactory
 * @see RunProgressMonitorConfigFactory
 * @see RunCompleteMailerConfigFactory
 * @see EmailConfigFactory
 */
@ExtendWith(TestServicesInjector.class)
public final class ConfigFactoryTest {
  /**
   * Verifies that all configuration objects are available for injection.
   */
  @Test
  public void testAllConfigsInjectable(
      Provider<ApplicationConfig> applicationConfigProvider,
      Provider<HttpServerConfig> httpServerConfigProvider,
      Provider<AssetsConfig> assetsConfigProvider,
      Provider<MustacheConfig> mustacheConfigProvider,
      Provider<FileStoreConfig> fileStoreConfigProvider,
      Provider<RunProgressMonitorConfig> runProgressMonitorConfigProvider,
      Provider<RunCompleteMailerConfig> runCompleteMailerConfigProvider,
      Provider<Optional<EmailConfig>> optionalEmailConfigProvider) {

    ApplicationConfig config = applicationConfigProvider.get();
    assertNotNull(config);

    assertEquals(
        config.http,
        httpServerConfigProvider.get());

    assertEquals(
        config.assets,
        assetsConfigProvider.get());

    assertEquals(
        config.mustache,
        mustacheConfigProvider.get());

    assertEquals(
        config.fileStore,
        fileStoreConfigProvider.get());

    assertEquals(
        config.runProgressMonitor,
        runProgressMonitorConfigProvider.get());

    assertEquals(
        config.runCompleteMailer,
        runCompleteMailerConfigProvider.get());

    assertEquals(
        Optional.ofNullable(config.email),
        optionalEmailConfigProvider.get());
  }

  /**
   * Verifies that the default configuration is used when no configuration file
   * is specified.
   */
  @Test
  public void testNoConfigFile(FileSystem fileSystem,
                               ObjectMapper objectMapper) {

    var configFactory =
        new ApplicationConfigFactory(
            fileSystem,
            objectMapper,
            Optional.empty());

    ApplicationConfig config = configFactory.provide();

    assertEquals(
        ApplicationConfig.defaultConfig(),
        config);
  }

  /**
   * Verifies that the default configuration is used when an empty configuration
   * file is specified.
   */
  @Test
  public void testEmptyConfigFile(FileSystem fileSystem,
                                  ObjectMapper objectMapper)
      throws IOException {

    Path file = fileSystem.getPath("empty_config.yml");

    Files.write(
        file,
        List.of(
            "# This is a comment line.",
            "# This is another comment line."));

    var configFactory =
        new ApplicationConfigFactory(
            fileSystem,
            objectMapper,
            Optional.of(file.toString()));

    ApplicationConfig config = configFactory.provide();

    assertEquals(
        ApplicationConfig.defaultConfig(),
        config);
  }

  /**
   * Verifies that an exception is thrown when a configuration file is specified
   * but that file does not exist.
   */
  @Test
  public void testMissingConfigFile(FileSystem fileSystem,
                                    ObjectMapper objectMapper) {

    var configFactory =
        new ApplicationConfigFactory(
            fileSystem,
            objectMapper,
            Optional.of("missing_file.yml"));

    assertThrows(
        RuntimeException.class,
        () -> configFactory.provide());
  }
}
