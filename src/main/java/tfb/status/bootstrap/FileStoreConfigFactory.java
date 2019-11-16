package tfb.status.bootstrap;

import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.glassfish.hk2.api.Factory;
import tfb.status.config.ApplicationConfig;
import tfb.status.config.FileStoreConfig;

/**
 * Provides the {@link FileStoreConfig} used by this application.
 */
@Singleton
final class FileStoreConfigFactory implements Factory<FileStoreConfig> {
  private final ApplicationConfig config;

  @Inject
  public FileStoreConfigFactory(ApplicationConfig config) {
    this.config = Objects.requireNonNull(config);
  }

  @Override
  @Singleton
  public FileStoreConfig provide() {
    return config.fileStore;
  }

  @Override
  public void dispose(FileStoreConfig instance) {
    // No cleanup required.
  }
}
