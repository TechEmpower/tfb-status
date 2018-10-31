package tfb.status.handler;

import static io.undertow.util.Methods.GET;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.DisableCacheHandler;
import io.undertow.server.handlers.SetHeaderHandler;
import io.undertow.server.handlers.sse.ServerSentEventConnection;
import io.undertow.server.handlers.sse.ServerSentEventHandler;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;
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

  @Inject
  public HomeUpdatesHandler(MustacheRenderer mustacheRenderer,
                            HomeResultsReader homeResultsReader) {

    this.mustacheRenderer = Objects.requireNonNull(mustacheRenderer);
    this.homeResultsReader = Objects.requireNonNull(homeResultsReader);

    sseHandler =
        new ServerSentEventHandler(
            /* callback= */
            (ServerSentEventConnection connection, String lastEventId) -> {
              // Prevent proxies such as nginx from terminating our idle
              // connections.
              connection.setKeepAliveTime(15000);
            });

    HttpHandler handler = sseHandler;

    // Prevent proxies such as nginx from buffering our output, which would
    // break this endpoint.
    handler = new SetHeaderHandler(handler, "X-Accel-Buffering", "no");

    handler = new MethodHandler().addMethod(GET, handler);
    handler = new DisableCacheHandler(handler);

    delegate = handler;
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
        uuid, connections.size());

    if (connections.isEmpty())
      // No one is listening.
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
      connection.send(html);
  }
}
