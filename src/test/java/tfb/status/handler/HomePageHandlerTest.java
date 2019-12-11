package tfb.status.handler;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.HTML_UTF_8;
import static io.undertow.util.StatusCodes.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static tfb.status.testlib.MoreAssertions.assertContains;
import static tfb.status.testlib.MoreAssertions.assertHtmlDocument;
import static tfb.status.testlib.MoreAssertions.assertMediaType;

import java.io.IOException;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.testlib.HttpTester;
import tfb.status.testlib.TestServicesInjector;

/**
 * Tests for {@link HomePageHandler}.
 */
@ExtendWith(TestServicesInjector.class)
public final class HomePageHandlerTest {
  /**
   * Verifies that {@code GET /} produces an HTML response.
   */
  @Test
  public void testGet(HttpTester http)
      throws IOException, InterruptedException {

    HttpResponse<String> response =
        http.getString("/");

    assertEquals(OK, response.statusCode());

    assertMediaType(
        HTML_UTF_8,
        response.headers()
                .firstValue(CONTENT_TYPE)
                .orElse(null));

    String body = response.body();

    assertHtmlDocument(body);

    assertContains(
        "This is the test announcement!",
        body);
  }
}
