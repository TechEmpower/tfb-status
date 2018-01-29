package tfb.status.handler;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static io.undertow.util.StatusCodes.OK;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static tfb.status.util.MoreAssertions.assertContains;
import static tfb.status.util.MoreAssertions.assertMediaType;

import com.google.common.net.MediaType;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.media.sse.EventInput;
import org.glassfish.jersey.media.sse.InboundEvent;
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
  public void testGet() {
    try (Response response =
             services.httpClient()
                     .target(services.localUri("/updates"))
                     .request()
                     .get()) {

      assertEquals(OK, response.getStatus());

      assertMediaType(
          MediaType.create("text", "event-stream"),
          response.getHeaderString(CONTENT_TYPE));

      ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
      try (EventInput eventInput = response.readEntity(EventInput.class)) {

        updates.sendUpdate("03da6340-d56c-4584-9ef2-702106203809");

        // The call to eventInput.read() has the potential to block the current
        // thread forever.  We schedule a timeout to ensure that won't happen.
        ScheduledFuture<?> timeout =
            scheduler.schedule(eventInput::close, 5, SECONDS);

        InboundEvent event = eventInput.read();

        // If we made it this far, we don't need the timeout anymore.
        timeout.cancel(false);

        assertNotNull(
            event,
            "The server should send an event before the timeout");

        String data = event.readData();

        assertContains(
            "03da6340-d56c-4584-9ef2-702106203809",
            event.readData());

      } finally {
        scheduler.shutdownNow();
      }
    }
  }
}
