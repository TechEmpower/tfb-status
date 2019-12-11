package tfb.status.undertow.extensions;

import static io.undertow.util.StatusCodes.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.undertow.server.HttpHandler;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.testlib.HttpTester;
import tfb.status.testlib.TestServicesInjector;

/**
 * Tests for {@link LazyHandler}.
 */
@ExtendWith(TestServicesInjector.class)
public final class LazyHandlerTest {

  /**
   * Verifies that {@link LazyHandler} delays initialization of the
   * caller-supplied handler until a request is received.
   */
  @Test
  public void testLazyInitialization(HttpTester http)
      throws IOException, InterruptedException {

    AtomicBoolean initialized1 = new AtomicBoolean(false);
    AtomicBoolean initialized2 = new AtomicBoolean(false);

    HttpHandler handler1 =
        new LazyHandler(() -> {
          initialized1.set(true);
          return new FixedResponseBodyHandler("lazy1");
        });

    HttpHandler handler2 =
        new LazyHandler(() -> {
          initialized2.set(true);
          return new FixedResponseBodyHandler("lazy2");
        });

    String path1 = http.addHandler(handler1);
    String path2 = http.addHandler(handler2);

    assertFalse(initialized1.get());
    assertFalse(initialized2.get());

    HttpResponse<String> response1 = http.getString(path1);

    assertTrue(initialized1.get());
    assertFalse(initialized2.get());

    assertEquals(OK, response1.statusCode());
    assertEquals("lazy1", response1.body());

    HttpResponse<String> response2 = http.getString(path2);

    assertTrue(initialized1.get());
    assertTrue(initialized2.get());

    assertEquals(OK, response2.statusCode());
    assertEquals("lazy2", response2.body());
  }
}
