package tfb.status.handler;

import static io.undertow.util.StatusCodes.NOT_FOUND;
import static io.undertow.util.StatusCodes.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.testlib.HttpTester;
import tfb.status.testlib.TestServicesInjector;
import tfb.status.view.Results;

/**
 * Tests for {@link ShareDownloadHandler}.
 */
@ExtendWith(TestServicesInjector.class)
public final class ShareDownloadHandlerTest {
  /**
   * Verifies that {@code GET /share/download/$share_id.json} produces the
   * results.json file for a share id that exists.
   */
  @Test
  public void testGet(HttpTester http, ObjectMapper objectMapper)
      throws IOException, InterruptedException {

    HttpResponse<byte[]> response =
        http.getBytes(
            "/share/download/a7044ac3-f729-4a41-952a-6302af8a65ae.json");

    assertEquals(OK, response.statusCode());

    byte[] responseBytes = response.body();

    Results results = objectMapper.readValue(responseBytes, Results.class);

    assertEquals("598923fe-6491-41bd-a2b6-047f70860aed", results.uuid);
  }

  /**
   * Verifies that {@code GET /share/download/$share_id.json} produces a
   * {@code 404 Not Found} response for share id that doesn't exist.
   */
  @Test
  public void testGet_shareIdNotFound(HttpTester http)
      throws IOException, InterruptedException {

    HttpResponse<String> response =
        http.getString("/share/download/not_a_share_id.json");

    assertEquals(NOT_FOUND, response.statusCode());

    assertEquals("", response.body());
  }
}
