package tfb.status.handler;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static io.undertow.util.StatusCodes.OK;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tfb.status.testlib.MoreAssertions.assertMediaType;

import crawlercommons.robots.BaseRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.testlib.HttpTester;
import tfb.status.testlib.TestServicesInjector;

/**
 * Tests for {@link RobotsHandler}.
 */
@ExtendWith(TestServicesInjector.class)
public final class RobotsHandlerTest {
  /**
   * Verifies that {@code GET /robots.txt} produces a response that defines the
   * expected rules for robots.
   */
  @Test
  public void testRobotRules(HttpTester http)
      throws IOException, InterruptedException {

    HttpResponse<byte[]> response =
        http.getBytes("/robots.txt");

    assertEquals(OK, response.statusCode());

    assertMediaType(
        PLAIN_TEXT_UTF_8,
        response.headers()
                .firstValue(CONTENT_TYPE)
                .orElse(null));

    byte[] responseBytes = response.body();

    BaseRobotRules robotRules =
        new SimpleRobotRulesParser().parseContent(
            /* url= */ http.uri("/robots.txt").toString(),
            /* content = */ responseBytes,
            /* contentType= */ response.headers()
                                       .firstValue(CONTENT_TYPE)
                                       .orElse(null),
            /* robotNames=*/ "Googlebot");

    Stream<String> allowedPaths =
        Stream.of("/",
                  "/assets/css/home.css",
                  "/assets/js/home.js");

    Stream<String> disallowedPaths =
        Stream.of("/about",
                  "/raw/results.2019-12-11-13-21-02-404.json",
                  "/unzip/results.2019-12-16-03-22-48-407.zip/gemini/build/gemini.log");

    assertAll(
        Stream.concat(
            allowedPaths
                .map(path -> http.uri(path).toString())
                .map(url -> () -> assertAllowed(robotRules, url)),
            disallowedPaths
                .map(path -> http.uri(path).toString())
                .map(url -> () -> assertDisallowed(robotRules, url))));
  }

  private static void assertAllowed(BaseRobotRules robotRules, String url) {
    assertTrue(
        robotRules.isAllowed(url),
        "should be allowed: " + url);
  }

  private static void assertDisallowed(BaseRobotRules robotRules, String url) {
    assertFalse(
        robotRules.isAllowed(url),
        "should be disallowed: " + url);
  }
}
