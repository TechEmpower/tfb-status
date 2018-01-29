package tfb.status.handler;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.resource.PathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
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
    delegate =
        new ResourceHandler(
            new PathResourceManager(
                Paths.get(fileStoreConfig.resultsDirectory)));
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    delegate.handleRequest(exchange);
  }
}
