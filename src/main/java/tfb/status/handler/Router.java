package tfb.status.handler;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import tfb.status.undertow.extensions.LazyHandler;

/**
 * Forwards to other HTTP handlers based on path.
 */
@Singleton
public final class Router implements HttpHandler {
  private final HttpHandler delegate;

  @Inject
  public Router(Provider<HomePageHandler> home,
                Provider<HomeUpdatesHandler> updates,
                Provider<UploadResultsHandler> upload,
                Provider<RobotsHandler> robots,
                Provider<DownloadResultsHandler> download,
                Provider<ExportResultsHandler> export,
                Provider<UnzipResultsHandler> unzip,
                Provider<TimelinePageHandler> timeline,
                Provider<DetailPageHandler> detail,
                Provider<AboutPageHandler> about,
                Provider<AssetsHandler> assets,
                Provider<AttributesPageHandler> attributes,
                Provider<SaveAttributesHandler> saveAttributes) {

    delegate =
        new PathHandler()
            .addExactPath("/", new LazyHandler(home))
            .addExactPath("/updates", new LazyHandler(updates))
            .addExactPath("/upload", new LazyHandler(upload))
            .addExactPath("/robots.txt", new LazyHandler(robots))
            .addPrefixPath("/raw", new LazyHandler(download))
            .addPrefixPath("/export", new LazyHandler(export))
            .addPrefixPath("/unzip", new LazyHandler(unzip))
            .addPrefixPath("/timeline", new LazyHandler(timeline))
            .addPrefixPath("/results", new LazyHandler(detail))
            .addPrefixPath("/about", new LazyHandler(about))
            .addPrefixPath("/assets", new LazyHandler(assets))
            .addExactPath("/saveAttributes", new LazyHandler(saveAttributes))
            .addExactPath("/attributes", new LazyHandler(attributes));
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    delegate.handleRequest(exchange);
  }
}
