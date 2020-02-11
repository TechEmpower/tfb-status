package tfb.status.handler;

import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.undertow.util.Headers.CONTENT_TYPE;
import static io.undertow.util.Methods.GET;
import static io.undertow.util.StatusCodes.NOT_FOUND;

import com.google.common.io.ByteSource;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.SetHeaderHandler;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import tfb.status.handler.routing.PrefixPath;
import tfb.status.hk2.extensions.Provides;
import tfb.status.service.ShareManager;
import tfb.status.undertow.extensions.HttpHandlers;
import tfb.status.undertow.extensions.MethodHandler;

/**
 * Handles requests to download results.json files that were shared by users.
 */
@Singleton
public final class ShareDownloadHandler implements HttpHandler {
  private final ShareManager shareManager;

  @Inject
  public ShareDownloadHandler(ShareManager shareManager) {
    this.shareManager = Objects.requireNonNull(shareManager);
  }

  @Provides
  @Singleton
  @PrefixPath("/share/download")
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
    Matcher matcher = REQUEST_PATH_PATTERN.matcher(exchange.getRelativePath());

    if (!matcher.matches()) {
      exchange.setStatusCode(NOT_FOUND);
      return;
    }

    String shareId = matcher.group("shareId");

    ByteSource resultsBytes = shareManager.findSharedResults(shareId);
    if (resultsBytes == null) {
      exchange.setStatusCode(NOT_FOUND);
      return;
    }

    exchange.getResponseHeaders().put(
        CONTENT_TYPE,
        JSON_UTF_8.toString());

    resultsBytes.copyTo(exchange.getOutputStream());
  }

  // Matches "/6f221937-b8e5-4b22-a52d-020d2538fa64.json", for example.
  private static final Pattern REQUEST_PATH_PATTERN =
      Pattern.compile("^/(?<shareId>[\\w-]+)\\.json$");
}