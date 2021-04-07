package tfb.status.handler;

import static io.undertow.util.StatusCodes.BAD_REQUEST;
import static io.undertow.util.StatusCodes.NO_CONTENT;
import static tfb.status.undertow.extensions.RequestValues.queryParameter;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.Objects;
import tfb.status.handler.routing.DisableCache;
import tfb.status.handler.routing.Route;
import tfb.status.service.HomeResultsReader;
import tfb.status.view.HomePageView.ResultsView;

/**
 * Handles requests to determine which git commit id was used most recently in a
 * given benchmarking environment.
 *
 * <p>The request must specify the benchmarking environment in the {@code
 * environment} query parameter, like {@code ?environment=Citrine}.  Otherwise,
 * this handler responds with {@code 400 Bad Request}.
 *
 * <p>The response from this handler is {@code 200 OK} with the commit id in the
 * response body as plain text.  If there is no commit known for the given
 * environment, the response is {@code 204 No Content} with no response body.
 *
 * <p>This handler is expected to be called from the cloud benchmarking
 * environment in order to align itself with the physical benchmarking
 * environment.  An official round on the results website always includes a run
 * from both of these environments, and those runs must use the same commit of
 * the TFB repository.  The physical environment runs continuously, checking out
 * the latest code from the master branch for each run, whereas the cloud
 * environment currently runs once per month.  We want to ensure that one cloud
 * run per month uses the same commit as one of the physical environment's runs,
 * meaning that pair of runs could be usable as an official round.
 *
 * <p>This handler is expected to be called by a bash script using {@code curl}.
 * Therefore, it responds in such a way that should be easy for that bash script
 * to consume -- plain text, with no parsing required.
 */
@Singleton
@Route(
    method = "GET",
    path = "/last-seen-commit",
    produces = "text/plain; charset=utf-8")
@DisableCache
public final class LastSeenCommitHandler implements HttpHandler {
  private final HomeResultsReader homeResultsReader;

  @Inject
  public LastSeenCommitHandler(HomeResultsReader homeResultsReader) {
    this.homeResultsReader = Objects.requireNonNull(homeResultsReader);
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws IOException {
    String environment = queryParameter(exchange, "environment");
    if (environment == null) {
      exchange.setStatusCode(BAD_REQUEST);
      return;
    }

    ResultsView mostRecent =
        homeResultsReader
            .results()
            .stream()
            .filter(result -> environment.equals(result.environmentDescription()))
            .findFirst()
            .orElse(null);

    if (mostRecent == null || mostRecent.commitId() == null) {
      exchange.setStatusCode(NO_CONTENT);
      return;
    }

    exchange.getResponseSender().send(mostRecent.commitId());
  }
}
