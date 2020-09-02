package tfb.status.undertow.extensions;

import static io.undertow.util.Headers.CONTENT_TYPE;
import static io.undertow.util.StatusCodes.UNSUPPORTED_MEDIA_TYPE;

import com.google.common.collect.ImmutableMap;
import com.google.common.net.MediaType;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * An HTTP handler that forwards requests to other HTTP handlers based on the
 * media type (the {@code Content-Type} header) of each request.  If the media
 * type of the incoming request does not map to any of the other HTTP handlers,
 * then this handler responds with {@code 415 Unsupported Media Type}.
 *
 * <p>A media type in the {@code Content-Type} header of a request is compatible
 * with a media type that was {@linkplain Builder#add(MediaType,
 * HttpHandler) added} to this handler when the request's media type {@link
 * MediaType#is(MediaType)} the handler's media type.  For example, a request
 * with {@code Content-Type: text/plain;charset=utf-8} is compatible with the
 * handler media types {@code text/plain;charset=utf-8}, {@code text/plain},
 * {@code text/*}, and <code>*&#47;*</code>, but that request is not compatible
 * with the handler media types {@code text/plain;charset=us-ascii} or {@code
 * text/plain;charset=utf-8;format=flowed}.
 *
 * <p>A request with no {@code Content-Type} header or a present but {@linkplain
 * MediaType#parse(String) unparseable} {@code Content-Type} header is
 * considered to have <code>Content-Type: *&#47;*</code>, meaning that it is
 * only compatible with the handler for the <code>*&#47;*</code> media type.
 *
 * <p>Instances of this class are immutable.  Use {@link #builder()} to obtain a
 * new, mutable {@link Builder} instance, use {@link Builder#add(MediaType,
 * HttpHandler)} to add mappings to that builder, and then use {@link
 * Builder#build()} to obtain an immutable {@link MediaTypeHandler} instance
 * containing those mappings.
 */
public final class MediaTypeHandler implements HttpHandler {
  private final ImmutableMap<MediaType, HttpHandler> handlers;

  private MediaTypeHandler(ImmutableMap<MediaType, HttpHandler> handlers) {
    this.handlers = ImmutableMap.copyOf(handlers);
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    MediaType requestMediaType = detectMediaType(exchange);

    Map.Entry<MediaType, HttpHandler> bestMatch =
        handlers
            .entrySet()
            .stream()
            .filter(
                entry -> {
                  MediaType consumedMediaType = entry.getKey();
                  return requestMediaType.is(consumedMediaType);
                })
            .findFirst()
            .orElse(null);

    if (bestMatch == null) {
      exchange.setStatusCode(UNSUPPORTED_MEDIA_TYPE);
      return;
    }

    HttpHandler handler = bestMatch.getValue();
    handler.handleRequest(exchange);
  }

  /**
   * Parses the {@code Content-Type} header of the HTTP request and returns the
   * result.  Falls back to {@link MediaType#ANY_TYPE} when the {@code
   * Content-Type} header is missing or malformed.
   *
   * @param exchange the HTTP request/response
   * @return the media type of the HTTP request
   */
  private static MediaType detectMediaType(HttpServerExchange exchange) {
    String contentType = exchange.getRequestHeaders().getFirst(CONTENT_TYPE);

    if (contentType == null)
      return MediaType.ANY_TYPE;

    try {
      return MediaType.parse(contentType);
    } catch (IllegalArgumentException ignored) {
      return MediaType.ANY_TYPE;
    }
  }

  /**
   * Returns a new, initially-empty {@link Builder} instance.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * A mutable builder class used to construct {@link MediaTypeHandler}
   * instances.
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
     * @param mediaType the required media type ({@code Content-Type} header) of
     *        the requests
     * @param handler the handler for requests having this media type
     * @return this {@link Builder} instance (for chaining)
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
     * Returns a new {@link MediaTypeHandler} instance containing the mappings
     * that have been added to this {@link Builder}.  Subsequent modifications
     * to this {@link Builder} do not affect previously-returned {@link
     * MediaTypeHandler} instances.
     */
    public MediaTypeHandler build() {
      return new MediaTypeHandler(ImmutableMap.copyOf(handlers));
    }
  }
}
