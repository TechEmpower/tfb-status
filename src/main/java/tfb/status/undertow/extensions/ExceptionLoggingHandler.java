package tfb.status.undertow.extensions;

import io.undertow.server.DefaultResponseListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ResponseCommitListener;
import java.io.IOException;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO: Figure out how to disable Undertow's default exception logging.

/**
 * An HTTP handler that ensures that <em>all</em> uncaught exceptions from a
 * caller-supplied HTTP handler are logged.
 *
 * <p>By default, Undertow only logs <em>some</em> uncaught exceptions.  In
 * particular, it does not log uncaught {@link IOException}s.  This class fixes
 * that problem.
 */
public final class ExceptionLoggingHandler implements HttpHandler {
  private final HttpHandler handler;
  private final ResponseCommitListener listener;

  /**
   * Constructs a new HTTP handler that forwards all requests to the provided
   * HTTP handler and logs any uncaught exceptions.
   */
  public ExceptionLoggingHandler(HttpHandler handler) {
    this.handler = Objects.requireNonNull(handler);
    this.listener = new ExceptionLoggingListener();
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    exchange.addResponseCommitListener(listener);
    handler.handleRequest(exchange);
  }

  private static final class ExceptionLoggingListener
      implements ResponseCommitListener {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public void beforeCommit(HttpServerExchange exchange) {
      Throwable exception =
          exchange.getAttachment(DefaultResponseListener.EXCEPTION);

      if (exception != null)
        logger.error(
            "Uncaught exception from HTTP handler {} {}",
            exchange.getRequestMethod(),
            exchange.getRequestURI(),
            exception);
    }
  }
}
