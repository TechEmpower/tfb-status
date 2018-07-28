package tfb.status.undertow.extensions;

import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static com.google.common.net.HttpHeaders.WWW_AUTHENTICATE;
import static io.undertow.util.StatusCodes.OK;
import static io.undertow.util.StatusCodes.UNAUTHORIZED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;
import java.security.Principal;
import java.util.Set;
import javax.annotation.Nullable;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;
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
  private static Client httpClient;

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
    httpClient = services.httpClient();

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
  public void testMissingCredentials() {
    String uri = services.httpUri("/basicAuth");

    try (Response response =
             httpClient.target(uri)
                       .request()
                       .get()) {

      assertEquals(UNAUTHORIZED, response.getStatus());

      assertEquals(
          "Basic realm=\"testRealm\"",
          response.getHeaderString(WWW_AUTHENTICATE));
    }
  }

  /**
   * Verifies that {@link BasicAuthenticationHandler} rejects requests that
   * specify invalid credentials.
   */
  @Test
  public void testInvalidCredentials() {
    String uri = services.httpUri("/basicAuth");

    String invalidCredentials =
        BasicAuthUtils.writeAuthorizationHeader(
            "wrong_username",
            "wrong_password");

    try (Response response =
             httpClient.target(uri)
                       .request()
                       .header(AUTHORIZATION, invalidCredentials)
                       .get()) {

      assertEquals(UNAUTHORIZED, response.getStatus());

      assertEquals(
          "Basic realm=\"testRealm\"",
          response.getHeaderString(WWW_AUTHENTICATE));
    }
  }

  /**
   * Verifies that {@link BasicAuthenticationHandler} accepts requests that
   * specify valid credentials.
   */
  @Test
  public void testValidCredentials() {
    String uri = services.httpUri("/basicAuth");

    String validCredentials =
        BasicAuthUtils.writeAuthorizationHeader(
            CORRECT_USERNAME,
            CORRECT_PASSWORD);

    try (Response response =
             httpClient.target(uri)
                       .request()
                       .header(AUTHORIZATION, validCredentials)
                       .get()) {

      assertEquals(OK, response.getStatus());

      assertNull(response.getHeaderString(WWW_AUTHENTICATE));
    }
  }
}
