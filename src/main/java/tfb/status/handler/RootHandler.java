package tfb.status.handler;

import io.undertow.server.DefaultResponseListener;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.accesslog.AccessLogHandler;
import java.io.IOException;
import java.util.Objects;
import javax.inject.Inject;
import org.glassfish.hk2.api.IterableProvider;
import org.glassfish.hk2.api.ServiceHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.undertow.extensions.LazyHandler;

/**
 * Handles every incoming HTTP request, routing to other handlers based on path.
 *
 * <p>In order for this root handler to recognize the other handlers, those
 * other handlers must:
 *
 * <ul>
 * <li>Use this application's dependency injection framework to bind themselves
 *     to the {@link HttpHandler} contract.
 * <li>Annotate themselves with either {@link ExactPath} or {@link PrefixPath}.
 * </ul>
 *
 * <p>This handler provides the following features in addition to routing:
 *
 * <ul>
 * <li>Incoming HTTP requests are logged.
 * <li>Exceptions thrown from other handlers are logged.
 * <li>Incoming HTTP requests are {@linkplain HttpServerExchange#startBlocking()
 *     blocking}.  Other handlers are permitted to perform blocking operations
 *     such as {@link HttpServerExchange#getInputStream()} and {@link
 *     HttpServerExchange#getOutputStream()}.
 * </ul>
 *
 * @see ExactPath
 * @see PrefixPath
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
        throw new InvalidHttpHandlerException(
            "HTTP handler class "
                + handlerClass.getName()
                + " has no @"
                + ExactPath.class.getSimpleName()
                + " or @"
                + PrefixPath.class.getSimpleName()
                + " annotation; it should have one or the other");

      if (exactPath != null && prefixPath != null)
        throw new InvalidHttpHandlerException(
            "HTTP handler class "
                + handlerClass.getName()
                + " has both @"
                + ExactPath.class.getSimpleName()
                + " and @"
                + PrefixPath.class.getSimpleName()
                + " annotations; it should have one or the other");

      HttpHandler handler = new LazyHandler(() -> serviceHandle.getService());

      if (exactPath != null)
        pathHandler.addExactPath(exactPath.value(), handler);
      else
        pathHandler.addPrefixPath(prefixPath.value(), handler);
    }

    Logger logger = LoggerFactory.getLogger("http");

    HttpHandler handler = pathHandler;

    handler = newAccessLoggingHandler(handler, logger);
    handler = new ExceptionLoggingHandler(handler, logger);
    handler = new BlockingHandler(handler);

    delegate = handler;
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    delegate.handleRequest(exchange);
  }

  private static HttpHandler newAccessLoggingHandler(HttpHandler handler,
                                                     Logger logger) {
    Objects.requireNonNull(handler);
    Objects.requireNonNull(logger);

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

  /**
   * An HTTP handler that ensures that <em>all</em> uncaught exceptions from a
   * caller-supplied HTTP handler are logged.
   *
   * <p>By default, Undertow only logs <em>some</em> uncaught exceptions.  In
   * particular, it does not log uncaught {@link IOException}s.  This class
   * fixes that problem.
   */
  // TODO: Figure out how to disable Undertow's default exception logging.
  private static final class ExceptionLoggingHandler implements HttpHandler {
    private final HttpHandler handler;
    private final ExchangeCompletionListener listener;

    ExceptionLoggingHandler(HttpHandler handler, Logger logger) {
      this.handler = Objects.requireNonNull(handler);
      this.listener = new ExceptionLoggingListener(logger);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
      exchange.addExchangeCompleteListener(listener);
      handler.handleRequest(exchange);
    }

    private static final class ExceptionLoggingListener
        implements ExchangeCompletionListener {

      private final Logger logger;

      ExceptionLoggingListener(Logger logger) {
        this.logger = Objects.requireNonNull(logger);
      }

      @Override
      public void exchangeEvent(HttpServerExchange exchange,
                                NextListener nextListener) {
        Throwable exception =
            exchange.getAttachment(DefaultResponseListener.EXCEPTION);

        if (exception != null)
          logger.error(
              "Uncaught exception from HTTP handler {} {}",
              exchange.getRequestMethod(),
              exchange.getRequestURI(),
              exception);

        nextListener.proceed();
      }
    }
  }

  /**
   * An exception thrown during the initialization of {@link RootHandler} when
   * a particular {@link HttpHandler} implementation appears to be invalid.
   */
  private static final class InvalidHttpHandlerException
      extends RuntimeException {

    InvalidHttpHandlerException(String message) {
      super(Objects.requireNonNull(message));
    }

    private static final long serialVersionUID = 0;
  }
}
