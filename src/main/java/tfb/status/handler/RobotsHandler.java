package tfb.status.handler;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static io.undertow.util.Methods.GET;

import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.SetHeaderHandler;
import javax.inject.Singleton;
import tfb.status.handler.routing.ExactPath;
import tfb.status.hk2.extensions.Provides;
import tfb.status.undertow.extensions.FixedResponseBodyHandler;
import tfb.status.undertow.extensions.HttpHandlers;
import tfb.status.undertow.extensions.MethodHandler;

/**
 * Handles requests for robots.txt.
 */
public final class RobotsHandler{
  private RobotsHandler() {
    throw new AssertionError("This class cannot be instantiated");
  }

  @Provides
  @Singleton
  @ExactPath("/robots.txt")
  public static HttpHandler robotsHandler() {
    return HttpHandlers.chain(
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
}
