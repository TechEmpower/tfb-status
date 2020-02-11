package tfb.status.undertow.extensions;

import static com.google.common.net.HttpHeaders.ALLOW;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static io.undertow.util.Methods.GET;
import static io.undertow.util.Methods.OPTIONS;
import static io.undertow.util.Methods.POST;
import static io.undertow.util.StatusCodes.METHOD_NOT_ALLOWED;
import static io.undertow.util.StatusCodes.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.base.Splitter;
import io.undertow.server.HttpHandler;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.testlib.HttpTester;
import tfb.status.testlib.TestServicesInjector;

/**
 * Tests for {@link MethodHandler}.
 */
@ExtendWith(TestServicesInjector.class)
public final class MethodHandlerTest {
  /**
   * Verifies that a {@link MethodHandler} with no handlers added only allows
   * OPTIONS requests.
   */
  @Test
  public void testNoMethodsAllowed(HttpTester http)
      throws IOException, InterruptedException {

    HttpHandler handler = new MethodHandler();

    String path = http.addHandler(handler);

    URI uri = http.uri(path);

    HttpResponse<String> response1 =
        http.client().send(
            HttpRequest.newBuilder(uri).build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(METHOD_NOT_ALLOWED, response1.statusCode());

    HttpResponse<String> response2 =
        http.client().send(
            HttpRequest.newBuilder(uri)
                       .header(CONTENT_TYPE, "text/plain")
                       .POST(HttpRequest.BodyPublishers.ofString("hi"))
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(METHOD_NOT_ALLOWED, response2.statusCode());

    HttpResponse<String> response3 =
        http.client().send(
            HttpRequest.newBuilder(uri)
                       .method("HEAD", HttpRequest.BodyPublishers.noBody())
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(METHOD_NOT_ALLOWED, response3.statusCode());

    HttpResponse<String> response4 =
        http.client().send(
            HttpRequest.newBuilder(uri)
                       .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(OK, response4.statusCode());
    assertEquals("", response4.body());
    assertEquals(
        Set.of("OPTIONS"),
        parseAllowHeader(response4));
  }

  /**
   * Verifies that a {@link MethodHandler} with only a GET handler added only
   * allows GET, HEAD, and OPTIONS requests.
   */
  @Test
  public void testGetOnly(HttpTester http)
      throws IOException, InterruptedException {

    HttpHandler handler =
        new MethodHandler()
            .addMethod(GET, new FixedResponseBodyHandler("getHandler"));

    String path = http.addHandler(handler);

    URI uri = http.uri(path);

    HttpResponse<String> response1 =
        http.client().send(
            HttpRequest.newBuilder(uri).build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(OK, response1.statusCode());
    assertEquals("getHandler", response1.body());

    HttpResponse<String> response2 =
        http.client().send(
            HttpRequest.newBuilder(uri)
                       .header(CONTENT_TYPE, "text/plain")
                       .POST(HttpRequest.BodyPublishers.ofString("hi"))
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(METHOD_NOT_ALLOWED, response2.statusCode());

    HttpResponse<String> response3 =
        http.client().send(
            HttpRequest.newBuilder(uri)
                       .method("HEAD", HttpRequest.BodyPublishers.noBody())
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(OK, response3.statusCode());
    assertEquals("", response3.body());

    HttpResponse<String> response4 =
        http.client().send(
            HttpRequest.newBuilder(uri)
                       .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(OK, response4.statusCode());
    assertEquals("", response4.body());
    assertEquals(
        Set.of("GET", "HEAD", "OPTIONS"),
        parseAllowHeader(response4));
  }

  /**
   * Verifies that a {@link MethodHandler} with only a POST handler added only
   * allows POST and OPTIONS requests.
   */
  @Test
  public void testPostOnly(HttpTester http)
      throws IOException, InterruptedException {

    HttpHandler handler =
        new MethodHandler()
            .addMethod(POST, new FixedResponseBodyHandler("postHandler"));

    String path = http.addHandler(handler);

    URI uri = http.uri(path);

    HttpResponse<String> response1 =
        http.client().send(
            HttpRequest.newBuilder(uri).build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(METHOD_NOT_ALLOWED, response1.statusCode());

    HttpResponse<String> response2 =
        http.client().send(
            HttpRequest.newBuilder(uri)
                       .header(CONTENT_TYPE, "text/plain")
                       .POST(HttpRequest.BodyPublishers.ofString("hi"))
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(OK, response2.statusCode());
    assertEquals("postHandler", response2.body());

    HttpResponse<String> response3 =
        http.client().send(
            HttpRequest.newBuilder(uri)
                       .method("HEAD", HttpRequest.BodyPublishers.noBody())
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(METHOD_NOT_ALLOWED, response3.statusCode());

    HttpResponse<String> response4 =
        http.client().send(
            HttpRequest.newBuilder(uri)
                       .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(OK, response4.statusCode());
    assertEquals("", response4.body());
    assertEquals(
        Set.of("POST", "OPTIONS"),
        parseAllowHeader(response4));
  }

  /**
   * Verifies that a {@link MethodHandler} with GET and POST handlers added
   * allows GET, POST, HEAD, and OPTIONS requests.
   */
  @Test
  public void testGetAndPost(HttpTester http)
      throws IOException, InterruptedException {

    HttpHandler handler =
        new MethodHandler()
            .addMethod(GET, new FixedResponseBodyHandler("getHandler"))
            .addMethod(POST, new FixedResponseBodyHandler("postHandler"));

    String path = http.addHandler(handler);

    URI uri = http.uri(path);

    HttpResponse<String> response1 =
        http.client().send(
            HttpRequest.newBuilder(uri).build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(OK, response1.statusCode());
    assertEquals("getHandler", response1.body());

    HttpResponse<String> response2 =
        http.client().send(
            HttpRequest.newBuilder(uri)
                       .header(CONTENT_TYPE, "text/plain")
                       .POST(HttpRequest.BodyPublishers.ofString("hi"))
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(OK, response2.statusCode());
    assertEquals("postHandler", response2.body());

    HttpResponse<String> response3 =
        http.client().send(
            HttpRequest.newBuilder(uri)
                       .method("HEAD", HttpRequest.BodyPublishers.noBody())
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(OK, response3.statusCode());
    assertEquals("", response3.body());

    HttpResponse<String> response4 =
        http.client().send(
            HttpRequest.newBuilder(uri)
                       .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(OK, response4.statusCode());
    assertEquals("", response4.body());
    assertEquals(
        Set.of("GET", "POST", "HEAD", "OPTIONS"),
        parseAllowHeader(response4));
  }

  /**
   * Verifies that it is possible to override the built-in OPTIONS handler of a
   * {@link MethodHandler}.
   */
  @Test
  public void testOverrideOptions(HttpTester http)
      throws IOException, InterruptedException {

    HttpHandler handler =
        new MethodHandler()
            .addMethod(OPTIONS, new FixedResponseBodyHandler("optionsHandler"));

    String path = http.addHandler(handler);

    URI uri = http.uri(path);

    HttpResponse<String> response =
        http.client().send(
            HttpRequest.newBuilder(uri)
                       .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(OK, response.statusCode());
    assertEquals("optionsHandler", response.body());
    assertEquals(
        Optional.empty(),
        response.headers().firstValue(ALLOW));
  }

  private static Set<String> parseAllowHeader(HttpResponse<?> response) {
    String headerValue = response.headers()
                                 .firstValue(ALLOW)
                                 .orElse(null);

    if (headerValue == null)
      return Set.of();

    List<String> list = Splitter.on(',')
                                .trimResults()
                                .splitToList(headerValue);

    Set<String> set = Set.copyOf(list);

    if (list.size() != set.size())
      throw new AssertionError(
          "Allow header contained duplicate values: " + headerValue);

    return set;
  }
}
