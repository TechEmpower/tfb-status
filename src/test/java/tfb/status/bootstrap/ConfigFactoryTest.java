package tfb.status.bootstrap;

import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.config.ApplicationConfig;
import tfb.status.config.AssetsConfig;
import tfb.status.config.EmailConfig;
import tfb.status.config.FileStoreConfig;
import tfb.status.config.HttpServerConfig;
import tfb.status.config.MustacheConfig;
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
 * @see EmailConfigFactory
 */
@ExtendWith(TestServicesInjector.class)
public final class ConfigFactoryTest {
  /**
   * Verifies that all configuration objects are available for injection.
   */
  @Test
  public void testAllConfigsInjectable(ApplicationConfig applicationConfig,
                                       HttpServerConfig httpServerConfig,
                                       AssetsConfig assetsConfig,
                                       MustacheConfig mustacheConfig,
                                       FileStoreConfig fileStoreConfig,
                                       RunProgressMonitorConfig runProgressMonitorConfig,
                                       Optional<EmailConfig> optionalEmailConfig) {

    assertSame(applicationConfig.http, httpServerConfig);
    assertSame(applicationConfig.assets, assetsConfig);
    assertSame(applicationConfig.mustache, mustacheConfig);
    assertSame(applicationConfig.fileStore, fileStoreConfig);
    assertSame(applicationConfig.runProgressMonitor, runProgressMonitorConfig);
    assertSame(applicationConfig.email, optionalEmailConfig.orElse(null));
  }
}
