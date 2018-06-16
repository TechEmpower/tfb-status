package tfb.status.handler;

import static tfb.status.util.MoreAssertions.assertContains;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
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
   * Verifies that {@code GET /updates} produces an event stream that broadcasts
   * updates sent via {@link HomeUpdatesHandler#sendUpdate(String)}.
   */
  @Test
  public void testGet() throws IOException {
    String uri = services.httpUri("/updates");

    var eventSource =
        new EventSource(
            /* endpoint= */ services.httpClient().target(uri),
            /* open= */ false);

    var incomingData = new AtomicReference<String>();

    eventSource.register(event -> incomingData.set(event.readData()));

    eventSource.open();
    try {
      updates.sendUpdate("03da6340-d56c-4584-9ef2-702106203809");
    } finally {
      eventSource.close();
    }

    assertContains(
        "03da6340-d56c-4584-9ef2-702106203809",
        incomingData.get());
  }
}
