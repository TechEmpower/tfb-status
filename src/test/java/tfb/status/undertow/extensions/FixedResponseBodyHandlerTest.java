package tfb.status.undertow.extensions;

import static io.undertow.util.StatusCodes.OK;
import static java.nio.charset.StandardCharsets.UTF_16;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.ws.rs.core.Response;
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
  public void testUtf8() {
    try (Response response = services.httpGet("/utf8")) {

      assertEquals(OK, response.getStatus());

      byte[] responseBytes = response.readEntity(byte[].class);

      assertArrayEquals(
          "utf8Handler".getBytes(UTF_8),
          responseBytes);
    }
  }

  /**
   * Verifies that a {@link FixedResponseBodyHandler} can send a string in a
   * charset other than UTF-8.
   */
  @Test
  public void testNotUtf8() {
    try (Response response = services.httpGet("/utf16")) {

      assertEquals(OK, response.getStatus());

      byte[] responseBytes = response.readEntity(byte[].class);

      assertArrayEquals(
          "utf16Handler".getBytes(UTF_16),
          responseBytes);
    }
  }
}
