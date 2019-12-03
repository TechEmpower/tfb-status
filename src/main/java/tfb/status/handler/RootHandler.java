package tfb.status.handler;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.accesslog.AccessLogHandler;
import java.util.Objects;
import javax.inject.Inject;
import org.glassfish.hk2.api.IterableProvider;
import org.glassfish.hk2.api.ServiceHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.undertow.extensions.ExceptionLoggingHandler;
import tfb.status.undertow.extensions.LazyHandler;

/**
 * Handles every incoming request, forwarding to other handlers based on path.
 */
public final class RootHandler implements HttpHandler {
  private final HttpHandler delegate;

  @Inject
  public RootHandler(IterableProvider<HttpHandler> handlers) {
    Objects.requireNonNull(handlers);

    PathHandler pathHandler = new PathHandler();

    for (ServiceHandle<HttpHandler> serviceHandle : handlers.handleIterator()) {
      Class<?> handlerClass =
          serviceHandle.getActiveDescriptor()
                       .getImplementationClass();

      ExactPath exactPath = handlerClass.getAnnotation(ExactPath.class);
      PrefixPath prefixPath = handlerClass.getAnnotation(PrefixPath.class);

      if (exactPath == null && prefixPath == null)
        throw new IllegalStateException(
            "HTTP handler class "
                + handlerClass.getName()
                + " has no @"
                + ExactPath.class.getSimpleName()
                + " or @" + PrefixPath.class.getSimpleName()
                + " annotation; it should have one or the other");

      if (exactPath != null && prefixPath != null)
        throw new IllegalStateException(
            "HTTP handler class "
                + handlerClass.getName()
                + " has both @"
                + ExactPath.class.getSimpleName()
                + " and @" + PrefixPath.class.getSimpleName()
                + " annotations; it should have one or the other");

      HttpHandler handler = new LazyHandler(() -> serviceHandle.getService());

      if (exactPath != null)
        pathHandler.addExactPath(exactPath.value(), handler);
      else
        pathHandler.addPrefixPath(prefixPath.value(), handler);
    }

    HttpHandler handler = pathHandler;

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
