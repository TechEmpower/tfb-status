package tfb.status.testlib;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Base64;
import java.util.Objects;

/**
 * Utility methods related to Basic authentication.
 */
public final class BasicAuthUtils {
  private BasicAuthUtils() {
    throw new AssertionError("This class cannot be instantiated");
  }

  /**
   * Produces the {@code Authorization} header value for an HTTP request where
   * the server is expected to perform Basic authentication.
   *
   * @param username the username to be included in the header
   * @param password the password to be included in the header
   */
  public static String writeAuthorizationHeader(String username,
                                                String password) {
    Objects.requireNonNull(username);
    Objects.requireNonNull(password);

    return "Basic " +
        Base64.getEncoder().encodeToString(
            (username + ":" + password).getBytes(UTF_8));
  }
}
