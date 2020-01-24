package tfb.status.handler;

import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.undertow.util.Headers.CONTENT_TYPE;
import static io.undertow.util.Headers.LOCATION;
import static io.undertow.util.Methods.POST;
import static io.undertow.util.StatusCodes.BAD_REQUEST;
import static io.undertow.util.StatusCodes.CREATED;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.DisableCacheHandler;
import io.undertow.server.handlers.SetHeaderHandler;
import io.undertow.server.handlers.form.EagerFormParsingHandler;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.server.handlers.form.MultiPartParserDefinition;
import java.io.IOException;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import tfb.status.handler.routing.ExactPath;
import tfb.status.hk2.extensions.Provides;
import tfb.status.service.ShareResultsUploader;
import tfb.status.undertow.extensions.HttpHandlers;
import tfb.status.undertow.extensions.MethodHandler;
import tfb.status.view.Results;
import tfb.status.view.ShareResultsErrorJsonView;

/**
 * Handles requests to share results.json files. This is intended for anyone to
 * use, it does not require authentication. This handler accepts fully formed
 * and completed results.json uploads.
 *
 * Submit a multipart/form-data POST request to this handler with the
 * results.json file included as a value under the name "results". The JSON must
 * conform to {@link Results} such that it can deserialize without error, and
 * must have a non-empty {@link Results#testMetadata} array. Upon success, JSON
 * is returned that contains info about how to access the raw JSON and also
 * visualize it on the TechEmpower benchmarks site.
 */
@Singleton
public class ShareResultsFormUploadHandler implements HttpHandler {
  private final ObjectMapper objectMapper;
  private final ShareResultsUploader shareResultsUploader;

  @Inject
  public ShareResultsFormUploadHandler(ObjectMapper objectMapper,
                                   ShareResultsUploader shareResultsUploader) {
    this.objectMapper = Objects.requireNonNull(objectMapper);
    this.shareResultsUploader = Objects.requireNonNull(shareResultsUploader);
  }

  @Provides
  @Singleton
  @ExactPath("/share-results/upload/form")
  public HttpHandler shareResultsFormUploadHandler() {
    FormParserFactory formParserFactory = FormParserFactory
        .builder(/* includeDefault= */ false)
        .addParsers(new MultiPartParserDefinition())
        .build();

    return HttpHandlers.chain(this,
        handler -> new EagerFormParsingHandler(formParserFactory)
            .setNext(handler),
        handler -> new MethodHandler().addMethod(POST, handler),
        handler -> new DisableCacheHandler(handler),
        handler -> new SetHeaderHandler(handler,
                                        ACCESS_CONTROL_ALLOW_ORIGIN,
                                        "*"));
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws IOException {
    FormData form = exchange.getAttachment(FormDataParser.FORM_DATA);

    if (form == null) {
      exchange.setStatusCode(BAD_REQUEST);
      return;
    }

    FormData.FormValue upload = form.getFirst("results");

    if (upload == null || !upload.isFileItem()) {
      exchange.setStatusCode(BAD_REQUEST);
      return;
    }

    ShareResultsUploader.ShareResultsUploadReport report =
        shareResultsUploader.upload(upload.getFileItem().getInputStream());

    exchange.getResponseHeaders().put(CONTENT_TYPE, JSON_UTF_8.toString());

    if (report.isError()) {
      exchange.setStatusCode(BAD_REQUEST);

      String json = objectMapper.writeValueAsString(
          new ShareResultsErrorJsonView(report.getErrorMessage()));
      exchange.getResponseSender().send(json, UTF_8);
    } else {
      exchange.setStatusCode(CREATED);
      exchange.getResponseHeaders().put(
          LOCATION,
          report.getSuccess().resultsUrl);

      String json = objectMapper.writeValueAsString(report.getSuccess());
      exchange.getResponseSender().send(json, UTF_8);
    }
  }
}
