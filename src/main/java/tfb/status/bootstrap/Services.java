package tfb.status.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.base.Ticker;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.api.TypeLiteral;
import org.glassfish.hk2.utilities.Binder;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import tfb.status.service.FileStore;
import tfb.status.service.HomeResultsReader;
import tfb.status.service.MustacheRenderer;
import tfb.status.service.RunProgressMonitor;
import tfb.status.service.StandardClock;
import tfb.status.service.StandardFileSystem;
import tfb.status.service.StandardObjectMapper;
import tfb.status.service.StandardTicker;

/**
 * Manages the instances of HTTP handlers and service classes within this
 * application.
 *
 * <p>Use {@link #getService(Class)} to retrieve instances of service classes.
 * For example, <code>getService(HttpServer.class)</code> returns an instance
 * of {@link HttpServer}.
 *
 * @see #getService(Class)
 * @see #shutdown()
 */
public class Services {
  private final ServiceLocator serviceLocator;

  /**
   * Constructs the interface for managing this application's services.
   *
   * @param configFilePath the path to this application's YAML configuration
   *        file, or {@code null} if a default configuration should be used
   */
  public Services(@Nullable String configFilePath) {
    this(new ServiceBinder(configFilePath));
  }

  /**
   * Initializes services using an HK2 binder.
   *
   * @param binder the HK2 binder that registers all of this application's
   *        service classes
   */
  protected Services(Binder binder) {
    Objects.requireNonNull(binder);

    ServiceLocator serviceLocator =
        ServiceLocatorUtilities.createAndPopulateServiceLocator();

    ServiceLocatorUtilities.bind(serviceLocator, binder);

    this.serviceLocator = serviceLocator;
  }

  /**
   * Shuts down all services.
   */
  public void shutdown() {
    serviceLocator.shutdown();
  }

  /**
   * Returns the service of the specified type.
   *
   * @throws NoSuchElementException if there is no service of that type
   */
  public <T> T getService(Class<T> type) {
    T service = serviceLocator.getService(type);
    if (service == null)
      throw new NoSuchElementException("There is no service of type " + type);

    return service;
  }

  /**
   * An HK2 binder that registers all of this application's service classes.
   */
  protected static class ServiceBinder extends AbstractBinder {
    private final @Nullable String configFilePath;

    /**
     * Constructs an HK2 binder for this application.
     *
     * @param configFilePath the path to this application's YAML configuration
     *        file, or {@code null} if a default configuration should be used
     */
    public ServiceBinder(@Nullable String configFilePath) {
      this.configFilePath = configFilePath;
    }

    @Override
    protected void configure() {
      bind(Optional.ofNullable(configFilePath))
          .to(new TypeLiteral<Optional<String>>() {})
          .named(CONFIG_FILE_PATH);

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
      bindAsContract(FileStore.class).in(Singleton.class);
      bindAsContract(RunProgressMonitor.class).in(Singleton.class);

      bindFactory(StandardObjectMapper.class, Singleton.class)
          .to(ObjectMapper.class)
          .in(Singleton.class);

      bindFactory(StandardClock.class, Singleton.class)
          .to(Clock.class)
          .in(Singleton.class);

      bindFactory(StandardTicker.class, Singleton.class)
          .to(Ticker.class)
          .in(Singleton.class);

      bindFactory(StandardFileSystem.class, Singleton.class)
          .to(FileSystem.class)
          .in(Singleton.class);
    }
  }

  private static final String CONFIG_FILE_PATH = "tfb.status.configFilePath";

  @Singleton
  private static final class ApplicationConfigFactory
      implements Factory<ApplicationConfig> {

    private final FileSystem fileSystem;
    private final @Nullable String path;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public ApplicationConfigFactory(
        FileSystem fileSystem,
        @Named(CONFIG_FILE_PATH) Optional<String> optionalPath) {

      this.fileSystem = Objects.requireNonNull(fileSystem);
      this.path = optionalPath.orElse(null);
    }

    @Override
    @Singleton
    public ApplicationConfig provide() {
      if (path == null) {
        logger.info("Using default configuration");
        return new ApplicationConfig(null, null, null, null, null);
      }

      Path yamlFile = fileSystem.getPath(path);
      logger.info("Using custom configuration file \"" + yamlFile + "\"");

      var yamlMapper = new ObjectMapper(new YAMLFactory());

      try (InputStream inputStream = Files.newInputStream(yamlFile)) {
        return yamlMapper.readValue(inputStream, ApplicationConfig.class);
      } catch (IOException e) {
        throw new IllegalStateException(
            "Unable to read configuration file \"" + yamlFile + "\"",
            e);
      }
    }

    @Override
    public void dispose(ApplicationConfig instance) {
      // No cleanup required.
    }
  }

  @Singleton
  private static final class AssetsConfigFactory
      implements Factory<AssetsConfig> {

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

  @Singleton
  private static final class MustacheConfigFactory
      implements Factory<MustacheConfig> {

    private final ApplicationConfig config;

    @Inject
    public MustacheConfigFactory(ApplicationConfig config) {
      this.config = Objects.requireNonNull(config);
    }

    @Override
    @Singleton
    public MustacheConfig provide() {
      return config.mustache;
    }

    @Override
    public void dispose(MustacheConfig instance) {
      // No cleanup required.
    }
  }

  @Singleton
  private static final class FileStoreConfigFactory
      implements Factory<FileStoreConfig> {

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

  @Singleton
  private static final class HttpServerConfigFactory
      implements Factory<HttpServerConfig> {

    private final ApplicationConfig config;

    @Inject
    public HttpServerConfigFactory(ApplicationConfig config) {
      this.config = Objects.requireNonNull(config);
    }

    @Override
    @Singleton
    public HttpServerConfig provide() {
      return config.http;
    }

    @Override
    public void dispose(HttpServerConfig instance) {
      // No cleanup required.
    }
  }

  @Singleton
  private static final class EmailConfigFactory
      implements Factory<Optional<EmailConfig>> {

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
}
