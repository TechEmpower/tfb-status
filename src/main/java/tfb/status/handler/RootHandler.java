package tfb.status.handler;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.accesslog.AccessLogHandler;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.undertow.extensions.ExceptionLoggingHandler;

/**
 * Handles every incoming request, forwarding to other handlers based on path.
 */
@Singleton
public final class RootHandler implements HttpHandler {
  private final HttpHandler delegate;

  @Inject
  public RootHandler(Provider<HomePageHandler> home,
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

    HttpHandler handler =
        new PathHandler().addExactPath("/", lazyInit(home))
                         .addExactPath("/updates", lazyInit(updates))
                         .addExactPath("/upload", lazyInit(upload))
                         .addExactPath("/robots.txt", lazyInit(robots))
                         .addPrefixPath("/raw", lazyInit(download))
                         .addPrefixPath("/export", lazyInit(export))
                         .addPrefixPath("/unzip", lazyInit(unzip))
                         .addPrefixPath("/timeline", lazyInit(timeline))
                         .addPrefixPath("/results", lazyInit(detail))
                         .addPrefixPath("/about", lazyInit(about))
                         .addPrefixPath("/assets", lazyInit(assets))
                         .addExactPath("/saveAttributes", lazyInit(saveAttributes))
                         .addExactPath("/attributes", lazyInit(attributes));

    handler = newAccessLoggingHandler(handler);
    handler = new ExceptionLoggingHandler(handler);
    handler = new BlockingHandler(handler);

    delegate = handler;
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    delegate.handleRequest(exchange);
  }

  private static HttpHandler lazyInit(Provider<? extends HttpHandler> provider) {
    Objects.requireNonNull(provider);
    return exchange -> provider.get().handleRequest(exchange);
  }

  private static HttpHandler newAccessLoggingHandler(HttpHandler handler) {
    Objects.requireNonNull(handler);
    Logger logger = LoggerFactory.getLogger("http");
    return new AccessLogHandler(
        /* next= */ handler,
        /* accessLogReceiver= */ message -> logger.info(message),
        /* formatString= */ "common",
        /* classLoader= */ Thread.currentThread().getContextClassLoader());
  }
}
