package tfb.status.undertow.extensions;

import static io.undertow.util.StatusCodes.NOT_FOUND;

import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.PathTemplateMatch;
import java.util.Objects;
import tfb.status.util.PathPattern;
import tfb.status.util.PathRouter;

/**
 * An HTTP handler that forwards requests to other HTTP handlers based on the
 * {@linkplain HttpServerExchange#getRelativePath() request path} of each
 * request, using {@link PathPattern} to identify the paths that each handler
 * can handle.  If the request path does not match any of the other HTTP
 * handlers, then this handler responds with {@code 404 Not Found}.
 *
 * <p>If the request path matches multiple path patterns, then the HTTP handler
 * with the {@linkplain PathPattern#SPECIFICITY_COMPARATOR most specific} path
 * pattern is chosen.
 *
 * <p>The values of the {@linkplain PathPattern.MatchResult#variables()
 * variables} declared in the path pattern for the current HTTP request can be
 * retrieved in the other HTTP handlers using {@link
 * RequestValues#pathParameter(HttpServerExchange, String)}.
 *
 * <p>Instances of this class are immutable.  Use {@link #builder()} to obtain a
 * new, mutable {@link Builder} instance, use {@link Builder#add(PathPattern,
 * HttpHandler)} to add mappings to that builder, and then use {@link
 * Builder#build()} to obtain a new, immutable {@link PathPatternHandler}
 * instance containing those mappings.
 */
public final class PathPatternHandler implements HttpHandler {
  private final PathRouter<HttpHandler> handlers;

  private PathPatternHandler(PathRouter<HttpHandler> handlers) {
    this.handlers = Objects.requireNonNull(handlers);
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    String requestedPath = exchange.getRelativePath();

    PathRouter.MatchingEndpoint<HttpHandler> bestMatch =
        handlers.find(requestedPath);

    if (bestMatch == null) {
      exchange.setStatusCode(NOT_FOUND);
      return;
    }

    PathPattern pathPattern = bestMatch.pathPattern();
    ImmutableMap<String, String> variables = bestMatch.variables();
    HttpHandler handler = bestMatch.value();

    var undertowPathMatch =
        new PathTemplateMatch(
            /* matchedTemplate= */ pathPattern.source(),
            /* parameters= */ variables);

    // TODO: Is this the behavior we want for chained path pattern handlers?
    //       This overrides the attachment from handlers up the chain.
    exchange.putAttachment(
        PathTemplateMatch.ATTACHMENT_KEY,
        undertowPathMatch);

    handler.handleRequest(exchange);
  }

  /**
   * Returns a new, initially-empty {@link Builder} instance.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * A mutable builder class used to construct {@link PathPatternHandler}
   * instances.
   */
  public static final class Builder {
    private final PathRouter.Builder<HttpHandler> handlers =
        PathRouter.builder();

    private Builder() {}

    /**
     * Shortcut for {@link #add(PathPattern, HttpHandler)}.
     *
     * @throws IllegalArgumentException if the input is not a valid path pattern
     *         (see {@link PathPattern#of(String)})
     */
    @CanIgnoreReturnValue
    public Builder add(String pathPattern, HttpHandler handler) {
      Objects.requireNonNull(pathPattern);
      Objects.requireNonNull(handler);

      return add(PathPattern.of(pathPattern), handler);
    }

    /**
     * Maps a path pattern to a handler.
     *
     * @param pathPattern the request path pattern to be matched
     * @param handler the handler for requests matching this path pattern
     * @return this {@link Builder} instance (for chaining)
     * @throws IllegalStateException if this path pattern was already mapped to
     *         another handler
     */
    @CanIgnoreReturnValue
    public Builder add(PathPattern pathPattern, HttpHandler handler) {
      Objects.requireNonNull(pathPattern);
      Objects.requireNonNull(handler);

      handlers.add(pathPattern, handler);
      return this;
    }

    /**
     * Returns a new {@link PathPatternHandler} instance containing the mappings
     * that have been added to this {@link Builder}.  Subsequent modifications
     * to this {@link Builder} do not affect previously-returned {@link
     * PathPatternHandler} instances.
     */
    public PathPatternHandler build() {
      return new PathPatternHandler(handlers.build());
    }
  }
}
