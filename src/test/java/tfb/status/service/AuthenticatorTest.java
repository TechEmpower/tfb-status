package tfb.status.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tfb.status.testlib.TestServices;

/**
 * Tests for {@link Authenticator}.
 */
public final class AuthenticatorTest {
  private static final String CORRECT_ACCOUNT_ID = "tester";
  private static final String CORRECT_PASSWORD = "password";
  private static final String WRONG_ACCOUNT_ID = "wrong_account";
  private static final String WRONG_PASSWORD = "wrong_password";

  private static TestServices services;
  private static Authenticator authenticator;

  @BeforeAll
  public static void beforeAll() {
    services = new TestServices();
    authenticator = services.getService(Authenticator.class);
  }

  @AfterAll
  public static void afterAll() {
    services.shutdown();
  }

  /**
   * Verifies that {@link Authenticator#accountExists(String)} works as
   * expected.
   */
  @Test
  public void testAccountExists() {
    assertTrue(authenticator.accountExists(CORRECT_ACCOUNT_ID));
    assertFalse(authenticator.accountExists(WRONG_ACCOUNT_ID));
  }

  /**
   * Verifies that {@link Authenticator#checkPassword(String, String)} works as
   * expected.
   */
  @Test
  public void testCheckPassword() throws IOException {
    assertTrue(authenticator.checkPassword(CORRECT_ACCOUNT_ID, CORRECT_PASSWORD));
    assertFalse(authenticator.checkPassword(CORRECT_ACCOUNT_ID, WRONG_PASSWORD));
    assertFalse(authenticator.checkPassword(WRONG_ACCOUNT_ID, CORRECT_PASSWORD));
  }
}
