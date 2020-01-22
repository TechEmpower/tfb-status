package tfb.status.handler;

import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.undertow.util.Headers.CONTENT_TYPE;
import static io.undertow.util.Methods.POST;
import static io.undertow.util.StatusCodes.BAD_REQUEST;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.DisableCacheHandler;
import io.undertow.server.handlers.SetHeaderHandler;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.handler.routing.ExactPath;
import tfb.status.hk2.extensions.Provides;
import tfb.status.service.ShareResultsUploader;
import tfb.status.undertow.extensions.HttpHandlers;
import tfb.status.undertow.extensions.MethodHandler;
import tfb.status.view.Results;
import tfb.status.view.ShareResultsJsonView;

/**
 * Handles requests to share results.json files. This is intended for
 * anyone to use, and requires no authentication. POST fully formed and
 * completed results.json files to this handler in order to upload them. The
 * JSON must conform to {@link Results} such that it can deserialize without
 * error, and must have a non-empty {@link Results#testMetadata} array. Upon
 * success, JSON is returned that contains info about how to access the raw
 * JSON and also visualize it on the TechEmpower benchmarks site.
 */
@Singleton
public final class ShareResultsUploadHandler implements HttpHandler {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final ObjectMapper objectMapper;
  private final ShareResultsUploader shareResultsUploader;

  @Inject
  public ShareResultsUploadHandler(ObjectMapper objectMapper,
                                   ShareResultsUploader shareResultsUploader) {
    this.objectMapper = Objects.requireNonNull(objectMapper);
    this.shareResultsUploader = Objects.requireNonNull(shareResultsUploader);
  }

  @Provides
  @Singleton
  @ExactPath("/share-results/upload")
  public HttpHandler shareResultsUploadHandler() {
    return HttpHandlers.chain(this,
        handler -> new MethodHandler().addMethod(POST, handler),
        handler -> new DisableCacheHandler(handler),
        handler -> new SetHeaderHandler(handler,
                                        ACCESS_CONTROL_ALLOW_ORIGIN,
                                        "*"));
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    ShareResultsJsonView success;
    try {
      success = shareResultsUploader.upload(exchange.getRequestChannel());
    } catch (ShareResultsUploader.ShareResultsUploadException e) {
      logger.info("Error uploading share results", e);
      exchange.setStatusCode(BAD_REQUEST);
      return;
    }

    String json = objectMapper.writeValueAsString(success);
    exchange.getResponseHeaders().put(CONTENT_TYPE, JSON_UTF_8.toString());
    exchange.getResponseSender().send(json, UTF_8);
  }
}
