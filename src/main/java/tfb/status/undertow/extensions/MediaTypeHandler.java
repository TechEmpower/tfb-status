package tfb.status.undertow.extensions;

import static io.undertow.util.Headers.CONTENT_TYPE;
import static io.undertow.util.StatusCodes.UNSUPPORTED_MEDIA_TYPE;
import static java.util.Comparator.comparing;

import com.google.common.net.MediaType;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An HTTP handler that forwards requests to other HTTP handlers based on the
 * media type (the {@code Content-Type} header) of each request.  If the media
 * type of the incoming request does not map to any of the other HTTP handlers,
 * then this handler responds with {@code 415 Unsupported Media Type}.
 *
 * <p>A media type in the {@code Content-Type} header of a request is compatible
 * with a media type that was {@linkplain #addMediaType(MediaType, HttpHandler)
 * added} to this handler when the request's media type {@link
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
 */
public final class MediaTypeHandler implements HttpHandler {
  private final List<Mapping> mappings = new CopyOnWriteArrayList<>();

  private static final class Mapping {
    final MediaType mediaType;
    final HttpHandler handler;

    Mapping(MediaType mediaType, HttpHandler handler) {
      this.mediaType = Objects.requireNonNull(mediaType);
      this.handler = Objects.requireNonNull(handler);
    }
  }

  /**
   * Shortcut for {@link #addMediaType(MediaType, HttpHandler)}.
   *
   * @throws IllegalArgumentException if the input is not a valid media type
   *         (see {@link MediaType#parse(String)})
   */
  @CanIgnoreReturnValue
  public MediaTypeHandler addMediaType(String mediaType, HttpHandler handler) {
    Objects.requireNonNull(mediaType);
    Objects.requireNonNull(handler);
    return addMediaType(MediaType.parse(mediaType), handler);
  }

  /**
   * Maps a media type to a handler.
   *
   * @param mediaType the required media type ({@code Content-Type} header) of
   *        the requests
   * @param handler the handler for requests having this media type
   * @return this {@link MediaTypeHandler} instance (for chaining)
   * @throws IllegalStateException if this media type was already mapped to
   *         another handler
   */
  @CanIgnoreReturnValue
  public MediaTypeHandler addMediaType(MediaType mediaType, HttpHandler handler) {
    Objects.requireNonNull(mediaType);
    Objects.requireNonNull(handler);

    for (Mapping mapping : mappings)
      if (mediaType.equals(mapping.mediaType))
        throw new IllegalStateException(mediaType + " already has a handler");

    mappings.add(new Mapping(mediaType, handler));
    return this;
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    MediaType requestMediaType = detectMediaType(exchange);

    Mapping bestMatch =
        mappings.stream()
                .filter(mapping -> requestMediaType.is(mapping.mediaType))
                .max(
                    comparing(
                        mapping -> mapping.mediaType,
                        MediaTypes.SPECIFICITY_ORDER))
                .orElse(null);

    if (bestMatch != null) {
      bestMatch.handler.handleRequest(exchange);
      return;
    }

    exchange.setStatusCode(UNSUPPORTED_MEDIA_TYPE);
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
}
