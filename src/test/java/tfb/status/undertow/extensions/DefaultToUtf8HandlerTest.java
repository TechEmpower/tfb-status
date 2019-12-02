package tfb.status.undertow.extensions;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static io.undertow.util.StatusCodes.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tfb.status.testlib.MoreAssertions.assertMediaType;

import com.google.common.net.MediaType;
import io.undertow.server.handlers.SetHeaderHandler;
import java.io.IOException;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.handler.RootHandler;
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
  public void testAlreadyUtf8(HttpTester http,
                              RootHandler rootHandler)
      throws IOException, InterruptedException {

    rootHandler.addExactPath(
        "/utf8",
        new DefaultToUtf8Handler(
            new SetHeaderHandler(exchange -> {},
                                 CONTENT_TYPE,
                                 "text/plain;charset=utf-8")));

    HttpResponse<String> response =
        http.getString("/utf8");

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
  public void testOtherCharset(HttpTester http,
                               RootHandler rootHandler)
      throws IOException, InterruptedException {

    rootHandler.addExactPath(
        "/otherCharset",
        new DefaultToUtf8Handler(
            new SetHeaderHandler(exchange -> {},
                                 CONTENT_TYPE,
                                 "text/plain;charset=us-ascii")));

    HttpResponse<String> response =
        http.getString("/otherCharset");

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
  public void testMissingCharset(HttpTester http,
                                 RootHandler rootHandler)
      throws IOException, InterruptedException {

    rootHandler.addExactPath(
        "/missingCharset",
        new DefaultToUtf8Handler(
            new SetHeaderHandler(exchange -> {},
                                 CONTENT_TYPE,
                                 "text/plain")));

    HttpResponse<String> response =
        http.getString("/missingCharset");

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
  public void testMissingCharset_js(HttpTester http,
                                    RootHandler rootHandler)
      throws IOException, InterruptedException {

    rootHandler.addExactPath(
        "/missingCharset.js",
        new DefaultToUtf8Handler(
            new SetHeaderHandler(exchange -> {},
                                 CONTENT_TYPE,
                                 "application/javascript")));

    HttpResponse<String> response =
        http.getString("/missingCharset.js");

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
  public void testNonText(HttpTester http,
                          RootHandler rootHandler)
      throws IOException, InterruptedException {

    rootHandler.addExactPath(
        "/nonText",
        new DefaultToUtf8Handler(
            new SetHeaderHandler(exchange -> {},
                                 CONTENT_TYPE,
                                 "application/octet-stream")));

    HttpResponse<String> response =
        http.getString("/nonText");

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
  public void testMissingContentType(HttpTester http,
                                     RootHandler rootHandler)
      throws IOException, InterruptedException {

    rootHandler.addExactPath(
        "/missingContentType",
        new DefaultToUtf8Handler(exchange -> {}));

    HttpResponse<String> response =
        http.getString("/missingContentType");

    assertEquals(OK, response.statusCode());

    assertTrue(response.headers()
                       .firstValue(CONTENT_TYPE)
                       .isEmpty());
  }
}
