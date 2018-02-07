package tfb.status.handler;

import static com.google.common.net.MediaType.HTML_UTF_8;
import static io.undertow.util.Headers.CONTENT_TYPE;
import static io.undertow.util.Methods.GET;
import static io.undertow.util.StatusCodes.NOT_FOUND;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.DisableCacheHandler;
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
                           MustacheRenderer mustacheRenderer) {

    HttpHandler handler = new CoreHandler(homeResultsReader,
                                          mustacheRenderer);

    handler = new MethodHandler().addMethod(GET, handler);
    handler = new DisableCacheHandler(handler);

    delegate = handler;
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    delegate.handleRequest(exchange);
  }

  private static final class CoreHandler implements HttpHandler {
    private final MustacheRenderer mustacheRenderer;
    private final HomeResultsReader homeResultsReader;

    CoreHandler(HomeResultsReader homeResultsReader,
                MustacheRenderer mustacheRenderer) {
      this.homeResultsReader = Objects.requireNonNull(homeResultsReader);
      this.mustacheRenderer = Objects.requireNonNull(mustacheRenderer);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) {
      String uuid = exchange.getRelativePath()
                            .substring(1); // omit leading slash

      ResultsView result = homeResultsReader.resultsByUuid(uuid);

      if (result == null) {
        exchange.setStatusCode(NOT_FOUND);
        return;
      }

      DetailPageView view = new DetailPageView(result);
      String html = mustacheRenderer.render("detail.mustache", view);

      exchange.getResponseHeaders().put(CONTENT_TYPE, HTML_UTF_8.toString());
      exchange.getResponseSender().send(html);
    }
  }
}
