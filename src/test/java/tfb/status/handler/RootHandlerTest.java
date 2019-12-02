package tfb.status.handler;

import static io.undertow.util.StatusCodes.NOT_FOUND;
import static io.undertow.util.StatusCodes.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.undertow.server.HttpHandler;
import java.io.IOException;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.testlib.TestServices;
import tfb.status.testlib.TestServicesInjector;
import tfb.status.undertow.extensions.FixedResponseBodyHandler;

/**
 * Tests for {@link RootHandler}.
 */
@ExtendWith(TestServicesInjector.class)
public final class RootHandlerTest {
  /**
   * Verifies that {@link RootHandler#addExactPath(String, HttpHandler)} works
   * with paths that have a single part.
   */
  @Test
  public void testAddExactPath_singlePart(TestServices services,
                                          RootHandler rootHandler)
      throws IOException, InterruptedException {

    rootHandler.addExactPath(
        "/exact",
        new FixedResponseBodyHandler("exactSingle"));

    HttpResponse<String> response1 =
        services.httpGetString("/exact");

    assertEquals(OK, response1.statusCode());
    assertEquals("exactSingle", response1.body());

    HttpResponse<String> response2 =
        services.httpGetString("/exact/sub/path");

    assertEquals(NOT_FOUND, response2.statusCode());
  }

  /**
   * Verifies that {@link RootHandler#addExactPath(String, HttpHandler)} works
   * with paths that have multiple parts.
   */
  @Test
  public void testAddExactPath_multiPart(TestServices services,
                                         RootHandler rootHandler)
      throws IOException, InterruptedException {

    rootHandler.addExactPath(
        "/complex/exact/path",
        new FixedResponseBodyHandler("exactMulti"));

    HttpResponse<String> response1 =
        services.httpGetString("/complex/exact/path");

    assertEquals(OK, response1.statusCode());
    assertEquals("exactMulti", response1.body());

    HttpResponse<String> response2 =
        services.httpGetString("/complex/exact/path/sub/path");

    assertEquals(NOT_FOUND, response2.statusCode());
  }

  /**
   * Verifies that {@link RootHandler#addPrefixPath(String, HttpHandler)} works
   * with paths that have a single part.
   */
  @Test
  public void testAddPrefixPath_singlePart(TestServices services,
                                           RootHandler rootHandler)
      throws IOException, InterruptedException {

    rootHandler.addPrefixPath(
        "/prefix",
        new FixedResponseBodyHandler("prefixSingle"));

    HttpResponse<String> response1 =
        services.httpGetString("/prefix");

    assertEquals(OK, response1.statusCode());
    assertEquals("prefixSingle", response1.body());

    HttpResponse<String> response2 =
        services.httpGetString("/prefix/sub/path");

    assertEquals(OK, response2.statusCode());
    assertEquals("prefixSingle", response2.body());
  }

  /**
   * Verifies that {@link RootHandler#addPrefixPath(String, HttpHandler)} works
   * with paths that have multiple parts.
   */
  @Test
  public void testAddPrefixPath_multiPart(TestServices services,
                                          RootHandler rootHandler)
      throws IOException, InterruptedException {

    rootHandler.addPrefixPath(
        "/complex/prefix/path",
        new FixedResponseBodyHandler("prefixMulti"));

    HttpResponse<String> response1 =
        services.httpGetString("/complex/prefix/path");

    assertEquals(OK, response1.statusCode());
    assertEquals("prefixMulti", response1.body());

    HttpResponse<String> response2 =
        services.httpGetString("/complex/prefix/path/sub/path");

    assertEquals(OK, response2.statusCode());
    assertEquals("prefixMulti", response2.body());
  }
}
