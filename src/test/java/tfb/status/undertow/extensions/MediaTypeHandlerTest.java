package tfb.status.undertow.extensions;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.ANY_TYPE;
import static io.undertow.util.StatusCodes.OK;
import static io.undertow.util.StatusCodes.UNSUPPORTED_MEDIA_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.net.MediaType;
import io.undertow.server.HttpHandler;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.testlib.HttpTester;
import tfb.status.testlib.TestServicesInjector;

/**
 * Tests for {@link MediaTypeHandler}.
 */
@ExtendWith(TestServicesInjector.class)
public final class MediaTypeHandlerTest {
  /**
   * Verifies that a {@link MediaTypeHandler} with no handlers added rejects all
   * requests.
   */
  @Test
  public void testNoMediaTypesAllowed(HttpTester http)
      throws IOException, InterruptedException {

    MediaTypeHandler handler = new MediaTypeHandler();

    String path = http.addHandler(handler);

    URI uri = http.uri(path);

    HttpResponse<String> response1 =
        http.client().send(
            HttpRequest.newBuilder(uri)
                       .header(CONTENT_TYPE, "text/plain")
                       .POST(HttpRequest.BodyPublishers.ofString("hi"))
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(UNSUPPORTED_MEDIA_TYPE, response1.statusCode());

    HttpResponse<String> response2 =
        http.client().send(
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
  public void testUnrelatedMediaTypes(HttpTester http)
      throws IOException, InterruptedException {

    MediaTypeHandler handler =
        new MediaTypeHandler()
            .addMediaType(
                "text/plain",
                new FixedResponseBodyHandler("plaintextHandler"))
            .addMediaType(
                "application/json",
                new FixedResponseBodyHandler("jsonHandler"));

    String path = http.addHandler(handler);

    URI uri = http.uri(path);

    HttpResponse<String> response1 =
        http.client().send(
            HttpRequest.newBuilder(uri)
                       .header(CONTENT_TYPE, "text/plain")
                       .POST(HttpRequest.BodyPublishers.ofString("hi"))
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(OK, response1.statusCode());
    assertEquals("plaintextHandler", response1.body());

    HttpResponse<String> response2 =
        http.client().send(
            HttpRequest.newBuilder(uri)
                       .header(CONTENT_TYPE, "application/json")
                       .POST(HttpRequest.BodyPublishers.ofString("hi"))
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(OK, response2.statusCode());
    assertEquals("jsonHandler", response2.body());

    HttpResponse<String> response3 =
        http.client().send(
            HttpRequest.newBuilder(uri)
                       .header(CONTENT_TYPE, "foo/bar")
                       .POST(HttpRequest.BodyPublishers.ofString("hi"))
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(UNSUPPORTED_MEDIA_TYPE, response3.statusCode());

    HttpResponse<String> response4 =
        http.client().send(
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
  public void testMostSpecificMediaType(HttpTester http)
      throws IOException, InterruptedException {

    MediaTypeHandler handler =
        new MediaTypeHandler()
            // Intentional ordering of addMediaType calls.
            .addMediaType(
                "text/plain",
                new FixedResponseBodyHandler("plainHandler"))
            // Add a more specific media type.
            .addMediaType(
                "text/plain;charset=utf-8",
                new FixedResponseBodyHandler("utf8Handler"))
            // Add a less specific media type.
            .addMediaType(
                "text/*",
                new FixedResponseBodyHandler("otherHandler"));

    String path = http.addHandler(handler);

    URI uri = http.uri(path);

    HttpResponse<String> response1 =
        http.client().send(
            HttpRequest.newBuilder(uri)
                       .header(CONTENT_TYPE, "text/plain;charset=utf-8")
                       .POST(HttpRequest.BodyPublishers.ofString("hi"))
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(OK, response1.statusCode());
    assertEquals("utf8Handler", response1.body());

    HttpResponse<String> response2 =
        http.client().send(
            HttpRequest.newBuilder(uri)
                       .header(CONTENT_TYPE, "text/plain;charset=us-ascii")
                       .POST(HttpRequest.BodyPublishers.ofString("hi"))
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(OK, response2.statusCode());
    assertEquals("plainHandler", response2.body());

    HttpResponse<String> response3 =
        http.client().send(
            HttpRequest.newBuilder(uri)
                       .header(CONTENT_TYPE, "text/plain")
                       .POST(HttpRequest.BodyPublishers.ofString("hi"))
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(OK, response3.statusCode());
    assertEquals("plainHandler", response3.body());

    HttpResponse<String> response4 =
        http.client().send(
            HttpRequest.newBuilder(uri)
                       .header(CONTENT_TYPE, "text/css")
                       .POST(HttpRequest.BodyPublishers.ofString("hi"))
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(OK, response4.statusCode());
    assertEquals("otherHandler", response4.body());

    HttpResponse<String> response5 =
        http.client().send(
            HttpRequest.newBuilder(uri)
                       .header(CONTENT_TYPE, "foo/bar")
                       .POST(HttpRequest.BodyPublishers.ofString("hi"))
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(UNSUPPORTED_MEDIA_TYPE, response5.statusCode());

    HttpResponse<String> response6 =
        http.client().send(
            HttpRequest.newBuilder(uri)
                       .POST(HttpRequest.BodyPublishers.noBody())
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(UNSUPPORTED_MEDIA_TYPE, response6.statusCode());
  }

  /**
   * Verifies that {@link MediaTypeHandler#addMediaType(String, HttpHandler)}
   * throws an exception for an already-added media type.
   */
  @Test
  public void testDuplicateMediaTypeRejected() {
    MediaTypeHandler handler =
        new MediaTypeHandler()
            .addMediaType("text/plain", exchange -> {});

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
  public void testAnyMediaType(HttpTester http)
      throws IOException, InterruptedException {

    MediaTypeHandler handler =
        new MediaTypeHandler()
            .addMediaType(
                ANY_TYPE,
                new FixedResponseBodyHandler("wildcardHandler"));

    String path = http.addHandler(handler);

    URI uri = http.uri(path);

    HttpResponse<String> response1 =
        http.client().send(
            HttpRequest.newBuilder(uri)
                       .header(CONTENT_TYPE, "foo/bar")
                       .POST(HttpRequest.BodyPublishers.ofString("hi"))
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(OK, response1.statusCode());
    assertEquals("wildcardHandler", response1.body());

    HttpResponse<String> response2 =
        http.client().send(
            HttpRequest.newBuilder(uri)
                       .POST(HttpRequest.BodyPublishers.noBody())
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(OK, response2.statusCode());
    assertEquals("wildcardHandler", response2.body());
  }
}
