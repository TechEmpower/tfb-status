package tfb.status.handler;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.HTML_UTF_8;
import static io.undertow.util.StatusCodes.NOT_FOUND;
import static io.undertow.util.StatusCodes.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static tfb.status.testlib.MoreAssertions.assertHtmlDocument;
import static tfb.status.testlib.MoreAssertions.assertMediaType;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.testlib.HttpTester;
import tfb.status.testlib.TestServicesInjector;
import tfb.status.view.DetailPageView;

/**
 * Tests for {@link DetailPageHandler}.
 */
@ExtendWith(TestServicesInjector.class)
public final class DetailPageHandlerTest {
  /**
   * Verifies that a GET request for the results detail page with a valid uuid
   * produces an HTML response.
   */
  @Test
  public void testGetHtml(HttpTester http)
      throws IOException, InterruptedException {

    HttpResponse<String> response =
        http.getString("/results/598923fe-6491-41bd-a2b6-047f70860aed");

    assertEquals(OK, response.statusCode());

    assertMediaType(
        HTML_UTF_8,
        response.headers()
                .firstValue(CONTENT_TYPE)
                .orElse(null));

    assertHtmlDocument(response.body());
  }

  /**
   * Verifies that a GET request for results detail JSON with a valid uuid
   * produces a response with the expected content.
   */
  @Test
  public void testGetJson(HttpTester http, ObjectMapper objectMapper)
      throws IOException, InterruptedException {

    HttpResponse<String> response =
        http.getString("/results/598923fe-6491-41bd-a2b6-047f70860aed.json");

    assertEquals(OK, response.statusCode());

    assertMediaType(
        "application/json",
        response.headers()
                .firstValue(CONTENT_TYPE)
                .orElse(null));

    DetailPageView view =
        objectMapper.readValue(
            response.body(),
            DetailPageView.class);

    assertEquals("598923fe-6491-41bd-a2b6-047f70860aed", view.result.uuid);
  }

  /**
   * Verifies that a GET request for the results detail page with an unknown
   * uuid produces a {@code 404 Not Found} response.
   */
  @Test
  public void testUnknownUuid(HttpTester http)
      throws IOException, InterruptedException {

    HttpResponse<String> response =
        http.getString("/results/notarealuuid");

    assertEquals(NOT_FOUND, response.statusCode());
  }
}
