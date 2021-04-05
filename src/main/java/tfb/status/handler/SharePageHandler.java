package tfb.status.handler;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Objects;
import tfb.status.handler.routing.DisableCache;
import tfb.status.handler.routing.Route;
import tfb.status.service.MustacheRenderer;

/**
 * Handles requests to render the pastebin-style HTML interface for sharing
 * results.json files.  This page allows anonymous users to upload a local
 * results.json file as a file or to enter the contents of a results.json file
 * as text.
 */
@Singleton
@Route(method = "GET", path = "/share", produces = "text/html; charset=utf-8")
@DisableCache
public final class SharePageHandler implements HttpHandler {
  private final MustacheRenderer mustacheRenderer;

  @Inject
  public SharePageHandler(MustacheRenderer mustacheRenderer) {
    this.mustacheRenderer = Objects.requireNonNull(mustacheRenderer);
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) {
    String html = mustacheRenderer.render("share.mustache");
    exchange.getResponseSender().send(html, UTF_8);
  }
}
