package tfb.status.handler;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.undertow.util.StatusCodes.BAD_REQUEST;
import static io.undertow.util.StatusCodes.CREATED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static tfb.status.testlib.MoreAssertions.assertMediaType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import com.google.common.net.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.Channels;
import java.nio.channels.Pipe;
import java.util.Objects;
import java.util.Random;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.service.ShareManager;
import tfb.status.service.TaskScheduler;
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

  /**
   * Verifies that {@code POST /share/upload} produces a {@code 201 Created}
   * response for a valid results.json file included as {@code
   * multipart/form-data}.
   */
  @Test
  public void testPostForm(HttpTester http,
                           ResultsTester resultsTester,
                           ObjectMapper objectMapper,
                           TaskScheduler taskScheduler)
      throws IOException, InterruptedException {

    Results results = resultsTester.newResults();
    ByteSource resultsBytes = resultsTester.asByteSource(results);

    HttpResponse<String> response =
        postForm(resultsBytes, http, taskScheduler);

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
   * Verifies that {@code POST /share/upload} produces a {@code 201 Created}
   * response for a valid results.json file included as {@code
   * multipart/form-data}.
   */
  @Test
  public void testPostForm_invalidFile(HttpTester http,
                                       ObjectMapper objectMapper,
                                       TaskScheduler taskScheduler)
      throws IOException, InterruptedException {

    ByteSource invalidJsonBytes =
        CharSource.wrap("not json").asByteSource(UTF_8);

    HttpResponse<String> response =
        postForm(invalidJsonBytes, http, taskScheduler);

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

  /**
   * Issues a POST request to {@code /share/upload} containing the specified
   * results.json file as {@code multipart/form-data}.
   *
   * @param resultsBytes the bytes of the results.json file
   */
  private static HttpResponse<String> postForm(ByteSource resultsBytes,
                                               HttpTester http,
                                               TaskScheduler taskScheduler)
      throws IOException, InterruptedException {

    Objects.requireNonNull(resultsBytes);
    Objects.requireNonNull(http);
    Objects.requireNonNull(taskScheduler);

    URI uri = http.uri("/share/upload");

    String boundary = new BigInteger(256, new Random()).toString();

    String contentType =
        MediaType.create("multipart", "form-data")
                 .withParameter("boundary", boundary)
                 .toString();

    Supplier<InputStream> bodySupplier =
        () -> {
          Pipe pipe;
          try {
            pipe = Pipe.open();
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }

          taskScheduler.submit(
              () -> {
                try (OutputStream outputStream =
                         Channels.newOutputStream(pipe.sink())) {

                  String header =
                      "--"
                          + boundary
                          + "\r\n"
                          + "Content-Disposition: form-data; "
                          + "name=\"results\"; filename=\"results.json\""
                          + "\r\n"
                          + "Content-Type: application/json"
                          + "\r\n"
                          + "\r\n";

                  outputStream.write(header.getBytes(UTF_8));
                  resultsBytes.copyTo(outputStream);
                  outputStream.write("\r\n".getBytes(UTF_8));

                  outputStream.write(
                      ("--" + boundary + "--").getBytes(UTF_8));

                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              });

          return Channels.newInputStream(pipe.source());
        };

    return http.client().send(
        HttpRequest.newBuilder(uri)
                   .header(CONTENT_TYPE, contentType)
                   .POST(HttpRequest.BodyPublishers.ofInputStream(bodySupplier))
                   .build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
