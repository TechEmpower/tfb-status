package tfb.status.undertow.extensions;

import static com.google.common.net.HttpHeaders.ALLOW;
import static io.undertow.util.Methods.GET;
import static io.undertow.util.Methods.OPTIONS;
import static io.undertow.util.Methods.POST;
import static io.undertow.util.StatusCodes.METHOD_NOT_ALLOWED;
import static io.undertow.util.StatusCodes.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.common.base.Splitter;
import java.util.List;
import java.util.Set;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tfb.status.bootstrap.TestServices;

/**
 * Tests for {@link MethodHandler}.
 */
public final class MethodHandlerTest {
  private static TestServices services;
  private static Client httpClient;

  @BeforeAll
  public static void beforeAll() {
    services = new TestServices();
    httpClient = services.httpClient();

    services.addExactPath(
        "/none",
        new MethodHandler());

    services.addExactPath(
        "/getOnly",
        new MethodHandler()
            .addMethod(GET, new FixedResponseBodyHandler("getHandler")));

    services.addExactPath(
        "/postOnly",
        new MethodHandler()
            .addMethod(POST, new FixedResponseBodyHandler("postHandler")));

    services.addExactPath(
        "/getAndPost",
        new MethodHandler()
            .addMethod(GET, new FixedResponseBodyHandler("getHandler"))
            .addMethod(POST, new FixedResponseBodyHandler("postHandler")));

    services.addExactPath(
        "/overrideOptions",
        new MethodHandler()
            .addMethod(OPTIONS, new FixedResponseBodyHandler("optionsHandler")));
  }

  @AfterAll
  public static void afterAll() {
    services.shutdown();
  }

  /**
   * Verifies that a {@link MethodHandler} with no handlers added only allows
   * OPTIONS requests.
   */
  @Test
  public void testNoMethodsAllowed() {
    String uri = services.httpUri("/none");

    try (Response response = httpClient.target(uri).request().get()) {
      assertEquals(METHOD_NOT_ALLOWED, response.getStatus());
    }

    try (Response response = httpClient.target(uri).request().post(Entity.text("hi"))) {
      assertEquals(METHOD_NOT_ALLOWED, response.getStatus());
    }

    try (Response response = httpClient.target(uri).request().head()) {
      assertEquals(METHOD_NOT_ALLOWED, response.getStatus());
    }

    try (Response response = httpClient.target(uri).request().options()) {
      assertEquals(OK, response.getStatus());
      assertEquals("", response.readEntity(String.class));
      assertEquals(
          Set.of("OPTIONS"),
          parseAllowHeader(response));
    }
  }

  /**
   * Verifies that a {@link MethodHandler} with only a GET handler added only
   * allows GET, HEAD, and OPTIONS requests.
   */
  @Test
  public void testGetOnly() {
    String uri = services.httpUri("/getOnly");

    try (Response response = httpClient.target(uri).request().get()) {
      assertEquals(OK, response.getStatus());
      assertEquals("getHandler", response.readEntity(String.class));
    }

    try (Response response = httpClient.target(uri).request().post(Entity.text("hi"))) {
      assertEquals(METHOD_NOT_ALLOWED, response.getStatus());
    }

    try (Response response = httpClient.target(uri).request().head()) {
      assertEquals(OK, response.getStatus());
      assertEquals("", response.readEntity(String.class));
    }

    try (Response response = httpClient.target(uri).request().options()) {
      assertEquals(OK, response.getStatus());
      assertEquals("", response.readEntity(String.class));
      assertEquals(
          Set.of("GET", "HEAD", "OPTIONS"),
          parseAllowHeader(response));
    }
  }

  /**
   * Verifies that a {@link MethodHandler} with only a POST handler added only
   * allows POST and OPTIONS requests.
   */
  @Test
  public void testPostOnly() {
    String uri = services.httpUri("/postOnly");

    try (Response response = httpClient.target(uri).request().get()) {
      assertEquals(METHOD_NOT_ALLOWED, response.getStatus());
    }

    try (Response response = httpClient.target(uri).request().post(Entity.text("hi"))) {
      assertEquals(OK, response.getStatus());
      assertEquals("postHandler", response.readEntity(String.class));
    }

    try (Response response = httpClient.target(uri).request().head()) {
      assertEquals(METHOD_NOT_ALLOWED, response.getStatus());
    }

    try (Response response = httpClient.target(uri).request().options()) {
      assertEquals(OK, response.getStatus());
      assertEquals("", response.readEntity(String.class));
      assertEquals(
          Set.of("POST", "OPTIONS"),
          parseAllowHeader(response));
    }
  }

  /**
   * Verifies that a {@link MethodHandler} with GET and POST handlers added
   * allows GET, POST, HEAD, and OPTIONS requests.
   */
  @Test
  public void testGetAndPost() {
    String uri = services.httpUri("/getAndPost");

    try (Response response = httpClient.target(uri).request().get()) {
      assertEquals(OK, response.getStatus());
      assertEquals("getHandler", response.readEntity(String.class));
    }

    try (Response response = httpClient.target(uri).request().post(Entity.text("hi"))) {
      assertEquals(OK, response.getStatus());
      assertEquals("postHandler", response.readEntity(String.class));
    }

    try (Response response = httpClient.target(uri).request().head()) {
      assertEquals(OK, response.getStatus());
      assertEquals("", response.readEntity(String.class));
    }

    try (Response response = httpClient.target(uri).request().options()) {
      assertEquals(OK, response.getStatus());
      assertEquals("", response.readEntity(String.class));
      assertEquals(
          Set.of("GET", "POST", "HEAD", "OPTIONS"),
          parseAllowHeader(response));
    }
  }

  /**
   * Verifies that it is possible to override the built-in OPTIONS handler of a
   * {@link MethodHandler}.
   */
  @Test
  public void testOverrideOptions() {
    String uri = services.httpUri("/overrideOptions");

    try (Response response = httpClient.target(uri).request().options()) {
      assertEquals(OK, response.getStatus());
      assertEquals("optionsHandler", response.readEntity(String.class));
      assertNull(response.getHeaderString(ALLOW));
    }
  }

  private static Set<String> parseAllowHeader(Response response) {
    String headerValue = response.getHeaderString(ALLOW);

    if (headerValue == null)
      return Set.of();

    List<String> list = Splitter.on(',')
                                .trimResults()
                                .splitToList(headerValue);

    Set<String> set = Set.copyOf(list);

    if (list.size() != set.size())
      throw new AssertionError(
          "Allow header contained duplicate values: " + headerValue);

    return set;
  }
}
