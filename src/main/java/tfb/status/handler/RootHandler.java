package tfb.status.handler;

import io.undertow.server.DefaultResponseListener;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.accesslog.AccessLogHandler;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Objects;
import java.util.Set;
import javax.inject.Singleton;
import org.glassfish.hk2.api.IterableProvider;
import org.glassfish.hk2.api.PerLookup;
import org.glassfish.hk2.api.ServiceHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.handler.routing.AllPaths;
import tfb.status.handler.routing.ExactPath;
import tfb.status.handler.routing.PrefixPath;
import tfb.status.hk2.extensions.Provides;
import tfb.status.undertow.extensions.HttpHandlers;

/**
 * Handles every incoming HTTP request, routing to other handlers based on path.
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
 */
public final class RootHandler {
  private RootHandler() {
    throw new AssertionError("This class cannot be instantiated");
  }

  @Provides
  @Singleton
  @AllPaths
  public static HttpHandler rootHandler(IterableProvider<HttpHandler> handlers) {
    Objects.requireNonNull(handlers);

    PathHandler pathHandler = new PathHandler();

    handlerLoop:
    for (ServiceHandle<HttpHandler> serviceHandle : handlers.handleIterator()) {
      Set<Annotation> qualifiers =
          serviceHandle.getActiveDescriptor()
                       .getQualifierAnnotations();

      ExactPath exactPath = null;
      PrefixPath prefixPath = null;

      for (Annotation annotation : qualifiers) {
        if (annotation.annotationType() == AllPaths.class) {
          continue handlerLoop;
        } else if (annotation.annotationType() == ExactPath.class) {
          exactPath = (ExactPath) annotation;
        } else if (annotation.annotationType() == PrefixPath.class) {
          prefixPath = (PrefixPath) annotation;
        }
      }

      if (exactPath == null && prefixPath == null)
        throw new InvalidHttpHandlerException(
            "HTTP handler has no @"
                + ExactPath.class.getSimpleName()
                + " or @"
                + PrefixPath.class.getSimpleName()
                + " annotation; it should have one or the other.\n"
                + "Handler info:\n"
                + "-----------------------------------------\n"
                + serviceHandle
                + "\n"
                + "-----------------------------------------");

      if (exactPath != null && prefixPath != null)
        throw new InvalidHttpHandlerException(
            "HTTP handler has both @"
                + ExactPath.class.getSimpleName()
                + " and @"
                + PrefixPath.class.getSimpleName()
                + " annotations; it should have one or the other.\n"
                + "Handler info:\n"
                + "-----------------------------------------\n"
                + serviceHandle
                + "\n"
                + "-----------------------------------------");

      if (exactPath != null) {
        HttpHandler handler = new ProvidedHandler(handlers.qualifiedWith(exactPath));
        pathHandler.addExactPath(exactPath.value(), handler);
      } else {
        // requireNonNull for NullAway's sake.
        Objects.requireNonNull(prefixPath);
        HttpHandler handler = new ProvidedHandler(handlers.qualifiedWith(prefixPath));
        pathHandler.addPrefixPath(prefixPath.value(), handler);
      }
    }

    Logger logger = LoggerFactory.getLogger("http");

    return HttpHandlers.chain(
        pathHandler,
        handler -> newAccessLoggingHandler(handler, logger),
        handler -> new ExceptionLoggingHandler(handler, logger),
        handler -> new BlockingHandler(handler));
  }

  /**
   * An HTTP handler that is lazily loaded from an {@link IterableProvider}.
   * For each incoming request, the handler will be obtained from its {@link
   * IterableProvider#getHandle()}, that handler will handle the request, and
   * then {@link ServiceHandle#close()} will be called if the handler has {@link
   * PerLookup} scope.
   */
  private static final class ProvidedHandler implements HttpHandler {
    final IterableProvider<HttpHandler> provider;

    private ProvidedHandler(IterableProvider<HttpHandler> provider) {
      this.provider = Objects.requireNonNull(provider);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
      ServiceHandle<HttpHandler> serviceHandle = provider.getHandle();

      boolean isPerLookup =
          serviceHandle.getActiveDescriptor()
                       .getScopeAnnotation() == PerLookup.class;

      try {
        HttpHandler handler = serviceHandle.getService();
        handler.handleRequest(exchange);
      } finally {
        if (isPerLookup)
          serviceHandle.close();
      }
    }
  }

  /**
   * An HTTP handler that logs all incoming requests and that delegates to a
   * caller-supplied HTTP handler.
   */
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
