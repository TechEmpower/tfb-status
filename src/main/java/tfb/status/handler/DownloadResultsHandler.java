package tfb.status.handler;

import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.undertow.util.Methods.GET;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.SetHeaderHandler;
import io.undertow.server.handlers.resource.PathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import javax.inject.Inject;
import javax.inject.Singleton;
import tfb.status.service.FileStore;
import tfb.status.undertow.extensions.MethodHandler;

/**
 * Handles requests to download full, raw, previously-uploaded results files.
 */
@Singleton
public final class DownloadResultsHandler implements HttpHandler {
  private final HttpHandler delegate;

  @Inject
  public DownloadResultsHandler(FileStore fileStore) {
    var resourceManager = new PathResourceManager(fileStore.resultsDirectory());

    HttpHandler handler = new ResourceHandler(resourceManager);
    handler = new MethodHandler().addMethod(GET, handler);
    handler = new SetHeaderHandler(handler, ACCESS_CONTROL_ALLOW_ORIGIN, "*");

    delegate = handler;
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    delegate.handleRequest(exchange);
  }
}
