package tfb.status.handler;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.accesslog.AccessLogHandler;
import java.util.Objects;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.undertow.extensions.ExceptionLoggingHandler;

/**
 * Handles every incoming request.
 */
public final class RootHandler implements HttpHandler {
  private final HttpHandler delegate;

  @Inject
  public RootHandler(HttpHandler router) {
    Objects.requireNonNull(router);

    HttpHandler handler = router;

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

    String formatString =
        String.join(
            " :: ",
            "%{REQUEST_LINE}",
            "%{RESPONSE_CODE} %{RESPONSE_REASON_PHRASE}",
            "%{BYTES_SENT} bytes",
            "%{RESPONSE_TIME} ms");

    return new AccessLogHandler(
        /* next= */ handler,
        /* accessLogReceiver= */ message -> logger.info(message),
        /* formatString= */ formatString,
        /* classLoader= */ Thread.currentThread().getContextClassLoader());
  }
}
