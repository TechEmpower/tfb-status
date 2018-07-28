package tfb.status.handler;

import static io.undertow.util.StatusCodes.NOT_FOUND;
import static io.undertow.util.StatusCodes.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.undertow.server.HttpHandler;
import javax.ws.rs.core.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tfb.status.bootstrap.TestServices;
import tfb.status.undertow.extensions.FixedResponseBodyHandler;

/**
 * Tests for {@link RootHandler}.
 */
public final class RootHandlerTest {
  private static TestServices services;
  private static RootHandler rootHandler;

  @BeforeAll
  public static void beforeAll() {
    services = new TestServices();
    rootHandler = services.serviceLocator().getService(RootHandler.class);
  }

  @AfterAll
  public static void afterAll() {
    services.shutdown();
  }

  /**
   * Verifies that {@link RootHandler#addExactPath(String, HttpHandler)} works
   * with paths that have a single part.
   */
  @Test
  public void testAddExactPath_singlePart() {
    rootHandler.addExactPath(
        "/exact",
        new FixedResponseBodyHandler("exactSingle"));

    try (Response response = services.httpGet("/exact")) {
      assertEquals(OK, response.getStatus());
      assertEquals("exactSingle", response.readEntity(String.class));
    }

    try (Response response = services.httpGet("/exact/sub/path")) {
      assertEquals(NOT_FOUND, response.getStatus());
    }
  }

  /**
   * Verifies that {@link RootHandler#addExactPath(String, HttpHandler)} works
   * with paths that have multiple parts.
   */
  @Test
  public void testAddExactPath_multiPart() {
    rootHandler.addExactPath(
        "/complex/exact/path",
        new FixedResponseBodyHandler("exactMulti"));

    try (Response response = services.httpGet("/complex/exact/path")) {
      assertEquals(OK, response.getStatus());
      assertEquals("exactMulti", response.readEntity(String.class));
    }

    try (Response response = services.httpGet("/complex/exact/path/sub/path")) {
      assertEquals(NOT_FOUND, response.getStatus());
    }
  }

  /**
   * Verifies that {@link RootHandler#addPrefixPath(String, HttpHandler)} works
   * with paths that have a single part.
   */
  @Test
  public void testAddPrefixPath_singlePart() {
    rootHandler.addPrefixPath(
        "/prefix",
        new FixedResponseBodyHandler("prefixSingle"));

    try (Response response = services.httpGet("/prefix")) {
      assertEquals(OK, response.getStatus());
      assertEquals("prefixSingle", response.readEntity(String.class));
    }

    try (Response response = services.httpGet("/prefix/sub/path")) {
      assertEquals(OK, response.getStatus());
      assertEquals("prefixSingle", response.readEntity(String.class));
    }
  }

  /**
   * Verifies that {@link RootHandler#addPrefixPath(String, HttpHandler)} works
   * with paths that have multiple parts.
   */
  @Test
  public void testAddPrefixPath_multiPart() {
    rootHandler.addPrefixPath(
        "/complex/prefix/path",
        new FixedResponseBodyHandler("prefixMulti"));

    try (Response response = services.httpGet("/complex/prefix/path")) {
      assertEquals(OK, response.getStatus());
      assertEquals("prefixMulti", response.readEntity(String.class));
    }

    try (Response response = services.httpGet("/complex/prefix/path/sub/path")) {
      assertEquals(OK, response.getStatus());
      assertEquals("prefixMulti", response.readEntity(String.class));
    }
  }
}
