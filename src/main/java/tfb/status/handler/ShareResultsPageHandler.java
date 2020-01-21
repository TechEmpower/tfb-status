package tfb.status.handler;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.DisableCacheHandler;
import io.undertow.server.handlers.form.EagerFormParsingHandler;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.server.handlers.form.MultiPartParserDefinition;
import tfb.status.handler.routing.ExactPath;
import tfb.status.hk2.extensions.Provides;
import tfb.status.service.MustacheRenderer;
import tfb.status.service.ShareResultsUploader;
import tfb.status.undertow.extensions.HttpHandlers;
import tfb.status.undertow.extensions.MethodHandler;
import tfb.status.view.ShareResultsJsonView;
import tfb.status.view.ShareResultsPageView;

import static com.google.common.net.MediaType.HTML_UTF_8;
import static io.undertow.util.Headers.CONTENT_TYPE;
import static io.undertow.util.Methods.GET;
import static io.undertow.util.Methods.POST;
import static io.undertow.util.StatusCodes.METHOD_NOT_ALLOWED;
import static java.nio.charset.StandardCharsets.UTF_8;

@Singleton
public class ShareResultsPageHandler implements HttpHandler {
  private final MustacheRenderer mustacheRenderer;
  private final ShareResultsUploader shareResultsUploader;

  @Inject
  public ShareResultsPageHandler(MustacheRenderer mustacheRenderer,
                                 ShareResultsUploader shareResultsUploader) {
    this.mustacheRenderer = Objects.requireNonNull(mustacheRenderer);
    this.shareResultsUploader = Objects.requireNonNull(shareResultsUploader);
  }

  @Provides
  @Singleton
  @ExactPath("/share-results-pastebin")
  public HttpHandler shareResultsPageHandler() {
    FormParserFactory formParserFactory = FormParserFactory.builder()
        .addParsers(new MultiPartParserDefinition())
        .build();

    return HttpHandlers.chain(
        this,
        handler -> new MethodHandler()
            .addMethod(GET, handler)
            .addMethod(POST,
                new EagerFormParsingHandler(formParserFactory).setNext(handler)),
        handler -> new DisableCacheHandler(handler));
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    ShareResultsPageView pageView;

    if (POST.equals(exchange.getRequestMethod())) {
      FormData form = exchange.getAttachment(FormDataParser.FORM_DATA);

      if (form == null) {
        pageView = new ShareResultsPageView(
            /* error= */ "Unable to parse the request form data");
      } else {
        FormData.FormValue shareTypeData = form.getFirst("share-type");
        String shareType;

        if (shareTypeData == null || (shareType = shareTypeData.getValue()) == null) {
          pageView = new ShareResultsPageView(/* error= */ "Share type is required");
        } else {
          switch (shareType) {
            case SHARE_TYPE_PASTE:
              pageView = handlePasteJson(form);
              break;
            case SHARE_TYPE_UPLOAD:
              pageView = handleUploadFile(form);
              break;
            default:
              pageView = new ShareResultsPageView(
                  /* error= */ "Invalid share type: " + shareType);
          }
        }
      }
    } else if (GET.equals(exchange.getRequestMethod())) {
      pageView = new ShareResultsPageView();
    } else {
      // This should never happen if the above handlers chain is configured correctly.
      throw new IllegalArgumentException(
          "Unsupported method: " + exchange.getRequestMethod());
    }

    String html = mustacheRenderer.render("share-results.mustache", pageView);
    exchange.getResponseHeaders().put(CONTENT_TYPE, HTML_UTF_8.toString());
    exchange.getResponseSender().send(html, UTF_8);
  }

  private ShareResultsPageView handlePasteJson(FormData form) throws IOException {
    FormData.FormValue pasteResultsJsonData = form.getFirst("paste-results-json");

    String pasteResultsJson = null;
    ShareResultsJsonView success = null;
    List<String> errors = new ArrayList<>();

    if (pasteResultsJsonData == null
        || (pasteResultsJson = pasteResultsJsonData.getValue()) == null) {
      errors.add("JSON is required");
    } else {
      try {
        success = shareResultsUploader.upload(pasteResultsJson);
        // Clear the form on successful submission.
        pasteResultsJson = null;
      } catch (ShareResultsUploader.ShareResultsUploadException e) {
        errors.add(e.getMessage());
      }
    }

    return new ShareResultsPageView(
        /* shareType= */ SHARE_TYPE_PASTE,
        /* pasteResultsJson= */ pasteResultsJson,
        /* errors= */ errors,
        /* success= */ success);
  }

  private ShareResultsPageView handleUploadFile(FormData form) throws IOException {
    FormData.FormValue uploadResultsJsonData = form.getFirst("upload-results-json");

    List<String> errors = new ArrayList<>();
    ShareResultsJsonView success = null;

    if (uploadResultsJsonData == null || !uploadResultsJsonData.isFileItem()) {
      errors.add("File upload is required");
    } else {
      FormData.FileItem uploadResultsJsonFileItem = uploadResultsJsonData.getFileItem();
      Path tempFile = uploadResultsJsonFileItem.getFile();

      try {
        success = shareResultsUploader.upload(tempFile);
      } catch (ShareResultsUploader.ShareResultsUploadException e) {
        errors.add(e.getMessage());
      }
    }

    return new ShareResultsPageView(
        /* shareType= */ "upload",
        /* pasteResultsJson= */ null,
        /* errors= */ errors,
        /* success= */ success);
  }

  private static final String SHARE_TYPE_PASTE = "paste";
  private static final String SHARE_TYPE_UPLOAD = "upload";
}
