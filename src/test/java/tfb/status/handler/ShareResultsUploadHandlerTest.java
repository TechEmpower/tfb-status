package tfb.status.handler;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.undertow.util.StatusCodes.BAD_REQUEST;
import static io.undertow.util.StatusCodes.CREATED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static tfb.status.testlib.MoreAssertions.assertMediaType;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.testlib.HttpTester;
import tfb.status.testlib.ResultsTester;
import tfb.status.testlib.TestServicesInjector;
import tfb.status.view.Results;
import tfb.status.view.ShareResultsErrorJsonView;
import tfb.status.view.ShareResultsJsonView;

/**
 * Tests for {@link ShareResultsUploadHandler}.
 */
@ExtendWith(TestServicesInjector.class)
public final class ShareResultsUploadHandlerTest {
  /**
   * Verifies that {@code POST /share-results/upload} produces a {@code 201
   * Created} response for a valid results.json file.
   */
  @Test
  public void testPost(HttpTester http,
                       ResultsTester resultsTester,
                       ObjectMapper objectMapper)
      throws IOException, InterruptedException {

    Results results = resultsTester.newResults();

    String resultsJson = objectMapper.writeValueAsString(results);

    HttpRequest request =
        HttpRequest.newBuilder(http.uri("/share-results/upload"))
                   .POST(HttpRequest.BodyPublishers.ofString(resultsJson))
                   .header(CONTENT_TYPE, JSON_UTF_8.toString())
                   .build();

    HttpResponse<String> response =
        http.client().send(
            request,
            HttpResponse.BodyHandlers.ofString());

    assertEquals(CREATED, response.statusCode());

    assertMediaType(
        JSON_UTF_8,
        response.headers().firstValue(CONTENT_TYPE).orElse(null));

    ShareResultsJsonView responseView =
        objectMapper.readValue(
            response.body(),
            ShareResultsJsonView.class);

    assertNotNull(responseView);
  }

  /**
   * Verifies that {@code POST /share-results/upload} produces a {@code 400 Bad
   * Request} response for an invalid results.json file.
   */
  @Test
  public void testPost_invalidFile(HttpTester http, ObjectMapper objectMapper)
      throws IOException, InterruptedException {

    HttpRequest request =
        HttpRequest.newBuilder(http.uri("/share-results/upload"))
                   .POST(HttpRequest.BodyPublishers.ofString("invalid json"))
                   .header(CONTENT_TYPE, JSON_UTF_8.toString())
                   .build();

    HttpResponse<String> response =
        http.client().send(
            request,
            HttpResponse.BodyHandlers.ofString());

    assertEquals(BAD_REQUEST, response.statusCode());

    assertMediaType(
        JSON_UTF_8,
        response.headers().firstValue(CONTENT_TYPE).orElse(null));

    ShareResultsErrorJsonView responseView =
        objectMapper.readValue(
            response.body(),
            ShareResultsErrorJsonView.class);

    assertNotNull(responseView);
  }
}
