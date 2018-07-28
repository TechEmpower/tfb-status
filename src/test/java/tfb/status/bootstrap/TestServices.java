package tfb.status.bootstrap;

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
import javax.annotation.Nullable;
import javax.mail.internet.MimeMessage;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.jersey.media.sse.SseFeature;
import org.threeten.extra.MutableClock;
import tfb.status.config.ApplicationConfig;
import tfb.status.config.EmailConfig;
import tfb.status.config.HttpServerConfig;
import tfb.status.handler.RootHandler;
import tfb.status.util.BasicAuthUtils;
import tfb.status.util.MutableTicker;

/**
 * Creates instances of our HTTP handlers and service classes for testing.
 */
public final class TestServices {
  private final ApplicationConfig config;
  private final MutableClock clock;
  private final MutableTicker ticker;
  private final ServiceLocator serviceLocator;
  private final Client httpClient;
  @Nullable private final GreenMail mailServer;

  public TestServices() {
    this.config = newApplicationConfig();
    this.clock = MutableClock.epochUTC();
    this.ticker = new MutableTicker();
    this.serviceLocator = Services.newServiceLocator(config, clock, ticker);
    this.httpClient = newHttpClient(config.http);

    if (config.email == null)
      mailServer = null;
    else {
      mailServer = newMailServer(config.email);
      mailServer.start();
    }

    HttpServer httpServer = serviceLocator.getService(HttpServer.class);
    httpServer.start();
  }

  /**
   * Shuts down all services.
   */
  public void shutdown() {
    try {
      httpClient.close();
    } finally {
      try {
        serviceLocator.shutdown();
      } finally {
        if (mailServer != null) {
          mailServer.stop();
        }
      }
    }
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
  public void addPrefixPath(String path, HttpHandler handler) {
    RootHandler rootHandler = serviceLocator.getService(RootHandler.class);
    rootHandler.addPrefixPath(path, handler);
  }

  /**
   * The {@linkplain Client HTTP client} that should be used for making requests
   * to the local {@linkplain HttpServer HTTP server} running this application.
   */
  public Client httpClient() {
    return httpClient;
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

  private static ApplicationConfig newApplicationConfig() {
    URL url = Resources.getResource("test_config.yml");
    ByteSource bytes = Resources.asByteSource(url);
    return ConfigReader.readYamlBytes(bytes);
  }

  private static Client newHttpClient(HttpServerConfig config) {
    ClientBuilder builder = ClientBuilder.newBuilder();

    builder.register(SseFeature.class);

    if (config.keyStore != null) {
      Path keyStoreFile = Paths.get(config.keyStore.path);

      builder.trustStore(
          KeyStores.readKeyStore(
              /* keyStoreBytes= */ MoreFiles.asByteSource(keyStoreFile),
              /* password= */ config.keyStore.password.toCharArray()));
    }

    return builder.build();
  }

  private static GreenMail newMailServer(EmailConfig config) {
    return new GreenMail(
        new ServerSetup(
            /* port= */ config.port,
            /* bindAddress= */ "localhost",
            /* protocol= */ "smtp"));
  }
}
