package tfb.status.handler;

import static io.undertow.util.Methods.GET;

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
import java.util.concurrent.TimeUnit;
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
  private final ServerSentEventHandler sseHandler;
  private final HttpHandler delegate;
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @GuardedBy("this") @Nullable private ScheduledThreadPoolExecutor pingScheduler;
  @GuardedBy("this") @Nullable private ScheduledFuture<?> pingTask;

  @Inject
  public HomeUpdatesHandler(MustacheRenderer mustacheRenderer,
                            HomeResultsReader homeResultsReader) {

    this.mustacheRenderer = Objects.requireNonNull(mustacheRenderer);
    this.homeResultsReader = Objects.requireNonNull(homeResultsReader);

    sseHandler = new ServerSentEventHandler();

    //
    // The "X-Accel-Buffering: no" header prevents a proxy server like nginx
    // from buffering the output from this endpoint, which would break it.
    // See: https://serverfault.com/a/801629
    //

    HttpHandler handler = sseHandler;
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
    // The connections to this endpoint are often idle for long periods of time.
    // If they are idle for too long, and a proxy server like nginx is between
    // this application and the client, the proxy server may kill the
    // connections.  To prevent this, we periodically broadcast a message to
    // every connection.
    //

    pingScheduler = new ScheduledThreadPoolExecutor(1);
    pingScheduler.setRemoveOnCancelPolicy(true);
    pingScheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    pingScheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);

    pingTask =
        pingScheduler.scheduleWithFixedDelay(
            /* command= */ () -> {
              try {
                pingAllConnections();
              } catch (RuntimeException e) {
                // An uncaught exception would de-schedule this task.
                logger.error("Error pinging SSE connections", e);
              }
            },
            /* initialDelay= */ 30,
            /* delay= */ 30,
            /* unit= */ TimeUnit.SECONDS);
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
   * @param uuid the UUID of the results that were updated
   * @throws IOException if an I/O error occurs while reading the results
   */
  public void sendUpdate(String uuid) throws IOException {
    Objects.requireNonNull(uuid);

    Set<ServerSentEventConnection> connections = sseHandler.getConnections();

    logger.info(
        "Result {} updated, {} listeners to be notified",
        uuid,  connections.size());

    if (connections.isEmpty())
      //
      // No one is listening.
      //
      return;

    ResultsView results = homeResultsReader.resultsByUuid(uuid);

    if (results == null) {
      //
      // Uh oh... what happened to the results?  Presumably someone called this
      // method because the results were uploaded just a moment ago, and they've
      // already been lost?
      //
      logger.warn(
          "Result {} not found... what happened?",
          uuid);
      return;
    }

    String html = mustacheRenderer.render("home-result.mustache", results);

    for (ServerSentEventConnection connection : connections)
      //
      // If the connection has been severed, this does nothing.
      //
      connection.send(html);
  }

  /**
   * Broadcasts the text {@code "ping"} to all currently-open SSE connections.
   * This text may be discarded client-side.
   */
  private void pingAllConnections() {
    for (ServerSentEventConnection connection : sseHandler.getConnections()) {
      connection.send("ping");
    }
  }
}
