package tfb.status.handler;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.HTML_UTF_8;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static io.undertow.util.StatusCodes.NOT_FOUND;
import static io.undertow.util.StatusCodes.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static tfb.status.testlib.MoreAssertions.assertContains;
import static tfb.status.testlib.MoreAssertions.assertHtmlDocument;
import static tfb.status.testlib.MoreAssertions.assertMediaType;

import java.io.IOException;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.testlib.HttpTester;
import tfb.status.testlib.TestServicesInjector;

/**
 * Tests for {@link UnzipResultsHandler}.
 */
@ExtendWith(TestServicesInjector.class)
public final class UnzipResultsHandlerTest {
  /**
   * Verifies that a GET request for a file within a results.zip file that
   * exists is successful.
   */
  @Test
  public void testGet(HttpTester http)
      throws IOException, InterruptedException {

    HttpResponse<byte[]> response =
        http.getBytes("/unzip/results.2019-12-16-03-22-48-407.zip/gemini/json/raw.txt");

    assertEquals(OK, response.statusCode());

    assertMediaType(
        PLAIN_TEXT_UTF_8,
        response.headers()
                .firstValue(CONTENT_TYPE)
                .orElse(null));

    byte[] responseBytes = response.body();

    assertEquals(6564L, responseBytes.length);
  }

  /**
   * Verifies that a GET request for a file within a results.zip file that
   * exists is successful, where the file is one of the TFB toolset's .log
   * files.  Verifies that the response {@code Content-Type} is {@code
   * text/plain; charset=utf-8}, since that is the correct type for the TFB
   * toolset's .log files, and since standard {@code Content-Type} detection
   * algorithms are unlikely to have any mapping for the ".log" file extension.
   */
  @Test
  public void testGet_logFile(HttpTester http)
      throws IOException, InterruptedException {

    HttpResponse<byte[]> response =
        http.getBytes("/unzip/results.2019-12-16-03-22-48-407.zip/gemini/build/gemini.log");

    assertEquals(OK, response.statusCode());

    assertMediaType(
        PLAIN_TEXT_UTF_8,
        response.headers()
                .firstValue(CONTENT_TYPE)
                .orElse(null));

    byte[] responseBytes = response.body();

    assertEquals(2554L, responseBytes.length);
  }

  /**
   * Verifies that a GET request for a directory within a results.zip file that
   * exists is successful.
   */
  @Test
  public void testGet_directory(HttpTester http)
      throws IOException, InterruptedException {

    HttpResponse<String> response =
        http.getString("/unzip/results.2019-12-16-03-22-48-407.zip/gemini/json");

    assertEquals(OK, response.statusCode());

    assertMediaType(
        HTML_UTF_8,
        response.headers()
                .firstValue(CONTENT_TYPE)
                .orElse(null));

    assertHtmlDocument(response.body());

    // This page should contain links to the files in this directory.
    assertContains("raw.txt", response.body());
  }

  /**
   * Verifies that a GET request that references a results.zip file that does
   * not exist produces a {@code 404 Not Found} response.
   */
  @Test
  public void testGet_zipFileNotFound(HttpTester http)
      throws IOException, InterruptedException {

    HttpResponse<String> response =
        http.getString("/unzip/not_a_real_zip_file.zip");

    assertEquals(NOT_FOUND, response.statusCode());

    assertEquals("", response.body());
  }

  /**
   * Verifies that a GET request for a file that does not exist within a
   * results.zip file that does exist produces a {@code 404 Not Found} response.
   */
  @Test
  public void testGet_zipEntryNotFound(HttpTester http)
      throws IOException, InterruptedException {

    HttpResponse<String> response =
        http.getString("/unzip/results.2019-12-16-03-22-48-407.zip/not_a_real_zip_entry.txt");

    assertEquals(NOT_FOUND, response.statusCode());

    assertEquals("", response.body());
  }
}
