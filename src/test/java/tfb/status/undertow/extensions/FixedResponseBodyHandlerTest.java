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
import tfb.status.bootstrap.TestServices;

/**
 * Tests for {@link FixedResponseBodyHandler}.
 */
public final class FixedResponseBodyHandlerTest {
  private static TestServices services;

  @BeforeAll
  public static void beforeAll() {
    services = new TestServices();

    services.addExactPath(
        "/utf8",
        new FixedResponseBodyHandler("utf8Handler"));

    services.addExactPath(
        "/utf16",
        new FixedResponseBodyHandler("utf16Handler", UTF_16));
  }

  @AfterAll
  public static void afterAll() {
    services.shutdown();
  }

  /**
   * Verifies that a {@link FixedResponseBodyHandler} can send a UTF-8 string.
   */
  @Test
  public void testUtf8() throws IOException, InterruptedException {
    HttpResponse<byte[]> response =
        services.httpGetBytes("/utf8");

    assertEquals(OK, response.statusCode());

    byte[] responseBytes = response.body();

    assertArrayEquals(
        "utf8Handler".getBytes(UTF_8),
        responseBytes);
  }

  /**
   * Verifies that a {@link FixedResponseBodyHandler} can send a string in a
   * charset other than UTF-8.
   */
  @Test
  public void testNotUtf8() throws IOException, InterruptedException {
    HttpResponse<byte[]> response =
        services.httpGetBytes("/utf16");

    assertEquals(OK, response.statusCode());

    byte[] responseBytes = response.body();

    assertArrayEquals(
        "utf16Handler".getBytes(UTF_16),
        responseBytes);
  }
}
