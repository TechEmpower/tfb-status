package tfb.status.bootstrap;

import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.glassfish.hk2.api.Factory;
import tfb.status.config.ApplicationConfig;
import tfb.status.config.MustacheConfig;

/**
 * Provides the {@link MustacheConfig} used by this application.
 */
@Singleton
final class MustacheConfigFactory implements Factory<MustacheConfig> {
  private final ApplicationConfig config;

  @Inject
  public MustacheConfigFactory(ApplicationConfig config) {
    this.config = Objects.requireNonNull(config);
  }

  @Override
  @Singleton
  public MustacheConfig provide() {
    return config.mustache;
  }

  @Override
  public void dispose(MustacheConfig instance) {
    // No cleanup required.
  }
}
