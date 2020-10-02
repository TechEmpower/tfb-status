package tfb.status.handler;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Objects;
import java.util.Properties;
import javax.inject.Inject;
import javax.inject.Singleton;
import tfb.status.handler.routing.DisableCache;
import tfb.status.handler.routing.Route;
import tfb.status.service.MustacheRenderer;
import tfb.status.view.AboutPageView;
import tfb.status.view.AboutPageView.GitPropertyView;

/**
 * Handles requests for the about page.
 */
@Singleton
@Route(method = "GET", path = "/about", produces = "text/html; charset=utf-8")
@DisableCache
public final class AboutPageHandler implements HttpHandler {
  private final MustacheRenderer mustacheRenderer;

  @Inject
  public AboutPageHandler(MustacheRenderer mustacheRenderer) {
    this.mustacheRenderer = Objects.requireNonNull(mustacheRenderer);
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws IOException {
    ImmutableMap<String, String> gitProperties;
    ClassLoader classLoader = getClass().getClassLoader();

    try (InputStream inputStream =
             classLoader.getResourceAsStream("git.properties")) {

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
    exchange.getResponseSender().send(html, UTF_8);
  }
}
