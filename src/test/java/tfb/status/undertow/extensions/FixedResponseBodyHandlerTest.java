package tfb.status.undertow.extensions;

import static io.undertow.util.StatusCodes.OK;
import static java.nio.charset.StandardCharsets.UTF_16;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.undertow.server.HttpHandler;
import java.io.IOException;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.testlib.HttpTester;
import tfb.status.testlib.TestServicesInjector;

/**
 * Tests for {@link FixedResponseBodyHandler}.
 */
@ExtendWith(TestServicesInjector.class)
public final class FixedResponseBodyHandlerTest {

  /**
   * Verifies that a {@link FixedResponseBodyHandler} can send a byte array.
   */
  @Test
  public void testBytes(HttpTester http)
      throws IOException, InterruptedException {

    byte[] expectedBytes = "hello".getBytes(UTF_8);

    HttpHandler handler = new FixedResponseBodyHandler(expectedBytes);

    String path = http.addHandler(handler);

    HttpResponse<byte[]> response = http.getBytes(path);

    assertEquals(OK, response.statusCode());

    assertArrayEquals(
        expectedBytes,
        response.body());
  }

  /**
   * Verifies that a {@link FixedResponseBodyHandler} can send a UTF-8 string.
   */
  @Test
  public void testUtf8(HttpTester http)
      throws IOException, InterruptedException {

    String expectedString = "hi";

    HttpHandler handler = new FixedResponseBodyHandler(expectedString);

    String path = http.addHandler(handler);

    HttpResponse<byte[]> response = http.getBytes(path);

    assertEquals(OK, response.statusCode());

    assertArrayEquals(
        expectedString.getBytes(UTF_8),
        response.body());
  }

  /**
   * Verifies that a {@link FixedResponseBodyHandler} can send a string in a
   * charset other than UTF-8.
   */
  @Test
  public void testNotUtf8(HttpTester http)
      throws IOException, InterruptedException {

    String expectedString = "hey";

    HttpHandler handler = new FixedResponseBodyHandler(expectedString, UTF_16);

    String path = http.addHandler(handler);

    HttpResponse<byte[]> response = http.getBytes(path);

    assertEquals(OK, response.statusCode());

    assertArrayEquals(
        expectedString.getBytes(UTF_16),
        response.body());
  }

  /**
   * Verifies that multiple requests to the same {@link
   * FixedResponseBodyHandler} each have the same response.
   */
  @Test
  public void testMultipleRequests(HttpTester http)
      throws IOException, InterruptedException {

    String expectedString = "greetings";

    HttpHandler handler = new FixedResponseBodyHandler(expectedString);

    String path = http.addHandler(handler);

    HttpResponse<String> response1 = http.getString(path);
    HttpResponse<String> response2 = http.getString(path);

    assertEquals(OK, response1.statusCode());
    assertEquals(OK, response2.statusCode());

    assertEquals(expectedString, response1.body());
    assertEquals(expectedString, response2.body());
  }
}
