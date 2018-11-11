package tfb.status.undertow.extensions;

import static io.undertow.util.StatusCodes.OK;
import static java.nio.charset.StandardCharsets.UTF_16;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tfb.status.testlib.TestServices;

/**
 * Tests for {@link FixedResponseBodyHandler}.
 */
public final class FixedResponseBodyHandlerTest {
  private static TestServices services;

  @BeforeAll
  public static void beforeAll() {
    services = new TestServices();
  }

  @AfterAll
  public static void afterAll() {
    services.shutdown();
  }

  /**
   * Verifies that a {@link FixedResponseBodyHandler} can send a byte array.
   */
  @Test
  public void testBytes() throws IOException, InterruptedException {
    byte[] expectedBytes = "hello".getBytes(UTF_8);

    services.addExactPath(
        "/bytes",
        new FixedResponseBodyHandler(expectedBytes));

    HttpResponse<byte[]> response = services.httpGetBytes("/bytes");

    assertEquals(OK, response.statusCode());

    assertArrayEquals(
        expectedBytes,
        response.body());
  }

  /**
   * Verifies that a {@link FixedResponseBodyHandler} can send a UTF-8 string.
   */
  @Test
  public void testUtf8() throws IOException, InterruptedException {
    String expectedString = "hi";

    services.addExactPath(
        "/utf8",
        new FixedResponseBodyHandler(expectedString));

    HttpResponse<byte[]> response = services.httpGetBytes("/utf8");

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
  public void testNotUtf8() throws IOException, InterruptedException {
    String expectedString = "hey";

    services.addExactPath(
        "/utf16",
        new FixedResponseBodyHandler(expectedString, UTF_16));

    HttpResponse<byte[]> response = services.httpGetBytes("/utf16");

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
  public void testMultipleRequests() throws IOException, InterruptedException {
    String expectedString = "greetings";

    services.addExactPath(
        "/multiple",
        new FixedResponseBodyHandler(expectedString));

    HttpResponse<String> response1 = services.httpGetString("/multiple");
    HttpResponse<String> response2 = services.httpGetString("/multiple");

    assertEquals(OK, response1.statusCode());
    assertEquals(OK, response2.statusCode());

    assertEquals(expectedString, response1.body());
    assertEquals(expectedString, response2.body());
  }
}
