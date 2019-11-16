package tfb.status.bootstrap;

import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.glassfish.hk2.api.Factory;
import tfb.status.config.ApplicationConfig;
import tfb.status.config.HttpServerConfig;

/**
 * Provides the {@link HttpServerConfig} used by this application.
 */
@Singleton
final class HttpServerConfigFactory implements Factory<HttpServerConfig> {
  private final ApplicationConfig config;

  @Inject
  public HttpServerConfigFactory(ApplicationConfig config) {
    this.config = Objects.requireNonNull(config);
  }

  @Override
  @Singleton
  public HttpServerConfig provide() {
    return config.http;
  }

  @Override
  public void dispose(HttpServerConfig instance) {
    // No cleanup required.
  }
}
