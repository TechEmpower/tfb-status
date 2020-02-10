package tfb.status.handler;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.undertow.util.StatusCodes.NOT_FOUND;
import static io.undertow.util.StatusCodes.OK;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.io.MoreFiles;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.service.FileStore;
import tfb.status.service.ShareResultsUploader;
import tfb.status.service.ShareResultsUploader.ShareResultsUploadReport;
import tfb.status.testlib.HttpTester;
import tfb.status.testlib.ResultsTester;
import tfb.status.testlib.TestServicesInjector;
import tfb.status.view.Results;

/**
 * Tests for {@link ShareResultsViewHandler}.
 */
@ExtendWith(TestServicesInjector.class)
public final class ShareResultsViewHandlerTest {
  /**
   * Verifies that {@code GET /share-results/view/$share_id.json} for a valid
   * share id produces that results.json file that was shared.
   */
  @Test
  public void testGet(HttpTester http,
                      ResultsTester resultsTester,
                      ShareResultsUploader shareResultsUploader,
                      FileSystem fileSystem)
      throws IOException, InterruptedException {

    Results results = resultsTester.newResults();

    Path jsonFile = fileSystem.getPath(UUID.randomUUID().toString() + ".json");
    resultsTester.saveJsonToFile(results, jsonFile);

    ShareResultsUploadReport report;
    try (InputStream inputStream = Files.newInputStream(jsonFile)) {
      report = shareResultsUploader.upload(inputStream);
    }

    assertFalse(report.isError());

    HttpResponse<String> response =
        http.getString("/share-results/view/" + report.getSuccess().fileName);

    assertEquals(OK, response.statusCode());
    assertEquals(
        JSON_UTF_8.toString(),
        response.headers().firstValue(CONTENT_TYPE).orElse(null));

    Path responseFile =
        fileSystem.getPath(UUID.randomUUID().toString() + ".json");
    Files.writeString(responseFile, response.body(), CREATE_NEW);

    assertTrue(MoreFiles.equal(jsonFile, responseFile));
  }

  /**
   * Verifies that {@code GET /share-results/view/$share_id.json} produces a
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
        http.getString("/share-results/view/" + shareId + ".json");

    assertEquals(NOT_FOUND, response.statusCode());

    assertEquals("", response.body());
  }
}
