package tfb.status.handler;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;

import io.undertow.server.HttpHandler;
import javax.inject.Singleton;
import tfb.status.handler.routing.Route;
import tfb.status.handler.routing.SetHeader;
import tfb.status.hk2.extensions.Provides;
import tfb.status.undertow.extensions.FixedResponseBodyHandler;

/**
 * Handles requests for robots.txt.
 */
public final class RobotsHandler{
  private RobotsHandler() {
    throw new AssertionError("This class cannot be instantiated");
  }

  @Provides
  @Singleton
  @Route(method = "GET", path = "/robots.txt")
  @SetHeader(name = CONTENT_TYPE, value = "text/plain; charset=utf-8")
  public static HttpHandler robotsHandler() {
    return new FixedResponseBodyHandler(
        String.join(
            "\n",
            "User-agent: *",
            "Allow: /$",
            "Allow: /assets",
            "Disallow: /"));
  }
}
