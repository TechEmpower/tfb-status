package tfb.status.handler;

import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.HTML_UTF_8;
import static io.undertow.util.StatusCodes.NOT_FOUND;
import static io.undertow.util.StatusCodes.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static tfb.status.util.MoreAssertions.assertMediaType;
import static tfb.status.util.MoreAssertions.assertStartsWith;

import com.google.errorprone.annotations.MustBeClosed;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.ws.rs.core.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tfb.status.bootstrap.TestServices;

/**
 * Tests for {@link TimelinePageHandler}.
 */
public final class TimelinePageHandlerTest {
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
   * Tests that a GET request for the timeline with valid parameters produces an
   * HTML response.
   */
  @Test
  public void testGet() {
    try (Response response = getTimeline("gemini",
                                         "json",
                                         services.authorizationHeader())) {

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
   * Tests that a GET request for the timeline with an unknown test type
   * specified produces a {@code 404 Not Found} response.
   */
  @Test
  public void testUnknownTestType() {
    try (Response response = getTimeline("gemini",
                                         "notarealtesttypename",
                                         services.authorizationHeader())) {

      assertEquals(NOT_FOUND, response.getStatus());
    }
  }

  /**
   * Tests that a GET request for the timeline with an unknown framework
   * specified produces a {@code 404 Not Found} response.
   */
  @Test
  public void testUnknownFramework() {
    try (Response response = getTimeline("notarealframeworkname",
                                         "json",
                                         services.authorizationHeader())) {

      assertEquals(NOT_FOUND, response.getStatus());
    }
  }

  @MustBeClosed
  private Response getTimeline(String framework,
                               String testType,
                               @Nullable String authorizationHeader) {
    Objects.requireNonNull(framework);
    Objects.requireNonNull(testType);
    String path = "/timeline/" + framework + "/" + testType;
    return services.httpClient()
                   .target(services.localUri(path))
                   .request()
                   .header(AUTHORIZATION, authorizationHeader)
                   .get();
  }
}
