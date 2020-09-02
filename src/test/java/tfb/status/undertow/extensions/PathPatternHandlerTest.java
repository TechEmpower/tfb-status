package tfb.status.undertow.extensions;

import static io.undertow.util.StatusCodes.NOT_FOUND;
import static io.undertow.util.StatusCodes.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static tfb.status.undertow.extensions.RequestValues.pathParameter;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.testlib.HttpTester;
import tfb.status.testlib.TestServicesInjector;

/**
 * Tests for {@link PathPatternHandler}.
 */
@ExtendWith(TestServicesInjector.class)
public class PathPatternHandlerTest {
  /**
   * Verifies that a {@link PathPatternHandler} with no handlers added rejects
   * all requests.
   */
  @Test
  public void testNoPaths(HttpTester http)
      throws IOException, InterruptedException {

    PathPatternHandler handler = PathPatternHandler.builder().build();

    String prefix = http.addHandler(handler);

    List<String> suffixes =
        List.of(
            "",
            "/",
            "/a",
            "/b",
            "/a.b",
            "/a/b",
            "/a/b/");

    for (String suffix : suffixes) {
      String path = prefix + suffix;

      HttpResponse<String> response = http.getString(path);

      assertEquals(
          NOT_FOUND,
          response.statusCode(),
          "Unexpected status code for path " + path);

      assertEquals(
          "",
          response.body(),
          "Unexpected response body for path " + path);
    }
  }

  /**
   * Verifies that a {@link PathPatternHandler} with a handler matching all
   * paths handles all requests.
   */
  @Test
  public void testAnyPath(HttpTester http)
      throws IOException, InterruptedException {

    PathPatternHandler handler =
        PathPatternHandler
            .builder()
            .add(
                "{path:.*}",
                new FixedResponseBodyHandler("anyPathHandler"))
            .build();

    String prefix = http.addHandler(handler);

    List<String> suffixes =
        List.of(
            "",
            "/",
            "/a",
            "/b",
            "/a.b",
            "/a/b",
            "/a/b/");

    for (String suffix : suffixes) {
      String path = prefix + suffix;

      HttpResponse<String> response = http.getString(path);

      assertEquals(
          OK,
          response.statusCode(),
          "Unexpected status code for path " + path);

      assertEquals(
          "anyPathHandler",
          response.body(),
          "Unexpected response body for path " + path);
    }
  }

  /**
   * Verifies that a {@link PathPatternHandler} routes requests to the most
   * specific matching handler when the path patterns for each handler are
   * overlapping.
   */
  @Test
  public void testMostSpecificPath(HttpTester http)
      throws IOException, InterruptedException {

    PathPatternHandler handler =
        PathPatternHandler
            .builder()
            // Intentional ordering of .add(...) calls.
            .add(
                "/{a}/",
                new FixedResponseBodyHandler("two_literals_one_variable"))
            // Add a more specific path.
            .add(
                "/a/",
                new FixedResponseBodyHandler("three_literals"))
            // Add a less specific path.
            .add(
                "/{a}/{b}",
                new FixedResponseBodyHandler("two_literals_two_variables"))
            .build();

    String prefix = http.addHandler(handler);

    HttpResponse<String> response1 = http.getString(prefix + "/a/");
    assertEquals(OK, response1.statusCode());
    assertEquals("three_literals", response1.body());

    HttpResponse<String> response2 = http.getString(prefix + "/x/");
    assertEquals(OK, response2.statusCode());
    assertEquals("two_literals_one_variable", response2.body());

    HttpResponse<String> response3 = http.getString(prefix + "/x/y");
    assertEquals(OK, response3.statusCode());
    assertEquals("two_literals_two_variables", response3.body());
  }

  /**
   * Verifies that {@link PathPatternHandler.Builder#add(String,
   * HttpHandler)} throws an exception for an already-added path.
   */
  @Test
  public void testDuplicatePathRejected() {
    PathPatternHandler.Builder builder =
        PathPatternHandler
            .builder()
            .add("/a/{b}", exchange -> {})
            .add("/z/{y:[a-z]}", exchange -> {});

    assertThrows(
        IllegalStateException.class,
        () -> builder.add("/a/{b}", exchange -> {}));

    assertThrows(
        IllegalStateException.class,
        () -> builder.add("/a/{c}", exchange -> {}));

    assertThrows(
        IllegalStateException.class,
        () -> builder.add("/z/{x:[a-z]}", exchange -> {}));
  }

  /**
   * Verifies that a {@link PathPatternHandler} applies variable value patterns
   * correctly when routing, and that the values of the path variables can be
   * retrieved in the HTTP handlers using {@link
   * RequestValues#pathParameter(HttpServerExchange, String)}.
   */
  @Test
  public void testPathVariables(HttpTester http)
      throws IOException, InterruptedException {

    PathPatternHandler handler =
        PathPatternHandler
            .builder()
            .add(
                "/{x}/{y:[a-z]+}/{z:.*}",
                exchange -> {
                  String x = pathParameter(exchange, "x").orElseThrow();
                  String y = pathParameter(exchange, "y").orElseThrow();
                  String z = pathParameter(exchange, "z").orElseThrow();
                  exchange.getResponseSender().send(String.join("\n", x, y, z));
                })
            .add(
                "/{x}/{y:[A-Z]+}/{z:.*}",
                new FixedResponseBodyHandler("capitalLetters"))
            .build();

    String prefix = http.addHandler(handler);

    HttpResponse<String> response1 = http.getString(prefix + "/foo/bar/baz");
    assertEquals(OK, response1.statusCode());
    assertEquals(
        String.join("\n", "foo", "bar", "baz"),
        response1.body());

    HttpResponse<String> response2 = http.getString(prefix + "/foo/bar/baz/qux");
    assertEquals(OK, response2.statusCode());
    assertEquals(
        String.join("\n", "foo", "bar", "baz/qux"),
        response2.body());

    HttpResponse<String> response3 = http.getString(prefix + "/foo/bar/");
    assertEquals(OK, response3.statusCode());
    assertEquals(
        String.join("\n", "foo", "bar", ""),
        response3.body());

    HttpResponse<String> response4 = http.getString(prefix + "//bar/baz");
    assertEquals(NOT_FOUND, response4.statusCode());
    assertEquals("", response4.body());

    HttpResponse<String> response5 = http.getString(prefix + "/foo//baz");
    assertEquals(NOT_FOUND, response5.statusCode());
    assertEquals("", response5.body());

    HttpResponse<String> response6 = http.getString(prefix + "/foo/123/baz");
    assertEquals(NOT_FOUND, response6.statusCode());
    assertEquals("", response6.body());

    HttpResponse<String> response7 = http.getString(prefix + "/foo/BAR/baz");
    assertEquals(OK, response7.statusCode());
    assertEquals("capitalLetters", response7.body());

    HttpResponse<String> response8 = http.getString(prefix + "/foo/bar");
    assertEquals(NOT_FOUND, response8.statusCode());
    assertEquals("", response8.body());
  }
}
