package tfb.status.undertow.extensions;

import static io.undertow.util.StatusCodes.OK;
import static io.undertow.util.StatusCodes.UNSUPPORTED_MEDIA_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.net.MediaType;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tfb.status.bootstrap.TestServices;

/**
 * Tests for {@link MediaTypeHandler}.
 */
public final class MediaTypeHandlerTest {
  private static TestServices services;
  private static Client httpClient;

  @BeforeAll
  public static void beforeAll() {
    services = new TestServices();
    httpClient = services.httpClient();

    services.addExactPath(
        "/none",
        new MediaTypeHandler());

    services.addExactPath(
        "/plaintextOrJson",
        new MediaTypeHandler()
            .addMediaType(
                "text/plain",
                new FixedResponseBodyHandler("plaintextHandler"))
            .addMediaType(
                "application/json",
                new FixedResponseBodyHandler("jsonHandler")));

    services.addExactPath(
        "/text",
        new MediaTypeHandler()
            .addMediaType(
                "text/plain;charset=utf-8",
                new FixedResponseBodyHandler("utf8Handler"))
            .addMediaType(
                "text/plain",
                new FixedResponseBodyHandler("plainHandler"))
            .addMediaType(
                "text/*",
                new FixedResponseBodyHandler("otherHandler")));

    services.addExactPath(
        "/wildcard",
        new MediaTypeHandler().addMediaType(
            "*/*",
            new FixedResponseBodyHandler("wildcardHandler")));
  }

  @AfterAll
  public static void afterAll() {
    services.shutdown();
  }

  /**
   * Verifies that a {@link MediaTypeHandler} with no handlers added rejects all
   * requests.
   */
  @Test
  public void testNoMediaTypesAllowed() {
    String uri = services.httpUri("/none");

    try (Response response =
             httpClient.target(uri)
                       .request()
                       .post(Entity.entity("hi", "text/plain"))) {

      assertEquals(UNSUPPORTED_MEDIA_TYPE, response.getStatus());
    }

    try (Response response = httpClient.target(uri).request().get()) {
      assertEquals(UNSUPPORTED_MEDIA_TYPE, response.getStatus());
    }
  }

  /**
   * Verifies that a {@link MediaTypeHandler} routes requests to the correct
   * handler when the media types for each handler are unrelated.
   */
  @Test
  public void testUnrelatedMediaTypes() {
    String uri = services.httpUri("/plaintextOrJson");

    try (Response response =
             httpClient.target(uri)
                       .request()
                       .post(Entity.entity("hi", "text/plain"))) {

      assertEquals(OK, response.getStatus());
      assertEquals("plaintextHandler", response.readEntity(String.class));
    }

    try (Response response =
             httpClient.target(uri)
                       .request()
                       .post(Entity.entity("hi", "application/json"))) {

      assertEquals(OK, response.getStatus());
      assertEquals("jsonHandler", response.readEntity(String.class));
    }

    try (Response response =
             httpClient.target(uri)
                       .request()
                       .post(Entity.entity("hi", "foo/bar"))) {

      assertEquals(UNSUPPORTED_MEDIA_TYPE, response.getStatus());
    }

    try (Response response = httpClient.target(uri).request().get()) {
      assertEquals(UNSUPPORTED_MEDIA_TYPE, response.getStatus());
    }
  }

  /**
   * Verifies that a {@link MediaTypeHandler} routes requests to the most
   * specific matching handler when the media types for each handler are related
   * and overlapping.
   */
  @Test
  public void testMostSpecificMediaType() {
    String uri = services.httpUri("/text");

    try (Response response =
             httpClient.target(uri)
                       .request()
                       .post(Entity.entity("hi", "text/plain;charset=utf-8"))) {

      assertEquals(OK, response.getStatus());
      assertEquals("utf8Handler", response.readEntity(String.class));
    }

    try (Response response =
             httpClient.target(uri)
                       .request()
                       .post(Entity.entity("hi", "text/plain;charset=us-ascii"))) {

      assertEquals(OK, response.getStatus());
      assertEquals("plainHandler", response.readEntity(String.class));
    }

    try (Response response =
             httpClient.target(uri)
                       .request()
                       .post(Entity.entity("hi", "text/plain"))) {

      assertEquals(OK, response.getStatus());
      assertEquals("plainHandler", response.readEntity(String.class));
    }

    try (Response response =
             httpClient.target(uri)
                       .request()
                       .post(Entity.entity("hi", "text/css"))) {

      assertEquals(OK, response.getStatus());
      assertEquals("otherHandler", response.readEntity(String.class));
    }

    try (Response response =
             httpClient.target(uri)
                       .request()
                       .post(Entity.entity("hi", "foo/bar"))) {

      assertEquals(UNSUPPORTED_MEDIA_TYPE, response.getStatus());
    }

    try (Response response = httpClient.target(uri).request().get()) {
      assertEquals(UNSUPPORTED_MEDIA_TYPE, response.getStatus());
    }
  }

  /**
   * Verifies that a {@link MediaTypeHandler} throws an exception when the
   * caller attempts to add a handler whose media type is completely encompassed
   * by a previously-added handler's media type.
   */
  @Test
  public void testUnusableHandlerRejected() {
    MediaTypeHandler handler = new MediaTypeHandler();
    handler.addMediaType("text/*", exchange -> {});

    assertThrows(
        IllegalStateException.class,
        () -> handler.addMediaType("text/plain", exchange -> {}));
  }

  /**
   * Verifies that a {@link MediaTypeHandler} with a handler for {@link
   * MediaType#ANY_TYPE} allows requests having any {@code Content-Type},
   * including requests with no {@code Content-Type} header at all.
   */
  @Test
  public void testAnyMediaType() {
    String uri = services.httpUri("/wildcard");

    try (Response response =
             httpClient.target(uri)
                       .request()
                       .post(Entity.entity("hi", "foo/bar"))) {

      assertEquals(OK, response.getStatus());
      assertEquals("wildcardHandler", response.readEntity(String.class));
    }

    try (Response response = httpClient.target(uri).request().get()) {
      assertEquals(OK, response.getStatus());
      assertEquals("wildcardHandler", response.readEntity(String.class));
    }
  }
}
