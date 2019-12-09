package tfb.status.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.testlib.TestServicesInjector;

/**
 * Tests for {@link Authenticator}.
 */
@ExtendWith(TestServicesInjector.class)
public final class AuthenticatorTest {
  private static final String CORRECT_ACCOUNT_ID = "tester";
  private static final String CORRECT_PASSWORD = "password";
  private static final String WRONG_ACCOUNT_ID = "wrong_account";
  private static final String WRONG_PASSWORD = "wrong_password";
  private static final String NEW_ACCOUNT_ID = "new_account";
  private static final String NEW_PASSWORD = "new_password";
  private static final String IMPOSSIBLE_ACCOUNT_ID = "\0";

  /**
   * Verifies that {@link Authenticator#accountExists(String)} works as
   * expected.
   */
  @Test
  public void testAccountExists(Authenticator authenticator) {
    assertTrue(authenticator.accountExists(CORRECT_ACCOUNT_ID));
    assertFalse(authenticator.accountExists(WRONG_ACCOUNT_ID));
    assertFalse(authenticator.accountExists(IMPOSSIBLE_ACCOUNT_ID));
  }

  /**
   * Verifies that {@link Authenticator#checkPassword(String, String)} works as
   * expected.
   */
  @Test
  public void testCheckPassword(Authenticator authenticator)
      throws IOException {

    assertTrue(authenticator.checkPassword(CORRECT_ACCOUNT_ID, CORRECT_PASSWORD));
    assertFalse(authenticator.checkPassword(CORRECT_ACCOUNT_ID, WRONG_PASSWORD));
    assertFalse(authenticator.checkPassword(WRONG_ACCOUNT_ID, CORRECT_PASSWORD));
    assertFalse(authenticator.checkPassword(IMPOSSIBLE_ACCOUNT_ID, CORRECT_PASSWORD));
  }

  /**
   * Verifies that {@link Authenticator#createNewAccount(String, String)} and
   * {@link Authenticator#deleteAccount(String)} work as expected.
   */
  @Test
  public void testCreateAndDeleteAccount(Authenticator authenticator)
      throws IOException {

    assertFalse(authenticator.accountExists(NEW_ACCOUNT_ID));

    authenticator.createNewAccount(NEW_ACCOUNT_ID, NEW_PASSWORD);

    assertTrue(authenticator.checkPassword(NEW_ACCOUNT_ID, NEW_PASSWORD));

    assertThrows(
        IllegalArgumentException.class,
        () -> authenticator.createNewAccount(NEW_ACCOUNT_ID, NEW_PASSWORD));

    authenticator.deleteAccount(NEW_ACCOUNT_ID);

    assertFalse(authenticator.accountExists(NEW_ACCOUNT_ID));

    assertThrows(
        IllegalArgumentException.class,
        () -> authenticator.deleteAccount(NEW_ACCOUNT_ID));

    assertThrows(
        IllegalArgumentException.class,
        () -> authenticator.createNewAccount(IMPOSSIBLE_ACCOUNT_ID, CORRECT_PASSWORD));

    assertThrows(
        IllegalArgumentException.class,
        () -> authenticator.deleteAccount(IMPOSSIBLE_ACCOUNT_ID));
  }
}
