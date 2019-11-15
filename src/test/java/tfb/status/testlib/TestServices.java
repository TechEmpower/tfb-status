package tfb.status.testlib;

import static org.glassfish.hk2.api.InstanceLifecycleEventType.PRE_PRODUCTION;

import com.google.common.base.Ticker;
import com.google.common.io.MoreFiles;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import io.undertow.server.HttpHandler;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.net.ssl.SSLContext;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.api.Filter;
import org.glassfish.hk2.api.InstanceLifecycleEvent;
import org.glassfish.hk2.api.InstanceLifecycleListener;
import org.threeten.extra.MutableClock;
import tfb.status.bootstrap.HttpServer;
import tfb.status.bootstrap.Services;
import tfb.status.config.HttpServerConfig;
import tfb.status.handler.RootHandler;
import tfb.status.service.EmailSender;
import tfb.status.util.KeyStores;

/**
 * Manages the instances of HTTP handlers and service classes within this
 * application during tests.
 *
 * <p>Use {@link #getService(Class)} to retrieve instances of service classes.
 * For example, <code>getService(HttpServer.class)</code> returns an instance
 * of {@link HttpServer}.
 *
 * <p><strong>Important:</strong> Call {@link #shutdown()} once the tests are
 * complete.
 *
 * @see #getService(Class)
 * @see #shutdown()
 */
public final class TestServices extends Services {
  /**
   * Constructs the interface for managing this application's services during
   * tests.
   */
  public TestServices() {
    super(new ServiceBinder("test_config.yml") {
      @Override
      protected void configure() {
        super.configure(); // register the default services

        bindFactory(MutableClockFactory.class, Singleton.class)
            .to(Clock.class)
            .in(Singleton.class)
            .ranked(1); // override the default clock

        bindFactory(MutableTickerFactory.class, Singleton.class)
            .to(Ticker.class)
            .in(Singleton.class)
            .ranked(1); // override the default ticker

        bindFactory(InMemoryFileSystemFactory.class, Singleton.class)
            .to(FileSystem.class)
            .in(Singleton.class)
            .ranked(1); // override the default file system

        bindFactory(HttpClientFactory.class, Singleton.class)
            .to(HttpClient.class)
            .in(Singleton.class);

        bindAsContract(MailServer.class).in(Singleton.class);

        bind(MailServerDependency.class)
            .to(InstanceLifecycleListener.class)
            .in(Singleton.class);
      }
    });
  }

  /**
   * The {@link Clock} used by every service that needs to read the current wall
   * clock time.  This clock can be adjusted manually.
   */
  public MutableClock clock() {
    return (MutableClock) getService(Clock.class);
  }

  /**
   * The {@link Ticker} used by every service that needs to measure elapsed
   * time.  This ticker can be adjusted manually.
   */
  public MutableTicker ticker() {
    return (MutableTicker) getService(Ticker.class);
  }

  /**
   * Provides access to email that was sent by this application during tests.
   */
  public MailServer mailServer() {
    return getService(MailServer.class);
  }

  /**
   * Adds a new exact route to the root handler of the HTTP server.
   *
   * @see RootHandler#addExactPath(String, HttpHandler)
   */
  public void addExactPath(String path, HttpHandler handler) {
    var rootHandler = getService(RootHandler.class);
    rootHandler.addExactPath(path, handler);
  }

  /**
   * Adds a new prefix route to the root handler of the HTTP server.
   *
   * @see RootHandler#addPrefixPath(String, HttpHandler)
   */
  public void addPrefixPath(String pathPrefix, HttpHandler handler) {
    var rootHandler = getService(RootHandler.class);
    rootHandler.addPrefixPath(pathPrefix, handler);
  }

  /**
   * The {@linkplain HttpClient HTTP client} that should be used for making
   * requests to the local {@linkplain HttpServer HTTP server} running this
   * application.
   */
  public HttpClient httpClient() {
    return getService(HttpClient.class);
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

    HttpServerConfig config = getService(HttpServerConfig.class);
    boolean encrypted = config.keyStore != null;
    int port = config.port;

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
   * Ensures that {@link MailServer} is initialized before {@link EmailSender}
   * even though {@link EmailSender} does not explicitly declare that
   * dependency.
   */
  @Singleton
  private static final class MailServerDependency
      implements InstanceLifecycleListener {

    private final Provider<MailServer> mailServerProvider;

    @Inject
    public MailServerDependency(Provider<MailServer> mailServerProvider) {
      this.mailServerProvider = Objects.requireNonNull(mailServerProvider);
    }

    @Override
    public Filter getFilter() {
      return descriptor -> descriptor.getAdvertisedContracts()
                                     .contains(EmailSender.class.getTypeName());
    }

    @Override
    public void lifecycleEvent(InstanceLifecycleEvent lifecycleEvent) {
      if (lifecycleEvent.getEventType() == PRE_PRODUCTION)
        mailServerProvider.get();
    }
  }

  @Singleton
  private static final class HttpClientFactory implements Factory<HttpClient> {
    private final HttpServerConfig config;
    private final FileSystem fileSystem;
    private final Provider<HttpServer> httpServerProvider;

    @Inject
    public HttpClientFactory(HttpServerConfig config,
                             FileSystem fileSystem,
                             Provider<HttpServer> httpServerProvider) {

      this.config = Objects.requireNonNull(config);
      this.fileSystem = Objects.requireNonNull(fileSystem);
      this.httpServerProvider = Objects.requireNonNull(httpServerProvider);
    }

    @Override
    @Singleton
    public HttpClient provide() {
      HttpClient.Builder builder = HttpClient.newBuilder();

      if (config.keyStore != null) {
        Path keyStoreFile = fileSystem.getPath(config.keyStore.path);

        SSLContext sslContext =
            KeyStores.readClientSslContext(
                /* keyStoreBytes= */ MoreFiles.asByteSource(keyStoreFile),
                /* password= */ config.keyStore.password.toCharArray());

        builder.sslContext(sslContext);
      }

      HttpClient client = builder.build();

      // Ensure that the HTTP server is initialized before the client is used.
      httpServerProvider.get();

      return client;
    }

    @Override
    public void dispose(HttpClient instance) {
      // No cleanup required.
    }
  }

  @Singleton
  private static final class MutableClockFactory implements Factory<Clock> {
    @Override
    @Singleton
    public Clock provide() {
      return MutableClock.epochUTC();
    }

    @Override
    public void dispose(Clock instance) {
      // No cleanup required.
    }
  }

  @Singleton
  private static final class MutableTickerFactory implements Factory<Ticker> {
    @Override
    @Singleton
    public Ticker provide() {
      return new MutableTicker();
    }

    @Override
    public void dispose(Ticker instance) {
      // No cleanup required.
    }
  }

  @Singleton
  private static final class InMemoryFileSystemFactory
      implements Factory<FileSystem> {

    @Override
    @Singleton
    public FileSystem provide() {
      FileSystem realFileSystem = FileSystems.getDefault();
      FileSystem fakeFileSystem = Jimfs.newFileSystem(Configuration.unix());

      Path sourceRoot = realFileSystem.getPath("src/test/resources");
      Path targetRoot = fakeFileSystem.getPath("");

      try (Stream<Path> sources = Files.walk(sourceRoot)) {
        sources.forEach(
            (Path source) -> {
              Path target = targetRoot;
              for (Path part : sourceRoot.relativize(source))
                target = target.resolve(part.toString());

              if (Files.exists(target))
                return;

              try {
                Files.copy(source, target);
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }

      return fakeFileSystem;
    }

    @Override
    public void dispose(FileSystem instance) {
      try {
        instance.close();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }
}
