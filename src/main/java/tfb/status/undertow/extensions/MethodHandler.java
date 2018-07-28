package tfb.status.undertow.extensions;

import static io.undertow.util.Headers.ALLOW;
import static io.undertow.util.Methods.GET;
import static io.undertow.util.Methods.HEAD;
import static io.undertow.util.Methods.OPTIONS;
import static io.undertow.util.StatusCodes.METHOD_NOT_ALLOWED;

import com.google.common.base.Joiner;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An HTTP handler that forwards requests to other HTTP handlers based on the
 * method (such as GET or POST) of each request.
 *
 * <p>This handler supports OPTIONS automatically.
 *
 * <p>This handler supports HEAD automatically if it has a handler for GET
 * requests.
 *
 * <p>For all unsupported HTTP methods, this handler responds with {@code 405
 * Method Not Allowed}.
 */
public final class MethodHandler implements HttpHandler {
  private final Map<HttpString, HttpHandler> handlers = new ConcurrentHashMap<>();

  /**
   * Maps a method to a handler.
   *
   * @param method the required method of the request, see {@link
   *        io.undertow.util.Methods}
   * @param handler the handler for requests having this method
   * @return this {@link MethodHandler} instance (for chaining)
   * @throws IllegalStateException if this method was already mapped to another
   *         handler
   */
  @CanIgnoreReturnValue
  public MethodHandler addMethod(HttpString method, HttpHandler handler) {
    Objects.requireNonNull(method);
    Objects.requireNonNull(handler);

    handlers.merge(
        method,
        handler,
        (m, h) -> {
          throw new IllegalStateException(m + " already has a handler");
        });

    return this;
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    HttpString method = exchange.getRequestMethod();
    HttpHandler specifiedHandler = handlers.get(method);

    if (specifiedHandler != null) {
      specifiedHandler.handleRequest(exchange);
      return;
    }

    if (method.equals(OPTIONS)) {
      setAllowHeader(exchange);
      return;
    }

    if (method.equals(HEAD)) {
      //
      // Undertow has special handling for HEAD built-in.  It ensures that the
      // response body remains empty even if our GET handler tries to write
      // something.
      //
      HttpHandler getHandler = handlers.get(GET);
      if (getHandler != null) {
        getHandler.handleRequest(exchange);
        return;
      }
    }

    setAllowHeader(exchange);
    exchange.setStatusCode(METHOD_NOT_ALLOWED);
  }

  /**
   * Sets the {@code Allow} HTTP header in the response, informing the client
   * which HTTP methods are supported by this handler.
   */
  private void setAllowHeader(HttpServerExchange exchange) {
    var methods = new HashSet<HttpString>(handlers.keySet());
    methods.add(OPTIONS);

    if (methods.contains(GET))
      methods.add(HEAD);

    String headerValue = Joiner.on(", ").join(methods);
    exchange.getResponseHeaders().put(ALLOW, headerValue);
  }
}
