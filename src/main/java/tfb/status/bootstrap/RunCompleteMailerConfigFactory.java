package tfb.status.bootstrap;

import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.glassfish.hk2.api.Factory;
import tfb.status.config.ApplicationConfig;
import tfb.status.config.RunCompleteMailerConfig;

/**
 * Provides the {@link RunCompleteMailerConfig} used by this application.
 */
@Singleton
final class RunCompleteMailerConfigFactory
    implements Factory<RunCompleteMailerConfig> {

  private final ApplicationConfig config;

  @Inject
  public RunCompleteMailerConfigFactory(ApplicationConfig config) {
    this.config = Objects.requireNonNull(config);
  }

  @Override
  @Singleton
  public RunCompleteMailerConfig provide() {
    return config.runCompleteMailer;
  }

  @Override
  public void dispose(RunCompleteMailerConfig instance) {
    // No cleanup required.
  }
}
