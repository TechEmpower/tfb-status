package tfb.status.handler;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static tfb.status.util.MoreAssertions.assertContains;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.ws.rs.client.WebTarget;
import org.glassfish.jersey.media.sse.EventSource;
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
   * Tests that {@code GET /updates} produces an event stream that broadcasts
   * updates sent via {@link HomeUpdatesHandler#sendUpdate(String)}.
   */
  @Test
  public void testGet() throws IOException {
    WebTarget target = services.httpClient()
                               .target(services.localUri("/updates"));

    var eventSource = new EventSource(target, /* open= */ false);

    var receivedEvent = new AtomicBoolean(false);

    eventSource.register(
        event -> {

          assertContains(
              "03da6340-d56c-4584-9ef2-702106203809",
              event.readData());

          receivedEvent.set(true);

        });

    eventSource.open();
    try {
      updates.sendUpdate("03da6340-d56c-4584-9ef2-702106203809");
    } finally {
      eventSource.close();
    }

    assertTrue(
        receivedEvent.get(),
        "The SSE client should have received an event from the server");
  }
}
