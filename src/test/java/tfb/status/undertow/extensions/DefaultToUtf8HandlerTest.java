package tfb.status.undertow.extensions;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static io.undertow.util.StatusCodes.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tfb.status.util.MoreAssertions.assertMediaType;

import com.google.common.net.MediaType;
import io.undertow.server.handlers.SetHeaderHandler;
import java.io.IOException;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tfb.status.bootstrap.TestServices;

/**
 * Tests for {@link DefaultToUtf8Handler}.
 */
public final class DefaultToUtf8HandlerTest {
  private static TestServices services;

  @BeforeAll
  public static void beforeAll() {
    services = new TestServices();

    services.addExactPath(
        "/utf8",
        new DefaultToUtf8Handler(
            new SetHeaderHandler(exchange -> {},
                                 CONTENT_TYPE,
                                 "text/plain;charset=utf-8")));

    services.addExactPath(
        "/otherCharset",
        new DefaultToUtf8Handler(
            new SetHeaderHandler(exchange -> {},
                                 CONTENT_TYPE,
                                 "text/plain;charset=us-ascii")));

    services.addExactPath(
        "/missingCharset",
        new DefaultToUtf8Handler(
            new SetHeaderHandler(exchange -> {},
                                 CONTENT_TYPE,
                                 "text/plain")));

    services.addExactPath(
        "/nonText",
        new DefaultToUtf8Handler(
            new SetHeaderHandler(exchange -> {},
                                 CONTENT_TYPE,
                                 "application/octet-stream")));

    services.addExactPath(
        "/missingContentType",
        new DefaultToUtf8Handler(exchange -> {}));
  }

  @AfterAll
  public static void afterAll() {
    services.shutdown();
  }

  /**
   * Verifies that {@link DefaultToUtf8Handler} does not modify the {@code
   * Content-Type} of text responses that already specify UTF-8 as the charset.
   */
  @Test
  public void testAlreadyUtf8() throws IOException, InterruptedException {
    HttpResponse<String> response =
        services.httpGetString("/utf8");

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
  public void testOtherCharset() throws IOException, InterruptedException {
    HttpResponse<String> response =
        services.httpGetString("/otherCharset");

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
  public void testMissingCharset() throws IOException, InterruptedException {
    HttpResponse<String> response =
        services.httpGetString("/missingCharset");

    assertEquals(OK, response.statusCode());

    assertMediaType(
        MediaType.parse("text/plain;charset=utf-8"),
        response.headers()
                .firstValue(CONTENT_TYPE)
                .orElse(null));
  }

  /**
   * Verifies that {@link DefaultToUtf8Handler} does not modify the {@code
   * Content-Type} of non-text responses.
   */
  @Test
  public void testNonText() throws IOException, InterruptedException {
    HttpResponse<String> response =
        services.httpGetString("/nonText");

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
  public void testMissingContentType() throws IOException, InterruptedException {
    HttpResponse<String> response =
        services.httpGetString("/missingContentType");

    assertEquals(OK, response.statusCode());

    assertTrue(response.headers()
                       .firstValue(CONTENT_TYPE)
                       .isEmpty());
  }
}
