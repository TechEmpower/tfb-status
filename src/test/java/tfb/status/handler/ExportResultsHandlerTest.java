package tfb.status.handler;

import static io.undertow.util.StatusCodes.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import javax.ws.rs.core.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tfb.status.bootstrap.TestServices;
import tfb.status.view.Results;

/**
 * Tests for {@link ExportResultsHandler}.
 */
public final class ExportResultsHandlerTest {
  private static TestServices services;
  private static ObjectMapper objectMapper;

  @BeforeAll
  public static void beforeAll() {
    services = new TestServices();
    objectMapper = services.serviceLocator().getService(ObjectMapper.class);
  }

  @AfterAll
  public static void afterAll() {
    services.shutdown();
  }

  /**
   * Tests that a GET request for a results.json file that exists is successful.
   */
  @Test
  public void testGetJson() throws IOException {
    try (Response response = services.httpGet("/export/results.2017-12-26-05-07-14-321.json")) {

      assertEquals(OK, response.getStatus());

      byte[] responseBytes = response.readEntity(byte[].class);

      Results.TfbWebsiteView minifiedResults =
          objectMapper.readValue(responseBytes,
                                 Results.TfbWebsiteView.class);

      assertEquals(
          "Continuous Benchmarking Run 2017-12-26 06:48:23",
          minifiedResults.name);
    }
  }

  /**
   * Tests that a GET request for a results.zip file that exists is successful.
   */
  @Test
  public void testGetZip() throws IOException {
    try (Response response = services.httpGet("/export/results.2017-12-29-23-04-02-541.zip")) {

      assertEquals(OK, response.getStatus());

      byte[] responseBytes = response.readEntity(byte[].class);

      Results.TfbWebsiteView minifiedResults =
          objectMapper.readValue(responseBytes,
                                 Results.TfbWebsiteView.class);

      assertEquals(
          "Continuous Benchmarking Run 2017-12-26 06:48:23",
          minifiedResults.name);
    }
  }
}
