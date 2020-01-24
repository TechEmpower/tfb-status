package tfb.status.handler;

import static com.google.common.net.MediaType.HTML_UTF_8;
import static io.undertow.util.Headers.CONTENT_TYPE;
import static io.undertow.util.Methods.GET;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.DisableCacheHandler;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import tfb.status.handler.routing.ExactPath;
import tfb.status.hk2.extensions.Provides;
import tfb.status.service.MustacheRenderer;
import tfb.status.service.ShareResultsUploader;
import tfb.status.undertow.extensions.HttpHandlers;
import tfb.status.undertow.extensions.MethodHandler;

/**
 * Handler that renders the standalone paste bin style page for sharing results
 * files. Allows anonymous users to upload a results.json file or paste in the
 * contents of a results.json file in order to share it.
 *
 * @see ShareResultsUploader
 */
@Singleton
public final class ShareResultsPageHandler implements HttpHandler {
  private final MustacheRenderer mustacheRenderer;

  @Inject
  public ShareResultsPageHandler(MustacheRenderer mustacheRenderer) {
    this.mustacheRenderer = Objects.requireNonNull(mustacheRenderer);
  }

  @Provides
  @Singleton
  @ExactPath("/share-results/pastebin")
  public HttpHandler shareResultsPageHandler() {
    return HttpHandlers.chain(
        this,
        handler -> new MethodHandler().addMethod(GET, handler),
        handler -> new DisableCacheHandler(handler));
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) {
    String html = mustacheRenderer.render("share-results.mustache");
    exchange.getResponseHeaders().put(CONTENT_TYPE, HTML_UTF_8.toString());
    exchange.getResponseSender().send(html, UTF_8);
  }
}
