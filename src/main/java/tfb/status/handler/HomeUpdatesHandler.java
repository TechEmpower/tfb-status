package tfb.status.handler;

import static io.undertow.util.Methods.GET;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.DisableCacheHandler;
import io.undertow.server.handlers.SetHeaderHandler;
import io.undertow.websockets.WebSocketProtocolHandshakeHandler;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.WebSocketCallback;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.glassfish.hk2.api.messaging.MessageReceiver;
import org.glassfish.hk2.api.messaging.SubscribeTo;
import org.jvnet.hk2.annotations.ContractsProvided;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.XnioExecutor;
import tfb.status.service.HomeResultsReader;
import tfb.status.service.MustacheRenderer;
import tfb.status.undertow.extensions.HttpHandlers;
import tfb.status.undertow.extensions.MethodHandler;
import tfb.status.view.HomePageView.ResultsView;
import tfb.status.view.UpdatedResultsEvent;

/**
 * Handles requests to listen for updates to the home page using web sockets.
 */
@Singleton
@ContractsProvided(HttpHandler.class)
@ExactPath("/updates")
@MessageReceiver
public final class HomeUpdatesHandler implements HttpHandler {
  private final WebSocketCallback<Void> wsSendCallback;
  private final WebSocketProtocolHandshakeHandler wsHandler;
  private final HttpHandler delegate;
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Inject
  public HomeUpdatesHandler() {
    wsSendCallback =
        new WebSocketCallback<Void>() {
          @Override
          public void complete(WebSocketChannel channel, Void context) {
            // Do nothing.
          }

          @Override
          public void onError(WebSocketChannel channel,
                              Void context,
                              Throwable throwable) {

            if (channel.isOpen()) {
              logger.warn(
                  "Error sending data to an open web socket",
                  throwable);
            }
          }
        };

    wsHandler =
        new WebSocketProtocolHandshakeHandler(
            /* callback= */
            (WebSocketHttpExchange exchange, WebSocketChannel channel) -> {

              Runnable pingTask =
                  () ->
                      WebSockets.sendPing(
                          UTF_8.encode("hello"),
                          channel,
                          wsSendCallback);

              XnioExecutor.Key pingTimer =
                  channel.getIoThread()
                         .executeAtInterval(
                             /* command= */ pingTask,
                             /* time= */ 15,
                             /* unit= */ TimeUnit.SECONDS);

              channel.addCloseTask(ch -> pingTimer.remove());

              channel.getReceiveSetter().set(new AbstractReceiveListener() {});
              channel.resumeReceives();
            });

    delegate =
        HttpHandlers.chain(
            wsHandler,

            // Prevent proxies such as nginx from buffering our output, which
            // would break this endpoint.
            handler -> new SetHeaderHandler(handler, "X-Accel-Buffering", "no"),

            handler -> new MethodHandler().addMethod(GET, handler),
            handler -> new DisableCacheHandler(handler));
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    delegate.handleRequest(exchange);
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
      // Uh oh... what happened to the results?  Presumably someone called this
      // method because the results were uploaded just a moment ago, and they've
      // already been lost?
      logger.warn(
          "Result {} not found... what happened?",
          uuid);
      return;
    }

    String html = mustacheRenderer.render("home-result.mustache", results);

    for (WebSocketChannel connection : wsConnections)
      WebSockets.sendText(html, connection, wsSendCallback);
  }
}
