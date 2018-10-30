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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tfb.status.testlib.TestServices;

/**
 * Tests for {@link RobotsHandler}.
 */
public final class RobotsHandlerTest {
  private static TestServices services;

  @BeforeAll
  public static void beforeAll() {
    services = new TestServices();
  }

  @AfterAll
  public static void afterAll() {
    services.shutdown();
  }

  /**
   * Verifies that {@code GET /robots.txt} produces a response that disallows
   * all robots.
   */
  @Test
  public void testDisallowAllRobots() throws IOException, InterruptedException {
    HttpResponse<byte[]> response =
        services.httpGetBytes("/robots.txt");

    assertEquals(OK, response.statusCode());

    assertMediaType(
        PLAIN_TEXT_UTF_8,
        response.headers()
                .firstValue(CONTENT_TYPE)
                .orElse(null));

    byte[] responseBytes = response.body();

    BaseRobotRules robotRules =
        new SimpleRobotRulesParser().parseContent(
            /* url= */ services.httpUri("/robots.txt").toString(),
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
                  "/raw/results.2017-12-26-05-07-14-321.json",
                  "/unzip/results.2017-12-29-23-04-02-541.zip/gemini/out.txt");

    assertAll(
        Stream.concat(
            allowedPaths
                .map(path -> services.httpUri(path).toString())
                .map(url -> () -> assertAllowed(robotRules, url)),
            disallowedPaths
                .map(path -> services.httpUri(path).toString())
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
