package tfb.status.bootstrap;

import org.checkerframework.checker.nullness.qual.Nullable;
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
import tfb.status.handler.ShareResultsPageHandler;
import tfb.status.handler.ShareResultsUploadHandler;
import tfb.status.handler.ShareResultsViewHandler;
import tfb.status.handler.TimelinePageHandler;
import tfb.status.handler.UnzipResultsHandler;
import tfb.status.handler.UploadResultsHandler;
import tfb.status.hk2.extensions.ProvidesListener;
import tfb.status.hk2.extensions.TopicsModule;
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
import tfb.status.service.ShareResultsUploader;
import tfb.status.service.TaskScheduler;
import tfb.status.service.TickerFactory;

/**
 * Registers all of this application's service classes.
 */
public final class ServicesBinder extends AbstractBinder {
  /**
   * The path to this application's YAML configuration file, or {@code null} if
   * a default configuration should be used.
   */
  private final @Nullable String configFilePath;

  public ServicesBinder(@Nullable String configFilePath) {
    this.configFilePath = configFilePath;
  }

  @Override
  protected void configure() {
    install(new TopicsModule());

    if (configFilePath != null)
      bind(configFilePath)
          .to(String.class)
          .named(ApplicationConfigFactory.CONFIG_FILE_PATH);

    addActiveDescriptor(ProvidesListener.class);
    addActiveDescriptor(ApplicationConfigFactory.class);
    addActiveDescriptor(ObjectMapperFactory.class);
    addActiveDescriptor(ClockFactory.class);
    addActiveDescriptor(TickerFactory.class);
    addActiveDescriptor(FileSystemFactory.class);
    addActiveDescriptor(HttpServer.class);
    addActiveDescriptor(Authenticator.class);
    addActiveDescriptor(MustacheRenderer.class);
    addActiveDescriptor(HomeResultsReader.class);
    addActiveDescriptor(EmailSender.class);
    addActiveDescriptor(DiffGenerator.class);
    addActiveDescriptor(FileStore.class);
    addActiveDescriptor(RunProgressMonitor.class);
    addActiveDescriptor(RunCompleteMailer.class);
    addActiveDescriptor(TaskScheduler.class);
    addActiveDescriptor(ShareResultsUploader.class);
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
    addActiveDescriptor(ShareResultsUploadHandler.class);
    addActiveDescriptor(ShareResultsViewHandler.class);
    addActiveDescriptor(ShareResultsPageHandler.class);
  }
}
