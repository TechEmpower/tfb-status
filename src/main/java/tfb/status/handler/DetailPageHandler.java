package tfb.status.handler;

import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static com.google.common.net.MediaType.HTML_UTF_8;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.undertow.util.Headers.CONTENT_TYPE;
import static io.undertow.util.Methods.GET;
import static io.undertow.util.StatusCodes.NOT_FOUND;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.DisableCacheHandler;
import io.undertow.server.handlers.SetHeaderHandler;
import java.io.IOException;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import tfb.status.handler.routing.PrefixPath;
import tfb.status.hk2.extensions.Provides;
import tfb.status.service.HomeResultsReader;
import tfb.status.service.MustacheRenderer;
import tfb.status.undertow.extensions.HttpHandlers;
import tfb.status.undertow.extensions.MethodHandler;
import tfb.status.view.DetailPageView;
import tfb.status.view.HomePageView.ResultsView;

/**
 * Handles requests for the results detail page.
 */
@Singleton
public final class DetailPageHandler implements HttpHandler {
  private final HomeResultsReader homeResultsReader;
  private final MustacheRenderer mustacheRenderer;
  private final ObjectMapper objectMapper;

  @Inject
  public DetailPageHandler(HomeResultsReader homeResultsReader,
                           MustacheRenderer mustacheRenderer,
                           ObjectMapper objectMapper) {

    this.homeResultsReader = Objects.requireNonNull(homeResultsReader);
    this.mustacheRenderer = Objects.requireNonNull(mustacheRenderer);
    this.objectMapper = Objects.requireNonNull(objectMapper);
  }

  @Provides
  @Singleton
  @PrefixPath("/results")
  public HttpHandler detailPageHandler() {
    return HttpHandlers.chain(
        this,
        handler -> new MethodHandler().addMethod(GET, handler),
        handler -> new DisableCacheHandler(handler),
        handler -> new SetHeaderHandler(handler,
                                        ACCESS_CONTROL_ALLOW_ORIGIN,
                                        "*"));
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws IOException {
    Matcher matcher = REQUEST_PATH_PATTERN.matcher(exchange.getRelativePath());

    if (!matcher.matches()) {
      exchange.setStatusCode(NOT_FOUND);
      return;
    }

    String uuid = matcher.group("uuid");
    boolean isJson = exchange.getRelativePath().endsWith(".json");

    ResultsView result = homeResultsReader.resultsByUuid(uuid);

    if (result == null) {
      exchange.setStatusCode(NOT_FOUND);
      return;
    }

    var detailPageView = new DetailPageView(result);

    if (isJson) {
      String json = objectMapper.writeValueAsString(detailPageView);
      exchange.getResponseHeaders().put(CONTENT_TYPE, JSON_UTF_8.toString());
      exchange.getResponseSender().send(json);
    } else {
      String html = mustacheRenderer.render("detail.mustache", detailPageView);
      exchange.getResponseHeaders().put(CONTENT_TYPE, HTML_UTF_8.toString());
      exchange.getResponseSender().send(html);
    }
  }

  // Matches "/6f221937-b8e5-4b22-a52d-020d2538fa64.json", for example.
  private static final Pattern REQUEST_PATH_PATTERN =
      Pattern.compile("^/(?<uuid>[\\w-]+)(\\.json)?$");
}
