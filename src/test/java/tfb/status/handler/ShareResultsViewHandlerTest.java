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
   * Ensure that after uploading a file, we can use the view handler to retrieve
   * that results file using the GET {@code /share-results/view/{shareId}.json}
   * endpoint.
   */
  @Test
  public void shareResultsViewHandler_getValidUpload(
      HttpTester http,
      ResultsTester resultsTester,
      ShareResultsUploader shareResultsUploader,
      FileSystem fileSystem) throws IOException, InterruptedException {

    Results results = resultsTester.newResults();

    Path jsonFile = fileSystem.getPath("results_to_upload.json");
    resultsTester.saveJsonToFile(results, jsonFile);

    ShareResultsUploader.ShareResultsUploadReport report;
    try (InputStream in = Files.newInputStream(jsonFile)) {
      report = shareResultsUploader.upload(in);
    }

    assertFalse(report.isError());

    HttpResponse<String> response =
        http.getString("/share-results/view/" + report.getSuccess().fileName);

    assertEquals(OK, response.statusCode());
    assertEquals(
        JSON_UTF_8.toString(),
        response.headers().firstValue(CONTENT_TYPE).orElse(null));

    Path responseFile = fileSystem.getPath("results_response.json");
    Files.writeString(responseFile, response.body(), CREATE_NEW);

    assertTrue(MoreFiles.equal(jsonFile, responseFile));
  }

  /**
   * Ensure the handler gives a 404 when trying to view a results file that does
   * not exist.
   */
  @Test
  public void shareResultsViewHandler_rejectMissingUpload(
      HttpTester http,
      FileStore fileStore) throws IOException, InterruptedException {

    String shareId;

    do {
      shareId = UUID.randomUUID().toString();
    } while (
        Files.exists(fileStore.shareDirectory().resolve(shareId + ".zip")));

    HttpResponse<String> response =
        http.getString("/share-results/view/" + shareId + ".json");

    assertEquals(NOT_FOUND, response.statusCode());

    assertEquals("", response.body());
  }
}
