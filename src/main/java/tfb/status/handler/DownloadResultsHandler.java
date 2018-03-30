package tfb.status.handler;

import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.SetHeaderHandler;
import io.undertow.server.handlers.resource.PathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.inject.Inject;
import javax.inject.Singleton;
import tfb.status.config.FileStoreConfig;

/**
 * Handles requests to download full, raw, previously-uploaded results files.
 */
@Singleton
public final class DownloadResultsHandler implements HttpHandler {
  private final HttpHandler delegate;

  @Inject
  public DownloadResultsHandler(FileStoreConfig fileStoreConfig) {
    Path resultsDirectory = Paths.get(fileStoreConfig.resultsDirectory);
    var resourceManager = new PathResourceManager(resultsDirectory);

    HttpHandler handler = new ResourceHandler(resourceManager);
    handler = new SetHeaderHandler(handler, ACCESS_CONTROL_ALLOW_ORIGIN, "*");

    delegate = handler;
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    delegate.handleRequest(exchange);
  }
}
