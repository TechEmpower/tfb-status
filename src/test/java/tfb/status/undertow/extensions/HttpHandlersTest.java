package tfb.status.undertow.extensions;

import static io.undertow.util.StatusCodes.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.SetHeaderHandler;
import io.undertow.util.HttpString;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.testlib.HttpTester;
import tfb.status.testlib.TestServicesInjector;

/**
 * Tests for {@link HttpHandlers}.
 */
@ExtendWith(TestServicesInjector.class)
public final class HttpHandlersTest {
  /**
   * Verifies that {@link HttpHandlers#chain(HttpHandler, HandlerWrapper...)}
   * produces an HTTP handler such that each request is handled by each of the
   * wrappers in reverse order and then finally by the provided handler.
   */
  @Test
  public void testChain(HttpTester http)
      throws IOException, InterruptedException {

    HttpString headerName = new HttpString("X-HttpHandlersTest");

    HttpHandler chainedHandler =
        HttpHandlers.chain(
            exchange -> {
              // Make this handler's behavior dependent on the wrappers, which
              // allow us to verify that the wrappers executed first.
              Iterable<String> headerValues =
                  exchange.getResponseHeaders().eachValue(headerName);

              String responseBody = String.join(",", headerValues);
              exchange.getResponseSender().send(responseBody);
            },
            handler -> new AddHeaderHandler(headerName, "1", handler),
            handler -> new AddHeaderHandler(headerName, "2", handler),
            handler -> new AddHeaderHandler(headerName, "3", handler),
            handler -> new AddHeaderHandler(headerName, "4", handler));

    String path = http.addHandler(chainedHandler);

    HttpResponse<String> response = http.getString(path);

    assertEquals(OK, response.statusCode());

    // The wrappers execute in reverse order.

    assertEquals(
        "4,3,2,1",
        response.body());

    assertEquals(
        List.of("4", "3", "2", "1"),
        response.headers().allValues(headerName.toString()));
  }

  /**
   * Appends a response header.  Like {@link SetHeaderHandler} except it appends
   * to the existing header values of the same name instead of replacing them.
   */
  private static final class AddHeaderHandler implements HttpHandler {
    private final HttpString headerName;
    private final String headerValue;
    private final HttpHandler next;

    AddHeaderHandler(HttpString headerName, String headerValue, HttpHandler next) {
      this.headerName = Objects.requireNonNull(headerName);
      this.headerValue = Objects.requireNonNull(headerValue);
      this.next = Objects.requireNonNull(next);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
      exchange.getResponseHeaders().add(headerName, headerValue);
      next.handleRequest(exchange);
    }
  }
}
