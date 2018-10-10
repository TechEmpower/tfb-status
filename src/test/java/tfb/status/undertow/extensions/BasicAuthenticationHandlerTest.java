package tfb.status.undertow.extensions;

import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static com.google.common.net.HttpHeaders.WWW_AUTHENTICATE;
import static io.undertow.util.StatusCodes.OK;
import static io.undertow.util.StatusCodes.UNAUTHORIZED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.Principal;
import java.util.Set;
import javax.annotation.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tfb.status.bootstrap.TestServices;
import tfb.status.util.BasicAuthUtils;

/**
 * Tests for {@link BasicAuthenticationHandler}.
 */
public final class BasicAuthenticationHandlerTest {
  private static TestServices services;

  private static final String CORRECT_USERNAME = "correct_username";
  private static final String CORRECT_PASSWORD = "correct_password";

  private enum OnlyPrincipal implements Principal {
    INSTANCE;

    @Override
    public String getName() {
      return CORRECT_USERNAME;
    }
  }

  private enum OnlyAccount implements Account {
    INSTANCE;

    @Override
    public Principal getPrincipal() {
      return OnlyPrincipal.INSTANCE;
    }

    @Override
    public Set<String> getRoles() {
      return Set.of();
    }
  }

  private enum OnlyIdentityManager implements IdentityManager {
    INSTANCE;

    @Override
    @Nullable
    public Account verify(Account account) {
      return (account == OnlyAccount.INSTANCE) ? account : null;
    }

    @Override
    @Nullable
    public Account verify(String id, Credential credential) {
      if (!id.equals(CORRECT_USERNAME))
        return null;

      if (!(credential instanceof PasswordCredential))
        return null;

      char[] passwordChars = ((PasswordCredential) credential).getPassword();
      String password = new String(passwordChars);

      if (!password.equals(CORRECT_PASSWORD))
        return null;

      return OnlyAccount.INSTANCE;
    }

    @Override
    @Nullable
    public Account verify(Credential credential) {
      return null;
    }
  }

  @BeforeAll
  public static void beforeAll() {
    services = new TestServices();

    services.addExactPath(
        "/basicAuth",
        new BasicAuthenticationHandler(
            "testRealm",
            OnlyIdentityManager.INSTANCE,
            exchange -> {}));
  }

  @AfterAll
  public static void afterAll() {
    services.shutdown();
  }

  /**
   * Verifies that {@link BasicAuthenticationHandler} rejects requests that do
   * not specify any credentials.
   */
  @Test
  public void testMissingCredentials() throws IOException, InterruptedException {
    URI uri = services.httpUri("/basicAuth");

    HttpResponse<String> response =
        services.httpClient().send(
            HttpRequest.newBuilder(uri)
                       .GET()
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(UNAUTHORIZED, response.statusCode());

    assertEquals(
        "Basic realm=\"testRealm\"",
        response.headers()
                .firstValue(WWW_AUTHENTICATE)
                .orElse(null));
  }

  /**
   * Verifies that {@link BasicAuthenticationHandler} rejects requests that
   * specify invalid credentials.
   */
  @Test
  public void testInvalidCredentials() throws IOException, InterruptedException {
    URI uri = services.httpUri("/basicAuth");

    String invalidCredentials =
        BasicAuthUtils.writeAuthorizationHeader(
            "wrong_username",
            "wrong_password");

    HttpResponse<String> response =
        services.httpClient().send(
            HttpRequest.newBuilder(uri)
                       .header(AUTHORIZATION, invalidCredentials)
                       .GET()
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(UNAUTHORIZED, response.statusCode());

    assertEquals(
        "Basic realm=\"testRealm\"",
        response.headers()
                .firstValue(WWW_AUTHENTICATE)
                .orElse(null));
  }

  /**
   * Verifies that {@link BasicAuthenticationHandler} accepts requests that
   * specify valid credentials.
   */
  @Test
  public void testValidCredentials() throws IOException, InterruptedException {
    URI uri = services.httpUri("/basicAuth");

    String validCredentials =
        BasicAuthUtils.writeAuthorizationHeader(
            CORRECT_USERNAME,
            CORRECT_PASSWORD);

    HttpResponse<String> response =
        services.httpClient().send(
            HttpRequest.newBuilder(uri)
                       .header(AUTHORIZATION, validCredentials)
                       .GET()
                       .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(OK, response.statusCode());

    assertTrue(response.headers()
                       .firstValue(WWW_AUTHENTICATE)
                       .isEmpty());
  }
}
