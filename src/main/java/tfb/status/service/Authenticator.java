package tfb.status.service;

import com.google.common.io.MoreFiles;
import com.google.errorprone.annotations.Immutable;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.sse.ServerSentEventConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.Principal;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.undertow.extensions.BasicAuthenticationHandler;

/**
 * Implements a simple password-based authentication scheme.
 */
@Singleton
public final class Authenticator {
  private final FileStore fileStore;
  private final Logger logger = LoggerFactory.getLogger(getClass());

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
    SecurityContext securityContext = exchange.getSecurityContext();
    if (securityContext == null)
      throw new IllegalStateException(
          "The exchange does not have a security context");

    Account account = securityContext.getAuthenticatedAccount();
    if (account == null)
      throw new IllegalStateException(
          "The exchange does not have an authenticated account");

    return accountToId(account);
  }

  /**
   * Returns the id of the already-authenticated, known-present account
   * associated with the SSE connection.  Throws {@link IllegalStateException}
   * if the authentication step never occurred.
   *
   * <p>This method should only be used with SSE connections originating from
   * HTTP handlers that have been wrapped by {@link
   * #newRequiredAuthHandler(HttpHandler)}.
   *
   * @param connection the SSE connection
   * @return the id of the account associated with the connection
   * @throws IllegalStateException if the connection was not authenticated
   * @see #newRequiredAuthHandler(HttpHandler)
   */
  public String requiredAccountId(ServerSentEventConnection connection) {
    Objects.requireNonNull(connection);
    Account account = connection.getAccount();
    if (account == null)
      throw new IllegalStateException(
          "The connection does not have an authenticated account");

    return accountToId(account);
  }

  private static String accountToId(Account account) {
    Objects.requireNonNull(account, "account");

    //
    // The principal and name should never be null.  If either one is null, then
    // an NPE is an appropriate outcome--indicating programmer error--because
    // whatever created the Account object is broken.
    //

    Principal principal = account.getPrincipal();
    Objects.requireNonNull(principal, "principal");

    String principalName = principal.getName();
    Objects.requireNonNull(principalName, "principal name");

    return principalName;
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

    return new BasicAuthenticationHandler(
        /* realmName= */ "TFB Status",
        /* identityManager= */ new ThisAsIdentityManager(),
        /* nextHandler= */ nextHandler);
  }

  private final class ThisAsIdentityManager implements IdentityManager {
    @Override
    @Nullable
    public Account verify(Account account) {
      String accountId = accountToId(account);
      return accountExists(accountId) ? account : null;
    }

    @Override
    @Nullable
    public Account verify(String accountId, Credential credential) {
      Objects.requireNonNull(accountId);
      Objects.requireNonNull(credential);

      if (!(credential instanceof PasswordCredential))
        return null;

      char[] passwordChars = ((PasswordCredential) credential).getPassword();
      String password = String.valueOf(passwordChars);

      boolean isPasswordCorrect;
      try {
        isPasswordCorrect = checkPassword(accountId, password);
      } catch (IOException e) {
        logger.error("Error checking password", e);
        return null;
      }

      if (isPasswordCorrect)
        return new IdAsAccount(accountId);
      else
        return null;
    }

    @Override
    @Nullable
    public Account verify(Credential credential) {
      Objects.requireNonNull(credential);
      return null;
    }
  }

  @Immutable
  private static final class IdAsAccount implements Account {
    private final String accountId;

    IdAsAccount(String accountId) {
      this.accountId = Objects.requireNonNull(accountId);
    }

    @Override
    public Principal getPrincipal() {
      return new IdAsPrincipal(accountId);
    }

    @Override
    public Set<String> getRoles() {
      return Set.of();
    }

    private static final long serialVersionUID = 0;
  }

  @Immutable
  private static final class IdAsPrincipal implements Principal, Serializable {
    private final String accountId;

    IdAsPrincipal(String accountId) {
      this.accountId = Objects.requireNonNull(accountId);
    }

    @Override
    public String getName() {
      return accountId;
    }

    @Override
    public boolean equals(Object object) {
      if (object == this)
        return true;

      if (!(object instanceof IdAsPrincipal))
        return false;

      var that = (IdAsPrincipal) object;
      return this.accountId.equals(that.accountId);
    }

    @Override
    public int hashCode() {
      return accountId.hashCode();
    }

    private static final long serialVersionUID = 0;
  }

  /**
   * Creates a new account with the given id and password.
   *
   * @throws IllegalArgumentException if there is already an account with that
   *         id or if the id cannot possibly be the id of an account
   * @throws IOException if an I/O error occurs while saving the account details
   */
  public void createNewAccount(String accountId, String password)
      throws IOException {

    Objects.requireNonNull(accountId);
    Objects.requireNonNull(password);

    Path passwordFile = getPasswordFile(accountId);
    if (passwordFile == null)
      throw new IllegalArgumentException("Invalid account id: " + accountId);

    if (Files.isRegularFile(passwordFile))
      throw new IllegalArgumentException(
          "Account with id " + accountId + " already exists");

    String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());
    MoreFiles.createParentDirectories(passwordFile);
    Files.write(passwordFile, List.of(passwordHash));
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

    Path passwordFile = getPasswordFile(accountId);
    if (passwordFile == null || !Files.isRegularFile(passwordFile))
      return false;

    String passwordHash;
    try (BufferedReader reader = Files.newBufferedReader(passwordFile)) {
      passwordHash = reader.readLine();
    }

    return passwordHash != null && BCrypt.checkpw(password, passwordHash);
  }

  /**
   * Returns {@code true} if an account with the given id exists.
   *
   * @param accountId the id of the account
   * @return {@code true} if the account exists
   */
  public boolean accountExists(String accountId) {
    Path passwordFile = getPasswordFile(accountId);
    return passwordFile != null && Files.isRegularFile(passwordFile);
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
  @Nullable
  private Path getPasswordFile(String accountId) {
    return resolveChildPath(fileStore.accountsDirectory(), accountId);
  }

  @Nullable
  private static Path resolveChildPath(Path directory, String fileName) {
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
