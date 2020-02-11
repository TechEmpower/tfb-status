package tfb.status.undertow.extensions;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static io.undertow.util.StatusCodes.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static tfb.status.testlib.MoreAssertions.assertMediaType;

import com.google.common.net.MediaType;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.SetHeaderHandler;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.testlib.HttpTester;
import tfb.status.testlib.TestServicesInjector;

/**
 * Tests for {@link DefaultToUtf8Handler}.
 */
@ExtendWith(TestServicesInjector.class)
public final class DefaultToUtf8HandlerTest {
  /**
   * Verifies that {@link DefaultToUtf8Handler} does not modify the {@code
   * Content-Type} of text responses that already specify UTF-8 as the charset.
   */
  @Test
  public void testAlreadyUtf8(HttpTester http)
      throws IOException, InterruptedException {

    HttpHandler handler =
        new DefaultToUtf8Handler(
            new SetHeaderHandler(exchange -> {},
                                 CONTENT_TYPE,
                                 "text/plain;charset=utf-8"));

    String path = http.addHandler(handler);

    HttpResponse<String> response =
        http.getString(path);

    assertEquals(OK, response.statusCode());

    assertMediaType(
        MediaType.parse("text/plain;charset=utf-8"),
        response.headers()
                .firstValue(CONTENT_TYPE)
                .orElse(null));
  }

  /**
   * Verifies that {@link DefaultToUtf8Handler} does not modify the {@code
   * Content-Type} of text responses that already specify a charset other than
   * UTF-8.
   */
  @Test
  public void testOtherCharset(HttpTester http)
      throws IOException, InterruptedException {

    HttpHandler handler =
        new DefaultToUtf8Handler(
            new SetHeaderHandler(exchange -> {},
                                 CONTENT_TYPE,
                                 "text/plain;charset=us-ascii"));

    String path = http.addHandler(handler);

    HttpResponse<String> response =
        http.getString(path);

    assertEquals(OK, response.statusCode());

    assertMediaType(
        MediaType.parse("text/plain;charset=us-ascii"),
        response.headers()
                .firstValue(CONTENT_TYPE)
                .orElse(null));
  }

  /**
   * Verifies that {@link DefaultToUtf8Handler} modifies the {@code
   * Content-Type} of text responses that do not specify a charset.
   */
  @Test
  public void testMissingCharset(HttpTester http)
      throws IOException, InterruptedException {

    HttpHandler handler =
        new DefaultToUtf8Handler(
            new SetHeaderHandler(exchange -> {},
                                 CONTENT_TYPE,
                                 "text/plain"));

    String path = http.addHandler(handler);

    HttpResponse<String> response =
        http.getString(path);

    assertEquals(OK, response.statusCode());

    assertMediaType(
        MediaType.parse("text/plain;charset=utf-8"),
        response.headers()
                .firstValue(CONTENT_TYPE)
                .orElse(null));
  }

  /**
   * Verifies that {@link DefaultToUtf8Handler} modifies the {@code
   * Content-Type} of JavaScript responses that do not specify a charset.
   */
  @Test
  public void testMissingCharset_js(HttpTester http)
      throws IOException, InterruptedException {

    HttpHandler handler =
        new DefaultToUtf8Handler(
            new SetHeaderHandler(exchange -> {},
                                 CONTENT_TYPE,
                                 "application/javascript"));

    String path = http.addHandler(handler);

    HttpResponse<String> response =
        http.getString(path);

    assertEquals(OK, response.statusCode());

    assertMediaType(
        MediaType.parse("application/javascript;charset=utf-8"),
        response.headers()
                .firstValue(CONTENT_TYPE)
                .orElse(null));
  }

  /**
   * Verifies that {@link DefaultToUtf8Handler} does not modify the {@code
   * Content-Type} of non-text responses.
   */
  @Test
  public void testNonText(HttpTester http)
      throws IOException, InterruptedException {

    HttpHandler handler =
        new DefaultToUtf8Handler(
            new SetHeaderHandler(exchange -> {},
                                 CONTENT_TYPE,
                                 "application/octet-stream"));

    String path = http.addHandler(handler);

    HttpResponse<String> response =
        http.getString(path);

    assertEquals(OK, response.statusCode());

    assertMediaType(
        MediaType.parse("application/octet-stream"),
        response.headers()
                .firstValue(CONTENT_TYPE)
                .orElse(null));
  }

  /**
   * Verifies that {@link DefaultToUtf8Handler} does not modify the {@code
   * Content-Type} of responses that do not specify a {@code Content-Type} at
   * all.
   */
  @Test
  public void testMissingContentType(HttpTester http)
      throws IOException, InterruptedException {

    HttpHandler handler = new DefaultToUtf8Handler(exchange -> {});

    String path = http.addHandler(handler);

    HttpResponse<String> response =
        http.getString(path);

    assertEquals(OK, response.statusCode());

    assertEquals(
        Optional.empty(),
        response.headers().firstValue(CONTENT_TYPE));
  }
}
