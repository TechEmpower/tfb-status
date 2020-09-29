package tfb.status.undertow.extensions;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.net.MediaType.ANY_TYPE;
import static io.undertow.util.Headers.ACCEPT;
import static io.undertow.util.Headers.CONTENT_TYPE;
import static io.undertow.util.Headers.VARY;
import static io.undertow.util.StatusCodes.NOT_ACCEPTABLE;
import static io.undertow.util.StatusCodes.NO_CONTENT;
import static java.util.Comparator.comparingDouble;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.net.MediaType;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.Immutable;
import io.undertow.server.DefaultResponseListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ResponseCommitListener;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Stream;

/**
 * An HTTP handler that forwards requests to other HTTP handlers based on the
 * {@code Accept} header of each request.  If none of the other HTTP handlers
 * produces a response with a media type accepted by the incoming request, then
 * this handler responds with {@code 406 Not Acceptable}.
 *
 * <p>The response from the other HTTP handler is modified to include a {@code
 * Content-Type} header whose value is the media type associated with that
 * handler.  This modification does not occur if any of the following statements
 * is true: the media type {@linkplain MediaType#hasWildcard() has a wildcard};
 * that handler already added a {@code Content-Type} header to the response;
 * that handler threw an exception; the status code is {@code 204 No Content};
 * the response body is empty and the status code is not 2xx.
 *
 * <p>A {@code Vary: Accept} header is added to all responses.  See <a
 * href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Content_negotiation#The_Vary_response_header"
 * >The Vary response header</a>.
 *
 * <p>A media type in the {@code Accept} header of a request is compatible with
 * a media type that was {@linkplain Builder#add(MediaType,
 * HttpHandler) added} to this handler when all of the following statements are
 * true:
 *
 * <ul>
 * <li>Their {@linkplain MediaType#type() types} are equal, or at least one has
 *     a wildcard type.
 * <li>Their {@linkplain MediaType#subtype() subtypes} are equal, or at least
 *     one has a wildcard subtype.
 * <li>For each key that appears in the {@linkplain MediaType#parameters()
 *     parameters} of both media types, the values associated with that key in
 *     either media type must also be associated with that key in the other
 *     media type.  For example, {@code text/html;level=1} is compatible with
 *     {@code text/html;charset=utf-8} but not compatible with {@code
 *     text/html;level=2}.
 * </ul>
 *
 * <p>A request with no {@code Accept} header is considered to have
 * <code>Accept: *&#47;*</code>, meaning that it is compatible with all
 * handlers.  A request with an unparseable {@code Accept} header is considered
 * to be incompatible with all handlers and will always receive a {@code 406 Not
 * Acceptable} response.
 *
 * <p>For a general definition of the {@code Accept} header, see
 * <a href="https://tools.ietf.org/html/rfc7231#section-5.3.2">RFC 7231, section
 * 5.3.2: Accept</a>.
 *
 * <p>Instances of this class are immutable.  Use {@link #builder()} to obtain a
 * new, mutable {@link Builder} instance, use {@link Builder#add(MediaType,
 * HttpHandler)} to add mappings to that builder, and then use {@link
 * Builder#build()} to obtain an immutable {@link AcceptHandler} instance
 * containing those mappings.
 */
public final class AcceptHandler implements HttpHandler {
  private final ImmutableMap<MediaType, HttpHandler> handlers;

  private AcceptHandler(ImmutableMap<MediaType, HttpHandler> handlers) {
    this.handlers = Objects.requireNonNull(handlers);
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    exchange.getResponseHeaders().add(VARY, "Accept");

    for (MediaType acceptedMediaType : acceptedMediaTypes(exchange)) {

      Map.Entry<MediaType, HttpHandler> bestMatch =
          handlers
              .entrySet()
              .stream()
              .filter(
                  entry -> {
                    MediaType producedMediaType = entry.getKey();
                    return isCompatible(producedMediaType, acceptedMediaType);
                  })
              .findFirst()
              .orElse(null);

      if (bestMatch == null)
        continue;

      MediaType producedMediaType = bestMatch.getKey();
      HttpHandler handler = bestMatch.getValue();

      if (!producedMediaType.hasWildcard())
        exchange.addResponseCommitListener(
            new SetContentTypeListener(producedMediaType));

      // TODO: Attach the accepted media type to the exchange?
      handler.handleRequest(exchange);
      return;
    }

    exchange.setStatusCode(NOT_ACCEPTABLE);
  }

  /**
   * Parses the {@code Accept} header of the HTTP request and returns the
   * result, where the media types in the returned list are ordered from
   * most-preferred to least-preferred.  If the {@code Accept} header is
   * missing, then a list containing {@link MediaType#ANY_TYPE} is returned.  If
   * the {@code Accept} header is malformed, then an empty list is returned.
   *
   * @param exchange the HTTP request/response
   * @return the media type of the HTTP request
   */
  private static ImmutableList<MediaType> acceptedMediaTypes(
      HttpServerExchange exchange) {

    Objects.requireNonNull(exchange);

    String acceptHeader = exchange.getRequestHeaders().getFirst(ACCEPT);
    if (acceptHeader == null)
      return ImmutableList.of(ANY_TYPE);

    try {
      return tokenizeAcceptHeader(acceptHeader)
          .map(token -> WeightedMediaType.parse(token))
          .sorted(WeightedMediaType.MOST_TO_LEAST_PREFERRED)
          .map(weighted -> weighted.mediaType)
          .collect(toImmutableList());

    } catch (IllegalArgumentException ignored) {
      // TODO: Log this exception?
      return ImmutableList.of();
    }
  }

  /**
   * Returns the result of splitting the specified {@code Accept} header value
   * into tokens, where each token specifies a media type and an optional
   * quality value.
   *
   * @param acceptHeader the value of the {@code Accept} header
   */
  // This method was adapted from Spring's MimeTypeUtils#tokenize(String).
  // https://github.com/spring-projects/spring-framework/blob/87c5b5a66485aa3d8071e96ec36c032092c8c3c7/spring-core/src/main/java/org/springframework/util/MimeTypeUtils.java#L286-L321
  private static Stream<String> tokenizeAcceptHeader(String acceptHeader) {
    Objects.requireNonNull(acceptHeader);

    Stream.Builder<String> tokens = Stream.builder();
    boolean inQuotes = false;
    int startIndex = 0;
    int i = 0;
    while (i < acceptHeader.length()) {
      switch (acceptHeader.charAt(i)) {
        case '"':
          inQuotes = !inQuotes;
          break;
        case ',':
          if (!inQuotes) {
            tokens.add(acceptHeader.substring(startIndex, i).strip());
            startIndex = i + 1;
          }
          break;
        case '\\':
          i++;
          break;
        default:
          // Do nothing.
          break;
      }
      i++;
    }
    tokens.add(acceptHeader.substring(startIndex).strip());
    return tokens.build();
  }

  /**
   * A media type and its associated quality value; the result of parsing a
   * token from the {@code Accept} header.  The quality value is also known as
   * the "weight".
   */
  @Immutable
  private static final class WeightedMediaType {
    final MediaType mediaType;
    final double qualityValue;

    WeightedMediaType(MediaType mediaType, double qualityValue) {
      this.mediaType = Objects.requireNonNull(mediaType);

      if (!(qualityValue >= 0.0 && qualityValue <= 1.0))
        throw new IllegalArgumentException(
            "The quality value must be greater than or equal to zero and less "
                + "than or equal to one, but the specified quality value was "
                + qualityValue);

      this.qualityValue = qualityValue;
    }

    /**
     * Returns the result of parsing the specified string, which represents a
     * media type and its associated quality value.
     *
     * @throws IllegalArgumentException if the token specifies an invalid media
     *         type or quality value
     */
    static WeightedMediaType parse(String input) {
      Objects.requireNonNull(input);

      MediaType mediaType = MediaType.parse(input);

      double qualityValue =
          switch (mediaType.parameters().get("q").size()) {
            case 0 -> 1.0;
            case 1 -> {
              String q = mediaType.parameters().get("q").get(0);
              yield Double.parseDouble(q);
            }
            default ->
                throw new IllegalArgumentException(
                    "Accept header token \""
                        + input
                        + "\" is malformed because it contains multiple "
                        + "quality values");
          };

      return new WeightedMediaType(
          // The quality value looks like any other parameter but it's not
          // considered part of the media type.
          mediaType.withParameters("q", List.of()),
          qualityValue);
    }

    /**
     * Orders {@link WeightedMediaType} instances from most-preferred to
     * least-preferred.  Higher quality values are preferred.  Between two
     * instances with equal quality values, the one with a more {@linkplain
     * MediaTypes#SPECIFICITY_ORDER specific} media type is preferred.
     */
    static final Comparator<WeightedMediaType> MOST_TO_LEAST_PREFERRED =
        comparingDouble((WeightedMediaType weighted) -> weighted.qualityValue)
            .thenComparing(weighted -> weighted.mediaType,
                           MediaTypes.SPECIFICITY_ORDER)
            .reversed();
  }

  /**
   * Returns {@code true} if the two media types are compatible with each other,
   * where "compatible" is defined in the the class-level documentation of
   * {@link AcceptHandler}.
   *
   * <p>This operation is symmetric.
   */
  private static boolean isCompatible(MediaType a, MediaType b) {
    Objects.requireNonNull(a);
    Objects.requireNonNull(b);

    if (!a.type().equals("*")
        && !b.type().equals("*")
        && !a.type().equals(b.type()))
      return false;

    if (!a.subtype().equals("*")
        && !b.subtype().equals("*")
        && !a.subtype().equals(b.subtype()))
      return false;

    for (String key : Sets.intersection(a.parameters().keySet(),
                                        b.parameters().keySet())) {

      ImmutableList<String> aValues = a.parameters().get(key);
      ImmutableList<String> bValues = b.parameters().get(key);

      if (!aValues.containsAll(bValues) || !bValues.containsAll(aValues))
        return false;
    }

    return true;
  }

  /**
   * Sets the {@code Content-Type} header of the response to the specified
   * value if no {@code Content-Type} header was already set.
   */
  private static final class SetContentTypeListener
      implements ResponseCommitListener {

    private final MediaType mediaType;

    SetContentTypeListener(MediaType mediaType) {
      this.mediaType = Objects.requireNonNull(mediaType);
    }

    @Override
    public void beforeCommit(HttpServerExchange exchange) {
      if (exchange.getResponseHeaders().getFirst(CONTENT_TYPE) != null)
        return;

      if (exchange.getAttachment(DefaultResponseListener.EXCEPTION) != null)
        return;

      if (exchange.getStatusCode() == NO_CONTENT)
        return;

      // TODO: Think more about whether this return condition makes sense.
      if (exchange.getResponseContentLength() == 0
          && exchange.getStatusCode() / 100 != 2)
        return;

      exchange.getResponseHeaders().put(CONTENT_TYPE, mediaType.toString());
    }
  }

  /**
   * Returns a new, initially-empty {@link Builder} instance.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * A mutable builder class used to construct {@link AcceptHandler} instances.
   */
  public static final class Builder {
    private final ConcurrentSkipListMap<MediaType, HttpHandler> handlers =
        new ConcurrentSkipListMap<>(
            MediaTypes.SPECIFICITY_ORDER
                .reversed()
                .thenComparing(mediaType -> mediaType.toString()));

    private Builder() {}

    /**
     * Shortcut for {@link #add(MediaType, HttpHandler)}.
     *
     * @throws IllegalArgumentException if the input is not a valid media type
     *         (see {@link MediaType#parse(String)})
     */
    @CanIgnoreReturnValue
    public Builder add(String mediaType, HttpHandler handler) {
      Objects.requireNonNull(mediaType);
      Objects.requireNonNull(handler);
      return add(MediaType.parse(mediaType), handler);
    }

    /**
     * Maps a media type to a handler.
     *
     * @param mediaType the response media type ({@code Content-Type} header) of
     *        responses from the handler
     * @param handler the handler for requests accepting this media type
     * @return this {@link AcceptHandler} instance (for chaining)
     * @throws IllegalStateException if this media type was already mapped to
     *         another handler
     */
    @CanIgnoreReturnValue
    public Builder add(MediaType mediaType, HttpHandler handler) {
      Objects.requireNonNull(mediaType);
      Objects.requireNonNull(handler);

      handlers.merge(
          mediaType,
          handler,
          (handler1, handler2) -> {
            throw new IllegalStateException(
                mediaType + " already has a handler");
          });

      return this;
    }

    /**
     * Returns a new {@link AcceptHandler} instance containing the mappings that
     * have been added to this {@link Builder}.  Subsequent modifications to
     * this {@link Builder} do not affect previously-returned {@link
     * AcceptHandler} instances.
     */
    public AcceptHandler build() {
      return new AcceptHandler(ImmutableMap.copyOf(handlers));
    }
  }
}
