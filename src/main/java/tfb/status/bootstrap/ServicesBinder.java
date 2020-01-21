package tfb.status.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Ticker;
import io.undertow.server.HttpHandler;
import java.nio.file.FileSystem;
import java.time.Clock;
import java.util.Optional;
import javax.inject.Singleton;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glassfish.hk2.api.TypeLiteral;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import tfb.status.config.ApplicationConfig;
import tfb.status.config.AssetsConfig;
import tfb.status.config.EmailConfig;
import tfb.status.config.FileStoreConfig;
import tfb.status.config.HttpServerConfig;
import tfb.status.config.MustacheConfig;
import tfb.status.config.RunProgressMonitorConfig;
import tfb.status.config.UrlsConfig;
import tfb.status.handler.AboutPageHandler;
import tfb.status.handler.AssetsHandler;
import tfb.status.handler.AttributesPageHandler;
import tfb.status.handler.DetailPageHandler;
import tfb.status.handler.DownloadResultsHandler;
import tfb.status.handler.ExportResultsHandler;
import tfb.status.handler.HomePageHandler;
import tfb.status.handler.HomeUpdatesHandler;
import tfb.status.handler.LastSeenCommitHandler;
import tfb.status.handler.RobotsHandler;
import tfb.status.handler.RootHandler;
import tfb.status.handler.SaveAttributesHandler;
import tfb.status.handler.ShareResultsHandler;
import tfb.status.handler.ShareResultsPageHandler;
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
import tfb.status.service.ShareResultsUploader;
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

    bindFactory(ApplicationConfigFactory.class, Singleton.class)
        .to(ApplicationConfig.class)
        .in(Singleton.class);

    bindFactory(AssetsConfigFactory.class, Singleton.class)
        .to(AssetsConfig.class)
        .in(Singleton.class);

    bindFactory(MustacheConfigFactory.class, Singleton.class)
        .to(MustacheConfig.class)
        .in(Singleton.class);

    bindFactory(FileStoreConfigFactory.class, Singleton.class)
        .to(FileStoreConfig.class)
        .in(Singleton.class);

    bindFactory(HttpServerConfigFactory.class, Singleton.class)
        .to(HttpServerConfig.class)
        .in(Singleton.class);

    bindFactory(EmailConfigFactory.class, Singleton.class)
        .to(new TypeLiteral<Optional<EmailConfig>>() {})
        .in(Singleton.class);

    bindFactory(UrlsConfigFactory.class, Singleton.class)
            .to(UrlsConfig.class)
            .in(Singleton.class);

    bindFactory(RunProgressMonitorConfigFactory.class, Singleton.class)
        .to(RunProgressMonitorConfig.class)
        .in(Singleton.class);

    bindAsContract(HttpServer.class).in(Singleton.class);
    bindAsContract(RootHandler.class).in(Singleton.class);

    bindAsContract(HomePageHandler.class)
        .to(HttpHandler.class)
        .in(Singleton.class);

    bindAsContract(HomeUpdatesHandler.class)
        .to(HttpHandler.class)
        .in(Singleton.class);

    bindAsContract(UploadResultsHandler.class)
        .to(HttpHandler.class)
        .in(Singleton.class);

    bindAsContract(RobotsHandler.class)
        .to(HttpHandler.class)
        .in(Singleton.class);

    bindAsContract(DownloadResultsHandler.class)
        .to(HttpHandler.class)
        .in(Singleton.class);

    bindAsContract(ExportResultsHandler.class)
        .to(HttpHandler.class)
        .in(Singleton.class);

    bindAsContract(UnzipResultsHandler.class)
        .to(HttpHandler.class)
        .in(Singleton.class);

    bindAsContract(TimelinePageHandler.class)
        .to(HttpHandler.class)
        .in(Singleton.class);

    bindAsContract(DetailPageHandler.class)
        .to(HttpHandler.class)
        .in(Singleton.class);

    bindAsContract(AboutPageHandler.class)
        .to(HttpHandler.class)
        .in(Singleton.class);

    bindAsContract(AssetsHandler.class)
        .to(HttpHandler.class)
        .in(Singleton.class);

    bindAsContract(AttributesPageHandler.class)
        .to(HttpHandler.class)
        .in(Singleton.class);

    bindAsContract(SaveAttributesHandler.class)
        .to(HttpHandler.class)
        .in(Singleton.class);

    bindAsContract(LastSeenCommitHandler.class)
        .to(HttpHandler.class)
        .in(Singleton.class);

    bindAsContract(ShareResultsHandler.class)
        .to(HttpHandler.class)
        .in(Singleton.class);

    bindAsContract(ShareResultsPageHandler.class)
            .to(HttpHandler.class)
            .in(Singleton.class);

    bindAsContract(Authenticator.class).in(Singleton.class);
    bindAsContract(MustacheRenderer.class).in(Singleton.class);
    bindAsContract(HomeResultsReader.class).in(Singleton.class);
    bindAsContract(EmailSender.class).in(Singleton.class);
    bindAsContract(DiffGenerator.class).in(Singleton.class);
    bindAsContract(FileStore.class).in(Singleton.class);
    bindAsContract(RunProgressMonitor.class).in(Singleton.class);
    bindAsContract(TaskScheduler.class).in(Singleton.class);
    bindAsContract(ShareResultsUploader.class).in(Singleton.class);

    bindFactory(ObjectMapperFactory.class, Singleton.class)
        .to(ObjectMapper.class)
        .in(Singleton.class);

    bindFactory(ClockFactory.class, Singleton.class)
        .to(Clock.class)
        .in(Singleton.class);

    bindFactory(TickerFactory.class, Singleton.class)
        .to(Ticker.class)
        .in(Singleton.class);

    bindFactory(FileSystemFactory.class, Singleton.class)
        .to(FileSystem.class)
        .in(Singleton.class);
  }
}
