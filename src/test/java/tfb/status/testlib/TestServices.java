package tfb.status.testlib;

import com.google.common.base.Ticker;
import io.undertow.server.HttpHandler;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.Objects;
import org.threeten.extra.MutableClock;
import tfb.status.bootstrap.HttpServer;
import tfb.status.bootstrap.Services;
import tfb.status.bootstrap.ServicesBinder;
import tfb.status.config.HttpServerConfig;
import tfb.status.handler.RootHandler;

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
    super(new ServicesBinder("test_config.yml"),
          new TestServicesBinder());
  }

  /**
   * The {@link Clock} used by every service that needs to read the current wall
   * clock time.  This clock can be adjusted manually.
   */
  public MutableClock clock() {
    // TODO: Make `getService(MutableClock.class)` return this same clock,
    //       meaning this method would be unnecessary.
    return (MutableClock) getService(Clock.class);
  }

  /**
   * The {@link Ticker} used by every service that needs to measure elapsed
   * time.  This ticker can be adjusted manually.
   */
  public MutableTicker ticker() {
    // TODO: Make `getService(MutableTicker.class)` return this same ticker,
    //       meaning this method would be unnecessary.
    return (MutableTicker) getService(Ticker.class);
  }

  /**
   * Adds a new exact route to the root handler of the HTTP server.
   *
   * @see RootHandler#addExactPath(String, HttpHandler)
   */
  public void addExactPath(String path, HttpHandler handler) {
    // TODO: Use RootHandler in tests directly and remove this method?
    var rootHandler = getService(RootHandler.class);
    rootHandler.addExactPath(path, handler);
  }

  /**
   * Adds a new prefix route to the root handler of the HTTP server.
   *
   * @see RootHandler#addPrefixPath(String, HttpHandler)
   */
  public void addPrefixPath(String pathPrefix, HttpHandler handler) {
    // TODO: Use RootHandler in tests directly and remove this method?
    var rootHandler = getService(RootHandler.class);
    rootHandler.addPrefixPath(pathPrefix, handler);
  }

  // TODO: Consider moving all HTTP-related methods into their own class.

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
}
