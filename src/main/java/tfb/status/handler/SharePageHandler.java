package tfb.status.handler;

import static com.google.common.net.MediaType.HTML_UTF_8;
import static io.undertow.util.Headers.CONTENT_TYPE;
import static io.undertow.util.Methods.GET;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.DisableCacheHandler;
import java.io.IOException;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import tfb.status.handler.routing.ExactPath;
import tfb.status.hk2.extensions.Provides;
import tfb.status.service.MustacheRenderer;
import tfb.status.service.ShareManager;
import tfb.status.undertow.extensions.HttpHandlers;
import tfb.status.undertow.extensions.MethodHandler;

/**
 * Handles requests to render the pastebin-style HTML interface for sharing
 * results.json files.  This page allows anonymous users to upload a local
 * results.json file as a file or to enter the contents of a results.json file
 * as text.
 */
@Singleton
public final class SharePageHandler implements HttpHandler {
  private final MustacheRenderer mustacheRenderer;

  @Inject
  public SharePageHandler(MustacheRenderer mustacheRenderer) {
    this.mustacheRenderer = Objects.requireNonNull(mustacheRenderer);
  }

  @Provides
  @Singleton
  @ExactPath("/share")
  public HttpHandler shareResultsPageHandler(ShareManager shareManager)
      throws IOException {

    // TODO: Once the zip files have been migrated, delete this.
    shareManager.migrateZipFiles();

    return HttpHandlers.chain(
        this,
        handler -> new MethodHandler().addMethod(GET, handler),
        handler -> new DisableCacheHandler(handler));
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) {
    String html = mustacheRenderer.render("share.mustache");
    exchange.getResponseHeaders().put(CONTENT_TYPE, HTML_UTF_8.toString());
    exchange.getResponseSender().send(html, UTF_8);
  }
}
