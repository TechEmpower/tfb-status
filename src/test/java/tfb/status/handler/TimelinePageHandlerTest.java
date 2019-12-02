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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.testlib.TestServices;
import tfb.status.testlib.TestServicesInjector;

/**
 * Tests for {@link TimelinePageHandler}.
 */
@ExtendWith(TestServicesInjector.class)
public final class TimelinePageHandlerTest {
  /**
   * Verifies that a GET request for the timeline with valid parameters produces
   * an HTML response.
   */
  @Test
  public void testGet(TestServices services)
      throws IOException, InterruptedException {

    HttpResponse<String> response =
        services.httpGetString("/timeline/gemini/json");

    assertEquals(OK, response.statusCode());

    assertMediaType(
        HTML_UTF_8,
        response.headers()
                .firstValue(CONTENT_TYPE)
                .orElse(null));

    assertHtmlDocument(response.body());
  }

  /**
   * Verifies that a GET request for the timeline with an unknown test type
   * specified produces a {@code 404 Not Found} response.
   */
  @Test
  public void testUnknownTestType(TestServices services)
      throws IOException, InterruptedException {

    HttpResponse<String> response =
        services.httpGetString("/timeline/gemini/notarealtesttypename");

    assertEquals(NOT_FOUND, response.statusCode());
  }

  /**
   * Verifies that a GET request for the timeline with an unknown framework
   * specified produces a {@code 404 Not Found} response.
   */
  @Test
  public void testUnknownFramework(TestServices services)
      throws IOException, InterruptedException {

    HttpResponse<String> response =
        services.httpGetString("/timeline/notarealframeworkname/json");

    assertEquals(NOT_FOUND, response.statusCode());
  }
}
