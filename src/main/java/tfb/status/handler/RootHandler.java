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
        new PathHandler().addExactPath("/", new LazyHandler(home))
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

    handler = newAccessLoggingHandler(handler);
    handler = new ExceptionLoggingHandler(handler);
    handler = new BlockingHandler(handler);

    delegate = handler;
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    delegate.handleRequest(exchange);
  }

  /**
   * Delays initialization of the provided handler until a request is received.
   */
  private static final class LazyHandler implements HttpHandler {
    private final Provider<? extends HttpHandler> handlerProvider;

    LazyHandler(Provider<? extends HttpHandler> handlerProvider) {
      this.handlerProvider = Objects.requireNonNull(handlerProvider);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
      HttpHandler handler = handlerProvider.get();
      handler.handleRequest(exchange);
    }
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
