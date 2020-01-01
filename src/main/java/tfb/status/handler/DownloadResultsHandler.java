package tfb.status.handler;

import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.undertow.util.Methods.GET;

import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.SetHeaderHandler;
import io.undertow.server.handlers.resource.PathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import java.util.Objects;
import javax.inject.Singleton;
import tfb.status.hk2.extensions.Provides;
import tfb.status.service.FileStore;
import tfb.status.undertow.extensions.HttpHandlers;
import tfb.status.undertow.extensions.MethodHandler;

/**
 * Handles requests to download full, raw, previously-uploaded results files.
 */
public final class DownloadResultsHandler {
  private DownloadResultsHandler() {
    throw new AssertionError("This class cannot be instantiated");
  }

  @Provides
  @Singleton
  @PrefixPath("/raw")
  public static HttpHandler downloadResultsHandler(FileStore fileStore) {
    Objects.requireNonNull(fileStore);

    return HttpHandlers.chain(
        new ResourceHandler(
            new PathResourceManager(fileStore.resultsDirectory())),
        handler -> new MethodHandler().addMethod(GET, handler),
        handler -> new SetHeaderHandler(handler,
                                        ACCESS_CONTROL_ALLOW_ORIGIN,
                                        "*"));
  }
}
