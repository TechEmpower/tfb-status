package tfb.status.testlib;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import java.util.Objects;
import java.util.UUID;
import javax.inject.Singleton;
import org.jvnet.hk2.annotations.ContractsProvided;
import tfb.status.handler.PrefixPath;

/**
 * Forwards requests to HTTP handlers that were {@linkplain
 * #addHandler(HttpHandler) added at runtime} during tests.
 */
@Singleton
@ContractsProvided({ HttpHandler.class, TestHandler.class })
@PrefixPath("/test")
public final class TestHandler implements HttpHandler {
  private final PathHandler pathHandler = new PathHandler();

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    pathHandler.handleRequest(exchange);
  }

  /**
   * Adds the specified HTTP handler at a new and distinct path.
   *
   * @param handler the HTTP handler to be assigned a path
   * @return the path assigned to the HTTP handler
   */
  public String addHandler(HttpHandler handler) {
    Objects.requireNonNull(handler);
    String path = "/" + UUID.randomUUID().toString();
    pathHandler.addExactPath(path, handler);
    return "/test" + path;
  }
}
