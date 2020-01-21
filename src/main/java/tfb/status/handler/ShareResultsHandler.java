package tfb.status.handler;

import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.ByteStreams;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.DisableCacheHandler;
import io.undertow.server.handlers.SetHeaderHandler;
import io.undertow.util.HttpString;
import tfb.status.handler.routing.PrefixPath;
import tfb.status.hk2.extensions.Provides;
import tfb.status.service.ShareResultsUploader;
import tfb.status.undertow.extensions.HttpHandlers;
import tfb.status.undertow.extensions.MethodHandler;
import tfb.status.view.Results;
import tfb.status.view.ShareResultsJsonView;

import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.undertow.util.Headers.CONTENT_TYPE;
import static io.undertow.util.Methods.GET;
import static io.undertow.util.Methods.POST;
import static io.undertow.util.StatusCodes.BAD_REQUEST;
import static io.undertow.util.StatusCodes.NOT_FOUND;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.READ;

/**
 * A handler for uploading and viewing results.json files. This is intended for
 * anyone to use, and requires no authentication. POST fully formed and completed
 * results.json files to this handler in order to upload them. The JSON must conform
 * to {@link Results} such that it can deserialize without error, and must have a
 * non-empty {@link Results#testMetadata} array. Upon success, JSON is returned
 * that contains info about how to access the raw JSON and also visualize it on
 * the TechEmpower benchmarks site.
 */
@Singleton
public class ShareResultsHandler implements HttpHandler {
  private final ObjectMapper objectMapper;
  private final ShareResultsUploader shareResultsUploader;

  @Inject
  public ShareResultsHandler(ObjectMapper objectMapper,
                             ShareResultsUploader shareResultsUploader) {
    this.objectMapper = Objects.requireNonNull(objectMapper);
    this.shareResultsUploader = Objects.requireNonNull(shareResultsUploader);
  }

  @Provides
  @Singleton
  @PrefixPath("/share-results")
  public HttpHandler shareResultsHandler() {
    return HttpHandlers.chain(
        this,
        handler -> new MethodHandler()
            .addMethod(GET, handler)
            .addMethod(POST, new DisableCacheHandler(handler)),
        handler -> new SetHeaderHandler(handler, ACCESS_CONTROL_ALLOW_ORIGIN, "*"));
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    HttpString method = exchange.getRequestMethod();

    if (method.equals(GET)) {
      handleView(exchange);
    } else if (method.equals(POST)) {
      handleUpload(exchange);
    } else {
      // This should never happen if the above handlers chain is configured correctly.
      throw new IllegalArgumentException("Unsupported method: " + method);
    }
  }

  /**
   * Handle POST upload requests. Every valid request gets placed in the share
   * directory as a new file.
   */
  private void handleUpload(HttpServerExchange exchange) throws Exception {
    if (!exchange.getRelativePath().isEmpty()
        && !exchange.getRelativePath().equals("/")) {
      exchange.setStatusCode(NOT_FOUND);
      return;
    }

    ShareResultsJsonView success;
    try {
      success = shareResultsUploader.upload(exchange.getRequestChannel());
    } catch (ShareResultsUploader.ShareResultsUploadException e) {
      exchange.setStatusCode(BAD_REQUEST);
      return;
    }

    String json = objectMapper.writeValueAsString(success);
    exchange.getResponseHeaders().put(CONTENT_TYPE, JSON_UTF_8.toString());
    exchange.getResponseSender().send(json, UTF_8);
  }

  /**
   * Handles GET requests for view files from the share directory.
   */
  private void handleView(HttpServerExchange exchange) throws Exception {
    if (exchange.getRelativePath().isEmpty()) {
      exchange.setStatusCode(NOT_FOUND);
      return;
    }

    // omit leading slash
    String relativePath = exchange.getRelativePath().substring(1);

    shareResultsUploader.getUpload(
        /* jsonFileName= */ relativePath,
        /* ifPresent= */ (Path zipEntry) -> {
          exchange.getResponseHeaders().put(CONTENT_TYPE, JSON_UTF_8.toString());

          try (ReadableByteChannel in = Files.newByteChannel(zipEntry, READ)) {
            WritableByteChannel out = exchange.getResponseChannel();
            ByteStreams.copy(in, out);
          }
        },
        /* ifAbsent= */ () -> exchange.setStatusCode(NOT_FOUND));
  }
}
