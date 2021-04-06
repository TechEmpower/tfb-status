package tfb.status.handler;

import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;

import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.resource.PathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import jakarta.inject.Singleton;
import java.util.Objects;
import tfb.status.handler.routing.Route;
import tfb.status.handler.routing.SetHeader;
import org.glassfish.hk2.extras.provides.Provides;
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
  @Route(method = "GET", path = "/raw/{resultsFileName}")
  // This endpoint is used by the TFB website when rendering results by uuid.
  @SetHeader(name = ACCESS_CONTROL_ALLOW_ORIGIN, value = "*")
  public static HttpHandler downloadResultsHandler(FileStore fileStore) {
    Objects.requireNonNull(fileStore);

    var resourceManager = new PathResourceManager(fileStore.resultsDirectory());
    var resourceHandler = new ResourceHandler(resourceManager);
    resourceHandler.setWelcomeFiles();

    // Trim the "/raw" prefix from the front of the request path, since that
    // prefix would confuse the ResourceHandler.
    return new PathHandler().addPrefixPath("/raw", resourceHandler);
  }
}
