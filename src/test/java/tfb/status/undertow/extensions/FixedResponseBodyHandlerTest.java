package tfb.status.undertow.extensions;

import static io.undertow.util.StatusCodes.OK;
import static java.nio.charset.StandardCharsets.UTF_16;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.handler.RootHandler;
import tfb.status.testlib.TestServices;
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
  public void testBytes(TestServices services,
                        RootHandler rootHandler)
      throws IOException, InterruptedException {

    byte[] expectedBytes = "hello".getBytes(UTF_8);

    rootHandler.addExactPath(
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
  public void testUtf8(TestServices services,
                       RootHandler rootHandler)
      throws IOException, InterruptedException {

    String expectedString = "hi";

    rootHandler.addExactPath(
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
  public void testNotUtf8(TestServices services,
                          RootHandler rootHandler)
      throws IOException, InterruptedException {

    String expectedString = "hey";

    rootHandler.addExactPath(
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
  public void testMultipleRequests(TestServices services,
                                   RootHandler rootHandler)
      throws IOException, InterruptedException {

    String expectedString = "greetings";

    rootHandler.addExactPath(
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
