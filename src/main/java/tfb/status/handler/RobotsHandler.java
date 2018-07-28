package tfb.status.handler;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static io.undertow.util.Methods.GET;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.SetHeaderHandler;
import javax.inject.Singleton;
import tfb.status.undertow.extensions.FixedResponseBodyHandler;
import tfb.status.undertow.extensions.MethodHandler;

/**
 * Handles requests for robots.txt.  We currently disallow all robots.
 */
@Singleton
public final class RobotsHandler implements HttpHandler {
  private final HttpHandler delegate;

  public RobotsHandler() {
    HttpHandler handler =
        new FixedResponseBodyHandler("User-agent: *\nDisallow: /");

    handler = new SetHeaderHandler(handler,
                                   CONTENT_TYPE,
                                   PLAIN_TEXT_UTF_8.toString());

    handler = new MethodHandler().addMethod(GET, handler);

    delegate = handler;
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    delegate.handleRequest(exchange);
  }
}
