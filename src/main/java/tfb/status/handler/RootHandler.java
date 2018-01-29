package tfb.status.handler;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.accesslog.AccessLogHandler;
import java.util.Objects;
import javax.inject.Inject;
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
  public RootHandler(HomePageHandler home,
                     HomeUpdatesHandler updates,
                     UploadResultsHandler upload,
                     RobotsHandler robots,
                     DownloadResultsHandler download,
                     ExportResultsHandler export,
                     UnzipResultsHandler unzip,
                     TimelinePageHandler timeline,
                     AboutPageHandler about,
                     AssetsHandler assets,
                     AttributesPageHandler attributes,
                     SaveAttributesHandler saveAttributes) {

    Objects.requireNonNull(home);
    Objects.requireNonNull(updates);
    Objects.requireNonNull(upload);
    Objects.requireNonNull(robots);
    Objects.requireNonNull(download);
    Objects.requireNonNull(export);
    Objects.requireNonNull(unzip);
    Objects.requireNonNull(timeline);
    Objects.requireNonNull(about);
    Objects.requireNonNull(assets);
    Objects.requireNonNull(attributes);
    Objects.requireNonNull(saveAttributes);

    HttpHandler handler =
        new PathHandler().addExactPath("/", home)
                         .addExactPath("/updates", updates)
                         .addExactPath("/upload", upload)
                         .addExactPath("/robots.txt", robots)
                         .addPrefixPath("/raw", download)
                         .addPrefixPath("/export", export)
                         .addPrefixPath("/unzip", unzip)
                         .addPrefixPath("/timeline", timeline)
                         .addPrefixPath("/about", about)
                         .addPrefixPath("/assets", assets)
                         .addPrefixPath("/saveAttributes", saveAttributes)
                         .addPrefixPath("/attributes", attributes);


    handler = newAccessLoggingHandler(handler);
    handler = new ExceptionLoggingHandler(handler);
    handler = new BlockingHandler(handler);

    delegate = handler;
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    delegate.handleRequest(exchange);
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
