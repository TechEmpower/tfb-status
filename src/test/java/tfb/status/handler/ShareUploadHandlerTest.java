package tfb.status.handler;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.undertow.util.StatusCodes.BAD_REQUEST;
import static io.undertow.util.StatusCodes.CREATED;
import static io.undertow.util.StatusCodes.OK;
import static io.undertow.util.StatusCodes.REQUEST_ENTITY_TOO_LARGE;
import static io.undertow.util.StatusCodes.SERVICE_UNAVAILABLE;
import static io.undertow.util.StatusCodes.UNSUPPORTED_MEDIA_TYPE;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tfb.status.testlib.MoreAssertions.assertMediaType;
import static tfb.status.testlib.MoreAssertions.assertStartsWith;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Ascii;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import javax.mail.MessagingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.threeten.extra.MutableClock;
import tfb.status.config.ShareConfig;
import tfb.status.service.FileStore;
import tfb.status.testlib.HttpTester;
import tfb.status.testlib.MailDelay;
import tfb.status.testlib.MailServer;
import tfb.status.testlib.ResultsTester;
import tfb.status.testlib.TestServicesInjector;
import tfb.status.view.Results;
import tfb.status.view.ShareFailure;
import tfb.status.view.ShareSuccess;

/**
 * Tests for {@link ShareUploadHandler}.
 */
@Execution(ExecutionMode.SAME_THREAD) // currently not parallelizable
@ExtendWith(TestServicesInjector.class)
public final class ShareUploadHandlerTest {
  /**
   * Verifies that {@code POST /share/upload} produces a {@code 201 Created}
   * response for a valid results.json file.
   */
  @Test
  public void testPost(HttpTester http,
                       ShareConfig shareConfig,
                       ResultsTester resultsTester,
                       ObjectMapper objectMapper)
      throws IOException, InterruptedException {

    Results results = resultsTester.newResults();
    ByteSource resultsBytes = resultsTester.asByteSource(results);

    HttpResponse<String> response =
        http.client().send(
            HttpRequest.newBuilder(http.uri("/share/upload"))
                       .POST(asBodyPublisher(resultsBytes))
                       .header(CONTENT_TYPE, JSON_UTF_8.toString())
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(CREATED, response.statusCode());

    assertMediaType(
        JSON_UTF_8,
        response.headers()
                .firstValue(CONTENT_TYPE)
                .orElse(null));

    ShareSuccess success =
        objectMapper.readValue(
            response.body(),
            ShareSuccess.class);

    String downloadPath = "/share/download/" + success.shareId + ".json";

    assertEquals(
        shareConfig.tfbStatusOrigin + downloadPath,
        success.resultsUrl);

    assertStartsWith(
        shareConfig.tfbWebsiteOrigin + "/benchmarks/",
        success.visualizeResultsUrl);

    //
    // Confirm the new results exist on the server now.
    //

    HttpResponse<byte[]> downloadResponse =
        http.getBytes(downloadPath);

    assertEquals(OK, downloadResponse.statusCode());

    assertTrue(
        resultsBytes.contentEquals(
            ByteSource.wrap(downloadResponse.body())));
  }

  /**
   * Verifies that {@code POST /share/upload} produces a {@code 415 Unsupported
   * Media Type} response for a request that does not specify {@code
   * application/json} as its {@code Content-Type}.
   */
  @Test
  public void testPost_invalidContentType(HttpTester http,
                                          ResultsTester resultsTester)
      throws IOException, InterruptedException {

    Results results = resultsTester.newResults();
    ByteSource resultsBytes = resultsTester.asByteSource(results);

    HttpResponse<String> response =
        http.client().send(
            HttpRequest.newBuilder(http.uri("/share/upload"))
                       .POST(asBodyPublisher(resultsBytes))
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(UNSUPPORTED_MEDIA_TYPE, response.statusCode());

    assertEquals("", response.body());
  }

  /**
   * Verifies that {@code POST /share/upload} produces a {@code 400 Bad Request}
   * response for input that is not JSON.
   */
  @Test
  public void testPost_invalidJson(HttpTester http,
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

    ShareFailure failure =
        objectMapper.readValue(
            response.body(),
            ShareFailure.class);

    assertEquals(
        ShareFailure.Kind.INVALID_JSON,
        failure.kind);
  }

  /**
   * Verifies that {@code POST /share/upload} produces a {@code 400 Bad Request}
   * response for a results.json file with no {@link Results#testMetadata}.
   */
  @Test
  public void testPost_noTestMetadata(HttpTester http,
                                      ResultsTester resultsTester,
                                   ObjectMapper objectMapper)
      throws IOException, InterruptedException {

    Results template = resultsTester.newResults();

    Results results =
        new Results(
            /* uuid= */ template.uuid,
            /* name= */ template.name,
            /* environmentDescription= */ template.environmentDescription,
            /* startTime= */ template.startTime,
            /* completionTime= */ template.completionTime,
            /* duration= */ template.duration,
            /* frameworks= */ template.frameworks,
            /* completed= */ template.completed,
            /* succeeded= */ template.succeeded,
            /* failed= */ template.failed,
            /* rawData= */ template.rawData,
            /* queryIntervals= */ template.queryIntervals,
            /* concurrencyLevels= */ template.concurrencyLevels,
            /* git= */ template.git,
            /* testMetadata= */ null);

    ByteSource resultsBytes = resultsTester.asByteSource(results);

    HttpResponse<String> response =
        http.client().send(
            HttpRequest.newBuilder(http.uri("/share/upload"))
                       .POST(asBodyPublisher(resultsBytes))
                       .header(CONTENT_TYPE, JSON_UTF_8.toString())
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(BAD_REQUEST, response.statusCode());

    assertMediaType(
        JSON_UTF_8,
        response.headers()
                .firstValue(CONTENT_TYPE)
                .orElse(null));

    ShareFailure failure =
        objectMapper.readValue(
            response.body(),
            ShareFailure.class);

    assertEquals(
        ShareFailure.Kind.MISSING_TEST_METADATA,
        failure.kind);
  }

  /**
   * Verifies that {@code POST /share/upload} produces a {@code 413 Request
   * Entity Too Large} response for a results.json file that is too large.
   */
  @Test
  public void testPost_fileTooLarge(HttpTester http,
                                    ShareConfig shareConfig,
                                    ResultsTester resultsTester,
                                    ObjectMapper objectMapper)
      throws IOException, InterruptedException {

    // Start with a valid results.json file and append spaces to the end to make
    // it too large (but valid otherwise).
    Results results = resultsTester.newResults();
    ByteSource resultsBytes = resultsTester.asByteSource(results);

    // Make the uploaded file exactly too large.
    long paddingNeeded =
        shareConfig.maxFileSizeInBytes + 1 - resultsBytes.size();

    ByteSource padding =
        new ByteSource() {
          @Override
          public InputStream openStream() {
            @SuppressWarnings("InputStreamSlowMultibyteRead")
            InputStream spaces =
                new InputStream() {
                  @Override
                  public int read() {
                    return Ascii.SPACE;
                  }
                };

            return ByteStreams.limit(spaces, paddingNeeded);
          }
        };

    ByteSource paddedResultsBytes =
        ByteSource.concat(resultsBytes, padding);

    HttpResponse<String> response =
        http.client().send(
            HttpRequest.newBuilder(http.uri("/share/upload"))
                       .POST(asBodyPublisher(paddedResultsBytes))
                       .header(CONTENT_TYPE, JSON_UTF_8.toString())
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(REQUEST_ENTITY_TOO_LARGE, response.statusCode());

    assertMediaType(
        JSON_UTF_8,
        response.headers()
                .firstValue(CONTENT_TYPE)
                .orElse(null));

    ShareFailure failure =
        objectMapper.readValue(
            response.body(),
            ShareFailure.class);

    assertEquals(
        ShareFailure.Kind.FILE_TOO_LARGE,
        failure.kind);
  }

  /**
   * Verifies that {@code POST /share/upload} produces a {@code 503 Service
   * Unavailable} response when the share directory is full.
   */
  @Test
  public void testPost_shareDirectoryFull(HttpTester http,
                                          MailServer mailServer,
                                          MailDelay mailDelay,
                                          FileStore fileStore,
                                          ShareConfig shareConfig,
                                          ResultsTester resultsTester,
                                          ObjectMapper objectMapper,
                                          MutableClock clock)
      throws IOException, InterruptedException, MessagingException {

    class ShareTester {
      /**
       * Attempts to share a new set of results, and asserts that the attempt
       * ends in failure because the share directory is full.
       */
      void assertShareDirectoryFull() throws IOException, InterruptedException {
        Results results = resultsTester.newResults();
        ByteSource resultsBytes = resultsTester.asByteSource(results);

        HttpResponse<String> response =
            http.client().send(
                HttpRequest.newBuilder(http.uri("/share/upload"))
                           .POST(asBodyPublisher(resultsBytes))
                           .header(CONTENT_TYPE, JSON_UTF_8.toString())
                           .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(SERVICE_UNAVAILABLE, response.statusCode());

        assertMediaType(
            JSON_UTF_8,
            response.headers()
                    .firstValue(CONTENT_TYPE)
                    .orElse(null));

        ShareFailure failure =
            objectMapper.readValue(
                response.body(),
                ShareFailure.class);

        assertEquals(
            ShareFailure.Kind.SHARE_DIRECTORY_FULL,
            failure.kind);
      }

      /**
       * Counts the number of emails received regarding the share directory
       * being full.
       */
      int countEmails() throws IOException, MessagingException {
        String subject = ShareUploadHandler.SHARE_DIRECTORY_FULL_SUBJECT;
        return mailServer.getMessages(m -> m.getSubject().equals(subject))
                         .size();
      }
    }

    var shareTester = new ShareTester();

    assertEquals(0, shareTester.countEmails());

    // Create a new large file in the share directory so that the directory
    // exceeds its maximum configured size.
    //
    // TODO: This is unfriendly to other tests running in parallel, since it
    //       temporarily prevents the share functionality from working for
    //       anyone.
    Path junk = fileStore.shareDirectory().resolve("junk.txt");
    try {
      try (FileChannel fileChannel =
               FileChannel.open(junk, CREATE_NEW, WRITE)) {
        fileChannel.write(
            ByteBuffer.wrap(new byte[0]),
            shareConfig.maxDirectorySizeInBytes);
      }

      shareTester.assertShareDirectoryFull();
      Thread.sleep(mailDelay.timeToSendOneEmail().toMillis());
      assertEquals(1, shareTester.countEmails());

      //
      // Assert that we don't receive a second email.
      //

      shareTester.assertShareDirectoryFull();
      Thread.sleep(mailDelay.timeToSendOneEmail().toMillis());
      assertEquals(1, shareTester.countEmails());

      //
      // Assert that once the configured amount of time has passed, another
      // upload will cause another email.
      //

      // TODO: A @Singleton mutable clock is inherently unfriendly to test
      //       parallelism.  Perhaps MutableClock should be provided in the
      //       @PerLookup scope, and the "clock" field of the service being
      //       tested should be made @VisibleForTesting, and the test for that
      //       service should directly reference that field.
      clock.add(Duration.ofSeconds(shareConfig.minSecondsBetweenEmails));

      shareTester.assertShareDirectoryFull();
      Thread.sleep(mailDelay.timeToSendOneEmail().toMillis());
      assertEquals(2, shareTester.countEmails());

    } finally {
      Files.delete(junk);
    }
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
