package tfb.status.bootstrap;

import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.glassfish.hk2.api.Factory;
import tfb.status.config.ApplicationConfig;
import tfb.status.config.EmailConfig;

/**
 * Provides the {@link EmailConfig} used by this application.
 */
@Singleton
final class EmailConfigFactory implements Factory<Optional<EmailConfig>> {
  private final ApplicationConfig config;

  @Inject
  public EmailConfigFactory(ApplicationConfig config) {
    this.config = Objects.requireNonNull(config);
  }

  @Override
  @Singleton
  public Optional<EmailConfig> provide() {
    return Optional.ofNullable(config.email);
  }

  @Override
  public void dispose(Optional<EmailConfig> instance) {
    // No cleanup required.
  }
}
