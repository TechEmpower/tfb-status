package tfb.status.handler;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.JAVASCRIPT_UTF_8;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tfb.status.testlib.TestServices;

/**
 * Tests for {@link AssetsHandler}.
 */
public final class AssetsHandlerTest {
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
   * Verifies that a GET request for an asset file that exists is successful.
   */
  @Test
  public void testGet() throws IOException, InterruptedException {
    HttpResponse<String> response =
        services.httpGetString("/assets/js/home.js");

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
  public void testNotFound() throws IOException, InterruptedException {
    HttpResponse<String> response =
        services.httpGetString("/assets/does_not_exist.txt");

    assertEquals(NOT_FOUND, response.statusCode());
  }
}
