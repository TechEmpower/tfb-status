package tfb.status.undertow.extensions;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static io.undertow.util.StatusCodes.OK;
import static io.undertow.util.StatusCodes.UNSUPPORTED_MEDIA_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.net.MediaType;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tfb.status.bootstrap.TestServices;

/**
 * Tests for {@link MediaTypeHandler}.
 */
public final class MediaTypeHandlerTest {
  private static TestServices services;

  @BeforeAll
  public static void beforeAll() {
    services = new TestServices();

    services.addExactPath(
        "/none",
        new MediaTypeHandler());

    services.addExactPath(
        "/plaintextOrJson",
        new MediaTypeHandler()
            .addMediaType(
                "text/plain",
                new FixedResponseBodyHandler("plaintextHandler"))
            .addMediaType(
                "application/json",
                new FixedResponseBodyHandler("jsonHandler")));

    services.addExactPath(
        "/text",
        new MediaTypeHandler()
            .addMediaType(
                "text/plain;charset=utf-8",
                new FixedResponseBodyHandler("utf8Handler"))
            .addMediaType(
                "text/plain",
                new FixedResponseBodyHandler("plainHandler"))
            .addMediaType(
                "text/*",
                new FixedResponseBodyHandler("otherHandler")));

    services.addExactPath(
        "/wildcard",
        new MediaTypeHandler().addMediaType(
            "*/*",
            new FixedResponseBodyHandler("wildcardHandler")));
  }

  @AfterAll
  public static void afterAll() {
    services.shutdown();
  }

  /**
   * Verifies that a {@link MediaTypeHandler} with no handlers added rejects all
   * requests.
   */
  @Test
  public void testNoMediaTypesAllowed() throws IOException, InterruptedException {
    URI uri = services.httpUri("/none");

    HttpResponse<String> response1 =
        services.httpClient().send(
            HttpRequest.newBuilder(uri)
                       .header(CONTENT_TYPE, "text/plain")
                       .POST(HttpRequest.BodyPublishers.ofString("hi"))
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(UNSUPPORTED_MEDIA_TYPE, response1.statusCode());

    HttpResponse<String> response2 =
        services.httpClient().send(
            HttpRequest.newBuilder(uri)
                       .POST(HttpRequest.BodyPublishers.noBody())
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(UNSUPPORTED_MEDIA_TYPE, response2.statusCode());
  }

  /**
   * Verifies that a {@link MediaTypeHandler} routes requests to the correct
   * handler when the media types for each handler are unrelated.
   */
  @Test
  public void testUnrelatedMediaTypes() throws IOException, InterruptedException {
    URI uri = services.httpUri("/plaintextOrJson");

    HttpResponse<String> response1 =
        services.httpClient().send(
            HttpRequest.newBuilder(uri)
                       .header(CONTENT_TYPE, "text/plain")
                       .POST(HttpRequest.BodyPublishers.ofString("hi"))
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(OK, response1.statusCode());
    assertEquals("plaintextHandler", response1.body());

    HttpResponse<String> response2 =
        services.httpClient().send(
            HttpRequest.newBuilder(uri)
                       .header(CONTENT_TYPE, "application/json")
                       .POST(HttpRequest.BodyPublishers.ofString("hi"))
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(OK, response2.statusCode());
    assertEquals("jsonHandler", response2.body());

    HttpResponse<String> response3 =
        services.httpClient().send(
            HttpRequest.newBuilder(uri)
                       .header(CONTENT_TYPE, "foo/bar")
                       .POST(HttpRequest.BodyPublishers.ofString("hi"))
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(UNSUPPORTED_MEDIA_TYPE, response3.statusCode());

    HttpResponse<String> response4 =
        services.httpClient().send(
            HttpRequest.newBuilder(uri)
                       .POST(HttpRequest.BodyPublishers.noBody())
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(UNSUPPORTED_MEDIA_TYPE, response4.statusCode());
  }

  /**
   * Verifies that a {@link MediaTypeHandler} routes requests to the most
   * specific matching handler when the media types for each handler are related
   * and overlapping.
   */
  @Test
  public void testMostSpecificMediaType() throws IOException, InterruptedException {
    URI uri = services.httpUri("/text");

    HttpResponse<String> response1 =
        services.httpClient().send(
            HttpRequest.newBuilder(uri)
                       .header(CONTENT_TYPE, "text/plain;charset=utf-8")
                       .POST(HttpRequest.BodyPublishers.ofString("hi"))
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(OK, response1.statusCode());
    assertEquals("utf8Handler", response1.body());

    HttpResponse<String> response2 =
        services.httpClient().send(
            HttpRequest.newBuilder(uri)
                       .header(CONTENT_TYPE, "text/plain;charset=us-ascii")
                       .POST(HttpRequest.BodyPublishers.ofString("hi"))
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(OK, response2.statusCode());
    assertEquals("plainHandler", response2.body());

    HttpResponse<String> response3 =
        services.httpClient().send(
            HttpRequest.newBuilder(uri)
                       .header(CONTENT_TYPE, "text/plain")
                       .POST(HttpRequest.BodyPublishers.ofString("hi"))
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(OK, response3.statusCode());
    assertEquals("plainHandler", response3.body());

    HttpResponse<String> response4 =
        services.httpClient().send(
            HttpRequest.newBuilder(uri)
                       .header(CONTENT_TYPE, "text/css")
                       .POST(HttpRequest.BodyPublishers.ofString("hi"))
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(OK, response4.statusCode());
    assertEquals("otherHandler", response4.body());

    HttpResponse<String> response5 =
        services.httpClient().send(
            HttpRequest.newBuilder(uri)
                       .header(CONTENT_TYPE, "foo/bar")
                       .POST(HttpRequest.BodyPublishers.ofString("hi"))
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(UNSUPPORTED_MEDIA_TYPE, response5.statusCode());

    HttpResponse<String> response6 =
        services.httpClient().send(
            HttpRequest.newBuilder(uri)
                       .POST(HttpRequest.BodyPublishers.noBody())
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(UNSUPPORTED_MEDIA_TYPE, response6.statusCode());
  }

  /**
   * Verifies that a {@link MediaTypeHandler} throws an exception when the
   * caller attempts to add a handler whose media type is completely encompassed
   * by a previously-added handler's media type.
   */
  @Test
  public void testUnusableHandlerRejected() {
    MediaTypeHandler handler = new MediaTypeHandler();
    handler.addMediaType("text/*", exchange -> {});

    assertThrows(
        IllegalStateException.class,
        () -> handler.addMediaType("text/plain", exchange -> {}));
  }

  /**
   * Verifies that a {@link MediaTypeHandler} with a handler for {@link
   * MediaType#ANY_TYPE} allows requests having any {@code Content-Type},
   * including requests with no {@code Content-Type} header at all.
   */
  @Test
  public void testAnyMediaType() throws IOException, InterruptedException {
    URI uri = services.httpUri("/wildcard");

    HttpResponse<String> response1 =
        services.httpClient().send(
            HttpRequest.newBuilder(uri)
                       .header(CONTENT_TYPE, "foo/bar")
                       .POST(HttpRequest.BodyPublishers.ofString("hi"))
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(OK, response1.statusCode());
    assertEquals("wildcardHandler", response1.body());

    HttpResponse<String> response2 =
        services.httpClient().send(
            HttpRequest.newBuilder(uri)
                       .POST(HttpRequest.BodyPublishers.noBody())
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(OK, response2.statusCode());
    assertEquals("wildcardHandler", response2.body());
  }
}
