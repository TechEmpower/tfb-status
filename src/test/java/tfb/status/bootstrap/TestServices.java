package tfb.status.bootstrap;

import static org.glassfish.hk2.api.InstanceLifecycleEventType.PRE_PRODUCTION;

import com.google.common.base.Ticker;
import com.google.common.io.ByteSource;
import com.google.common.io.MoreFiles;
import com.google.common.io.Resources;
import com.google.errorprone.annotations.MustBeClosed;
import com.icegreen.greenmail.store.FolderException;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import io.undertow.server.HttpHandler;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.mail.internet.MimeMessage;
import javax.net.ssl.SSLContext;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.api.Filter;
import org.glassfish.hk2.api.InstanceLifecycleEvent;
import org.glassfish.hk2.api.InstanceLifecycleListener;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.media.sse.SseFeature;
import org.threeten.extra.MutableClock;
import tfb.status.config.ApplicationConfig;
import tfb.status.config.EmailConfig;
import tfb.status.config.HttpServerConfig;
import tfb.status.handler.RootHandler;
import tfb.status.service.EmailSender;
import tfb.status.util.BasicAuthUtils;
import tfb.status.util.KeyStores;
import tfb.status.util.MutableTicker;

/**
 * Creates instances of our HTTP handlers and service classes for testing.
 */
public final class TestServices {
  final ApplicationConfig config;
  private final MutableClock clock;
  private final MutableTicker ticker;
  private final ServiceLocator serviceLocator;

  public TestServices() {
    this.config = newApplicationConfig();
    this.clock = MutableClock.epochUTC();
    this.ticker = new MutableTicker();
    this.serviceLocator = Services.newServiceLocator(config, clock, ticker);

    var binder = new AbstractBinder() {

      @Override
      protected void configure() {
        bindFactory(HttpClientFactory.class, Singleton.class)
            .to(Client.class)
            .in(Singleton.class);

        if (config.email != null) {
          bindFactory(MailServerFactory.class, Singleton.class)
              .to(GreenMail.class)
              .in(Singleton.class);
        }
      }
    };

    ServiceLocatorUtilities.bind(serviceLocator, binder);

    forceDependency(
        /* serviceLocator= */ serviceLocator,
        /* fromClass= */ Client.class,
        /* toClass= */ HttpServer.class);

    forceDependency(
        /* serviceLocator= */ serviceLocator,
        /* fromClass= */ EmailSender.class,
        /* toClass= */ GreenMail.class);
  }

  /**
   * Shuts down all services.
   */
  public void shutdown() {
    serviceLocator.shutdown();
  }

  /**
   * The {@link ApplicationConfig} used by every service that is configurable.
   */
  public ApplicationConfig config() {
    return config;
  }

  /**
   * The {@link Clock} used by every service that needs to read the current wall
   * clock time.  This clock can be adjusted manually.
   */
  public MutableClock clock() {
    return clock;
  }

  /**
   * The {@link Ticker} used by every service that needs to measure elapsed
   * time.  This ticker can be adjusted manually.
   */
  public MutableTicker ticker() {
    return ticker;
  }

  /**
   * The {@link ServiceLocator} capable of producing all services in this
   * application.
   *
   * @see Services#newServiceLocator(ApplicationConfig, Clock, Ticker)
   */
  public ServiceLocator serviceLocator() {
    return serviceLocator;
  }

  /**
   * Returns the only email message that has been received by the email server
   * since the last time this method was invoked.
   *
   * @throws IllegalStateException if there is not exactly one email message
   */
  public synchronized MimeMessage onlyEmailMessage() {
    GreenMail mailServer = serviceLocator.getService(GreenMail.class);
    if (mailServer == null)
      throw new IllegalStateException(
          "Email was disabled in this application's config");

    MimeMessage[] messages = mailServer.getReceivedMessages();
    try {
      mailServer.purgeEmailFromAllMailboxes();
    } catch (FolderException e) {
      throw new RuntimeException(e);
    }

    if (messages.length != 1)
      throw new IllegalStateException("There is not exactly one email");

    return messages[0];
  }

  /**
   * Adds a new exact route to the root handler of the HTTP server.
   *
   * @see RootHandler#addExactPath(String, HttpHandler)
   */
  public void addExactPath(String path, HttpHandler handler) {
    RootHandler rootHandler = serviceLocator.getService(RootHandler.class);
    rootHandler.addExactPath(path, handler);
  }

  /**
   * Adds a new prefix route to the root handler of the HTTP server.
   *
   * @see RootHandler#addPrefixPath(String, HttpHandler)
   */
  public void addPrefixPath(String pathPrefix, HttpHandler handler) {
    RootHandler rootHandler = serviceLocator.getService(RootHandler.class);
    rootHandler.addPrefixPath(pathPrefix, handler);
  }

  /**
   * The {@linkplain Client HTTP client} that should be used for making requests
   * to the local {@linkplain HttpServer HTTP server} running this application.
   */
  public Client httpClient() {
    return serviceLocator.getService(Client.class);
  }

  /**
   * Produces a URI that points at the local HTTP server.
   *
   * @param path the path part of the URI, such as "/robots.txt"
   */
  public String httpUri(String path) {
    Objects.requireNonNull(path);

    String protocol =
        (config.http.keyStore == null)
            ? "http"
            : "https";

    return UriBuilder.fromUri(protocol + "://localhost")
                     .port(config.http.port)
                     .path(path)
                     .build()
                     .toString();
  }

  /**
   * Issues a GET request to the local HTTP server.
   *
   * <p>This is a shortcut for using {@link #httpClient()} and {@link
   * #httpUri(String)} for one common case.  To customize the request -- to use
   * POST instead of GET or to attach custom HTTP headers for example -- use
   * those other methods directly.
   *
   * @param path the path part of the URI, such as "/robots.txt"
   */
  @MustBeClosed
  public Response httpGet(String path) {
    Objects.requireNonNull(path);

    String uri = httpUri(path);

    return httpClient()
        .target(uri)
        .request()
        .get();
  }

  /**
   * A value that can be submitted in the {@code Authorization} header of an
   * HTTP request to the local server, making that request pass authentication.
   */
  public String authorizationHeader() {
    return BasicAuthUtils.writeAuthorizationHeader("tester", "password");
  }

  /**
   * Returns an {@link ApplicationConfig} suitable for use in testing.
   */
  private static ApplicationConfig newApplicationConfig() {
    URL url = Resources.getResource("test_config.yml");
    ByteSource bytes = Resources.asByteSource(url);
    return ApplicationConfig.readYamlBytes(bytes);
  }

  /**
   * Tells the service locator that {@code toClass} must be initialized before
   * {@code fromClass} even if {@code fromClass} does not explicitly declare
   * that dependency.
   */
  private static void forceDependency(ServiceLocator serviceLocator,
                                      Class<?> fromClass,
                                      Class<?> toClass) {

    Objects.requireNonNull(serviceLocator);
    Objects.requireNonNull(fromClass);
    Objects.requireNonNull(toClass);

    var binder = new AbstractBinder() {

      @Override
      protected void configure() {
        bind(new ForcedDependency(serviceLocator, fromClass, toClass))
            .to(InstanceLifecycleListener.class);
      }
    };

    ServiceLocatorUtilities.bind(serviceLocator, binder);
  }

  @Singleton
  private static final class ForcedDependency
      implements InstanceLifecycleListener {

    private final ServiceLocator serviceLocator;
    private final Class<?> fromClass;
    private final Class<?> toClass;

    ForcedDependency(ServiceLocator serviceLocator,
                     Class<?> fromClass,
                     Class<?> toClass) {

      this.serviceLocator = Objects.requireNonNull(serviceLocator);
      this.fromClass = Objects.requireNonNull(fromClass);
      this.toClass = Objects.requireNonNull(toClass);
    }

    @Override
    public Filter getFilter() {
      return descriptor -> descriptor.getAdvertisedContracts()
                                     .contains(fromClass.getName());
    }

    @Override
    public void lifecycleEvent(InstanceLifecycleEvent lifecycleEvent) {
      if (lifecycleEvent.getEventType() == PRE_PRODUCTION) {
        serviceLocator.getService(toClass);
      }
    }
  }

  @Singleton
  private static final class HttpClientFactory implements Factory<Client> {
    private final HttpServerConfig config;

    @Inject
    public HttpClientFactory(HttpServerConfig config) {
      this.config = Objects.requireNonNull(config);
    }

    @Override
    public Client provide() {
      ClientBuilder builder = ClientBuilder.newBuilder();

      builder.register(SseFeature.class);

      if (config.keyStore != null) {
        Path keyStoreFile = Paths.get(config.keyStore.path);

        SSLContext sslContext =
            KeyStores.readClientSslContext(
                /* keyStoreBytes= */ MoreFiles.asByteSource(keyStoreFile),
                /* password= */ config.keyStore.password.toCharArray());

        builder.sslContext(sslContext);
      }

      return builder.build();
    }

    @Override
    public void dispose(Client instance) {
      instance.close();
    }
  }

  @Singleton
  private static final class MailServerFactory implements Factory<GreenMail> {
    private final EmailConfig config;

    @Inject
    public MailServerFactory(EmailConfig config) {
      this.config = Objects.requireNonNull(config);
    }

    @Override
    public GreenMail provide() {
      GreenMail mailServer =
          new GreenMail(
              new ServerSetup(
                  /* port= */ config.port,
                  /* bindAddress= */ "localhost",
                  /* protocol= */ "smtp"));

      mailServer.start();
      return mailServer;
    }

    @Override
    public void dispose(GreenMail instance) {
      instance.stop();
    }
  }
}
