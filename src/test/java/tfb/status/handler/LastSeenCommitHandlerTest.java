package tfb.status.handler;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static io.undertow.util.StatusCodes.BAD_REQUEST;
import static io.undertow.util.StatusCodes.NO_CONTENT;
import static io.undertow.util.StatusCodes.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static tfb.status.testlib.MoreAssertions.assertMediaType;

import java.io.IOException;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.testlib.HttpTester;
import tfb.status.testlib.TestServicesInjector;

/**
 * Tests for {@link LastSeenCommitHandler}.
 */
@ExtendWith(TestServicesInjector.class)
public final class LastSeenCommitHandlerTest {
  /**
   * Verifies that {@code GET /last-seen-commit?environment=...} provides the
   * commit id for an environment known to have results.
   */
  @Test
  public void testEnvironmentWithResults(HttpTester http)
      throws IOException, InterruptedException {

    HttpResponse<String> response =
        http.getString("/last-seen-commit?environment=Citrine");

    assertEquals(OK, response.statusCode());

    assertMediaType(
        PLAIN_TEXT_UTF_8,
        response.headers()
                .firstValue(CONTENT_TYPE)
                .orElse(null));

    assertEquals(
        "57c558b30dd57e2421b8cbaeedfa90c1a59f02fe",
        response.body());
  }

  /**
   * Verifies that {@code GET /last-seen-commit?environment=...} provides an
   * empty response for an environment known to have no results.
   */
  @Test
  public void testEnvironmentWithoutResults(HttpTester http)
      throws IOException, InterruptedException {

    HttpResponse<String> response =
        http.getString("/last-seen-commit?environment=NotARealEnvironment");

    assertEquals(NO_CONTENT, response.statusCode());

    assertEquals("", response.body());
  }

  /**
   * Verifies that {@code GET /last-seen-commit?environment=...} requires the
   * {@code environment} query string parameter.
   */
  @Test
  public void testNoEnvironment(HttpTester http)
      throws IOException, InterruptedException {

    HttpResponse<String> response =
        http.getString("/last-seen-commit");

    assertEquals(BAD_REQUEST, response.statusCode());

    assertEquals("", response.body());
  }
}
