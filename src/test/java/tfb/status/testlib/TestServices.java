package tfb.status.testlib;

import static org.glassfish.hk2.api.InstanceLifecycleEventType.PRE_PRODUCTION;

import com.google.common.base.Ticker;
import com.google.common.io.ByteSource;
import com.google.common.io.MoreFiles;
import com.google.common.io.Resources;
import com.icegreen.greenmail.store.FolderException;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import io.undertow.server.HttpHandler;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.mail.internet.MimeMessage;
import javax.net.ssl.SSLContext;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.api.Filter;
import org.glassfish.hk2.api.InstanceLifecycleEvent;
import org.glassfish.hk2.api.InstanceLifecycleListener;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.threeten.extra.MutableClock;
import tfb.status.bootstrap.HttpServer;
import tfb.status.bootstrap.Services;
import tfb.status.config.ApplicationConfig;
import tfb.status.config.EmailConfig;
import tfb.status.config.HttpServerConfig;
import tfb.status.handler.RootHandler;
import tfb.status.service.EmailSender;
import tfb.status.util.KeyStores;

/**
 * Creates instances of our HTTP handlers and service classes for testing.
 *
 * <p>Use {@link #serviceLocator()} to retrieve instances of service classes.
 * For example, <code>serviceLocator().getService(EmailSender.class)</code>
 * returns an instance of {@link EmailSender}.
 *
 * <p><strong>Important:</strong> Call {@link #shutdown()} once the tests are
 * complete.
 */
public final class TestServices {
  private final ApplicationConfig config;
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
            .to(HttpClient.class)
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
        /* fromClass= */ HttpClient.class,
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
   * The {@linkplain HttpClient HTTP client} that should be used for making
   * requests to the local {@linkplain HttpServer HTTP server} running this
   * application.
   */
  public HttpClient httpClient() {
    return serviceLocator.getService(HttpClient.class);
  }

  /**
   * Produces a URI that points at the local HTTP server.
   *
   * @param path the path part of the URI, such as "/robots.txt"
   */
  public URI httpUri(String path) {
    return createUri(path, /* isWebSocket= */ false);
  }

  /**
   * Produces a URI that points at the local web socket server.
   *
   * @param path the path part of the URI, such as "/robots.txt"
   */
  public URI webSocketUri(String path) {
    return createUri(path, /* isWebSocket= */ true);
  }

  private URI createUri(String path, boolean isWebSocket) {
    if (!path.startsWith("/"))
      throw new IllegalArgumentException("The path must start with '/'");

    boolean encrypted = config.http.keyStore != null;
    int port = config.http.port;

    boolean nonStandardPort =
        (encrypted && port != 443) || (!encrypted && port != 80);

    var uri = new StringBuilder();

    if (isWebSocket)
      uri.append(encrypted ? "wss" : "ws");
    else
      uri.append(encrypted ? "https" : "http");

    uri.append("://localhost");

    if (nonStandardPort) {
      uri.append(":");
      uri.append(port);
    }

    uri.append(path);

    return URI.create(uri.toString());
  }

  /**
   * Issues a GET request to the local HTTP server, reading the response body as
   * a string.
   *
   * <p>This is a shortcut for using {@link #httpClient()} and {@link
   * #httpUri(String)} for one common case.  To customize the request -- to use
   * POST instead of GET or to attach custom HTTP headers for example -- use
   * those other methods directly.
   *
   * @param path the path part of the URI, such as "/robots.txt"
   */
  public HttpResponse<String> httpGetString(String path)
      throws IOException, InterruptedException {

    Objects.requireNonNull(path);

    URI uri = httpUri(path);

    return httpClient().send(
        HttpRequest.newBuilder(uri).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  /**
   * Issues a GET request to the local HTTP server, reading the response body as
   * a byte array.
   *
   * <p>This is a shortcut for using {@link #httpClient()} and {@link
   * #httpUri(String)} for one common case.  To customize the request -- to use
   * POST instead of GET or to attach custom HTTP headers for example -- use
   * those other methods directly.
   *
   * @param path the path part of the URI, such as "/robots.txt"
   */
  public HttpResponse<byte[]> httpGetBytes(String path)
      throws IOException, InterruptedException {

    Objects.requireNonNull(path);

    URI uri = httpUri(path);

    return httpClient().send(
        HttpRequest.newBuilder(uri).build(),
        HttpResponse.BodyHandlers.ofByteArray());
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
  private static final class HttpClientFactory implements Factory<HttpClient> {
    private final HttpServerConfig config;

    @Inject
    public HttpClientFactory(HttpServerConfig config) {
      this.config = Objects.requireNonNull(config);
    }

    @Override
    @Singleton
    public HttpClient provide() {
      HttpClient.Builder builder = HttpClient.newBuilder();

      if (config.keyStore != null) {
        Path keyStoreFile = Path.of(config.keyStore.path);

        SSLContext sslContext =
            KeyStores.readClientSslContext(
                /* keyStoreBytes= */ MoreFiles.asByteSource(keyStoreFile),
                /* password= */ config.keyStore.password.toCharArray());

        builder.sslContext(sslContext);
      }

      return builder.build();
    }

    @Override
    public void dispose(HttpClient instance) {
      Objects.requireNonNull(instance);
      // No cleanup required.
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
    @Singleton
    public GreenMail provide() {
      var mailServer =
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
