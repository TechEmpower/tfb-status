package tfb.status.handler;

import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.undertow.util.Headers.CONTENT_TYPE;
import static io.undertow.util.Methods.GET;
import static io.undertow.util.StatusCodes.NOT_FOUND;
import static java.nio.file.StandardOpenOption.READ;

import com.google.common.io.ByteStreams;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.SetHeaderHandler;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import tfb.status.handler.routing.PrefixPath;
import tfb.status.hk2.extensions.Provides;
import tfb.status.service.ShareResultsUploader;
import tfb.status.undertow.extensions.HttpHandlers;
import tfb.status.undertow.extensions.MethodHandler;

/**
 * Handles GET requests to view files from the share directory.
 *
 * @see ShareResultsUploadHandler
 */
@Singleton
public class ShareResultsViewHandler implements HttpHandler {
  private final ShareResultsUploader shareResultsUploader;

  @Inject
  public ShareResultsViewHandler(ShareResultsUploader shareResultsUploader) {
    this.shareResultsUploader = Objects.requireNonNull(shareResultsUploader);
  }

  @Provides
  @Singleton
  @PrefixPath("/share-results/view")
  public HttpHandler shareResultsViewHandler() {
    return HttpHandlers.chain(
        this,
        handler -> new MethodHandler().addMethod(GET, handler),
        handler -> new SetHeaderHandler(handler,
                                        ACCESS_CONTROL_ALLOW_ORIGIN,
                                        "*"));
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    if (exchange.getRelativePath().isEmpty()) {
      exchange.setStatusCode(NOT_FOUND);
      return;
    }

    // omit leading slash
    String relativePath = exchange.getRelativePath().substring(1);

    shareResultsUploader.getUpload(
        /* jsonFileName= */ relativePath,
        /* ifPresent= */ (Path zipEntry) -> {
          exchange.getResponseHeaders().put(
              CONTENT_TYPE, JSON_UTF_8.toString());

          try (ReadableByteChannel in = Files.newByteChannel(zipEntry, READ)) {
            WritableByteChannel out = exchange.getResponseChannel();
            ByteStreams.copy(in, out);
          }
        },
        /* ifAbsent= */ () -> exchange.setStatusCode(NOT_FOUND));
  }
}
