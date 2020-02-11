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
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.Executor;
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

    HttpRequest request =
        HttpRequest.newBuilder(http.uri("/share/upload"))
                   .POST(resultsTester.asBodyPublisher(results))
                   .header(CONTENT_TYPE, JSON_UTF_8.toString())
                   .build();

    HttpResponse<String> response =
        http.client().send(
            request,
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

    HttpRequest request =
        HttpRequest.newBuilder(http.uri("/share/upload"))
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

    List<FileElement> files =
        List.of(
            new FileElement(
                /* elementName= */ "results",
                /* fileName= */ "results.json",
                /* mediaType= */ JSON_UTF_8,
                /* fileBytes= */ resultsBytes));

    HttpResponse<String> response =
        http.client().send(
            postFiles(
                http.uri("/share/upload"),
                files,
                taskScheduler),
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
   * Verifies that {@code POST /share/upload} produces a {@code 201 Created}
   * response for a valid results.json file included as {@code
   * multipart/form-data}.
   */
  @Test
  public void testPostForm_invalidFile(HttpTester http,
                                       ObjectMapper objectMapper,
                                       TaskScheduler taskScheduler)
      throws IOException, InterruptedException {

    ByteSource invalidJsonBytes = CharSource.wrap("foo").asByteSource(UTF_8);

    List<FileElement> files =
        List.of(
            new FileElement(
                /* elementName= */ "results",
                /* fileName= */ "results.json",
                /* mediaType= */ JSON_UTF_8,
                /* fileBytes= */ invalidJsonBytes));

    HttpResponse<String> response =
        http.client().send(
            postFiles(
                http.uri("/share/upload"),
                files,
                taskScheduler),
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
   * Constructs a POST request containing the specified files as {@code
   * multipart/form-data}.
   */
  private static HttpRequest postFiles(URI uri,
                                       List<FileElement> files,
                                       Executor executor) {

    Objects.requireNonNull(uri);
    Objects.requireNonNull(files);
    Objects.requireNonNull(executor);

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

          executor.execute(
              () -> {
                try (OutputStream outputStream =
                         Channels.newOutputStream(pipe.sink())) {

                  for (FileElement file : files) {
                    String header =
                        "--"
                            + boundary
                            + "\r\n"
                            + "Content-Disposition: form-data; name=\""
                            + file.elementName
                            + "\"; "
                            + "filename=\""
                            + file.fileName
                            + "\""
                            + "\r\n"
                            + "Content-Type: "
                            + file.mediaType
                            + "\r\n"
                            + "\r\n";

                    outputStream.write(header.getBytes(UTF_8));
                    file.fileBytes.copyTo(outputStream);
                    outputStream.write("\r\n".getBytes(UTF_8));
                  }

                  outputStream.write(
                      ("--" + boundary + "--").getBytes(UTF_8));

                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              });

          return Channels.newInputStream(pipe.source());
        };

    return HttpRequest.newBuilder(uri)
                      .header(CONTENT_TYPE, contentType)
                      .POST(HttpRequest.BodyPublishers.ofInputStream(bodySupplier))
                      .build();
  }

  /**
   * An {@code <input type="file">} element attached to a {@code
   * multipart/form-data} form submission.
   */
  private static final class FileElement {
    /**
     * The name of the {@code <input type="file">} element.
     */
    final String elementName;

    /**
     * The {@linkplain Path#getFileName() name} of the attached file.
     */
    final String fileName;

    /**
     * The media type of the attached file.
     */
    final MediaType mediaType;

    /**
     * The bytes of the attached file.
     */
    final ByteSource fileBytes;

    FileElement(String elementName,
                String fileName,
                MediaType mediaType,
                ByteSource fileBytes) {

      this.elementName = Objects.requireNonNull(elementName);
      this.fileName = Objects.requireNonNull(fileName);
      this.mediaType = Objects.requireNonNull(mediaType);
      this.fileBytes = Objects.requireNonNull(fileBytes);
    }
  }
}
