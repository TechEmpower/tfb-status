package tfb.status.handler;

import static io.undertow.util.StatusCodes.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.testlib.TestServices;
import tfb.status.testlib.TestServicesInjector;
import tfb.status.view.Results;

/**
 * Tests for {@link DownloadResultsHandler}.
 */
@ExtendWith(TestServicesInjector.class)
public final class DownloadResultsHandlerTest {
  /**
   * Verifies that a GET request for a results.json file that exists is
   * successful.
   */
  @Test
  public void testGetJson(TestServices services, ObjectMapper objectMapper)
      throws IOException, InterruptedException {

    HttpResponse<byte[]> response =
        services.httpGetBytes("/raw/results.2017-12-26-05-07-14-321.json");

    assertEquals(OK, response.statusCode());

    byte[] responseBytes = response.body();

    Results results = objectMapper.readValue(responseBytes, Results.class);

    assertEquals("03da6340-d56c-4584-9ef2-702106203809", results.uuid);
  }

  /**
   * Verifies that a GET request for a results.zip file that exists is
   * successful.
   */
  @Test
  public void testGetZip(TestServices services, ObjectMapper objectMapper)
      throws IOException, InterruptedException {

    HttpResponse<byte[]> response =
        services.httpGetBytes("/raw/results.2017-12-29-23-04-02-541.zip");

    assertEquals(OK, response.statusCode());

    byte[] responseBytes = response.body();

    Results results;

    try (var bais = new ByteArrayInputStream(responseBytes);
         var zip = new ZipInputStream(bais)) {

      ZipEntry entry;
      do entry = zip.getNextEntry();
      while (entry != null && !entry.getName().endsWith("/results.json"));

      assertNotNull(entry, "results.json entry not found");

      results = objectMapper.readValue(zip, Results.class);
    }

    assertEquals("03da6340-d56c-4584-9ef2-702106203809", results.uuid);
  }
}
