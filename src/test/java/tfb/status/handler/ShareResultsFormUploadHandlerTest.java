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
import tfb.status.service.TaskScheduler;
import tfb.status.testlib.HttpTester;
import tfb.status.testlib.ResultsTester;
import tfb.status.testlib.TestServicesInjector;
import tfb.status.view.Results;
import tfb.status.view.ShareResultsErrorJsonView;
import tfb.status.view.ShareResultsJsonView;

/**
 * Tests for {@link ShareResultsFormUploadHandler}.
 */
@ExtendWith(TestServicesInjector.class)
public final class ShareResultsFormUploadHandlerTest {
  /**
   * Verifies that {@code POST /share-results/upload/form} produces a {@code 201
   * Created} response for a valid results.json file.
   */
  @Test
  public void testPost(HttpTester http,
                       ResultsTester resultsTester,
                       ObjectMapper objectMapper,
                       TaskScheduler taskScheduler)
      throws IOException, InterruptedException {

    Results results = resultsTester.newResults();
    ByteSource resultsBytes = resultsTester.asByteSource(results);

    List<MultipartFormDataFile> files =
        List.of(
            new MultipartFormDataFile(
                /* elementName= */ "results",
                /* fileName= */ "results.json",
                /* mediaType= */ JSON_UTF_8,
                /* fileBytes= */ resultsBytes));

    HttpResponse<String> response =
        http.client().send(
            postFiles(
                http.uri("/share-results/upload/form"),
                files,
                taskScheduler),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(CREATED, response.statusCode());

    assertMediaType(
        JSON_UTF_8,
        response.headers().firstValue(CONTENT_TYPE).orElse(null));

    ShareResultsJsonView responseView =
        objectMapper.readValue(
            response.body(),
            ShareResultsJsonView.class);

    assertNotNull(responseView);
  }

  /**
   * Verifies that {@code POST /share-results/upload/form} produces a {@code 201
   * Created} response for a valid results.json file.
   */
  @Test
  public void testPost_invalidFile(HttpTester http,
                                   ObjectMapper objectMapper,
                                   TaskScheduler taskScheduler)
      throws IOException, InterruptedException {

    ByteSource invalidJsonBytes = CharSource.wrap("foo").asByteSource(UTF_8);

    List<MultipartFormDataFile> files =
        List.of(
            new MultipartFormDataFile(
                /* elementName= */ "results",
                /* fileName= */ "results.json",
                /* mediaType= */ JSON_UTF_8,
                /* fileBytes= */ invalidJsonBytes));

    HttpResponse<String> response =
        http.client().send(
            postFiles(
                http.uri("/share-results/upload/form"),
                files,
                taskScheduler),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(BAD_REQUEST, response.statusCode());

    assertMediaType(
        JSON_UTF_8,
        response.headers().firstValue(CONTENT_TYPE).orElse(null));

    ShareResultsErrorJsonView responseView =
        objectMapper.readValue(
            response.body(),
            ShareResultsErrorJsonView.class);

    assertNotNull(responseView);
  }

  /**
   * Constructs a POST request containing the specified files as {@code
   * multipart/form-data}.
   */
  private static HttpRequest postFiles(URI uri,
                                       List<MultipartFormDataFile> files,
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

                  for (MultipartFormDataFile file : files) {
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
  private static final class MultipartFormDataFile {
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

    MultipartFormDataFile(String elementName,
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
