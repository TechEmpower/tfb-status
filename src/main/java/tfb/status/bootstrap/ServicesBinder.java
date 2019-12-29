package tfb.status.bootstrap;

import java.util.Optional;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glassfish.hk2.api.TypeLiteral;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import tfb.status.handler.AboutPageHandler;
import tfb.status.handler.AssetsHandler;
import tfb.status.handler.AttributesPageHandler;
import tfb.status.handler.DetailPageHandler;
import tfb.status.handler.DownloadResultsHandler;
import tfb.status.handler.HomePageHandler;
import tfb.status.handler.HomeUpdatesHandler;
import tfb.status.handler.LastSeenCommitHandler;
import tfb.status.handler.RobotsHandler;
import tfb.status.handler.RootHandler;
import tfb.status.handler.SaveAttributesHandler;
import tfb.status.handler.TimelinePageHandler;
import tfb.status.handler.UnzipResultsHandler;
import tfb.status.handler.UploadResultsHandler;
import tfb.status.service.Authenticator;
import tfb.status.service.DiffGenerator;
import tfb.status.service.EmailSender;
import tfb.status.service.FileStore;
import tfb.status.service.HomeResultsReader;
import tfb.status.service.MustacheRenderer;
import tfb.status.service.RunProgressMonitor;
import tfb.status.service.TaskScheduler;

/**
 * Registers all of this application's service classes.
 */
public final class ServicesBinder extends AbstractBinder {
  private final @Nullable String configFilePath;

  /**
   * Constructs a binder for this application.
   *
   * @param configFilePath the path to this application's YAML configuration
   *        file, or {@code null} if a default configuration should be used
   */
  public ServicesBinder(@Nullable String configFilePath) {
    this.configFilePath = configFilePath;
  }

  @Override
  protected void configure() {
    bind(Optional.ofNullable(configFilePath))
        .to(new TypeLiteral<Optional<String>>() {})
        .named(ApplicationConfigFactory.CONFIG_FILE_PATH);

    addActiveFactoryDescriptor(ApplicationConfigFactory.class);
    addActiveFactoryDescriptor(AssetsConfigFactory.class);
    addActiveFactoryDescriptor(MustacheConfigFactory.class);
    addActiveFactoryDescriptor(FileStoreConfigFactory.class);
    addActiveFactoryDescriptor(HttpServerConfigFactory.class);
    addActiveFactoryDescriptor(EmailConfigFactory.class);
    addActiveFactoryDescriptor(RunProgressMonitorConfigFactory.class);

    addActiveFactoryDescriptor(ObjectMapperFactory.class);
    addActiveFactoryDescriptor(ClockFactory.class);
    addActiveFactoryDescriptor(TickerFactory.class);
    addActiveFactoryDescriptor(FileSystemFactory.class);

    addActiveDescriptor(HttpServer.class);
    addActiveDescriptor(RootHandler.class);
    addActiveDescriptor(HomePageHandler.class);
    addActiveDescriptor(HomeUpdatesHandler.class);
    addActiveDescriptor(UploadResultsHandler.class);
    addActiveDescriptor(RobotsHandler.class);
    addActiveDescriptor(DownloadResultsHandler.class);
    addActiveDescriptor(UnzipResultsHandler.class);
    addActiveDescriptor(TimelinePageHandler.class);
    addActiveDescriptor(DetailPageHandler.class);
    addActiveDescriptor(AboutPageHandler.class);
    addActiveDescriptor(AssetsHandler.class);
    addActiveDescriptor(AttributesPageHandler.class);
    addActiveDescriptor(SaveAttributesHandler.class);
    addActiveDescriptor(LastSeenCommitHandler.class);

    addActiveDescriptor(Authenticator.class);
    addActiveDescriptor(MustacheRenderer.class);
    addActiveDescriptor(HomeResultsReader.class);
    addActiveDescriptor(EmailSender.class);
    addActiveDescriptor(DiffGenerator.class);
    addActiveDescriptor(FileStore.class);
    addActiveDescriptor(RunProgressMonitor.class);
    addActiveDescriptor(TaskScheduler.class);
  }
}
