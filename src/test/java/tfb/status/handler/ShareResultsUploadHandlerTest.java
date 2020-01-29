package tfb.status.handler;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.undertow.util.StatusCodes.BAD_REQUEST;
import static io.undertow.util.StatusCodes.CREATED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
   * Ensure that given a valid results json file, the handler successfully
   * uploads and responds as expected with a 201. This also verifies that the
   * response body has JSON that de-serialized to a
   * {@link ShareResultsJsonView}.
   */
  @Test
  public void shareResultsUploadHandler_uploadValid(HttpTester http,
                                                    ResultsTester resultsTester,
                                                    ObjectMapper objectMapper)
      throws IOException, InterruptedException {

    Results results = resultsTester.newResults();

    HttpRequest request =
        HttpRequest.newBuilder(http.uri("/share-results/upload"))
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    objectMapper.writeValueAsString(results)))
            .header(CONTENT_TYPE, JSON_UTF_8.toString())
        .build();

    HttpResponse<String> response = http.client().send(
        request,
        HttpResponse.BodyHandlers.ofString());

    assertEquals(CREATED, response.statusCode());
    assertEquals(
        JSON_UTF_8.toString(),
        response.headers().firstValue(CONTENT_TYPE).orElse(null));

    ShareResultsJsonView responseView = objectMapper.readValue(
        response.body(),
        ShareResultsJsonView.class);

    assertNotNull(responseView);
  }

  /**
   * Ensure that given an invalid results json file, the handler responds as
   * expected with a 400. This also verifies that the response body has JSON
   * that de-serialized to a {@link ShareResultsErrorJsonView}.
   */
  @Test
  public void shareResultsUploadHandler_rejectUploadInvalid(
      HttpTester http,
      ObjectMapper objectMapper)
      throws IOException, InterruptedException {

    HttpRequest request =
        HttpRequest.newBuilder(http.uri("/share-results/upload"))
            .POST(HttpRequest.BodyPublishers.ofString("invalid json"))
            .header(CONTENT_TYPE, JSON_UTF_8.toString())
            .build();

    HttpResponse<String> response = http.client().send(
        request,
        HttpResponse.BodyHandlers.ofString());

    assertEquals(BAD_REQUEST, response.statusCode());
    assertEquals(
        JSON_UTF_8.toString(),
        response.headers().firstValue(CONTENT_TYPE).orElse(null));

    ShareResultsErrorJsonView responseView = objectMapper.readValue(
        response.body(),
        ShareResultsErrorJsonView.class);

    assertNotNull(responseView);
  }
}
