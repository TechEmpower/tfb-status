package tfb.status.handler;

import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.undertow.util.Methods.GET;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.SetHeaderHandler;
import io.undertow.server.handlers.resource.PathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.jvnet.hk2.annotations.ContractsProvided;
import tfb.status.service.FileStore;
import tfb.status.undertow.extensions.HttpHandlers;
import tfb.status.undertow.extensions.MethodHandler;

/**
 * Handles requests to download full, raw, previously-uploaded results files.
 */
@Singleton
@ContractsProvided(HttpHandler.class)
@PrefixPath("/raw")
public final class DownloadResultsHandler implements HttpHandler {
  private final HttpHandler delegate;

  @Inject
  public DownloadResultsHandler(FileStore fileStore) {
    Objects.requireNonNull(fileStore);

    delegate =
        HttpHandlers.chain(
            new ResourceHandler(
                new PathResourceManager(fileStore.resultsDirectory())),
            handler -> new MethodHandler().addMethod(GET, handler),
            handler -> new SetHeaderHandler(handler,
                                            ACCESS_CONTROL_ALLOW_ORIGIN,
                                            "*"));
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    delegate.handleRequest(exchange);
  }
}
