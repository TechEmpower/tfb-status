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
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import tfb.status.service.FileStore;
import tfb.status.service.HomeResultsReader;
import tfb.status.service.MustacheRenderer;
import tfb.status.undertow.extensions.HttpHandlers;
import tfb.status.undertow.extensions.MethodHandler;
import tfb.status.view.HomePageView;
import tfb.status.view.HomePageView.ResultsView;

/**
 * Handles requests for the home page.
 */
@Singleton
@ExactPath("/")
public final class HomePageHandler implements HttpHandler {
  private final HttpHandler delegate;

  @Inject
  public HomePageHandler(MustacheRenderer mustacheRenderer,
                         HomeResultsReader homeResultsReader,
                         FileStore fileStore) {

    Objects.requireNonNull(mustacheRenderer);
    Objects.requireNonNull(homeResultsReader);
    Objects.requireNonNull(fileStore);

    delegate =
        HttpHandlers.chain(
            exchange ->
                internalHandleRequest(
                    exchange,
                    mustacheRenderer,
                    homeResultsReader,
                    fileStore),
            handler -> new MethodHandler().addMethod(GET, handler),
            handler -> new DisableCacheHandler(handler));
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    delegate.handleRequest(exchange);
  }

  private static void internalHandleRequest(HttpServerExchange exchange,
                                            MustacheRenderer mustacheRenderer,
                                            HomeResultsReader homeResultsReader,
                                            FileStore fileStore)
      throws IOException {

    ImmutableList<ResultsView> results = homeResultsReader.results();

    int skip =
        queryParameterAsInt(
            /* exchange= */ exchange,
            /* parameterName= */ "skip",
            /* valueIfAbsent= */ 0,
            /* valueIfMalformed= */ -1);

    int limit =
        queryParameterAsInt(
            /* exchange= */ exchange,
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

    String announcement = null;

    if (Files.isRegularFile(fileStore.announcementFile())) {
      List<String> lines =
          Files.readAllLines(fileStore.announcementFile(), UTF_8);

      announcement = String.join("\n", lines).strip();

      if (announcement.isEmpty())
        announcement = null;
    }

    var homePageView =
        new HomePageView(
            /* results= */ resultsOnThisPage,
            /* skip= */ skip,
            /* limit= */ limit,
            /* next= */ skip + limit,
            /* hasNext= */ skip + limit < results.size(),
            /* announcement= */ announcement);

    String html = mustacheRenderer.render("home.mustache", homePageView);
    exchange.getResponseHeaders().put(CONTENT_TYPE, HTML_UTF_8.toString());
    exchange.getResponseSender().send(html, UTF_8);
  }
}
