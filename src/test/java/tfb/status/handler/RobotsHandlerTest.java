package tfb.status.handler;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static io.undertow.util.StatusCodes.OK;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static tfb.status.util.MoreAssertions.assertMediaType;

import crawlercommons.robots.BaseRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import java.util.stream.Stream;
import javax.ws.rs.core.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tfb.status.bootstrap.TestServices;

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
  public void testDisallowAllRobots() {
    try (Response response = services.httpGet("/robots.txt")) {

      assertEquals(OK, response.getStatus());

      assertMediaType(
          PLAIN_TEXT_UTF_8,
          response.getHeaderString(CONTENT_TYPE));

      byte[] responseBytes = response.readEntity(byte[].class);

      BaseRobotRules robotRules =
          new SimpleRobotRulesParser().parseContent(
              /* url= */ services.httpUri("/robots.txt"),
              /* content = */ responseBytes,
              /* contentType= */ response.getHeaderString(CONTENT_TYPE),
              /* robotNames=*/ "Googlebot");

      assertAll(
          Stream.of("/",
                    "/about",
                    "/raw/results.2017-12-26-05-07-14-321.json",
                    "/unzip/results.2017-12-29-23-04-02-541.zip/gemini/out.txt")
                .map(path -> services.httpUri(path))
                .map(url -> () -> assertDisallowed(robotRules, url)));
    }
  }

  private static void assertDisallowed(BaseRobotRules robotRules, String url) {
    assertFalse(
        robotRules.isAllowed(url),
        "should be disallowed: " + url);
  }
}
