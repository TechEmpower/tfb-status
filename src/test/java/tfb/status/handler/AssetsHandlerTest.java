package tfb.status.handler;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static io.undertow.util.StatusCodes.NOT_FOUND;
import static io.undertow.util.StatusCodes.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static tfb.status.util.MoreAssertions.assertLinesEqual;
import static tfb.status.util.MoreAssertions.assertMediaType;

import java.util.List;
import javax.ws.rs.core.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tfb.status.bootstrap.TestServices;

/**
 * Tests for {@link AssetsHandler}.
 */
public final class AssetsHandlerTest {
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
   * Tests that a GET request for an asset file that exists is successful.
   */
  @Test
  public void testGet() {
    try (Response response = services.httpGet("/assets/test_asset.txt")) {

      assertEquals(OK, response.getStatus());

      assertMediaType(
          PLAIN_TEXT_UTF_8,
          response.getHeaderString(CONTENT_TYPE));

      assertLinesEqual(
          List.of("Hello, World!"),
          response.readEntity(String.class));
    }
  }

  /**
   * Tests that a GET request for an assets file that does not exists results in
   * {@code 404 Not Found}.
   */
  @Test
  public void testNotFound() {
    try (Response response = services.httpGet("/assets/does_not_exist.txt")) {

      assertEquals(NOT_FOUND, response.getStatus());
    }
  }
}
