package tfb.status.handler;

import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static io.undertow.util.Headers.CONTENT_TYPE;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import javax.inject.Singleton;

/**
 * Handles requests for robots.txt.  We currently disallow all robots.
 */
@Singleton
public final class RobotsHandler implements HttpHandler {
  @Override
  public void handleRequest(HttpServerExchange exchange) {
    exchange.getResponseHeaders().put(CONTENT_TYPE, PLAIN_TEXT_UTF_8.toString());
    exchange.getResponseSender().send("User-agent: *\nDisallow: /");
  }
}
