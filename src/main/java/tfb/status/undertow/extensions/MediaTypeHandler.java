package tfb.status.undertow.extensions;

import static io.undertow.util.Headers.CONTENT_TYPE;
import static io.undertow.util.StatusCodes.UNSUPPORTED_MEDIA_TYPE;

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
    MediaType requestedMediaType = detectMediaType(exchange);

    // TODO: Document the algorithm for choosing the most specific media type.
    // TODO: Use an algorithm that does not depend on the ordering of mappings.
    Mapping mostSpecific = null;

    for (Mapping mapping : mappings)
      if (requestedMediaType.is(mapping.mediaType))
        if (mostSpecific == null
            || mapping.mediaType.is(mostSpecific.mediaType))
          mostSpecific = mapping;

    if (mostSpecific == null)
      exchange.setStatusCode(UNSUPPORTED_MEDIA_TYPE);
    else
      mostSpecific.handler.handleRequest(exchange);
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
