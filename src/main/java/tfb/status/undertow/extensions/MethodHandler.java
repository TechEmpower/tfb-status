package tfb.status.undertow.extensions;

import static io.undertow.util.Headers.ALLOW;
import static io.undertow.util.Methods.GET;
import static io.undertow.util.Methods.HEAD;
import static io.undertow.util.Methods.OPTIONS;
import static io.undertow.util.StatusCodes.METHOD_NOT_ALLOWED;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import java.util.HashSet;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An HTTP handler that forwards requests to other HTTP handlers based on the
 * method (such as GET or POST) of each request.
 *
 * <p>This handler supports OPTIONS automatically.
 *
 * <p>This handler supports HEAD automatically if it contains a handler for GET
 * requests.
 *
 * <p>For all unsupported HTTP methods, this handler responds with {@code 405
 * Method Not Allowed}.
 *
 * <p>Instances of this class are immutable.  Use {@link #builder()} to obtain a
 * new, mutable {@link Builder} instance, use {@link Builder#add(HttpString,
 * HttpHandler)} to add mappings to that builder, and then use {@link
 * Builder#build()} to obtain an immutable {@link MethodHandler} instance
 * containing those mappings.
 */
public final class MethodHandler implements HttpHandler {
  private final ImmutableMap<HttpString, HttpHandler> handlers;

  private MethodHandler(ImmutableMap<HttpString, HttpHandler> handlers) {
    this.handlers = Objects.requireNonNull(handlers);
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

  /**
   * Returns a new, initially-empty {@link Builder} instance.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * A mutable builder class used to construct {@link MethodHandler} instances.
   */
  public static final class Builder {
    private final ConcurrentHashMap<HttpString, HttpHandler> handlers =
        new ConcurrentHashMap<>();

    private Builder() {}

    /**
     * Shortcut for {@link #add(HttpString, HttpHandler)}.
     *
     * @throws IllegalArgumentException if the input is not a valid method
     */
    @CanIgnoreReturnValue
    public Builder add(String method, HttpHandler handler) {
      Objects.requireNonNull(method);
      Objects.requireNonNull(handler);
      return add(Methods.fromString(method), handler);
    }

    /**
     * Maps a method to a handler.
     *
     * @param method the required method of the request, see {@link
     *        io.undertow.util.Methods}
     * @param handler the handler for requests having this method
     * @return this {@link Builder} instance (for chaining)
     * @throws IllegalStateException if this method was already mapped to
     *         another handler
     */
    @CanIgnoreReturnValue
    public Builder add(HttpString method, HttpHandler handler) {
      Objects.requireNonNull(method);
      Objects.requireNonNull(handler);

      handlers.merge(
          method,
          handler,
          (handler1, handler2) -> {
            throw new IllegalStateException(method + " already has a handler");
          });

      return this;
    }

    /**
     * Returns a new {@link MethodHandler} instance containing the mappings that
     * have been added to this {@link Builder}.  Subsequent modifications to
     * this {@link Builder} do not affect previously-returned {@link
     * MethodHandler} instances.
     */
    public MethodHandler build() {
      return new MethodHandler(ImmutableMap.copyOf(handlers));
    }
  }
}
