package tfb.status.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Ticker;
import java.lang.annotation.Annotation;
import java.time.Clock;
import java.util.Objects;
import javax.inject.Singleton;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import tfb.status.config.ApplicationConfig;
import tfb.status.config.AssetsConfig;
import tfb.status.config.EmailConfig;
import tfb.status.config.FileStoreConfig;
import tfb.status.config.HttpServerConfig;
import tfb.status.config.MustacheConfig;
import tfb.status.handler.AboutPageHandler;
import tfb.status.handler.AssetsHandler;
import tfb.status.handler.AttributesPageHandler;
import tfb.status.handler.DetailPageHandler;
import tfb.status.handler.DownloadResultsHandler;
import tfb.status.handler.ExportResultsHandler;
import tfb.status.handler.HomePageHandler;
import tfb.status.handler.HomeUpdatesHandler;
import tfb.status.handler.RobotsHandler;
import tfb.status.handler.RootHandler;
import tfb.status.handler.SaveAttributesHandler;
import tfb.status.handler.TimelinePageHandler;
import tfb.status.handler.UnzipResultsHandler;
import tfb.status.handler.UploadResultsHandler;
import tfb.status.service.Authenticator;
import tfb.status.service.DiffGenerator;
import tfb.status.service.EmailSender;
import tfb.status.service.HomeResultsReader;
import tfb.status.service.HttpServer;
import tfb.status.service.MustacheRenderer;
import tfb.status.service.StandardObjectMapper;

/**
 * Obtains instances of the service classes in this application.
 */
public final class Services {
  private Services() {
    throw new AssertionError("This class cannot be instantiated");
  }

  /**
   * Returns a new {@link ServiceLocator} that is capable of producing all of
   * the services in this application.
   *
   * <p>For example, to obtain the {@link HttpServer}:
   *
   * <pre>
   *   ServiceLocator serviceLocator = newServiceLocator(config, clock, ticker);
   *   HttpServer httpServer = serviceLocator.getService(HttpServer.class);
   * </pre>
   *
   * @see ServiceLocator#getService(Class, Annotation...)
   */
  public static ServiceLocator newServiceLocator(ApplicationConfig config,
                                                 Clock clock,
                                                 Ticker ticker) {
    Objects.requireNonNull(config);
    Objects.requireNonNull(clock);
    Objects.requireNonNull(ticker);

    var binder = new AbstractBinder() {

      @Override
      public void configure() {
        bind(config).to(ApplicationConfig.class);
        bind(config.assets).to(AssetsConfig.class);
        bind(config.mustache).to(MustacheConfig.class);
        bind(config.fileStore).to(FileStoreConfig.class);
        bind(config.http).to(HttpServerConfig.class);

        if (config.email != null)
          bind(config.email).to(EmailConfig.class);

        bindAsContract(HttpServer.class).in(Singleton.class);
        bindAsContract(RootHandler.class).in(Singleton.class);
        bindAsContract(HomePageHandler.class).in(Singleton.class);
        bindAsContract(HomeUpdatesHandler.class).in(Singleton.class);
        bindAsContract(UploadResultsHandler.class).in(Singleton.class);
        bindAsContract(RobotsHandler.class).in(Singleton.class);
        bindAsContract(DownloadResultsHandler.class).in(Singleton.class);
        bindAsContract(ExportResultsHandler.class).in(Singleton.class);
        bindAsContract(UnzipResultsHandler.class).in(Singleton.class);
        bindAsContract(TimelinePageHandler.class).in(Singleton.class);
        bindAsContract(DetailPageHandler.class).in(Singleton.class);
        bindAsContract(AboutPageHandler.class).in(Singleton.class);
        bindAsContract(AssetsHandler.class).in(Singleton.class);
        bindAsContract(AttributesPageHandler.class).in(Singleton.class);
        bindAsContract(SaveAttributesHandler.class).in(Singleton.class);
        bindAsContract(Authenticator.class).in(Singleton.class);
        bindAsContract(MustacheRenderer.class).in(Singleton.class);
        bindAsContract(HomeResultsReader.class).in(Singleton.class);
        bindAsContract(EmailSender.class).in(Singleton.class);
        bindAsContract(DiffGenerator.class).in(Singleton.class);

        bindFactory(StandardObjectMapper.class, Singleton.class)
            .to(ObjectMapper.class)
            .in(Singleton.class);

        //
        // To get the current date, time, or time-zone, or to measure elapsed
        // time, use these injected Clock and Ticker instances.  Avoid using
        // APIs that implicitly rely on the system clock or the default
        // time-zone.
        //
        // Here are some APIs to avoid and their replacements:
        //
        //   |----------------------------|---------------------------------|
        //   | Don't use this             | Use this instead                |
        //   |----------------------------|---------------------------------|
        //   | LocalDateTime.now()        | LocalDateTime.now(clock)        |
        //   | Instant.now()              | clock.instant()                 |
        //   | System.currentTimeMillis() | clock.millis()                  |
        //   | System.nanoTime()          | ticker.read()                   |
        //   | Stopwatch.createStarted()  | Stopwatch.createStarted(ticker) |
        //   |----------------------------|---------------------------------|
        //
        bind(clock).to(Clock.class);
        bind(ticker).to(Ticker.class);
      }
    };

    ServiceLocator serviceLocator =
        ServiceLocatorUtilities.createAndPopulateServiceLocator();

    ServiceLocatorUtilities.bind(serviceLocator, binder);

    return serviceLocator;
  }
}
