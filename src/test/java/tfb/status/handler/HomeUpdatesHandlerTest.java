package tfb.status.handler;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static io.undertow.util.StatusCodes.OK;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static tfb.status.util.MoreAssertions.assertContains;
import static tfb.status.util.MoreAssertions.assertMediaType;

import com.google.common.net.MediaType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.StringJoiner;
import javax.ws.rs.core.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tfb.status.bootstrap.TestServices;

/**
 * Tests for {@link HomeUpdatesHandler}.
 */
public final class HomeUpdatesHandlerTest {
  private static TestServices services;
  private static HomeUpdatesHandler updates;

  @BeforeAll
  public static void beforeAll() {
    services = new TestServices();
    updates = services.serviceLocator().getService(HomeUpdatesHandler.class);
  }

  @AfterAll
  public static void afterAll() {
    services.shutdown();
  }

  /**
   * Verifies that {@code GET /updates} produces an event stream that broadcasts
   * updates sent via {@link HomeUpdatesHandler#sendUpdate(String)}.
   */
  @Test
  public void testGet() throws IOException, InterruptedException {
    try (Response response = services.httpGet("/updates")) {

      assertEquals(OK, response.getStatus());

      assertMediaType(
          MediaType.create("text", "event-stream"),
          response.getHeaderString(CONTENT_TYPE));

      //
      // Undertow adds our incoming SSE connection to its internal collection,
      // but it does so *after* it responds to our HTTP request.  We need to
      // wait for that collection to be updated before we proceed.
      //
      Thread.sleep(100);

      updates.sendUpdate("03da6340-d56c-4584-9ef2-702106203809");

      assertContains(
          "03da6340-d56c-4584-9ef2-702106203809",
          readSseMessage(response));
    }
  }

  /**
   * Reads one SSE message from the provided HTTP response.
   */
  private static String readSseMessage(Response response) throws IOException {
    var joiner = new StringJoiner("\n");

    try (var is = response.readEntity(InputStream.class);
         var isr = new InputStreamReader(is, UTF_8);
         var br = new BufferedReader(isr)) {

      for (String line = br.readLine();
           line != null && line.startsWith("data:");
           line = br.readLine()) {

        joiner.add(line.substring("data:".length()));
      }
    }

    return joiner.toString();
  }
}
