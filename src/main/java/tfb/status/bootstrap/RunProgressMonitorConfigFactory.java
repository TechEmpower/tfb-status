package tfb.status.bootstrap;

import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.glassfish.hk2.api.Factory;
import tfb.status.config.ApplicationConfig;
import tfb.status.config.RunProgressMonitorConfig;

/**
 * Provides the {@link RunProgressMonitorConfig} used by this application.
 */
@Singleton
final class RunProgressMonitorConfigFactory
    implements Factory<RunProgressMonitorConfig> {

  private final ApplicationConfig config;

  @Inject
  public RunProgressMonitorConfigFactory(ApplicationConfig config) {
    this.config = Objects.requireNonNull(config);
  }

  @Override
  @Singleton
  public RunProgressMonitorConfig provide() {
    return config.runProgressMonitor;
  }

  @Override
  public void dispose(RunProgressMonitorConfig instance) {
    // No cleanup required.
  }
}
