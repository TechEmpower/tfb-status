package tfb.status.undertow.extensions;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import java.util.Objects;
import javax.inject.Provider;

/**
 * Delays initialization of a caller-supplied HTTP handler until a request is
 * received.
 */
public final class LazyHandler implements HttpHandler {
  private final Provider<? extends HttpHandler> handlerProvider;

  public LazyHandler(Provider<? extends HttpHandler> handlerProvider) {
    this.handlerProvider = Objects.requireNonNull(handlerProvider);
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    HttpHandler handler = handlerProvider.get();
    handler.handleRequest(exchange);
  }
}
