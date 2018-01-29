package tfb.status.handler;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.net.MediaType.HTML_UTF_8;
import static io.undertow.util.Headers.CONTENT_TYPE;
import static io.undertow.util.Methods.GET;
import static io.undertow.util.StatusCodes.BAD_REQUEST;
import static io.undertow.util.StatusCodes.NOT_FOUND;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.comparing;
import static tfb.status.service.ParsedResultsUtils.TEST_TYPES;
import static tfb.status.service.ParsedResultsUtils.getRps;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.DisableCacheHandler;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.config.FileStoreConfig;
import tfb.status.service.MustacheRenderer;
import tfb.status.undertow.extensions.MethodHandler;
import tfb.status.util.ZipFiles;
import tfb.status.view.ParsedResults;
import tfb.status.view.TimelinePageView;
import tfb.status.view.TimelinePageView.DataPointView;
import tfb.status.view.TimelinePageView.FrameworkOptionView;
import tfb.status.view.TimelinePageView.TestTypeOptionView;

/**
 * Handles requests for the timeline page.
 */
@Singleton
public final class TimelinePageHandler implements HttpHandler {
  private final HttpHandler delegate;

  @Inject
  public TimelinePageHandler(FileStoreConfig fileStoreConfig,
                             MustacheRenderer mustacheRenderer,
                             ObjectMapper objectMapper) {

    HttpHandler handler = new CoreHandler(fileStoreConfig,
                                          mustacheRenderer,
                                          objectMapper);

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
    private final ObjectMapper objectMapper;
    private final Path resultsDirectory;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    CoreHandler(FileStoreConfig fileStoreConfig,
                MustacheRenderer mustacheRenderer,
                ObjectMapper objectMapper) {
      this.mustacheRenderer = Objects.requireNonNull(mustacheRenderer);
      this.objectMapper = Objects.requireNonNull(objectMapper);
      this.resultsDirectory = Paths.get(fileStoreConfig.resultsDirectory);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {

      String relativePath = exchange.getRelativePath();
      Matcher matcher = REQUEST_PATH_PATTERN.matcher(relativePath);

      if (!matcher.matches()) {
        exchange.setStatusCode(BAD_REQUEST);
        return;
      }

      String selectedFramework = matcher.group("framework");
      String selectedTestType = matcher.group("testType");

      if (!TEST_TYPES.contains(selectedTestType)) {
        exchange.setStatusCode(NOT_FOUND);
        return;
      }

      Set<String> allFrameworks = new HashSet<>();
      Set<String> absentTestTypes = new HashSet<>(TEST_TYPES);
      List<DataPointView> dataPoints = new ArrayList<>();

      for (Path zipFile : listFiles(resultsDirectory, "*.zip")) {
        ParsedResults results;
        try {
          results =
              ZipFiles.readZipEntry(
                  /* zipFile= */ zipFile,
                  /* entryPath= */ "results.json",
                  /* entryReader= */ in -> objectMapper.readValue(in, ParsedResults.class));
        } catch (IOException e) {
          logger.warn(
              "Ignoring results.zip file {} whose results.json file "
                  + "could not be parsed",
              zipFile, e);
          continue;
        }

        if (results == null) {
          logger.warn(
              "Ignoring results.zip file {} that did not contain a "
                  + "results.json file",
              zipFile);
          continue;
        }

        if (results.startTime == null)
          // We could try to read the timestamp from somewhere else, but
          // it's not worth the added complexity.
          continue;

        allFrameworks.addAll(results.frameworks);

        absentTestTypes.removeIf(
            testType -> getRps(/* results= */ results,
                               /* testType= */ testType,
                               /* framework= */ selectedFramework)
                        != 0);

        double rps = getRps(/* results= */ results,
                            /* testType= */ selectedTestType,
                            /* framework= */ selectedFramework);

        if (rps != 0) {
          dataPoints.add(
              new DataPointView(
                  /* time= */ results.startTime,
                  /* rps= */ rps));
        }
      }

      if (!allFrameworks.contains(selectedFramework)) {
        exchange.setStatusCode(NOT_FOUND);
        return;
      }

      dataPoints.sort(comparing(dataPoint -> dataPoint.time));

      ImmutableList<TestTypeOptionView> testTypeOptions =
          TEST_TYPES.stream()
                    .sorted()
                    .map(testType -> new TestTypeOptionView(
                         /* testType= */ testType,
                         /* isPresent= */ !absentTestTypes.contains(testType),
                         /* isSelected= */ testType.equals(selectedTestType)))
                    .collect(toImmutableList());

      ImmutableList<FrameworkOptionView> frameworkOptions =
          allFrameworks.stream()
                       .sorted(String.CASE_INSENSITIVE_ORDER)
                       .map(framework -> new FrameworkOptionView(
                           /* framework= */ framework,
                           /* isSelected= */ framework.equals(selectedFramework)))
                       .collect(toImmutableList());

      TimelinePageView timelinePageView =
          new TimelinePageView(
              /* framework= */ selectedFramework,
              /* testType= */ selectedTestType,
              /* dataPoints= */ ImmutableList.copyOf(dataPoints),
              /* testTypeOptions= */ testTypeOptions,
              /* frameworkOptions= */ frameworkOptions);

      String html = mustacheRenderer.render("timeline.mustache", timelinePageView);
      exchange.getResponseHeaders().put(CONTENT_TYPE, HTML_UTF_8.toString());
      exchange.getResponseSender().send(html, UTF_8);
    }

    private static ImmutableList<Path> listFiles(Path directory, String glob)
        throws IOException {

      Objects.requireNonNull(directory);
      Objects.requireNonNull(glob);

      if (!Files.isDirectory(directory))
        return ImmutableList.of();

      try (DirectoryStream<Path> files =
               Files.newDirectoryStream(directory, glob)) {

        return ImmutableList.copyOf(files);
      }
    }

    // Matches "/gemini-mysql/fortune", for example.
    private static final Pattern REQUEST_PATH_PATTERN =
        Pattern.compile("^/(?<framework>[\\w-]+)/(?<testType>[\\w-]+)$");
  }
}
