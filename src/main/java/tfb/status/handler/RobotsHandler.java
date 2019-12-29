package tfb.status.handler;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static io.undertow.util.Methods.GET;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.SetHeaderHandler;
import javax.inject.Singleton;
import org.jvnet.hk2.annotations.ContractsProvided;
import tfb.status.undertow.extensions.FixedResponseBodyHandler;
import tfb.status.undertow.extensions.HttpHandlers;
import tfb.status.undertow.extensions.MethodHandler;

/**
 * Handles requests for robots.txt.
 */
@Singleton
@ContractsProvided(HttpHandler.class)
@ExactPath("/robots.txt")
public final class RobotsHandler implements HttpHandler {
  private final HttpHandler delegate;

  public RobotsHandler() {
    delegate =
        HttpHandlers.chain(
            new FixedResponseBodyHandler(
                String.join(
                    "\n",
                    "User-agent: *",
                    "Allow: /$",
                    "Allow: /assets",
                    "Disallow: /")),
            handler -> new SetHeaderHandler(handler,
                                            CONTENT_TYPE,
                                            PLAIN_TEXT_UTF_8.toString()),
            handler -> new MethodHandler().addMethod(GET, handler));
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    delegate.handleRequest(exchange);
  }
}
