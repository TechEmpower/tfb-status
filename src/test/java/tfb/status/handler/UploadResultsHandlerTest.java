package tfb.status.handler;

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
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import java.io.IOException;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import tfb.status.service.RunCompleteMailer;
import tfb.status.testlib.HttpTester;
import tfb.status.testlib.MailServer;
import tfb.status.testlib.ResultsTester;
import tfb.status.testlib.TestServicesInjector;
import tfb.status.view.DetailPageView;
import tfb.status.view.Results;

/**
 * Tests for {@link UploadResultsHandler}.
 */
@Execution(ExecutionMode.SAME_THREAD) // currently not parallelizable
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
                         ResultsTester resultsTester,
                         MailServer mailServer)
      throws ExecutionException,
             InterruptedException,
             IOException,
             MessagingException,
             TimeoutException {

    //
    // Create new results.
    //

    Results newResults = resultsTester.newResults();
    assertNotNull(newResults.uuid);

    String uuid = newResults.uuid;

    //
    // Confirm the new results don't exist on the server yet.
    //

    HttpResponse<byte[]> responseBeforeUpload =
        http.getBytes("/results/" + uuid + ".json");

    assertEquals(NOT_FOUND, responseBeforeUpload.statusCode());

    //
    // Confirm that authorization is required for uploads.
    //

    HttpResponse<Void> responseToUnauthorized =
        http.client().send(
            HttpRequest.newBuilder(http.uri("/upload"))
                       .POST(asBodyPublisher(resultsTester.asByteSource(newResults)))
                       .header(CONTENT_TYPE, JSON_UTF_8.toString())
                       .build(),
            HttpResponse.BodyHandlers.discarding());

    assertEquals(UNAUTHORIZED, responseToUnauthorized.statusCode());

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

      HttpRequest.Builder requestWithNewJson =
          HttpRequest.newBuilder(http.uri("/upload"))
                     .POST(asBodyPublisher(resultsTester.asByteSource(newResults)))
                     .header(CONTENT_TYPE, JSON_UTF_8.toString());

      HttpResponse<Void> responseToNewJson =
          http.client().send(
              http.addAuthorization(requestWithNewJson).build(),
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

      // TODO: Read home updates in a parallel-friendly way.
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
              /* git= */ newResults.git,
              /* testMetadata= */ newResults.testMetadata);

      //
      // Upload the updated results JSON.
      //

      HttpRequest.Builder requestWithUpdatedJson =
          HttpRequest.newBuilder(http.uri("/upload"))
                     .POST(asBodyPublisher(resultsTester.asByteSource(updatedResults)))
                     .header(CONTENT_TYPE, JSON_UTF_8.toString());

      HttpResponse<Void> responseToUpdatedJson =
          http.client().send(
              http.addAuthorization(requestWithUpdatedJson).build(),
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

      // TODO: Read home updates in a parallel-friendly way.
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
              /* git= */ updatedResults.git,
              /* testMetadata= */ updatedResults.testMetadata);

      String zipFileName =
          getClass().getSimpleName()
              + "_results_to_upload_"
              + UUID.randomUUID()
              + ".zip";

      Path zipFile = fileSystem.getPath(zipFileName);
      resultsTester.saveZipToFile(finalResults, zipFile);

      //
      // Upload the final results zip.
      //

      HttpRequest.Builder requestWithZip =
          HttpRequest.newBuilder(http.uri("/upload"))
                     .POST(filePublisher(zipFile))
                     .header(CONTENT_TYPE, ZIP.toString());

      HttpResponse<Void> responseToZip =
          http.client().send(
              http.addAuthorization(requestWithZip).build(),
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

      // TODO: Read home updates in a parallel-friendly way.
      String updateAboutFinalResults = updates.poll();
      assertContains(uuid, updateAboutFinalResults);
      assertTrue(updates.isEmpty());

      //
      // Confirm the "Run complete" email was sent.
      //

      String subject =
          RunCompleteMailer.runCompleteEmailSubject(finalResults);

      ImmutableList<MimeMessage> messages =
          mailServer.getMessages(m -> m.getSubject().equals(subject));

      assertEquals(1, messages.size());

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

  private static HttpRequest.BodyPublisher asBodyPublisher(ByteSource bytes) {
    Objects.requireNonNull(bytes);
    return HttpRequest.BodyPublishers.ofInputStream(
        () -> {
          try {
            return bytes.openStream();
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        });
  }
}
