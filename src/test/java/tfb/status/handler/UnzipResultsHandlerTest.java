package tfb.status.handler;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static io.undertow.util.StatusCodes.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static tfb.status.testlib.MoreAssertions.assertMediaType;

import java.io.IOException;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.testlib.HttpTester;
import tfb.status.testlib.TestServicesInjector;

/**
 * Tests for {@link UnzipResultsHandler}.
 */
@ExtendWith(TestServicesInjector.class)
public final class UnzipResultsHandlerTest {
  /**
   * Verifies that a GET request for a file within a results.zip file that
   * exists is successful.
   */
  @Test
  public void testGet(HttpTester http)
      throws IOException, InterruptedException {

    HttpResponse<byte[]> response =
        http.getBytes("/unzip/results.2017-12-29-23-04-02-541.zip/gemini/out.txt");

    assertEquals(OK, response.statusCode());

    assertMediaType(
        PLAIN_TEXT_UTF_8,
        response.headers()
                .firstValue(CONTENT_TYPE)
                .orElse(null));

    byte[] responseBytes = response.body();

    assertEquals(33399L, responseBytes.length);
  }
}
