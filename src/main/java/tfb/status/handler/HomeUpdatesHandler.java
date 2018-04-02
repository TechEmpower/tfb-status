package tfb.status.handler;

import static io.undertow.util.Methods.GET;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.errorprone.annotations.concurrent.GuardedBy;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.DisableCacheHandler;
import io.undertow.server.handlers.SetHeaderHandler;
import io.undertow.server.handlers.sse.ServerSentEventConnection;
import io.undertow.server.handlers.sse.ServerSentEventHandler;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.service.HomeResultsReader;
import tfb.status.service.MustacheRenderer;
import tfb.status.undertow.extensions.MethodHandler;
import tfb.status.view.HomePageView.ResultsView;

/**
 * Handles request to subscribe to updates to the home page.  This relies on
 * server-sent events.
 */
@Singleton
public final class HomeUpdatesHandler implements HttpHandler {
  private final MustacheRenderer mustacheRenderer;
  private final HomeResultsReader homeResultsReader;
  private final ServerSentEventHandler eventHandler;
  private final HttpHandler delegate;
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @GuardedBy("this") @Nullable private ScheduledThreadPoolExecutor pingScheduler;
  @GuardedBy("this") @Nullable private ScheduledFuture<?> pingTask;

  @Inject
  public HomeUpdatesHandler(MustacheRenderer mustacheRenderer,
                            HomeResultsReader homeResultsReader) {

    this.mustacheRenderer = Objects.requireNonNull(mustacheRenderer);
    this.homeResultsReader = Objects.requireNonNull(homeResultsReader);

    eventHandler = new ServerSentEventHandler();

    //
    // The "X-Accel-Buffering: no" header prevents a proxy server like nginx
    // from buffering the output from this endpoint, which would break it.
    // See: https://serverfault.com/a/801629
    //

    HttpHandler handler = eventHandler;
    handler = new SetHeaderHandler(handler, "X-Accel-Buffering", "no");
    handler = new MethodHandler().addMethod(GET, handler);
    handler = new DisableCacheHandler(handler);

    delegate = handler;
  }

  /**
   * Initializes resources used by this handler.
   */
  @PostConstruct
  public synchronized void start() {

    //
    // The connections to this endpoint are often idle for long periods of time,
    // and if they are idle for too long, and a proxy server like nginx is
    // between this application and the client, the proxy server may kill the
    // connections.  To prevent this, we periodically broadcast a message to
    // every connection.
    //

    pingScheduler = new ScheduledThreadPoolExecutor(1);
    pingScheduler.setRemoveOnCancelPolicy(true);
    pingScheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    pingScheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);

    pingTask =
        pingScheduler.scheduleWithFixedDelay(
            this::pingAllConnections, 30, 30, SECONDS);
  }

  /**
   * Cleans up resources used by this handler.
   */
  @PreDestroy
  public synchronized void stop() {
    ScheduledFuture<?> task = this.pingTask;
    if (task != null) {
      task.cancel(false);
      this.pingTask = null;
    }

    ScheduledThreadPoolExecutor scheduler = this.pingScheduler;
    if (scheduler != null) {
      scheduler.shutdownNow();
      this.pingScheduler = null;
    }
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    delegate.handleRequest(exchange);
  }

  /**
   * Notifies all active listeners that a set of results has been updated.
   *
   * @param resultsUuid the resultsUuid of the results that were updated
   * @throws IOException if an I/O error occurs while reading the results
   */
  public void sendUpdate(String resultsUuid) throws IOException {
    Objects.requireNonNull(resultsUuid);

    Set<ServerSentEventConnection> connections = eventHandler.getConnections();

    logger.info(
        "Result {} updated, {} listeners to be notified",
        resultsUuid,  connections.size());

    if (connections.isEmpty())
      //
      // No one is listening.
      //
      return;

    ResultsView results = homeResultsReader.resultsByUuid(resultsUuid);

    if (results == null) {
      //
      // Uh oh... what happened to the results?  Presumably someone called this
      // method because the results were uploaded just a moment ago, and they've
      // already been lost?
      //
      logger.warn(
          "Result {} not found... what happened?",
          resultsUuid);
      return;
    }

    String html = mustacheRenderer.render("home-result.mustache", results);

    for (ServerSentEventConnection connection : connections)
      //
      // If the connection has been severed, this does nothing.
      //
      connection.send(html);
  }

  private void pingAllConnections() {
    //
    // This is conservatively written to catch RuntimeException because this
    // code is meant to run periodically in a ScheduledThreadPoolExecutor.  If
    // this code were to throw an uncaught exception, the executor would not
    // schedule any more executions of this code.  We'd rather that it record
    // the failure (which we hope is temporary) and try again next time.
    //
    try {
      for (ServerSentEventConnection connection : eventHandler.getConnections()) {
        connection.send("ping");
      }
    } catch (RuntimeException e) {
      logger.error("Error pinging SSE connections", e);
    }
  }
}
