package tfb.status.testlib;

import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.undertow.server.HttpHandler;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import tfb.status.bootstrap.HttpServer;
import tfb.status.config.HttpServerConfig;
import tfb.status.service.Authenticator;

/**
 * Provides an API for making requests to the local HTTP server during tests.
 */
@Singleton
public final class HttpTester {
  private final Provider<HttpClient> clientProvider;
  private final Provider<HttpServerConfig> configProvider;
  private final Provider<TestHandler> testHandlerProvider;

  @Inject
  public HttpTester(Provider<HttpClient> clientProvider,
                    Provider<HttpServerConfig> configProvider,
                    Provider<TestHandler> testHandlerProvider) {

    this.clientProvider = Objects.requireNonNull(clientProvider);
    this.configProvider = Objects.requireNonNull(configProvider);
    this.testHandlerProvider = Objects.requireNonNull(testHandlerProvider);
  }

  /**
   * Adds the specified HTTP handler at a new and distinct path.
   *
   * @param handler the HTTP handler to be assigned a path
   * @return the path assigned to the HTTP handler
   */
  public String addHandler(HttpHandler handler) {
    TestHandler testHandler = testHandlerProvider.get();
    return testHandler.addHandler(handler);
  }

  /**
   * The {@linkplain HttpClient HTTP client} that should be used for making
   * requests to the local {@linkplain HttpServer HTTP server} running this
   * application.
   */
  public HttpClient client() {
    return clientProvider.get();
  }

  /**
   * Produces a URI that points at the local HTTP server.
   *
   * @param path the path part of the URI, such as "/robots.txt"
   */
  public URI uri(String path) {
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

    HttpServerConfig config = configProvider.get();
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
   * <p>This is a shortcut for using {@link #client()} and {@link
   * #uri(String)} for one common case.  To customize the request -- to use
   * POST instead of GET or to attach custom HTTP headers for example -- use
   * those other methods directly.
   *
   * @param path the path part of the URI, such as "/robots.txt"
   */
  public HttpResponse<String> getString(String path)
      throws IOException, InterruptedException {

    Objects.requireNonNull(path);

    URI uri = uri(path);

    return client().send(
        HttpRequest.newBuilder(uri).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  /**
   * Issues a GET request to the local HTTP server, reading the response body as
   * a byte array.
   *
   * <p>This is a shortcut for using {@link #client()} and {@link
   * #uri(String)} for one common case.  To customize the request -- to use
   * POST instead of GET or to attach custom HTTP headers for example -- use
   * those other methods directly.
   *
   * @param path the path part of the URI, such as "/robots.txt"
   */
  public HttpResponse<byte[]> getBytes(String path)
      throws IOException, InterruptedException {

    Objects.requireNonNull(path);

    URI uri = uri(path);

    return client().send(
        HttpRequest.newBuilder(uri).build(),
        HttpResponse.BodyHandlers.ofByteArray());
  }

  /**
   * Modifies the request such that it will pass authentication implemented by
   * {@link Authenticator#newRequiredAuthHandler(HttpHandler)}.
   */
  public HttpRequest.Builder addAuthorization(HttpRequest.Builder request) {
    return addAuthorization(request, "tester", "password");
  }

  /**
   * Modifies the request such that it will pass authentication implemented by
   * {@link Authenticator#newRequiredAuthHandler(HttpHandler)} as long the
   * specified account id and password are {@linkplain
   * Authenticator#checkPassword(String, String) correct}.
   */
  public HttpRequest.Builder addAuthorization(HttpRequest.Builder request,
                                              String accountId,
                                              String password) {
    Objects.requireNonNull(request);
    Objects.requireNonNull(accountId);
    Objects.requireNonNull(password);

    return request.header(
        AUTHORIZATION,
        writeBasicAuthorizationHeader(accountId, password));
  }

  /**
   * Produces the {@code Authorization} header value for an HTTP request where
   * the server is expected to perform Basic authentication.
   *
   * @param username the username to be included in the header
   * @param password the password to be included in the header
   */
  private static String writeBasicAuthorizationHeader(String username,
                                                      String password) {
    Objects.requireNonNull(username);
    Objects.requireNonNull(password);

    return "Basic " +
        Base64.getEncoder().encodeToString(
            (username + ":" + password).getBytes(UTF_8));
  }
}
