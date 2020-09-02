package tfb.status.handler;

import static io.undertow.util.StatusCodes.NOT_FOUND;
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
import tfb.status.testlib.HttpTester;
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
  public void testGetJson(HttpTester http, ObjectMapper objectMapper)
      throws IOException, InterruptedException {

    HttpResponse<byte[]> response =
        http.getBytes("/raw/results.2019-12-11-13-21-02-404.json");

    assertEquals(OK, response.statusCode());

    byte[] responseBytes = response.body();

    Results results = objectMapper.readValue(responseBytes, Results.class);

    assertEquals("598923fe-6491-41bd-a2b6-047f70860aed", results.uuid);
  }

  /**
   * Verifies that a GET request for a results.zip file that exists is
   * successful.
   */
  @Test
  public void testGetZip(HttpTester http, ObjectMapper objectMapper)
      throws IOException, InterruptedException {

    HttpResponse<byte[]> response =
        http.getBytes("/raw/results.2019-12-16-03-22-48-407.zip");

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

    assertEquals("598923fe-6491-41bd-a2b6-047f70860aed", results.uuid);
  }

  /**
   * Verifies that a GET request for the root results directory results in
   * either {@code 404 Not Found} or {@code 403 Forbidden}.
   */
  @Test
  public void testDirectoryNotFound(HttpTester http)
      throws IOException, InterruptedException {

    HttpResponse<String> response1 = http.getString("/raw");
    assertEquals(NOT_FOUND, response1.statusCode());
    assertEquals("", response1.body());

    HttpResponse<String> response2 = http.getString("/raw/");
    assertEquals(NOT_FOUND, response2.statusCode());
    assertEquals("", response2.body());
  }
}
