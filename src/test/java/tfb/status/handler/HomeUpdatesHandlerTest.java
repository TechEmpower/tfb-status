package tfb.status.handler;
import static tfb.status.testlib.MoreAssertions.assertContains;

import java.net.URI;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glassfish.hk2.api.messaging.Topic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import tfb.status.testlib.HttpTester;
import tfb.status.testlib.TestServicesInjector;
import tfb.status.view.UpdatedResultsEvent;

/**
 * Tests for {@link HomeUpdatesHandler}.
 */
@Execution(ExecutionMode.SAME_THREAD) // currently not parallelizable
@ExtendWith(TestServicesInjector.class)
public final class HomeUpdatesHandlerTest {
  /**
   * Verifies that a web socket client can use {@code GET /updates} to listen
   * for updates to the home page, which are broadcast via {@link
   * UpdatedResultsEvent}.
   */
  @Test
  public void testWebSocketGet(HttpTester http,
                               Topic<UpdatedResultsEvent> updatedResultsTopic)
      throws InterruptedException, ExecutionException, TimeoutException {

    String uuid = "598923fe-6491-41bd-a2b6-047f70860aed";

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
      updatedResultsTopic.publish(new UpdatedResultsEvent(uuid));
      String message = future.get(1, TimeUnit.SECONDS);
      assertContains(uuid, message);
    } finally {
      webSocket.abort();
    }
  }
}
