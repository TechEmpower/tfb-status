package tfb.status.bootstrap;

import javax.inject.Named;
import org.checkerframework.checker.nullness.qual.Nullable;
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
import tfb.status.hk2.extensions.Provides;
import tfb.status.hk2.extensions.Registers;
import tfb.status.service.ApplicationConfigFactory;
import tfb.status.service.Authenticator;
import tfb.status.service.ClockFactory;
import tfb.status.service.DiffGenerator;
import tfb.status.service.EmailSender;
import tfb.status.service.FileStore;
import tfb.status.service.FileSystemFactory;
import tfb.status.service.HomeResultsReader;
import tfb.status.service.HttpServer;
import tfb.status.service.MustacheRenderer;
import tfb.status.service.ObjectMapperFactory;
import tfb.status.service.RunCompleteMailer;
import tfb.status.service.RunProgressMonitor;
import tfb.status.service.TaskScheduler;
import tfb.status.service.TickerFactory;

/**
 * Registers all of this application's service classes.
 */
@Registers({
    ApplicationConfigFactory.class,
    ObjectMapperFactory.class,
    ClockFactory.class,
    TickerFactory.class,
    FileSystemFactory.class,
    HttpServer.class,
    Authenticator.class,
    MustacheRenderer.class,
    HomeResultsReader.class,
    EmailSender.class,
    DiffGenerator.class,
    FileStore.class,
    RunProgressMonitor.class,
    RunCompleteMailer.class,
    TaskScheduler.class,
    RootHandler.class,
    HomePageHandler.class,
    HomeUpdatesHandler.class,
    UploadResultsHandler.class,
    RobotsHandler.class,
    DownloadResultsHandler.class,
    UnzipResultsHandler.class,
    TimelinePageHandler.class,
    DetailPageHandler.class,
    AboutPageHandler.class,
    AssetsHandler.class,
    AttributesPageHandler.class,
    SaveAttributesHandler.class,
    LastSeenCommitHandler.class
})
public final class ServicesBinder {
  /**
   * The path to this application's YAML configuration file, or {@code null} if
   * a default configuration should be used.
   */
  @Provides
  @Named(ApplicationConfigFactory.CONFIG_FILE_PATH)
  public final @Nullable String configFilePath;

  public ServicesBinder(@Nullable String configFilePath) {
    this.configFilePath = configFilePath;
  }
}
