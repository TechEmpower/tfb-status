package tfb.status.service;

import static io.undertow.util.StatusCodes.OK;
import static io.undertow.util.StatusCodes.UNAUTHORIZED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.sse.ServerSentEventConnection;
import io.undertow.server.handlers.sse.ServerSentEventHandler;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.testlib.HttpTester;
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
  private static final String TEMP_ACCOUNT_ID = "temp_account";
  private static final String TEMP_PASSWORD = "temp_password";
  private static final String IMPOSSIBLE_ACCOUNT_ID = "\0";

  /**
   * Verifies that {@link Authenticator#accountExists(String)} works as
   * expected.
   */
  @Test
  public void testAccountExists(Authenticator authenticator)
      throws IOException {

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
   * Verifies that {@link Authenticator#createAccountIfAbsent(String, String)}
   * and {@link Authenticator#deleteAccountIfPresent(String)} work as expected.
   */
  @Test
  public void testCreateAndDeleteAccount(Authenticator authenticator)
      throws IOException {

    assertFalse(authenticator.accountExists(NEW_ACCOUNT_ID));
    assertTrue(authenticator.createAccountIfAbsent(NEW_ACCOUNT_ID, NEW_PASSWORD));
    assertFalse(authenticator.createAccountIfAbsent(NEW_ACCOUNT_ID, NEW_PASSWORD));
    assertTrue(authenticator.checkPassword(NEW_ACCOUNT_ID, NEW_PASSWORD));

    assertThrows(
        IllegalArgumentException.class,
        () -> authenticator.createAccountIfAbsent(NEW_ACCOUNT_ID, WRONG_PASSWORD));

    assertTrue(authenticator.deleteAccountIfPresent(NEW_ACCOUNT_ID));
    assertFalse(authenticator.deleteAccountIfPresent(NEW_ACCOUNT_ID));
    assertFalse(authenticator.accountExists(NEW_ACCOUNT_ID));

    assertThrows(
        IllegalArgumentException.class,
        () -> authenticator.createAccountIfAbsent(IMPOSSIBLE_ACCOUNT_ID, CORRECT_PASSWORD));

    assertThrows(
        IllegalArgumentException.class,
        () -> authenticator.deleteAccountIfPresent(IMPOSSIBLE_ACCOUNT_ID));
  }

  /**
   * Verifies that {@link Authenticator}'s methods are thread-safe.
   */
  @Test
  public void testThreadSafety(Authenticator authenticator)
      throws InterruptedException, ExecutionException, TimeoutException {

    // TODO: How few resources can we use without invalidating the test?
    int numTasks = 64;
    int numThreads = 8;

    Callable<boolean[]> task =
        () -> new boolean[] {
            authenticator.accountExists(TEMP_ACCOUNT_ID),
            authenticator.createAccountIfAbsent(TEMP_ACCOUNT_ID, TEMP_PASSWORD),
            authenticator.checkPassword(TEMP_ACCOUNT_ID, TEMP_PASSWORD),
            authenticator.deleteAccountIfPresent(TEMP_ACCOUNT_ID)
        };

    List<Callable<boolean[]>> tasks = Collections.nCopies(numTasks, task);

    List<Future<boolean[]>> futures;

    ExecutorService executor = Executors.newFixedThreadPool(numThreads);
    try {
      futures = executor.invokeAll(tasks);
      executor.shutdown();
      executor.awaitTermination(1, TimeUnit.MINUTES);
    } finally {
      if (!executor.isTerminated()) {
        executor.shutdownNow();
      }
    }

    for (Future<boolean[]> future : futures) {
      future.get(0, TimeUnit.MILLISECONDS);
    }
  }

  /**
   * Verifies that {@link Authenticator#newRequiredAuthHandler(HttpHandler)}
   * accepts requests containing valid credentials and that {@link
   * Authenticator#requiredAccountId(HttpServerExchange)} makes the verified
   * account id available within the HTTP exchange.
   */
  @Test
  public void testNewRequiredAuthHandler_validCredentials(Authenticator authenticator,
                                                          HttpTester http)
      throws IOException, InterruptedException {

    HttpHandler handler =
        authenticator.newRequiredAuthHandler(
            exchange -> {
              String accountId = authenticator.requiredAccountId(exchange);
              exchange.getResponseSender().send(accountId);
            });

    String path = http.addHandler(handler);

    URI uri = http.uri(path);

    for (Account account : VALID_ACCOUNTS) {
      HttpResponse<String> response =
          http.client().send(
              http.addAuthorization(HttpRequest.newBuilder(uri),
                                    account.accountId,
                                    account.password)
                  .build(),
              HttpResponse.BodyHandlers.ofString());

      assertEquals(OK, response.statusCode());
      assertEquals(account.accountId, response.body());
    }
  }

  /**
   * Verifies that {@link Authenticator#newRequiredAuthHandler(HttpHandler)}
   * accepts SSE requests containing valid credentials and that {@link
   * Authenticator#requiredAccountId(ServerSentEventConnection)} makes the
   * verified account id available within the SSE connection.
   */
  @Test
  public void testNewRequiredAuthHandler_sse_validCredentials(Authenticator authenticator,
                                                              HttpTester http)
      throws IOException, InterruptedException {

    HttpHandler handler =
        authenticator.newRequiredAuthHandler(
            new ServerSentEventHandler(
                /* callback= */
                (ServerSentEventConnection connection, String lastEventId) -> {
                  String accountId = authenticator.requiredAccountId(connection);
                  connection.send(accountId);
                }));

    String path = http.addHandler(handler);

    URI uri = http.uri(path);

    for (Account account : VALID_ACCOUNTS) {
      HttpResponse<InputStream> response =
          http.client().send(
              http.addAuthorization(HttpRequest.newBuilder(uri),
                                    account.accountId,
                                    account.password)
                  .build(),
              HttpResponse.BodyHandlers.ofInputStream());

      try (var is = response.body();
           var isr = new InputStreamReader(is, UTF_8);
           var br = new BufferedReader(isr)) {

        assertEquals(OK, response.statusCode());

        var message = new StringJoiner("\n");

        for (String line = br.readLine();
             line != null && line.startsWith("data:");
             line = br.readLine()) {

          message.add(line.substring("data:".length()));
        }

        assertEquals(account.accountId, message.toString());
      }
    }
  }

  /**
   * Verifies that {@link Authenticator#newRequiredAuthHandler(HttpHandler)}
   * rejects requests containing no credentials.
   */
  @Test
  public void testNewRequiredAuthHandler_noCredentials(Authenticator authenticator,
                                                       HttpTester http)
      throws IOException, InterruptedException {

    HttpHandler handler =
        authenticator.newRequiredAuthHandler(
            exchange -> {
              String accountId = authenticator.requiredAccountId(exchange);
              exchange.getResponseSender().send(accountId);
            });

    String path = http.addHandler(handler);

    URI uri = http.uri(path);

    HttpResponse<String> response =
        http.client().send(
            HttpRequest.newBuilder(uri).build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(UNAUTHORIZED, response.statusCode());
    assertEquals("", response.body());
  }

  /**
   * Verifies that {@link Authenticator#newRequiredAuthHandler(HttpHandler)}
   * rejects requests containing invalid credentials.
   */
  @Test
  public void testNewRequiredAuthHandler_badCredentials(Authenticator authenticator,
                                                        HttpTester http)
      throws IOException, InterruptedException {

    HttpHandler handler =
        authenticator.newRequiredAuthHandler(
            exchange -> {
              String accountId = authenticator.requiredAccountId(exchange);
              exchange.getResponseSender().send(accountId);
            });

    String path = http.addHandler(handler);

    URI uri = http.uri(path);

    for (Account account : INVALID_ACCOUNTS) {
      HttpResponse<String> response =
          http.client().send(
              http.addAuthorization(HttpRequest.newBuilder(uri),
                                    account.accountId,
                                    account.password)
                  .build(),
              HttpResponse.BodyHandlers.ofString());

      assertEquals(UNAUTHORIZED, response.statusCode());
      assertEquals("", response.body());
    }
  }

  /**
   * Verifies that {@link Authenticator#newRequiredAuthHandler(HttpHandler)}
   * accepts requests containing a mix of valid and invalid credentials.
   */
  @Test
  public void testNewRequiredAuthHandler_mixedCredentials(Authenticator authenticator,
                                                          HttpTester http)
      throws IOException, InterruptedException {

    HttpHandler handler =
        authenticator.newRequiredAuthHandler(
            exchange -> {
              String accountId = authenticator.requiredAccountId(exchange);
              exchange.getResponseSender().send(accountId);
            });

    String path = http.addHandler(handler);

    URI uri = http.uri(path);

    HttpRequest.Builder builder = HttpRequest.newBuilder(uri);

    for (Account account : INVALID_ACCOUNTS) {
      builder = http.addAuthorization(builder,
                                      account.accountId,
                                      account.password);
    }

    for (Account account : VALID_ACCOUNTS) {
      builder = http.addAuthorization(builder,
                                      account.accountId,
                                      account.password);
    }

    HttpResponse<String> response =
        http.client().send(
            builder.build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(OK, response.statusCode());
  }

  /**
   * Verifies that {@link Authenticator#requiredAccountId(HttpServerExchange)}
   * throws an exception when used in an HTTP handler that was not wrapped by
   * {@link Authenticator#newRequiredAuthHandler(HttpHandler)}.
   */
  @Test
  public void testRequiredAccountId_noAuthentication(Authenticator authenticator,
                                                     HttpTester http)
      throws IOException, InterruptedException {

    String expectedMessage = "___NOT_AN_ACCOUNT_ID___";

    HttpHandler handler =
        exchange -> {
          String message;
          try {
            message = authenticator.requiredAccountId(exchange);
          } catch (IllegalStateException expected) {
            message = expectedMessage;
          }
          exchange.getResponseSender().send(message);
        };

    String path = http.addHandler(handler);

    URI uri = http.uri(path);

    HttpResponse<String> response =
        http.client().send(
            HttpRequest.newBuilder(uri).build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(OK, response.statusCode());

    assertEquals(expectedMessage, response.body());
  }

  /**
   * Verifies that {@link
   * Authenticator#requiredAccountId(ServerSentEventConnection)} throws an
   * exception when used in an SSE handler that was not wrapped by {@link
   * Authenticator#newRequiredAuthHandler(HttpHandler)}.
   */
  @Test
  public void testRequiredAccountId_sse_noAuthentication(Authenticator authenticator,
                                                         HttpTester http)
      throws IOException, InterruptedException {

    String expectedMessage = "___NOT_AN_ACCOUNT_ID___";

    HttpHandler handler =
        new ServerSentEventHandler(
            /* callback= */
            (ServerSentEventConnection connection, String lastEventId) -> {
              String message;
              try {
                message = authenticator.requiredAccountId(connection);
              } catch (IllegalStateException expected) {
                message = expectedMessage;
              }
              connection.send(message);
            });

    String path = http.addHandler(handler);

    URI uri = http.uri(path);

    HttpResponse<InputStream> response =
        http.client().send(
            HttpRequest.newBuilder(uri).build(),
            HttpResponse.BodyHandlers.ofInputStream());

    try (var is = response.body();
         var isr = new InputStreamReader(is, UTF_8);
         var br = new BufferedReader(isr)) {

      assertEquals(OK, response.statusCode());

      var message = new StringJoiner("\n");

      for (String line = br.readLine();
           line != null && line.startsWith("data:");
           line = br.readLine()) {

        message.add(line.substring("data:".length()));
      }

      assertEquals(expectedMessage, message.toString());
    }
  }

  @Immutable
  private static final class Account {
    final String accountId;
    final String password;

    Account(String accountId, String password) {
      this.accountId = Objects.requireNonNull(accountId);
      this.password = Objects.requireNonNull(password);
    }
  }

  private static final ImmutableList<Account> VALID_ACCOUNTS =
      ImmutableList.of(
          new Account(CORRECT_ACCOUNT_ID, CORRECT_PASSWORD));

  private static final ImmutableList<Account> INVALID_ACCOUNTS =
      ImmutableList.of(
          new Account(CORRECT_ACCOUNT_ID, WRONG_PASSWORD),
          new Account(WRONG_ACCOUNT_ID, CORRECT_PASSWORD),
          new Account(WRONG_ACCOUNT_ID, WRONG_PASSWORD),
          new Account(IMPOSSIBLE_ACCOUNT_ID, CORRECT_PASSWORD),
          new Account(CORRECT_ACCOUNT_ID, ""),
          new Account("", CORRECT_PASSWORD),
          new Account("", ""));
}
