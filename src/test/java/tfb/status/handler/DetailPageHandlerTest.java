package tfb.status.handler;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.HTML_UTF_8;
import static io.undertow.util.StatusCodes.NOT_FOUND;
import static io.undertow.util.StatusCodes.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static tfb.status.util.MoreAssertions.assertMediaType;
import static tfb.status.util.MoreAssertions.assertStartsWith;

import javax.ws.rs.core.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tfb.status.bootstrap.TestServices;

/**
 * Tests for {@link DetailPageHandler}.
 */
public final class DetailPageHandlerTest {
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
   * Tests that a GET request for the results detail page with a valid uuid
   * produces an HTML response.
   */
  @Test
  public void testGet() {
    try (Response response = services.httpGet("/results/03da6340-d56c-4584-9ef2-702106203809")) {

      assertEquals(OK, response.getStatus());

      assertMediaType(
          HTML_UTF_8,
          response.getHeaderString(CONTENT_TYPE));

      assertStartsWith(
          "<!DOCTYPE html>",
          response.readEntity(String.class));
    }
  }

  /**
   * Tests that a GET request for the results detail page with an unknown uuid
   * produces a {@code 404 Not Found} response.
   */
  public void testUnknownUuid() {
    try (Response response = services.httpGet("/results/notarealuuid")) {

      assertEquals(NOT_FOUND, response.getStatus());
    }
  }
}
