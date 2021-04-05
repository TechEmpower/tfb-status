package tfb.status.handler;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.undertow.server.HttpHandler;
import io.undertow.websockets.WebSocketProtocolHandshakeHandler;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Future;
import org.glassfish.hk2.api.messaging.MessageReceiver;
import org.glassfish.hk2.api.messaging.SubscribeTo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.handler.routing.DisableCache;
import tfb.status.handler.routing.Route;
import tfb.status.handler.routing.SetHeader;
import tfb.status.hk2.extensions.Provides;
import tfb.status.service.HomeResultsReader;
import tfb.status.service.MustacheRenderer;
import tfb.status.service.TaskScheduler;
import tfb.status.view.HomePageView.ResultsView;
import tfb.status.view.UpdatedResultsEvent;

/**
 * Handles requests to listen for updates to the home page using web sockets.
 */
@Singleton
@MessageReceiver
public final class HomeUpdatesHandler {
  private final WebSocketProtocolHandshakeHandler wsHandler;
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Inject
  public HomeUpdatesHandler(TaskScheduler taskScheduler) {
    Objects.requireNonNull(taskScheduler);

    wsHandler =
        new WebSocketProtocolHandshakeHandler(
            /* callback= */
            (WebSocketHttpExchange exchange, WebSocketChannel channel) -> {

              Future<?> pingTask =
                  taskScheduler.repeat(
                      /* task= */
                      () -> {
                        WebSockets.sendPingBlocking(
                            UTF_8.encode("hello"),
                            channel);
                        return null;
                      },
                      /* initialDelay= */
                      Duration.ofSeconds(15),
                      /* interval= */
                      Duration.ofSeconds(15));

              channel.addCloseTask(ch -> pingTask.cancel(true));

              channel.getReceiveSetter().set(new AbstractReceiveListener() {});
              channel.resumeReceives();
            });
  }

  @Provides
  @Singleton
  @Route(method = "GET", path = "/updates")
  @DisableCache
  // Prevent proxies such as nginx from buffering our output, which would break
  // this endpoint.
  @SetHeader(name = "X-Accel-Buffering", value = "no")
  public HttpHandler homeUpdatesHandler() {
    return wsHandler;
  }

  /**
   * Notifies all active listeners that a set of results has been updated.
   *
   * @throws IOException if an I/O error occurs while reading the results
   */
  public void onUpdatedResults(@SubscribeTo UpdatedResultsEvent event,
                               MustacheRenderer mustacheRenderer,
                               HomeResultsReader homeResultsReader)
      throws IOException {

    Objects.requireNonNull(event);
    Objects.requireNonNull(mustacheRenderer);
    Objects.requireNonNull(homeResultsReader);

    String uuid = event.uuid;

    Set<WebSocketChannel> wsConnections = wsHandler.getPeerConnections();

    logger.info(
        "Result {} updated, {} listeners to be notified",
        uuid,
        wsConnections.size());

    if (wsConnections.isEmpty())
      // No one is listening.
      return;

    ResultsView results = homeResultsReader.resultsByUuid(uuid);
    if (results == null) {
      logger.warn(
          "Result {} not found... what happened?",
          uuid);
      return;
    }

    String html = mustacheRenderer.render("home-result.mustache", results);

    for (WebSocketChannel connection : wsConnections)
      WebSockets.sendTextBlocking(html, connection);
  }
}
