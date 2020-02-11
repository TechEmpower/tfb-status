package tfb.status.handler;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.undertow.util.StatusCodes.NOT_FOUND;
import static io.undertow.util.StatusCodes.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tfb.status.testlib.MoreAssertions.assertMediaType;

import com.google.common.io.ByteSource;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.service.FileStore;
import tfb.status.service.ShareManager;
import tfb.status.service.ShareManager.ShareOutcome;
import tfb.status.testlib.HttpTester;
import tfb.status.testlib.ResultsTester;
import tfb.status.testlib.TestServicesInjector;
import tfb.status.view.Results;

/**
 * Tests for {@link ShareDownloadHandler}.
 */
@ExtendWith(TestServicesInjector.class)
public final class ShareDownloadHandlerTest {
  /**
   * Verifies that {@code GET /share/download/$share_id.json} for a valid
   * share id produces that results.json file that was shared.
   */
  @Test
  public void testGet(HttpTester http,
                      ResultsTester resultsTester,
                      ShareManager shareManager)
      throws IOException, InterruptedException {

    Results results = resultsTester.newResults();
    ByteSource resultsBytes = resultsTester.asByteSource(results);

    ShareOutcome outcome;
    try (InputStream inputStream = resultsBytes.openStream()) {
      outcome = shareManager.shareResults(inputStream);
    }

    assertFalse(outcome.isFailure());

    HttpResponse<byte[]> response =
        http.getBytes(
            "/share/download/" + outcome.getSuccess().shareId + ".json");

    assertEquals(OK, response.statusCode());

    assertMediaType(
        JSON_UTF_8,
        response.headers()
                .firstValue(CONTENT_TYPE)
                .orElse(null));

    assertTrue(resultsBytes.contentEquals(ByteSource.wrap(response.body())));
  }

  /**
   * Verifies that {@code GET /share/download/$share_id.json} produces a
   * {@code 404 Not Found} response for an invalid share id.
   */
  @Test
  public void testGet_invalidShareId(HttpTester http,
                                     FileStore fileStore)
      throws IOException, InterruptedException {

    String shareId;
    do shareId = UUID.randomUUID().toString();
    while (Files.exists(fileStore.shareDirectory().resolve(shareId + ".zip")));

    HttpResponse<String> response =
        http.getString("/share/download/" + shareId + ".json");

    assertEquals(NOT_FOUND, response.statusCode());

    assertEquals("", response.body());
  }
}