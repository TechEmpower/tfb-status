package tfb.status.testlib;

import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PathHandler;
import java.util.Objects;
import java.util.UUID;
import javax.inject.Singleton;
import tfb.status.handler.routing.Route;
import tfb.status.hk2.extensions.Provides;

/**
 * Forwards requests to HTTP handlers that were {@linkplain
 * #addHandler(HttpHandler) added at runtime} during tests.
 */
@Singleton
public final class TestHandler {
  private final PathHandler pathHandler = new PathHandler();

  @Provides
  @Singleton
  @Route(method = "GET", path = "/test/{anyPath:.+}")
  @Route(method = "POST", path = "/test/{anyPath:.+}")
  // Pass through HEAD and OPTIONS for the sake of MethodHandlerTest.
  @Route(method = "HEAD", path = "/test/{anyPath:.+}")
  @Route(method = "OPTIONS", path = "/test/{anyPath:.+}")
  public HttpHandler getHandler() {
    return pathHandler;
  }

  /**
   * Adds the specified HTTP handler at a new and distinct path.
   *
   * @param handler the HTTP handler to be assigned a path
   * @return the path assigned to the HTTP handler
   */
  public String addHandler(HttpHandler handler) {
    Objects.requireNonNull(handler);
    String path = "/test/" + UUID.randomUUID().toString();
    pathHandler.addPrefixPath(path, handler);
    return path;
  }
}
