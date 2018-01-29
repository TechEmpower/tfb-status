package tfb.status.handler;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static io.undertow.util.StatusCodes.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static tfb.status.util.MoreAssertions.assertMediaType;

import javax.ws.rs.core.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tfb.status.bootstrap.TestServices;

/**
 * Tests for {@link UnzipResultsHandler}.
 */
public final class UnzipResultsHandlerTest {
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
   * Tests that a GET request for a file within a results.zip file that exists
   * is successful.
   */
  @Test
  public void testGet() {
    try (Response response =
             services.httpClient()
                     .target(services.localUri("/unzip/results.2017-12-29-23-04-02-541.zip/gemini/out.txt"))
                     .request()
                     .get()) {

      assertEquals(OK, response.getStatus());

      assertMediaType(
          PLAIN_TEXT_UTF_8,
          response.getHeaderString(CONTENT_TYPE));

      byte[] responseBytes = response.readEntity(byte[].class);

      assertEquals(33399L, responseBytes.length);
    }
  }
}
