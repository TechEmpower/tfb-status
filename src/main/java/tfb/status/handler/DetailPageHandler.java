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
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import tfb.status.service.HomeResultsReader;
import tfb.status.service.MustacheRenderer;
import tfb.status.undertow.extensions.MethodHandler;
import tfb.status.view.DetailPageView;
import tfb.status.view.HomePageView.ResultsView;

/**
 * Handles requests for the results detail page.
 */
@Singleton
public final class DetailPageHandler implements HttpHandler {
  private final HttpHandler delegate;

  @Inject
  public DetailPageHandler(HomeResultsReader homeResultsReader,
                           MustacheRenderer mustacheRenderer,
                           ObjectMapper objectMapper) {

    HttpHandler handler = new CoreHandler(homeResultsReader,
                                          mustacheRenderer,
                                          objectMapper);

    handler = new MethodHandler().addMethod(GET, handler);
    handler = new DisableCacheHandler(handler);
    handler = new SetHeaderHandler(handler, ACCESS_CONTROL_ALLOW_ORIGIN, "*");

    delegate = handler;
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    delegate.handleRequest(exchange);
  }

  private static final class CoreHandler implements HttpHandler {
    private final HomeResultsReader homeResultsReader;
    private final MustacheRenderer mustacheRenderer;
    private final ObjectMapper objectMapper;

    CoreHandler(HomeResultsReader homeResultsReader,
                MustacheRenderer mustacheRenderer,
                ObjectMapper objectMapper) {
      this.homeResultsReader = Objects.requireNonNull(homeResultsReader);
      this.mustacheRenderer = Objects.requireNonNull(mustacheRenderer);
      this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
      String uuid = exchange.getRelativePath()
                            .substring(1); // omit leading slash

      boolean isJson = uuid.endsWith(".json");
      if (isJson)
        uuid = uuid.substring(0, uuid.length() - ".json".length());

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
  }
}
