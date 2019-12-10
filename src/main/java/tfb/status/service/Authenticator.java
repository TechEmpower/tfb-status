package tfb.status.service;

import static io.undertow.util.Headers.AUTHORIZATION;
import static io.undertow.util.Headers.WWW_AUTHENTICATE;
import static io.undertow.util.StatusCodes.UNAUTHORIZED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE_NEW;

import com.google.common.io.MoreFiles;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.HeaderValues;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.mindrot.jbcrypt.BCrypt;

/**
 * Implements a simple password-based authentication scheme.
 */
@Singleton
public final class Authenticator {
  private final FileStore fileStore;
  private final ReadWriteLock lock = new ReentrantReadWriteLock();

  @Inject
  public Authenticator(FileStore fileStore) {
    this.fileStore = Objects.requireNonNull(fileStore);
  }

  /**
   * Returns the id of the already-authenticated, known-present account
   * associated with the HTTP exchange.  Throws {@link IllegalStateException} if
   * the authentication step never occurred.
   *
   * <p>This method should only be used in HTTP handlers that have been wrapped
   * by {@link #newRequiredAuthHandler(HttpHandler)}.
   *
   * @param exchange the current HTTP exchange
   * @return the id of the account associated with the exchange
   * @throws IllegalStateException if the exchange was not authenticated
   * @see #newRequiredAuthHandler(HttpHandler)
   */
  public String requiredAccountId(HttpServerExchange exchange) {
    Objects.requireNonNull(exchange);

    String accountId = exchange.getAttachment(ACCOUNT_ID);
    if (accountId == null)
      throw new IllegalStateException(
          "The exchange does not have an authenticated account");

    return accountId;
  }

  /**
   * Returns an HTTP handler that requires password-based authentication (as
   * implemented by this {@link Authenticator}) and forwards authenticated
   * requests to the given handler.
   *
   * <p>In the wrapped HTTP handler, the id of the current (authenticated) user
   * can be retrieved using the {@link #requiredAccountId(HttpServerExchange)}
   * method:
   *
   * <pre>
   *   &#64;Override
   *   public void handleRequest(HttpServerExchange exchange) {
   *     String accountId = authenticator.requiredAccountId(exchange);
   *     ...
   *   }
   * </pre>
   *
   * @param nextHandler the HTTP handler to be invoked after the user has
   *        authenticated successfully
   * @return an HTTP handler that requires authentication
   * @see #requiredAccountId(HttpServerExchange)
   */
  public HttpHandler newRequiredAuthHandler(HttpHandler nextHandler) {
    Objects.requireNonNull(nextHandler);
    return new RequiredAuthHandler(this, nextHandler);
  }

  // Currently implements Basic authentication.
  // TODO: Use some other form of authentication.
  private static final class RequiredAuthHandler implements HttpHandler {
    private final Authenticator authenticator;
    private final HttpHandler nextHandler;

    RequiredAuthHandler(Authenticator authenticator, HttpHandler nextHandler) {
      this.authenticator = Objects.requireNonNull(authenticator);
      this.nextHandler = Objects.requireNonNull(nextHandler);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
      HeaderValues authValues = exchange.getRequestHeaders().get(AUTHORIZATION);
      if (authValues != null) {
        for (String auth : authValues) {
          if (auth.startsWith("Basic ")) {
            String encoded = auth.substring("Basic ".length());
            byte[] decodedBytes;
            try {
              decodedBytes = Base64.getDecoder().decode(encoded);
            } catch (IllegalArgumentException ignored) {
              continue;
            }
            String decoded = new String(decodedBytes, UTF_8);
            int colonIndex = decoded.indexOf(':');
            if (colonIndex == -1)
              continue;

            String accountId = decoded.substring(0, colonIndex);
            String password = decoded.substring(colonIndex + 1);
            if (authenticator.checkPassword(accountId, password)) {
              exchange.putAttachment(ACCOUNT_ID, accountId);
              nextHandler.handleRequest(exchange);
              return;
            }
          }
        }
      }

      exchange.setStatusCode(UNAUTHORIZED);
      exchange.getResponseHeaders().put(
          WWW_AUTHENTICATE,
          "Basic realm=\"TFB Status\", charset=\"UTF-8\"");
    }
  }

  private static final AttachmentKey<String> ACCOUNT_ID =
      AttachmentKey.create(String.class);

  /**
   * Creates a new account with the given id and password if that account does
   * not already exist.
   *
   * @return {@code true} if the account was created as a result of this call
   * @throws IllegalArgumentException if there is already an account with that
   *         id but it has a different password or if the id cannot possibly be
   *         the id of an account
   * @throws IOException if an I/O error occurs while saving the account details
   */
  @CanIgnoreReturnValue
  public boolean createAccountIfAbsent(String accountId, String password)
      throws IOException {

    Objects.requireNonNull(accountId);
    Objects.requireNonNull(password);

    lock.writeLock().lock();
    try {
      Path passwordFile = getPasswordFile(accountId);
      if (passwordFile == null)
        throw new IllegalArgumentException("Invalid account id: " + accountId);

      if (Files.isRegularFile(passwordFile)) {
        if (!checkPassword(accountId, password))
          throw new IllegalArgumentException(
              "Account with id "
                  + accountId
                  + " already exists with a different password");

        return false;
      }

      String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());
      MoreFiles.createParentDirectories(passwordFile);
      Files.write(passwordFile, List.of(passwordHash), CREATE_NEW);
      return true;
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * Deletes the account with the given id if it exists.
   *
   * @param accountId the id of the account
   * @return {@code true} if the account was deleted as a result of this call
   * @throws IllegalArgumentException the id cannot possibly be the id of an
   *         account
   * @throws IOException if an I/O error occurs while deleting the account
   */
  @CanIgnoreReturnValue
  public boolean deleteAccountIfPresent(String accountId)
      throws IOException {

    Objects.requireNonNull(accountId);

    lock.writeLock().lock();
    try {
      Path passwordFile = getPasswordFile(accountId);
      if (passwordFile == null)
        throw new IllegalArgumentException("Invalid account id: " + accountId);

      if (!Files.isRegularFile(passwordFile))
        return false;

      Files.delete(passwordFile);
      return true;
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * Returns {@code true} if an account with the given id exists and its
   * password matches the given password.
   *
   * @param accountId the id of the account
   * @param password the password of the account
   * @return {@code true} if the credentials are valid
   * @throws IOException if an I/O error occurs while verifying the account
   */
  public boolean checkPassword(String accountId, String password)
      throws IOException {

    Objects.requireNonNull(accountId);
    Objects.requireNonNull(password);

    lock.readLock().lock();
    try {
      Path passwordFile = getPasswordFile(accountId);
      if (passwordFile == null || !Files.isRegularFile(passwordFile))
        return false;

      String passwordHash;
      try (BufferedReader reader = Files.newBufferedReader(passwordFile)) {
        passwordHash = reader.readLine();
      }

      return passwordHash != null && BCrypt.checkpw(password, passwordHash);
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Returns {@code true} if an account with the given id exists.
   *
   * @param accountId the id of the account
   * @return {@code true} if the account exists
   * @throws IOException if an I/O error occurs while verifying the account
   */
  public boolean accountExists(String accountId)
      throws IOException {

    lock.readLock().lock();
    try {
      Path passwordFile = getPasswordFile(accountId);
      return passwordFile != null && Files.isRegularFile(passwordFile);
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Returns the file containing the password hash for the account with the
   * given id, or {@code null} if the string is not a possible account id.  The
   * returned file exists if the account exists.
   *
   * @param accountId the id of the account
   * @return the file containing the password hash, or {@code null} if the
   *         account id is invalid
   */
  private @Nullable Path getPasswordFile(String accountId) {
    return resolveChildPath(fileStore.accountsDirectory(), accountId);
  }

  private static @Nullable Path resolveChildPath(Path directory,
                                                 String fileName) {
    Objects.requireNonNull(directory);
    Objects.requireNonNull(fileName);

    Path child;
    try {
      child = directory.resolve(fileName);
    } catch (InvalidPathException ignored) {
      return null;
    }

    if (!child.equals(child.normalize()))
      return null;

    if (!directory.equals(child.getParent()))
      return null;

    return child;
  }
}
