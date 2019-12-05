package tfb.status.handler;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static io.undertow.util.StatusCodes.OK;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static tfb.status.testlib.MoreAssertions.assertContains;
import static tfb.status.testlib.MoreAssertions.assertMediaType;

import com.google.common.net.MediaType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import tfb.status.testlib.HttpTester;
import tfb.status.testlib.TestServicesInjector;

/**
 * Tests for {@link HomeUpdatesHandler}.
 */
@Execution(ExecutionMode.SAME_THREAD) // currently not parallelizable
@ExtendWith(TestServicesInjector.class)
public final class HomeUpdatesHandlerTest {
  /**
   * Verifies that an SSE client can use {@code GET /updates} to listen for
   * updates to the home page, which are broadcast via {@link
   * HomeUpdatesHandler#sendUpdate(String)}.
   */
  @Test
  public void testSseGet(HttpTester http, HomeUpdatesHandler updates)
      throws IOException, InterruptedException {

    URI uri = http.uri("/updates");

    HttpResponse<InputStream> response =
        http.client().send(
            HttpRequest.newBuilder(uri).build(),
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

      // Undertow adds our incoming SSE connection to its internal collection,
      // but it does so *after* it responds to our HTTP request.  We need to
      // wait for that collection to be updated before we proceed.
      Thread.sleep(100);

      updates.sendUpdate("03da6340-d56c-4584-9ef2-702106203809");

      var message = new StringJoiner("\n");

      // TODO: Read home updates in a parallel-friendly way.
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

  /**
   * Verifies that a web socket client can use {@code GET /updates} to listen
   * for updates to the home page, which are broadcast via {@link
   * HomeUpdatesHandler#sendUpdate(String)}.
   */
  @Test
  public void testWebSocketGet(HttpTester http,
                               HomeUpdatesHandler updates)
      throws IOException, InterruptedException, ExecutionException, TimeoutException {

    URI uri = http.webSocketUri("/updates");

    // TODO: Read home updates in a parallel-friendly way.
    var future = new CompletableFuture<String>();

    var listener =
        new WebSocket.Listener() {
          @Override
          public @Nullable CompletionStage<?> onText(WebSocket webSocket,
                                                     CharSequence data,
                                                     boolean last) {
            future.complete(data.toString());
            webSocket.request(1);
            return null;
          }
        };

    WebSocket webSocket =
        http.client()
            .newWebSocketBuilder()
            .buildAsync(uri, listener)
            .get(1, TimeUnit.SECONDS);

    try {

      updates.sendUpdate("03da6340-d56c-4584-9ef2-702106203809");

      String message = future.get(1, TimeUnit.SECONDS);

      assertContains(
          "03da6340-d56c-4584-9ef2-702106203809",
          message);

    } finally {
      webSocket.abort();
    }
  }
}
