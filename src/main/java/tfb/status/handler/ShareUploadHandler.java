package tfb.status.handler;

import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.undertow.util.Headers.CONTENT_TYPE;
import static io.undertow.util.Headers.LOCATION;
import static io.undertow.util.Methods.POST;
import static io.undertow.util.StatusCodes.BAD_REQUEST;
import static io.undertow.util.StatusCodes.CREATED;
import static io.undertow.util.StatusCodes.UNSUPPORTED_MEDIA_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static tfb.status.undertow.extensions.RequestValues.detectMediaType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.MediaType;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.DisableCacheHandler;
import io.undertow.server.handlers.SetHeaderHandler;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.server.handlers.form.MultiPartParserDefinition;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import tfb.status.handler.routing.ExactPath;
import tfb.status.hk2.extensions.Provides;
import tfb.status.service.ShareManager;
import tfb.status.service.ShareManager.ShareOutcome;
import tfb.status.undertow.extensions.HttpHandlers;
import tfb.status.undertow.extensions.MethodHandler;
import tfb.status.view.Results;

/**
 * Handles requests from users to share results.json files.
 *
 * <p>This feature is intended for anyone to use; no authentication is required.
 *
 * <p>The results.json files must conform to {@link Results}, meaning that they
 * can deserialize from JSON without error.  The results must also have
 * non-empty {@link Results#testMetadata}.
 *
 * <p>Upon a successful upload, the response body is JSON that describes how to
 * access the raw JSON and how to visualize it on the TFB website.
 *
 * <p>The {@code Content-Type} of incoming requests must be either {@code
 * application/json} or {@code multipart/form-data}.  If the {@code
 * Content-Type} of the request is {@code application/json}, then the request
 * body must be the content of the results.json file.  If the {@code
 * Content-Type} of the request is {@code multipart/form-data}, then the
 * results.json file must be included as an element whose name is "results" and
 * that element must be a file.  Here is an example {@code Content-Disposition}
 * header value for such an element:
 *
 * <pre>
 *   Content-Disposition: form-data; name="results"; filename="results.json"
 * </pre>
 */
@Singleton
public final class ShareUploadHandler implements HttpHandler {
  private final ObjectMapper objectMapper;
  private final ShareManager shareManager;

  @Inject
  public ShareUploadHandler(ObjectMapper objectMapper,
                            ShareManager shareManager) {

    this.objectMapper = Objects.requireNonNull(objectMapper);
    this.shareManager = Objects.requireNonNull(shareManager);
  }

  @Provides
  @Singleton
  @ExactPath("/share/upload")
  public HttpHandler shareUploadHandler() {
    return HttpHandlers.chain(
        this,
        handler -> new MethodHandler().addMethod(POST, handler),
        handler -> new DisableCacheHandler(handler),
        handler -> new SetHeaderHandler(handler,
                                        ACCESS_CONTROL_ALLOW_ORIGIN,
                                        "*"));
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws IOException {
    MediaType contentType = detectMediaType(exchange);
    ShareOutcome outcome;

    if (contentType.is(JSON_MEDIA_TYPE))
      outcome = shareManager.shareResults(exchange.getInputStream());

    else if (contentType.is(MULTIPART_FORM_DATA_MEDIA_TYPE)) {
      FormParserFactory parserFactory =
          FormParserFactory
              .builder(/* includeDefault= */ false)
              .addParsers(new MultiPartParserDefinition())
              .build();
      try (FormDataParser parser = parserFactory.createParser(exchange)) {
        FormData form = parser.parseBlocking();
        FormData.FormValue element = form.getFirst("results");
        if (element == null || !element.isFileItem()) {
          exchange.setStatusCode(BAD_REQUEST);
          return;
        }
        try (InputStream inputStream = element.getFileItem().getInputStream()) {
          outcome = shareManager.shareResults(inputStream);
        }
      }

    } else {
      exchange.setStatusCode(UNSUPPORTED_MEDIA_TYPE);
      return;
    }

    if (outcome.isFailure()) {
      String json = objectMapper.writeValueAsString(outcome.getFailure());
      exchange.setStatusCode(BAD_REQUEST);
      exchange.getResponseHeaders().put(CONTENT_TYPE, JSON_UTF_8.toString());
      exchange.getResponseSender().send(json, UTF_8);
      return;
    }

    String json = objectMapper.writeValueAsString(outcome.getSuccess());
    exchange.setStatusCode(CREATED);
    exchange.getResponseHeaders().put(LOCATION, outcome.getSuccess().resultsUrl);
    exchange.getResponseHeaders().put(CONTENT_TYPE, JSON_UTF_8.toString());
    exchange.getResponseSender().send(json, UTF_8);
  }

  private static final MediaType JSON_MEDIA_TYPE =
      MediaType.create("application", "json");

  private static final MediaType MULTIPART_FORM_DATA_MEDIA_TYPE =
      MediaType.create("multipart", "form-data");
}
