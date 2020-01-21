package tfb.status.undertow.extensions;

import io.undertow.Handlers;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import java.util.Objects;

/**
 * Utility methods related to {@link HttpHandler}.  Similar to {@link Handlers}.
 */
public final class HttpHandlers {
  private HttpHandlers() {
    throw new AssertionError("This class cannot be instantiated");
  }

  /**
   * Returns the result of wrapping the specified HTTP handler in each of the
   * specified wrappers.
   */
  public static HttpHandler chain(HttpHandler handler,
                                  HandlerWrapper... wrappers) {

    HttpHandler result = Objects.requireNonNull(handler);

    for (HandlerWrapper wrapper : wrappers)
      result = wrapper.wrap(result);

    return result;
  }
}
