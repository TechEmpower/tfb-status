package tfb.status.undertow.extensions;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Objects;

/**
 * Writes a fixed value to the body of all responses.
 *
 * <p>This handler does not modify any response headers such as the {@code
 * Content-Type} header.
 */
public final class FixedResponseBodyHandler implements HttpHandler {
  private final ByteBuffer buffer;

  /**
   * Constructs a new handler that writes a string in UTF-8.
   *
   * @param responseBody the body of all responses from this handler
   */
  public FixedResponseBodyHandler(String responseBody) {
    this(responseBody, UTF_8);
  }

  /**
   * Constructs a new handler that writes a string in the provided charset.
   *
   * @param responseBody the body of all responses from this handler
   * @param charset the charset for encoding the string to bytes
   */
  public FixedResponseBodyHandler(String responseBody, Charset charset) {
    Objects.requireNonNull(responseBody);
    Objects.requireNonNull(charset);
    byte[] bytes = responseBody.getBytes(charset);
    this.buffer = ByteBuffer.wrap(bytes).asReadOnlyBuffer();
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) {
    exchange.getResponseSender().send(buffer.duplicate());
  }
}
