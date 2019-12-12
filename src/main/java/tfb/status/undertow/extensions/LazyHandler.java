package tfb.status.undertow.extensions;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Delays initialization of a caller-supplied HTTP handler until a request is
 * received.
 */
public final class LazyHandler implements HttpHandler {
  private final Supplier<? extends HttpHandler> handlerSupplier;

  public LazyHandler(Supplier<? extends HttpHandler> handlerSupplier) {
    this.handlerSupplier = Objects.requireNonNull(handlerSupplier);
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    HttpHandler handler = handlerSupplier.get();
    handler.handleRequest(exchange);
  }
}
