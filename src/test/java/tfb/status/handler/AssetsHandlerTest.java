package tfb.status.handler;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.JAVASCRIPT_UTF_8;
import static io.undertow.util.StatusCodes.FORBIDDEN;
import static io.undertow.util.StatusCodes.NOT_FOUND;
import static io.undertow.util.StatusCodes.OK;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static tfb.status.testlib.MoreAssertions.assertMediaType;

import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.testlib.HttpTester;
import tfb.status.testlib.TestServicesInjector;

/**
 * Tests for {@link AssetsHandler}.
 */
@ExtendWith(TestServicesInjector.class)
public final class AssetsHandlerTest {
  /**
   * Verifies that a GET request for an asset file that exists is successful.
   */
  @Test
  public void testGet(HttpTester http)
      throws IOException, InterruptedException {

    HttpResponse<String> response =
        http.getString("/assets/js/home.js");

    assertEquals(OK, response.statusCode());

    assertMediaType(
        JAVASCRIPT_UTF_8,
        response.headers()
                .firstValue(CONTENT_TYPE)
                .orElse(null));

    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    URL url = classLoader.getResource("assets/js/home.js");
    assertNotNull(url);
    String expected = Resources.asCharSource(url, UTF_8).read();
    assertEquals(expected, response.body());
  }

  /**
   * Verifies that a GET request for an assets file that does not exists results
   * in {@code 404 Not Found}.
   */
  @Test
  public void testNotFound(HttpTester http)
      throws IOException, InterruptedException {

    HttpResponse<String> response =
        http.getString("/assets/does_not_exist.txt");

    assertEquals(NOT_FOUND, response.statusCode());
    assertEquals("", response.body());
  }

  /**
   * Verifies that a GET request for an assets directory results in either
   * {@code 404 Not Found} or {@code 403 Forbidden}.
   */
  @Test
  public void testDirectoryNotFound(HttpTester http)
      throws IOException, InterruptedException {

    HttpResponse<String> response1 = http.getString("/assets");
    assertEquals(NOT_FOUND, response1.statusCode());
    assertEquals("", response1.body());

    // We'd prefer 404 Not Found for all of these, but 403 Forbidden is the
    // behavior of Undertow's ResourceHandler.

    HttpResponse<String> response2 = http.getString("/assets/");
    assertEquals(FORBIDDEN, response2.statusCode());
    assertEquals("", response2.body());

    HttpResponse<String> response3 = http.getString("/assets/js");
    assertEquals(FORBIDDEN, response3.statusCode());
    assertEquals("", response3.body());

    HttpResponse<String> response4 = http.getString("/assets/js/");
    assertEquals(FORBIDDEN, response4.statusCode());
    assertEquals("", response4.body());
  }
}
