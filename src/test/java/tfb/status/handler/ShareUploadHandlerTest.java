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
import tfb.status.service.ShareManager;
import tfb.status.testlib.HttpTester;
import tfb.status.testlib.ResultsTester;
import tfb.status.testlib.TestServicesInjector;
import tfb.status.view.Results;

/**
 * Tests for {@link ShareUploadHandler}.
 */
@ExtendWith(TestServicesInjector.class)
public final class ShareUploadHandlerTest {
  /**
   * Verifies that {@code POST /share/upload} produces a {@code 201 Created}
   * response for a valid results.json file included as {@code
   * application/json}.
   */
  @Test
  public void testPostJson(HttpTester http,
                           ResultsTester resultsTester,
                           ObjectMapper objectMapper)
      throws IOException, InterruptedException {

    Results results = resultsTester.newResults();

    HttpResponse<String> response =
        http.client().send(
            HttpRequest.newBuilder(http.uri("/share/upload"))
                       .POST(resultsTester.asBodyPublisher(results))
                       .header(CONTENT_TYPE, JSON_UTF_8.toString())
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(CREATED, response.statusCode());

    assertMediaType(
        JSON_UTF_8,
        response.headers()
                .firstValue(CONTENT_TYPE)
                .orElse(null));

    ShareManager.ShareOutcome.Success success =
        objectMapper.readValue(
            response.body(),
            ShareManager.ShareOutcome.Success.class);

    assertNotNull(success);
  }

  /**
   * Verifies that {@code POST /share/upload} produces a {@code 400 Bad Request}
   * response for an invalid results.json file included as {@code
   * application/json}.
   */
  @Test
  public void testPostJson_invalidFile(HttpTester http,
                                       ObjectMapper objectMapper)
      throws IOException, InterruptedException {

    HttpResponse<String> response =
        http.client().send(
            HttpRequest.newBuilder(http.uri("/share/upload"))
                       .POST(HttpRequest.BodyPublishers.ofString("not json"))
                       .header(CONTENT_TYPE, JSON_UTF_8.toString())
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(BAD_REQUEST, response.statusCode());

    assertMediaType(
        JSON_UTF_8,
        response.headers()
                .firstValue(CONTENT_TYPE)
                .orElse(null));

    ShareManager.ShareOutcome.Failure failure =
        objectMapper.readValue(
            response.body(),
            ShareManager.ShareOutcome.Failure.class);

    assertNotNull(failure);
  }
}
