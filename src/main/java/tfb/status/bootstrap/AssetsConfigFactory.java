package tfb.status.bootstrap;

import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.glassfish.hk2.api.Factory;
import tfb.status.config.ApplicationConfig;
import tfb.status.config.AssetsConfig;

/**
 * Provides the {@link AssetsConfig} used by this application.
 */
@Singleton
final class AssetsConfigFactory implements Factory<AssetsConfig> {
  private final ApplicationConfig config;

  @Inject
  public AssetsConfigFactory(ApplicationConfig config) {
    this.config = Objects.requireNonNull(config);
  }

  @Override
  @Singleton
  public AssetsConfig provide() {
    return config.assets;
  }

  @Override
  public void dispose(AssetsConfig instance) {
    // No cleanup required.
  }
}
