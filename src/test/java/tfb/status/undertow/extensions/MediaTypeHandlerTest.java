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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.handler.RootHandler;
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
  public void testNoMediaTypesAllowed(HttpTester http,
                                      RootHandler rootHandler)
      throws IOException, InterruptedException {

    String path = "/none" + getClass().getName();

    rootHandler.addExactPath(
        path,
        new MediaTypeHandler());

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
  public void testUnrelatedMediaTypes(HttpTester http,
                                      RootHandler rootHandler)
      throws IOException, InterruptedException {

    String path = "/plaintextOrJson" + getClass().getName();

    rootHandler.addExactPath(
        path,
        new MediaTypeHandler()
            .addMediaType(
                "text/plain",
                new FixedResponseBodyHandler("plaintextHandler"))
            .addMediaType(
                "application/json",
                new FixedResponseBodyHandler("jsonHandler")));

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
  public void testMostSpecificMediaType(HttpTester http,
                                        RootHandler rootHandler)
      throws IOException, InterruptedException {

    String path = "/text" + getClass().getName();

    rootHandler.addExactPath(
        path,
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
   * Verifies that a {@link MediaTypeHandler} throws an exception when the
   * caller attempts to add a handler whose media type is completely encompassed
   * by a previously-added handler's media type.
   */
  @Test
  public void testUnusableHandlerRejected() {
    var handler = new MediaTypeHandler();
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
  public void testAnyMediaType(HttpTester http,
                               RootHandler rootHandler)
      throws IOException, InterruptedException {

    String path = "/wildcard" + getClass().getName();

    rootHandler.addExactPath(
        path,
        new MediaTypeHandler().addMediaType(
            "*/*",
            new FixedResponseBodyHandler("wildcardHandler")));

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
