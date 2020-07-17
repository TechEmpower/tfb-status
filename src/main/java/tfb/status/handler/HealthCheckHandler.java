package tfb.status.handler;

import static io.undertow.util.StatusCodes.OK;
import static io.undertow.util.StatusCodes.SERVICE_UNAVAILABLE;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import tfb.status.handler.routing.DisableCache;
import tfb.status.handler.routing.Route;
import tfb.status.service.HealthChecker;

/**
 * Handles requests to check this application's health.  Responds with {@code
 * 200 OK} when this application is {@linkplain HealthChecker#isHealthy()
 * healthy} and {@code 503 Service Unavailable} otherwise.
 *
 * <p>This handler is expected to be called by external monitoring tools in
 * environments where this application is deployed.  Appropriate ways for such
 * tools to use this handler include:
 *
 * <ul>
 * <li>After launching this application, treating this application instance as
 *     "not started yet" until this handler produces a successful response.
 * <li>Not routing traffic from a load balancer to this application instance as
 *     long as this handler is producing non-successful responses.
 * <li>Restarting or terminating this application instance if this handler
 *     produces an unacceptable number of consecutive non-successful responses.
 * </ul>
 */
@Singleton
@Route(method = "GET", path = "/health")
@DisableCache
public final class HealthCheckHandler implements HttpHandler {
  private final HealthChecker healthChecker;

  @Inject
  public HealthCheckHandler(HealthChecker healthChecker) {
    this.healthChecker = Objects.requireNonNull(healthChecker);
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) {
    // Although the response body is empty, use 200 OK to indicate success, not
    // 204 No Content.  200 OK is more likely to be the default expected value
    // for the external tool making this request.
    int statusCode = healthChecker.isHealthy() ? OK : SERVICE_UNAVAILABLE;
    exchange.setStatusCode(statusCode);
  }
}
