package tfb.status.handler;

import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static tfb.status.undertow.extensions.RequestValues.pathParameter;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.resource.PathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import java.util.Objects;
import javax.inject.Singleton;
import tfb.status.handler.routing.Route;
import tfb.status.handler.routing.SetHeader;
import tfb.status.hk2.extensions.Provides;
import tfb.status.service.FileStore;

/**
 * Handles requests to download full, raw, previously-uploaded results files.
 */
public final class DownloadResultsHandler {
  private DownloadResultsHandler() {
    throw new AssertionError("This class cannot be instantiated");
  }

  @Provides
  @Singleton
  @Route(method = "GET", path = "/raw/*")
  // This endpoint is used by the TFB website when rendering results by uuid.
  @SetHeader(name = ACCESS_CONTROL_ALLOW_ORIGIN, value = "*")
  public static HttpHandler downloadResultsHandler(FileStore fileStore) {
    Objects.requireNonNull(fileStore);

    HttpHandler handler =
        new ResourceHandler(
            new PathResourceManager(fileStore.resultsDirectory()));

    handler = new FixResourcePathHandler(handler);

    return handler;
  }

  /**
   * Trims the "/raw" prefix from the front of the request path, since that
   * prefix would confuse the {@link ResourceHandler}.
   */
  private static final class FixResourcePathHandler implements HttpHandler {
    private final HttpHandler next;

    FixResourcePathHandler(HttpHandler next) {
      this.next = Objects.requireNonNull(next);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
      String rest = pathParameter(exchange, "*").orElseThrow();
      exchange.setRelativePath("/" + rest);
      next.handleRequest(exchange);
    }
  }
}
