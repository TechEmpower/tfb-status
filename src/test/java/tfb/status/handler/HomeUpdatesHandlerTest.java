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
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.StringJoiner;
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
    URI uri = services.httpUri("/updates");

    HttpResponse<InputStream> response =
        services.httpClient().send(
            HttpRequest.newBuilder(uri)
                       .GET()
                       .build(),
            HttpResponse.BodyHandlers.ofInputStream());

    try (var is = response.body();
         var isr = new InputStreamReader(is, UTF_8);
         var br = new BufferedReader(isr)) {

      assertEquals(OK, response.statusCode());

      assertMediaType(
          MediaType.create("text", "event-stream"),
          response.headers()
                  .firstValue(CONTENT_TYPE)
                  .orElse(null));

      //
      // Undertow adds our incoming SSE connection to its internal collection,
      // but it does so *after* it responds to our HTTP request.  We need to
      // wait for that collection to be updated before we proceed.
      //
      Thread.sleep(100);

      updates.sendUpdate("03da6340-d56c-4584-9ef2-702106203809");

      var message = new StringJoiner("\n");

      for (String line = br.readLine();
           line != null && line.startsWith("data:");
           line = br.readLine()) {

        message.add(line.substring("data:".length()));
      }

      assertContains(
          "03da6340-d56c-4584-9ef2-702106203809",
          message.toString());
    }
  }
}
