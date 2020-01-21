package tfb.status.handler;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.net.MediaType.HTML_UTF_8;
import static io.undertow.util.Headers.CONTENT_TYPE;
import static io.undertow.util.Methods.GET;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.DisableCacheHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Objects;
import java.util.Properties;
import javax.inject.Inject;
import javax.inject.Singleton;
import tfb.status.handler.routing.ExactPath;
import tfb.status.hk2.extensions.Provides;
import tfb.status.service.MustacheRenderer;
import tfb.status.undertow.extensions.HttpHandlers;
import tfb.status.undertow.extensions.MethodHandler;
import tfb.status.view.AboutPageView;
import tfb.status.view.AboutPageView.GitPropertyView;

/**
 * Handles requests for the about page.
 */
@Singleton
public final class AboutPageHandler implements HttpHandler {
  private final MustacheRenderer mustacheRenderer;

  @Inject
  public AboutPageHandler(MustacheRenderer mustacheRenderer) {
    this.mustacheRenderer = Objects.requireNonNull(mustacheRenderer);
  }

  @Provides
  @Singleton
  @ExactPath("/about")
  public HttpHandler aboutPageHandler() {
    return HttpHandlers.chain(
        this,
        handler -> new MethodHandler().addMethod(GET, handler),
        handler -> new DisableCacheHandler(handler));
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws IOException {
    ImmutableMap<String, String> gitProperties;

    try (InputStream inputStream =
             Thread.currentThread()
                   .getContextClassLoader()
                   .getResourceAsStream("git.properties")) {

      if (inputStream == null)
        gitProperties = ImmutableMap.of();

      else {
        try (var reader = new InputStreamReader(inputStream, UTF_8)) {
          var props = new Properties();
          props.load(reader);
          gitProperties = Maps.fromProperties(props);
        }
      }
    }

    var aboutPageView =
        new AboutPageView(
            /* gitProperties= */
            gitProperties
                .entrySet()
                .stream()
                .map(
                    entry ->
                        new GitPropertyView(
                            /* name= */ entry.getKey(),
                            /* value= */ entry.getValue()))
                .collect(toImmutableList()));

    String html = mustacheRenderer.render("about.mustache", aboutPageView);
    exchange.getResponseHeaders().put(CONTENT_TYPE, HTML_UTF_8.toString());
    exchange.getResponseSender().send(html, UTF_8);
  }
}
