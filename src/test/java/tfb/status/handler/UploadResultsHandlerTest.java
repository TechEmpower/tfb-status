package tfb.status.handler;

import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static com.google.common.net.MediaType.ZIP;
import static io.undertow.util.StatusCodes.NOT_FOUND;
import static io.undertow.util.StatusCodes.OK;
import static io.undertow.util.StatusCodes.UNAUTHORIZED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tfb.status.testlib.MoreAssertions.assertContains;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import javax.mail.internet.MimeMessage;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.testlib.BasicAuthUtils;
import tfb.status.testlib.HttpTester;
import tfb.status.testlib.MailServer;
import tfb.status.testlib.TestServicesInjector;
import tfb.status.util.ZipFiles;
import tfb.status.view.DetailPageView;
import tfb.status.view.Results;

/**
 * Tests for {@link UploadResultsHandler}.
 */
@ExtendWith(TestServicesInjector.class)
public final class UploadResultsHandlerTest {
  /**
   * Verifies the process of uploading results, simulating what would occur
   * during a full run.
   */
  @Test
  public void testUpload(HttpTester http,
                         FileSystem fileSystem,
                         ObjectMapper objectMapper,
                         MailServer mailServer)
      throws Exception {

    //
    // Download the original results.
    //
    // We'll upload slightly altered versions of these original results during
    // this test.  That's simpler than having to maintain another independent
    // set of results.
    //

    Path jsonFile = fileSystem.getPath("results_to_upload.json");
    Path zipFile = fileSystem.getPath("results_to_upload.zip");

    URI jsonUri = http.uri("/raw/results.2017-12-26-05-07-14-321.json");
    URI zipUri = http.uri("/raw/results.2017-12-29-23-04-02-541.zip");

    HttpResponse<Path> responseToOriginalJson =
        http.client().send(
            HttpRequest.newBuilder(jsonUri).build(),
            HttpResponse.BodyHandlers.ofFile(jsonFile));

    assertEquals(OK, responseToOriginalJson.statusCode());

    HttpResponse<Path> responseToOriginalZip =
        http.client().send(
            HttpRequest.newBuilder(zipUri).build(),
            HttpResponse.BodyHandlers.ofFile(zipFile));

    assertEquals(OK, responseToOriginalZip.statusCode());

    //
    // Create new results with a different UUID.
    //

    String uuid = UUID.randomUUID().toString();

    Results originalResults;
    try (InputStream inputStream = Files.newInputStream(jsonFile)) {
      originalResults = objectMapper.readValue(inputStream, Results.class);
    }

    Results newResults =
        new Results(
            /* uuid= */ uuid,
            /* name= */ originalResults.name,
            /* environmentDescription= */ originalResults.environmentDescription,
            /* startTime= */ originalResults.startTime,
            /* completionTime= */ originalResults.completionTime,
            /* duration= */ originalResults.duration,
            /* frameworks= */ originalResults.frameworks,
            /* completed= */ originalResults.completed,
            /* succeeded= */ originalResults.succeeded,
            /* failed= */ originalResults.failed,
            /* rawData= */ originalResults.rawData,
            /* queryIntervals= */ originalResults.queryIntervals,
            /* concurrencyLevels= */ originalResults.concurrencyLevels,
            /* git= */ originalResults.git);

    try (BufferedWriter writer = Files.newBufferedWriter(jsonFile)) {
      objectMapper.writeValue(writer, newResults);
    }

    //
    // Confirm the new results don't exist on the server yet.
    //

    HttpResponse<byte[]> responseBeforeUpload =
        http.getBytes("/results/" + uuid + ".json");

    assertEquals(NOT_FOUND, responseBeforeUpload.statusCode());

    //
    // Confirm that authorization is required for uploads.
    //

    String invalidCredentials =
        BasicAuthUtils.writeAuthorizationHeader(
            "wrong_username",
            "wrong_password");

    HttpResponse<Void> responseToInvalidCredentials =
        http.client().send(
            HttpRequest.newBuilder(http.uri("/upload"))
                       .POST(filePublisher(jsonFile))
                       .header(AUTHORIZATION, invalidCredentials)
                       .header(CONTENT_TYPE, JSON_UTF_8.toString())
                       .build(),
            HttpResponse.BodyHandlers.discarding());

    assertEquals(UNAUTHORIZED, responseToInvalidCredentials.statusCode());

    //
    // Begin listening for updates to the home page.
    //

    var updates = new ConcurrentLinkedQueue<String>();

    URI updatesUri = http.webSocketUri("/updates");

    var updatesListener =
        new WebSocket.Listener() {
          @Override
          public @Nullable CompletionStage<?> onText(WebSocket webSocket,
                                                     CharSequence data,
                                                     boolean last) {
            updates.add(data.toString());
            webSocket.request(1);
            return null;
          }
        };

    WebSocket updatesWebSocket =
        http.client()
            .newWebSocketBuilder()
            .buildAsync(updatesUri, updatesListener)
            .get(1, TimeUnit.SECONDS);

    try {

      //
      // Upload the new results JSON.
      //

      HttpResponse<Void> responseToNewJson =
          http.client().send(
              HttpRequest.newBuilder(http.uri("/upload"))
                         .POST(filePublisher(jsonFile))
                         .header(AUTHORIZATION, http.authorizationHeader())
                         .header(CONTENT_TYPE, JSON_UTF_8.toString())
                         .build(),
              HttpResponse.BodyHandlers.discarding());

      assertEquals(OK, responseToNewJson.statusCode());

      //
      // Confirm the new results exist on the server now.
      //

      HttpResponse<byte[]> newDetailsResponse =
          http.getBytes("/results/" + uuid + ".json");

      assertEquals(OK, newDetailsResponse.statusCode());

      byte[] newDetailsBytes = newDetailsResponse.body();

      DetailPageView newDetails =
          objectMapper.readValue(newDetailsBytes, DetailPageView.class);

      assertEquals(uuid, newDetails.result.uuid);
      assertNotNull(newDetails.result.jsonFileName);
      assertNull(newDetails.result.zipFileName);

      //
      // Confirm that we received a notification about the new results.
      //

      String updateAboutNewResults = updates.poll();
      assertContains(uuid, updateAboutNewResults);
      assertTrue(updates.isEmpty());

      //
      // Update the results, keeping the same UUID, but choosing a new name.
      //

      Results updatedResults =
          new Results(
              /* uuid= */ uuid,
              /* name= */ "modified " + newResults.name,
              /* environmentDescription= */ newResults.environmentDescription,
              /* startTime= */ newResults.startTime,
              /* completionTime= */ newResults.completionTime,
              /* duration= */ newResults.duration,
              /* frameworks= */ newResults.frameworks,
              /* completed= */ newResults.completed,
              /* succeeded= */ newResults.succeeded,
              /* failed= */ newResults.failed,
              /* rawData= */ newResults.rawData,
              /* queryIntervals= */ newResults.queryIntervals,
              /* concurrencyLevels= */ newResults.concurrencyLevels,
              /* git= */ newResults.git);

      try (BufferedWriter writer = Files.newBufferedWriter(jsonFile)) {
        objectMapper.writeValue(writer, updatedResults);
      }

      //
      // Upload the updated results JSON.
      //

      HttpResponse<Void> responseToUpdatedJson =
          http.client().send(
              HttpRequest.newBuilder(http.uri("/upload"))
                         .POST(filePublisher(jsonFile))
                         .header(AUTHORIZATION, http.authorizationHeader())
                         .header(CONTENT_TYPE, JSON_UTF_8.toString())
                         .build(),
              HttpResponse.BodyHandlers.discarding());

      assertEquals(OK, responseToUpdatedJson.statusCode());

      //
      // Confirm a notification about the updated results was sent.
      //

      HttpResponse<byte[]> updatedDetailsResponse =
          http.getBytes("/results/" + uuid + ".json");

      assertEquals(OK, updatedDetailsResponse.statusCode());

      byte[] updatedDetailsBytes = updatedDetailsResponse.body();

      DetailPageView updatedDetails =
          objectMapper.readValue(updatedDetailsBytes, DetailPageView.class);

      assertEquals(uuid, updatedDetails.result.uuid);
      assertEquals(updatedResults.name, updatedDetails.result.name);
      assertNotNull(updatedDetails.result.jsonFileName);
      assertNull(updatedDetails.result.zipFileName);

      //
      // Confirm that we received a notification about the updated results.
      //

      String updateAboutUpdatedResults = updates.poll();
      assertContains(uuid, updateAboutUpdatedResults);
      assertTrue(updates.isEmpty());

      //
      // Update the results a final time prior to uploading as a zip.
      //

      Results finalResults =
          new Results(
              /* uuid= */ uuid,
              /* name= */ "final " + updatedResults.name,
              /* environmentDescription= */ updatedResults.environmentDescription,
              /* startTime= */ updatedResults.startTime,
              /* completionTime= */ updatedResults.completionTime,
              /* duration= */ updatedResults.duration,
              /* frameworks= */ updatedResults.frameworks,
              /* completed= */ updatedResults.completed,
              /* succeeded= */ updatedResults.succeeded,
              /* failed= */ updatedResults.failed,
              /* rawData= */ updatedResults.rawData,
              /* queryIntervals= */ updatedResults.queryIntervals,
              /* concurrencyLevels= */ updatedResults.concurrencyLevels,
              /* git= */ updatedResults.git);

      ZipFiles.findZipEntry(
          /* zipFile= */ zipFile,
          /* entryPath= */ "results.json",
          /* ifPresent= */
          (Path zipEntry) -> {
            try (BufferedWriter writer = Files.newBufferedWriter(zipEntry)) {
              objectMapper.writeValue(writer, finalResults);
            }
          },
          /* ifAbsent= */
          () -> {
            throw new AssertionError(
                "The results.zip file should include a results.json");
          });

      //
      // Upload the final results zip.
      //

      HttpResponse<Void> responseToZip =
          http.client().send(
              HttpRequest.newBuilder(http.uri("/upload"))
                         .POST(filePublisher(zipFile))
                         .header(AUTHORIZATION, http.authorizationHeader())
                         .header(CONTENT_TYPE, ZIP.toString())
                         .build(),
              HttpResponse.BodyHandlers.discarding());

      assertEquals(OK, responseToZip.statusCode());

      //
      // Confirm the results on the server include the final zip.
      //

      HttpResponse<byte[]> finalDetailsResponse =
          http.getBytes("/results/" + uuid + ".json");

      assertEquals(OK, finalDetailsResponse.statusCode());

      byte[] finalDetailsBytes = finalDetailsResponse.body();

      DetailPageView finalDetails =
          objectMapper.readValue(finalDetailsBytes, DetailPageView.class);

      assertEquals(uuid, finalDetails.result.uuid);
      assertEquals(finalResults.name, finalDetails.result.name);
      assertNotNull(finalDetails.result.jsonFileName);
      assertNotNull(finalDetails.result.zipFileName);

      //
      // Confirm that we received a notification about the final results.
      //

      String updateAboutFinalResults = updates.poll();
      assertContains(uuid, updateAboutFinalResults);
      assertTrue(updates.isEmpty());

      //
      // Confirm the "Run complete" email was sent.
      //

      MimeMessage emailMessage = mailServer.onlyEmailMessage();

      assertEquals(
          UploadResultsHandler.runCompleteEmailSubject(finalResults),
          emailMessage.getSubject());

    } finally {
      updatesWebSocket.abort();
    }
  }

  // The JDK's version of this, `HttpRequest.BodyPublishers.ofFile(file)`, is
  // incompatible with the in-memory file system that we use during tests.
  private static HttpRequest.BodyPublisher filePublisher(Path file) {
    Objects.requireNonNull(file);
    return HttpRequest.BodyPublishers.ofInputStream(
        () -> {
          try {
            return Files.newInputStream(file);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        });
  }
}
