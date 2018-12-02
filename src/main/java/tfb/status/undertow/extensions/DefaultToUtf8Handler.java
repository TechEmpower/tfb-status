package tfb.status.undertow.extensions;

import static com.google.common.net.MediaType.ANY_TEXT_TYPE;
import static io.undertow.util.Headers.CONTENT_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.net.MediaType;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ResponseCommitListener;
import io.undertow.util.HeaderValues;
import java.util.Objects;

/**
 * An HTTP handler that modifies the {@code Content-Type} response headers of a
 * caller-supplied HTTP handler such that UTF-8 is specified as the charset for
 * {@code text/*} content when no charset was originally specified.  For
 * example, if the caller-supplied HTTP handler would have responded to a
 * request with {@code Content-Type: text/plain}, then this class will respond
 * with {@code Content-Type: text/plain; charset=utf-8} instead.
 */
public final class DefaultToUtf8Handler implements HttpHandler {
  private final HttpHandler handler;

  public DefaultToUtf8Handler(HttpHandler handler) {
    this.handler = Objects.requireNonNull(handler);
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    exchange.addResponseCommitListener(DefaultToUtf8Listener.INSTANCE);
    handler.handleRequest(exchange);
  }

  private static final class DefaultToUtf8Listener
      implements ResponseCommitListener {

    static final DefaultToUtf8Listener INSTANCE = new DefaultToUtf8Listener();

    @Override
    public void beforeCommit(HttpServerExchange exchange) {
      HeaderValues headerValues =
          exchange.getResponseHeaders().get(CONTENT_TYPE);

      if (headerValues == null || headerValues.size() != 1)
        return;

      String onlyHeaderValue = Iterables.getOnlyElement(headerValues);
      MediaType mediaType;
      try {
        mediaType = MediaType.parse(onlyHeaderValue);
      } catch (IllegalArgumentException ignored) {
        return;
      }

      if (mediaType.charset().isPresent() || !isTextType(mediaType))
        return;

      MediaType newMediaType = mediaType.withCharset(UTF_8);
      exchange.getResponseHeaders().put(CONTENT_TYPE, newMediaType.toString());
    }

    private static boolean isTextType(MediaType mediaType) {
      for (MediaType textType : KNOWN_TEXT_TYPES)
        if (mediaType.is(textType))
          return true;

      return false;
    }

    private static ImmutableSet<MediaType> KNOWN_TEXT_TYPES =
        ImmutableSet.of(
            ANY_TEXT_TYPE,
            MediaType.create("application", "javascript"));
  }
}
