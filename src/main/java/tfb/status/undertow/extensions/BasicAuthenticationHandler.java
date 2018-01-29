package tfb.status.undertow.extensions;

import static io.undertow.security.api.AuthenticationMode.PRO_ACTIVE;

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.handlers.AuthenticationCallHandler;
import io.undertow.security.handlers.AuthenticationConstraintHandler;
import io.undertow.security.handlers.AuthenticationMechanismsHandler;
import io.undertow.security.handlers.SecurityInitialHandler;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.impl.BasicAuthenticationMechanism;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import java.util.List;
import java.util.Objects;

/**
 * An HTTP handler that forwards requests to a caller-supplied HTTP handler
 * after performing Basic authentication.  The caller-supplied handler is only
 * invoked when the incoming HTTP request is from an authenticated user.
 *
 * <p>The following code demonstrates how to read the authenticated user from
 * the {@link #handleRequest(HttpServerExchange)} method of an HTTP handler that
 * has been wrapped by an instance of this class.
 *
 * <pre>
 *   &#64;Override
 *   public void handleRequest(HttpServerExchange exchange) {
 *     // This account is guaranteed to be non-null.
 *     Account account =
 *         exchange.getSecurityContext().getAuthenticatedAccount();
 *     ...
 *   }
 * </pre>
 */
public final class BasicAuthenticationHandler implements HttpHandler {
  private final HttpHandler delegate;

  /**
   * Constructs a new HTTP handler that requires Basic authentication.
   *
   * @param realmName the name of the realm for authentication (when a user
   *        authenticates for a handler having this realm, that user should then
   *        have access to all handlers having the same realm)
   * @param identityManager verifies accounts from authentication credentials
   * @param nextHandler the HTTP handler to be invoked after the user has
   *        authenticated successfully
   */
  public BasicAuthenticationHandler(String realmName,
                                    IdentityManager identityManager,
                                    HttpHandler nextHandler) {

    Objects.requireNonNull(realmName);
    Objects.requireNonNull(identityManager);
    Objects.requireNonNull(nextHandler);

    List<AuthenticationMechanism> authenticationMechanisms =
        List.of(new BasicAuthenticationMechanism(realmName));

    HttpHandler handler = nextHandler;
    handler = new AuthenticationCallHandler(handler);
    handler = new AuthenticationConstraintHandler(handler);
    handler = new AuthenticationMechanismsHandler(handler, authenticationMechanisms);

    handler =
        new SecurityInitialHandler(
            /* authenticationMode= */ PRO_ACTIVE,
            /* identityManager= */ identityManager,
            /* next= */ handler);

    delegate = handler;
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    delegate.handleRequest(exchange);
  }
}
