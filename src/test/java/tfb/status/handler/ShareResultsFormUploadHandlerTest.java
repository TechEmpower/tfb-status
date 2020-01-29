package tfb.status.handler;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.undertow.util.StatusCodes.BAD_REQUEST;
import static io.undertow.util.StatusCodes.CREATED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
public class ShareResultsFormUploadHandlerTest {
  /**
   * Ensure that given a valid results json file, the handler successfully
   * uploads and responds as expected with a 201. This also verifies that the
   * response body has JSON that de-serialized to a
   * {@link ShareResultsJsonView}.
   */
  @Test
  public void shareResultsFormUploadHandler_uploadValid(
      HttpTester http,
      ResultsTester resultsTester,
      ObjectMapper objectMapper,
      FileSystem fileSystem) throws IOException, InterruptedException {

    Results results = resultsTester.newResults();

    Path jsonFile = fileSystem.getPath("results_to_upload.json");
    resultsTester.saveJsonToFile(results, jsonFile);

    List<MultipartFormFileEntry> files =
        ImmutableList.of(
            new MultipartFormFileEntry(
                /* name= */ "results",
                /* path= */ jsonFile,
                /* fileName= */ jsonFile.getFileName().toString(),
                /* mimeType= */ "application/json"));

    HttpRequest.Builder builder = HttpRequest.newBuilder(
        http.uri("/share-results/upload/form"));

    HttpRequest request = addPostMultipartFormData(builder, files).build();

    HttpResponse<String> response = http.client().send(
        request, HttpResponse.BodyHandlers.ofString());

    assertEquals(CREATED, response.statusCode());
    assertEquals(
        JSON_UTF_8.toString(),
        response.headers().firstValue(CONTENT_TYPE).orElse(null));

    ShareResultsJsonView responseView = objectMapper.readValue(
        response.body(),
        ShareResultsJsonView.class);

    assertNotNull(responseView);
  }

  /**
   * Ensure that given an invalid results json file, the handler responds as
   * expected with a 400. This also verifies that the response body has JSON
   * that de-serialized to a {@link ShareResultsErrorJsonView}.
   */
  @Test
  public void shareResultsFormUploadHandler_rejectUploadInvalid(
      HttpTester http,
      ObjectMapper objectMapper,
      FileSystem fileSystem) throws IOException, InterruptedException {

    Path jsonFile = fileSystem.getPath("invalid_results.json");
    Files.writeString(jsonFile, "invalid json", CREATE_NEW);

    List<MultipartFormFileEntry> files =
        ImmutableList.of(
            new MultipartFormFileEntry(
                /* name= */ "results",
                /* path= */ jsonFile,
                /* fileName= */ jsonFile.getFileName().toString(),
                /* mimeType= */ "application/json"));

    HttpRequest.Builder builder = HttpRequest.newBuilder(
        http.uri("/share-results/upload/form"));

    HttpRequest request = addPostMultipartFormData(builder, files).build();

    HttpResponse<String> response = http.client().send(
        request, HttpResponse.BodyHandlers.ofString());

    assertEquals(BAD_REQUEST, response.statusCode());
    assertEquals(
        JSON_UTF_8.toString(),
        response.headers().firstValue(CONTENT_TYPE).orElse(null));

    ShareResultsErrorJsonView responseView = objectMapper.readValue(
        response.body(),
        ShareResultsErrorJsonView.class);

    assertNotNull(responseView);
  }

  /**
   * Add multipart form data to a http request. Generates a new boundary,
   * specifies the Content-Type header, and includes POST data by constructing
   * a new body publisher containing all form data.
   *
   * @param request The builder to add to.
   * @param files A list of files to include in the form data.
   */
  private static HttpRequest.Builder addPostMultipartFormData(
      HttpRequest.Builder request,
      List<MultipartFormFileEntry> files) throws IOException {

    String boundary = new BigInteger(256, new Random()).toString();

    return request
        .header(CONTENT_TYPE, "multipart/form-data;boundary=" + boundary)
        .POST(multipartFormDataPublisher(boundary, files));
  }

  /**
   * Construct a new body publisher that includes all specified data as form
   * data.
   */
  private static HttpRequest.BodyPublisher multipartFormDataPublisher(
      String boundary,
      List<MultipartFormFileEntry> files) throws IOException {

    List<byte[]> byteArrays = new ArrayList<>();

    byte[] separator = concatUtf8Bytes(
        "--", boundary,
        "\r\n",
        "Content-Disposition: form-data; name=");

    for (MultipartFormFileEntry file : files) {
      byteArrays.add(separator);

      byteArrays.add(
          concatUtf8Bytes(
              "\"", file.name, "\";",
              "filename=\"", file.fileName, "\"",
              "\r\n",
              "Content-Type: ", file.mimeType,
              "\r\n\r\n"));

      byteArrays.add(Files.readAllBytes(file.path));

      byteArrays.add("\r\n".getBytes(UTF_8));
    }

    byteArrays.add(concatUtf8Bytes("--", boundary, "--"));

    return HttpRequest.BodyPublishers.ofByteArrays(byteArrays);
  }

  /**
   * Create a byte array containing all specified parts' bytes (UTF_8)
   * concatenated together without a separator.
   */
  private static byte[] concatUtf8Bytes(String... parts) throws IOException {
    Objects.requireNonNull(parts);

    ByteArrayOutputStream os = new ByteArrayOutputStream();

    for (String part : parts) {
      Objects.requireNonNull(part);
      os.write(part.getBytes(UTF_8));
    }

    return os.toByteArray();
  }

  @Immutable
  private static final class MultipartFormFileEntry {
    final String name;
    final Path path;
    final String fileName;
    final String mimeType;

    MultipartFormFileEntry(String name,
                                  Path path,
                                  String fileName,
                                  String mimeType) {
      this.name = Objects.requireNonNull(name);
      this.path = Objects.requireNonNull(path);
      this.fileName = Objects.requireNonNull(fileName);
      this.mimeType = Objects.requireNonNull(mimeType);
    }
  }
}
