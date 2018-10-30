package tfb.status.handler;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.HTML_UTF_8;
import static io.undertow.util.StatusCodes.NOT_FOUND;
import static io.undertow.util.StatusCodes.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static tfb.status.testlib.MoreAssertions.assertHtmlDocument;
import static tfb.status.testlib.MoreAssertions.assertMediaType;

import java.io.IOException;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tfb.status.testlib.TestServices;

/**
 * Tests for {@link DetailPageHandler}.
 */
public final class DetailPageHandlerTest {
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
   * Verifies that a GET request for the results detail page with a valid uuid
   * produces an HTML response.
   */
  @Test
  public void testGet() throws IOException, InterruptedException {
    HttpResponse<String> response =
        services.httpGetString("/results/03da6340-d56c-4584-9ef2-702106203809");

    assertEquals(OK, response.statusCode());

    assertMediaType(
        HTML_UTF_8,
        response.headers()
                .firstValue(CONTENT_TYPE)
                .orElse(null));

    assertHtmlDocument(response.body());
  }

  /**
   * Verifies that a GET request for the results detail page with an unknown
   * uuid produces a {@code 404 Not Found} response.
   */
  public void testUnknownUuid() throws IOException, InterruptedException {
    HttpResponse<String> response =
        services.httpGetString("/results/notarealuuid");

    assertEquals(NOT_FOUND, response.statusCode());
  }
}
