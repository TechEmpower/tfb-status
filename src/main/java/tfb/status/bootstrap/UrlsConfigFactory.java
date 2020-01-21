package tfb.status.bootstrap;

import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.glassfish.hk2.api.Factory;
import tfb.status.config.ApplicationConfig;
import tfb.status.config.UrlsConfig;

/**
 * Provides the {@link UrlsConfig} used by this application.
 */
@Singleton
final class UrlsConfigFactory implements Factory<UrlsConfig> {
  private final ApplicationConfig config;

  @Inject
  public UrlsConfigFactory(ApplicationConfig config) {
    this.config = Objects.requireNonNull(config);
  }

  @Override
  public UrlsConfig provide() {
    return config.urls;
  }

  @Override
  public void dispose(UrlsConfig instance) {
    // No cleanup required.
  }
}
