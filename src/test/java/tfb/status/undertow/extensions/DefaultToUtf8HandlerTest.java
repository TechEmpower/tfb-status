package tfb.status.undertow.extensions;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static io.undertow.util.StatusCodes.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static tfb.status.util.MoreAssertions.assertMediaType;

import com.google.common.net.MediaType;
import io.undertow.server.handlers.SetHeaderHandler;
import javax.ws.rs.core.Response;
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
  public void testAlreadyUtf8() {
    try (Response response = services.httpGet("/utf8")) {
      assertEquals(OK, response.getStatus());
      assertMediaType(
          MediaType.parse("text/plain;charset=utf-8"),
          response.getHeaderString(CONTENT_TYPE));
    }
  }

  /**
   * Verifies that {@link DefaultToUtf8Handler} does not modify the {@code
   * Content-Type} of text responses that already specify a charset other than
   * UTF-8.
   */
  @Test
  public void testOtherCharset() {
    try (Response response = services.httpGet("/otherCharset")) {
      assertEquals(OK, response.getStatus());
      assertMediaType(
          MediaType.parse("text/plain;charset=us-ascii"),
          response.getHeaderString(CONTENT_TYPE));
    }
  }

  /**
   * Verifies that {@link DefaultToUtf8Handler} modifies the {@code
   * Content-Type} of text responses that do not specify a charset.
   */
  @Test
  public void testMissingCharset() {
    try (Response response = services.httpGet("/missingCharset")) {
      assertEquals(OK, response.getStatus());
      assertMediaType(
          MediaType.parse("text/plain;charset=utf-8"),
          response.getHeaderString(CONTENT_TYPE));
    }
  }

  /**
   * Verifies that {@link DefaultToUtf8Handler} does not modify the {@code
   * Content-Type} of non-text responses.
   */
  @Test
  public void testNonText() {
    try (Response response = services.httpGet("/nonText")) {
      assertEquals(OK, response.getStatus());
      assertMediaType(
          MediaType.parse("application/octet-stream"),
          response.getHeaderString(CONTENT_TYPE));
    }
  }

  /**
   * Verifies that {@link DefaultToUtf8Handler} does not modify the {@code
   * Content-Type} of responses that do not specify a {@code Content-Type} at
   * all.
   */
  @Test
  public void testMissingContentType() {
    try (Response response = services.httpGet("/missingContentType")) {
      assertEquals(OK, response.getStatus());
      assertNull(response.getHeaderString(CONTENT_TYPE));
    }
  }
}
