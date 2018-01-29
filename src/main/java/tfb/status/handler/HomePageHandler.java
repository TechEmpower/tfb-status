package tfb.status.handler;

import static com.google.common.net.MediaType.HTML_UTF_8;
import static io.undertow.util.Headers.CONTENT_TYPE;
import static io.undertow.util.Methods.GET;
import static io.undertow.util.StatusCodes.BAD_REQUEST;
import static java.nio.charset.StandardCharsets.UTF_8;
import static tfb.status.undertow.extensions.RequestValues.queryParameterAsInt;

import com.google.common.collect.ImmutableList;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.DisableCacheHandler;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import tfb.status.service.HomeResultsReader;
import tfb.status.service.MustacheRenderer;
import tfb.status.undertow.extensions.MethodHandler;
import tfb.status.view.HomePageView;
import tfb.status.view.HomePageView.ResultsView;

/**
 * Handles requests for the home page.
 */
@Singleton
public final class HomePageHandler implements HttpHandler {
  private final HttpHandler delegate;

  @Inject
  public HomePageHandler(MustacheRenderer mustacheRenderer,
                         HomeResultsReader homeResultsReader) {

    HttpHandler handler = new CoreHandler(mustacheRenderer,
                                          homeResultsReader);

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

    CoreHandler(MustacheRenderer mustacheRenderer,
                HomeResultsReader homeResultsReader) {
      this.mustacheRenderer = Objects.requireNonNull(mustacheRenderer);
      this.homeResultsReader = Objects.requireNonNull(homeResultsReader);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) {
      ImmutableList<ResultsView> results = homeResultsReader.results();

      int skip = queryParameterAsInt(/* exchange= */ exchange,
                                     /* parameterName= */ "skip",
                                     /* valueIfAbsent= */ 0,
                                     /* valueIfMalformed= */ -1);

      int limit = queryParameterAsInt(/* exchange= */ exchange,
                                      /* parameterName= */ "limit",
                                      /* valueIfAbsent= */ 50,
                                      /* valueIfMalformed= */ -1);

      if (skip < 0 || limit < 0) {
        exchange.setStatusCode(BAD_REQUEST);
        return;
      }

      ImmutableList<ResultsView> resultsOnThisPage =
          results.subList(Math.min(results.size(), skip),
                          Math.min(results.size(), skip + limit));

      HomePageView homePageView =
          new HomePageView(
              /* results= */ resultsOnThisPage,
              /* skip= */ skip,
              /* limit= */ limit,
              /* next= */ skip + limit,
              /* hasNext= */ skip + limit < results.size());

      String html = mustacheRenderer.render("home.mustache", homePageView);
      exchange.getResponseHeaders().put(CONTENT_TYPE, HTML_UTF_8.toString());
      exchange.getResponseSender().send(html, UTF_8);
    }
  }
}
