package tfb.status.handler;

import static io.undertow.util.StatusCodes.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.ws.rs.core.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tfb.status.bootstrap.TestServices;
import tfb.status.view.ParsedResults;

/**
 * Tests for {@link DownloadResultsHandler}.
 */
public final class DownloadResultsHandlerTest {
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
    try (Response response =
             services.httpClient()
                     .target(services.localUri("/raw/results.2017-12-26-05-07-14-321.json"))
                     .request()
                     .get()) {

      assertEquals(OK, response.getStatus());

      byte[] responseBytes = response.readEntity(byte[].class);

      ParsedResults results = objectMapper.readValue(responseBytes,
                                                     ParsedResults.class);

      assertEquals("03da6340-d56c-4584-9ef2-702106203809", results.uuid);
    }
  }

  /**
   * Tests that a GET request for a results.zip file that exists is successful.
   */
  @Test
  public void testGetZip() throws IOException {
    try (Response response =
             services.httpClient()
                     .target(services.localUri("/raw/results.2017-12-29-23-04-02-541.zip"))
                     .request()
                     .get()) {

      assertEquals(OK, response.getStatus());

      byte[] responseBytes = response.readEntity(byte[].class);

      ParsedResults results;
      try (ZipInputStream zip =
               new ZipInputStream(new ByteArrayInputStream(responseBytes))) {

        ZipEntry entry;
        do entry = zip.getNextEntry();
        while (entry != null && !entry.getName().endsWith("/results.json"));

        assertNotNull(entry, "results.json entry not found");

        results = objectMapper.readValue(zip, ParsedResults.class);
      }

      assertEquals("03da6340-d56c-4584-9ef2-702106203809", results.uuid);
    }
  }
}
