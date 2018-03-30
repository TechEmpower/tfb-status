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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import javax.inject.Inject;
import javax.inject.Singleton;
import tfb.status.service.MustacheRenderer;
import tfb.status.undertow.extensions.MethodHandler;
import tfb.status.view.AboutPageView;
import tfb.status.view.AboutPageView.GitPropertyView;

/**
 * Handles requests for the about page.
 */
@Singleton
public final class AboutPageHandler implements HttpHandler {
  private final HttpHandler delegate;

  @Inject
  public AboutPageHandler(MustacheRenderer mustacheRenderer, Clock clock) {
    HttpHandler handler = new CoreHandler(mustacheRenderer, clock);

    handler = new MethodHandler().addMethod(GET, handler);
    handler = new DisableCacheHandler(handler);

    delegate = handler;
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    delegate.handleRequest(exchange);
  }

  private static final class CoreHandler implements HttpHandler {
    private final MustacheRenderer mustacheRenderer;
    private final Clock clock;
    private final Instant startTime;

    CoreHandler(MustacheRenderer mustacheRenderer, Clock clock) {
      this.mustacheRenderer = Objects.requireNonNull(mustacheRenderer);
      this.clock = Objects.requireNonNull(clock);
      this.startTime = clock.instant();
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
      Instant now = clock.instant();
      Duration uptime = Duration.between(startTime, now);

      ImmutableMap<String, String> gitProperties;
      try (InputStream in = Thread.currentThread()
                                  .getContextClassLoader()
                                  .getResourceAsStream("git.properties")) {
        if (in == null)
          gitProperties = ImmutableMap.of();

        else {
          try (InputStreamReader reader = new InputStreamReader(in, UTF_8)) {
            Properties properties = new Properties();
            properties.load(reader);
            gitProperties = Maps.fromProperties(properties);
          }
        }
      }

      AboutPageView aboutPageView =
          new AboutPageView(
              /* uptime= */ formatDuration(uptime),
              /* gitProperties= */
              gitProperties.entrySet()
                           .stream()
                           .map(entry -> new GitPropertyView(
                               /* name= */ entry.getKey(),
                               /* value= */ entry.getValue()))
                           .collect(toImmutableList()));

      String html = mustacheRenderer.render("about.mustache", aboutPageView);
      exchange.getResponseHeaders().put(CONTENT_TYPE, HTML_UTF_8.toString());
      exchange.getResponseSender().send(html, UTF_8);
    }

    /**
     * Returns a string representing the duration like "1d 3h 12m 55s".
     */
    private static String formatDuration(Duration duration) {
      Objects.requireNonNull(duration);

      long days = duration.toDaysPart();
      int hours = duration.toHoursPart();
      int minutes = duration.toMinutesPart();
      int seconds = duration.toSecondsPart();

      NumberFormat integerFormat = NumberFormat.getIntegerInstance(Locale.ROOT);
      List<String> parts = new ArrayList<>();

      if (days > 0)    parts.add(integerFormat.format(days) + "d");
      if (hours > 0)   parts.add(integerFormat.format(hours) + "h");
      if (minutes > 0) parts.add(integerFormat.format(minutes) + "m");
      if (seconds > 0) parts.add(integerFormat.format(seconds) + "s");

      return parts.isEmpty() ? "0s" : String.join(" ", parts);
    }
  }
}
