package tfb.status.handler;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.HTML_UTF_8;
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
 * Tests for {@link AboutPageHandler}.
 */
@ExtendWith(TestServicesInjector.class)
public final class AboutPageHandlerTest {
  /**
   * Verifies that {@code GET /about} produces an HTML response.
   */
  @Test
  public void testGet(TestServices services)
      throws IOException, InterruptedException {

    HttpResponse<String> response =
        services.httpGetString("/about");

    assertEquals(OK, response.statusCode());

    assertMediaType(
        HTML_UTF_8,
        response.headers()
                .firstValue(CONTENT_TYPE)
                .orElse(null));

    assertHtmlDocument(response.body());
  }
}
