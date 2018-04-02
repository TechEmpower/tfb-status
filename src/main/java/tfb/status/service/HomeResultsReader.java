package tfb.status.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.comparing;
import static java.util.Comparator.nullsLast;
import static java.util.Comparator.reverseOrder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.Immutable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.config.FileStoreConfig;
import tfb.status.util.OtherFiles;
import tfb.status.util.ZipFiles;
import tfb.status.view.HomePageView.ResultsGitView;
import tfb.status.view.HomePageView.ResultsJsonView;
import tfb.status.view.HomePageView.ResultsView;
import tfb.status.view.HomePageView.ResultsZipView;
import tfb.status.view.HomePageView.ResultsZipView.Failure;
import tfb.status.view.Results;

/**
 * Loads previously-uploaded results for display on the home page.
 */
@Singleton
public final class HomeResultsReader {
  private final Path resultsDirectory;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final LoadingCache<ViewCacheKey, ResultsJsonView> jsonCache =
      Caffeine.newBuilder()
              .maximumSize(INSANE_NUMBER_OF_CACHE_ENTRIES)
              .build(key -> viewJsonFile(key.file));

  private final LoadingCache<ViewCacheKey, ResultsZipView> zipCache =
      Caffeine.newBuilder()
              .maximumSize(INSANE_NUMBER_OF_CACHE_ENTRIES)
              .build(key -> viewZipFile(key.file));

  @Inject
  public HomeResultsReader(FileStoreConfig fileStoreConfig,
                           ObjectMapper objectMapper,
                           Clock clock) {
    this.resultsDirectory = Paths.get(fileStoreConfig.resultsDirectory);
    this.objectMapper = Objects.requireNonNull(objectMapper);
    this.clock = Objects.requireNonNull(clock);
  }

  /**
   * Returns a view of all the previously-uploaded results, suitable for
   * rendering on the home page.
   *
   * @return a view of the results
   * @throws IOException if an I/O error occurs while reading the results
   */
  public ImmutableList<ResultsView> results() throws IOException {
    var jsonByUuid = new HashMap<String, ResultsJsonView>();
    var jsonWithoutUuid = new ArrayList<ResultsJsonView>();

    viewAllJsonFiles().forEach(
        (ResultsJsonView view) -> {
          if (view.uuid == null)
            jsonWithoutUuid.add(view);

          else
            jsonByUuid.merge(
                view.uuid,
                view,
                (v1, v2) -> {
                  logger.warn(
                      "Ignoring results.json file {}, which has the same "
                          + "uuid ({}) as another results.json file.",
                      view.fileName, view.uuid);
                  return v1;
                });
        });

    var zipByUuid = new HashMap<String, ResultsZipView>();
    var zipWithoutUuid = new ArrayList<ResultsZipView>();

    viewAllZipFiles().forEach(
        (ResultsZipView view) -> {
          if (view.uuid == null)
            zipWithoutUuid.add(view);

          else
            zipByUuid.merge(
                view.uuid,
                view,
                (v1, v2) -> {
                  logger.warn(
                      "Ignoring results.zip file {}, which has the same "
                          + "uuid ({}) as another results.zip file.",
                      view.fileName, view.uuid);
                  return v1;
                });
        });

    var results = new ArrayList<ResultsView>();

    Set<String> uuids = Sets.union(jsonByUuid.keySet(),
                                   zipByUuid.keySet());

    for (String uuid : uuids) {
      ResultsJsonView json = jsonByUuid.get(uuid);
      ResultsZipView zip = zipByUuid.get(uuid);
      results.add(new ResultsView(json, zip));
    }

    for (ResultsJsonView json : jsonWithoutUuid)
      results.add(
          new ResultsView(
              /* json= */ json,
              /* zip= */ null));

    for (ResultsZipView zip : zipWithoutUuid)
      results.add(
          new ResultsView(
              /* json= */ null,
              /* zip= */ zip));

    return ImmutableList.sortedCopyOf(RESULTS_COMPARATOR, results);
  }

  /**
   * Returns a view of the only previously-uploaded results having the given
   * UUID, suitable for rendering on the home page, or {@code null} if there are
   * no results with the given UUID.
   *
   * @param resultsUuid the UUID of the results to be viewed
   * @return a view of the results, or {@code null} if there are no matching
   *         results
   * @throws IOException if an I/O error occurs while reading the results
   */
  @Nullable
  public ResultsView resultsByUuid(String resultsUuid) throws IOException {
    Objects.requireNonNull(resultsUuid);

    ResultsJsonView json =
        viewAllJsonFiles()
            .filter(view -> resultsUuid.equals(view.uuid))
            .findAny()
            .orElse(null);

    ResultsZipView zip =
        viewAllZipFiles()
            .filter(view -> resultsUuid.equals(view.uuid))
            .findAny()
            .orElse(null);

    return (json == null && zip == null)
        ? null
        : new ResultsView(json, zip);
  }

  private Stream<ResultsJsonView> viewAllJsonFiles() throws IOException {
    Stream.Builder<ViewCacheKey> keys = Stream.builder();
    for (Path file : OtherFiles.listFiles(resultsDirectory, "*.json")) {
      keys.add(new ViewCacheKey(file));
    }
    return keys.build()
               .map(key -> jsonCache.get(key))
               .filter(view -> view != null);
  }

  private Stream<ResultsZipView> viewAllZipFiles() throws IOException {
    Stream.Builder<ViewCacheKey> keys = Stream.builder();
    for (Path file : OtherFiles.listFiles(resultsDirectory, "*.zip")) {
      keys.add(new ViewCacheKey(file));
    }
    return keys.build()
               .map(key -> zipCache.get(key))
               .filter(view -> view != null);
  }

  @Nullable
  private ResultsJsonView viewJsonFile(Path jsonFile) {
    Objects.requireNonNull(jsonFile);

    Results results;
    try {
      results = objectMapper.readValue(jsonFile.toFile(), Results.class);
    } catch (IOException e) {
      logger.warn("Exception reading json file {}", jsonFile, e);
      return null;
    }

    String uuid = results.uuid;
    String name = results.name;
    String environmentDescription = results.environmentDescription;

    // The "completed" map in the results includes frameworks that won't show up
    // in the "succeeded" or "failed" maps because they had an error before they
    // could execute any of the test types.  For example, this will happen when
    // a rogue process holds onto a common port like 8080 and prevents a lot of
    // frameworks from starting up.
    int frameworksWithCleanSetup = 0;
    int frameworksWithSetupProblems = 0;

    for (Map.Entry<String, String> entry : results.completed.entrySet()) {
      String framework = entry.getKey();
      String message = entry.getValue();
      if (isCompletedTimestamp(message))
        frameworksWithCleanSetup++;
      else
        frameworksWithSetupProblems++;
    }

    int completedFrameworks =
        frameworksWithCleanSetup + frameworksWithSetupProblems;

    int totalFrameworks = results.frameworks.size();
    int successfulTests = results.succeeded.values().size();
    int failedTests = results.failed.values().size();

    LocalDateTime startTime =
        (results.startTime == null)
            ? null
            : epochMillisToDateTime(results.startTime, clock.getZone());

    LocalDateTime completionTime =
        (results.completionTime == null)
            ? null
            : epochMillisToDateTime(results.completionTime, clock.getZone());

    Duration elapsedDuration;
    Duration estimatedRemainingDuration;

    if (startTime == null)
      elapsedDuration = null;

    else {
      LocalDateTime endTime =
          (completionTime == null)
              ? LocalDateTime.now(clock)
              : completionTime;
      elapsedDuration = Duration.between(startTime, endTime);
    }

    if (completionTime != null
        || startTime == null
        || elapsedDuration == null
        || completedFrameworks == 0)
      estimatedRemainingDuration = null;

    else
      estimatedRemainingDuration =
          elapsedDuration.multipliedBy(totalFrameworks)
                         .dividedBy(completedFrameworks)
                         .minus(elapsedDuration);

    //
    // TODO: Avoid using the last modified time of the file on disk (which may
    //       change for reasons completely unrelated to the run itself), and use
    //       something from the results.json file to give us a last modified
    //       time instead.  The datetime strings in the "completed" object are
    //       no good because they are local to the TFB server, and we don't know
    //       that server's time zone.
    //
    FileTime lastUpdatedTime;
    try {
      lastUpdatedTime = Files.getLastModifiedTime(jsonFile);
    } catch (IOException e) {
      logger.warn("Exception reading last modified time of file {}", jsonFile, e);
      return null;
    }

    DateTimeFormatter displayedTimeFormatter =
        DateTimeFormatter.ofPattern(
            "yyyy-MM-dd 'at' h:mm a",
            Locale.ROOT);

    String startTimeString =
        (startTime == null)
            ? null
            : displayedTimeFormatter.format(startTime);

    String completionTimeString =
        (completionTime == null)
            ? null
            : displayedTimeFormatter.format(completionTime);

    String lastUpdatedString =
        lastUpdatedTime.toInstant()
                       .atZone(clock.getZone())
                       .toLocalDateTime()
                       .format(displayedTimeFormatter);

    String elapsedDurationString =
        (elapsedDuration == null)
            ? null
            : formatDuration(elapsedDuration);

    String estimatedRemainingDurationString =
        (estimatedRemainingDuration == null)
            ? null
            : formatDuration(estimatedRemainingDuration);

    Path relativePath = resultsDirectory.relativize(jsonFile);
    String fileName = Joiner.on('/').join(relativePath);

    ResultsGitView git;
    if (results.git == null) {
      git = null;
    } else {
      git = new ResultsGitView(
          /* commitId= */ results.git.commitId,
          /* repositoryUrl= */ results.git.repositoryUrl,
          /* branchName= */ results.git.branchName);
    }

    return new ResultsJsonView(
        /* uuid= */ uuid,
        /* git= */ git,
        /* fileName= */ fileName,
        /* name= */ name,
        /* environmentDescription= */ environmentDescription,
        /* startTime= */ startTimeString,
        /* completionTime= */ completionTimeString,
        /* completedFrameworks= */ completedFrameworks,
        /* frameworksWithCleanSetup= */ frameworksWithCleanSetup,
        /* frameworksWithSetupProblems= */ frameworksWithSetupProblems,
        /* totalFrameworks= */ totalFrameworks,
        /* successfulTests= */ successfulTests,
        /* failedTests= */ failedTests,
        /* lastUpdated= */ lastUpdatedString,
        /* elapsedDuration= */ elapsedDurationString,
        /* estimatedRemainingDuration= */ estimatedRemainingDurationString);
  }

  @Nullable
  private ResultsZipView viewZipFile(Path zipFile) {
    Objects.requireNonNull(zipFile);

    Results results;
    try {
      results =
          ZipFiles.readZipEntry(
              /* zipFile= */ zipFile,
              /* entryPath= */ "results.json",
              /* entryReader= */ inputStream -> objectMapper.readValue(inputStream, Results.class));
    } catch (IOException e) {
      logger.warn("Exception reading zip file {}", zipFile, e);
      return null;
    }

    //
    // If the zip doesn't contain a results.json at all, then we have nothing
    // useful to say to users about it, and we want to pretend it doesn't exist.
    //
    if (results == null)
      return null;

    String uuid = results.uuid;
    Path relativePath = resultsDirectory.relativize(zipFile);
    String fileName = Joiner.on('/').join(relativePath);

    var failures = new ArrayList<Failure>();

    results.failed.inverse().asMap().forEach(
        (String framework, Collection<String> failedTestTypes) -> {
          failures.add(
              new Failure(
                  /* framework= */ framework,
                  /* failedTestTypes= */ ImmutableList.sortedCopyOf(failedTestTypes)));
        });

    results.completed.forEach(
        (String framework, String message) -> {
          if (!results.failed.inverse().containsKey(framework)
              && !isCompletedTimestamp(message)) {
            failures.add(
                new Failure(
                    /* framework= */ framework,
                    /* failedTestTypes= */ ImmutableList.of()));
          }
        });

    failures.sort(comparing(failure -> failure.framework,
                            String.CASE_INSENSITIVE_ORDER));

    ResultsGitView git;
    if (results.git == null) {

      // We used to collect the git commit id as a separate "commit_id.txt" file.
      String gitCommitId;
      try {
        gitCommitId =
            ZipFiles.readZipEntry(
                /* zipFile= */ zipFile,
                /* entryPath= */ "commit_id.txt",
                /* entryReader= */
                inputStream -> {
                  try (var reader = new BufferedReader(new InputStreamReader(inputStream, UTF_8))) {
                    return reader.readLine();
                  }
                });
      } catch (IOException e) {
        logger.warn("Exception reading git commit id from zip file {}", zipFile, e);
        gitCommitId = null;
      }

      if (gitCommitId == null) {
        git = null;
      } else {
        git = new ResultsGitView(
            /* commitId= */ gitCommitId,
            /* repositoryUrl= */ null,
            /* branchName= */ null);
      }

    } else {
      git = new ResultsGitView(
          /* commitId= */ results.git.commitId,
          /* repositoryUrl= */ results.git.repositoryUrl,
          /* branchName= */ results.git.branchName);
    }

    return new ResultsZipView(
        /* uuid= */ uuid,
        /* git= */ git,
        /* fileName= */ fileName,
        /* failures= */ ImmutableList.copyOf(failures));
  }

  @Immutable
  private static final class ViewCacheKey {
    final Path file;

    // When the file is modified, this cache key becomes unreachable.
    final FileTime lastModifiedTime;

    ViewCacheKey(Path file) throws IOException {
      this.file = Objects.requireNonNull(file);
      this.lastModifiedTime = Files.getLastModifiedTime(file);
    }

    @Override
    public boolean equals(@Nullable Object object) {
      if (object == this)
        return true;

      if (!(object instanceof ViewCacheKey))
        return false;

      ViewCacheKey that = (ViewCacheKey) object;
      return this.file.equals(that.file)
          && this.lastModifiedTime.equals(that.lastModifiedTime);
    }

    @Override
    public int hashCode() {
      return file.hashCode() ^ lastModifiedTime.hashCode();
    }
  }

  private static LocalDateTime epochMillisToDateTime(long epochMillis,
                                                     ZoneId zone) {
    Objects.requireNonNull(zone);
    Instant instant = Instant.ofEpochMilli(epochMillis);
    return LocalDateTime.ofInstant(instant, zone);
  }

  private static String formatDuration(Duration duration) {
    long seconds = duration.toSeconds();
    long minutes = seconds / 60;
    long hours = minutes / 60;
    seconds %= 60;
    minutes %= 60;

    if (minutes >= 30)
      hours++;

    if (seconds >= 30)
      minutes++;

    if (hours > 0)
      return "~"
          + NumberFormat.getIntegerInstance(Locale.ROOT).format(hours)
          + " hour"
          + ((hours == 1) ? "" : "s");

    if (minutes > 0)
      return "~"
          + NumberFormat.getIntegerInstance(Locale.ROOT).format(minutes)
          + " minute"
          + ((minutes == 1) ? "" : "s");

    return "< 1 minute";
  }

  /**
   * {@code true} if the message looks like a timestamp in the {@link
   * Results#completed} map.
   *
   * @param message a value from the {@link Results#completed} map
   * @return {@code true} if the value is a timestamp, indicating that the
   *         framework started and stopped correctly, or {@code false} if the
   *         message is an error message, indicating that the framework did not
   *         start or stop correctly
   */
  private static boolean isCompletedTimestamp(String message) {
    Objects.requireNonNull(message);
    try {
      LocalDateTime.parse(message, COMPLETED_TIMESTAMP_FORMATTER);
      return true;
    } catch (DateTimeParseException ignored) {
      return false;
    }
  }

  private static final DateTimeFormatter COMPLETED_TIMESTAMP_FORMATTER =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ROOT);

  /**
   * The ordering of results displayed on the home page.
   */
  private static final Comparator<ResultsView> RESULTS_COMPARATOR;
  static {
    //
    // In practice, the results files are named like this:
    //
    //   results.{uploaded_at_date_time}.{json|zip}.
    //
    // Therefore, sorting by file name effectively sorts the results by when
    // they were uploaded, and this comparator puts the most recently uploaded
    // results first.
    //

    Comparator<ResultsJsonView> jsonFilesOrderedByFileName =
        comparing(json -> json.fileName,
                  reverseOrder());

    Comparator<ResultsZipView> zipFilesOrderedByFileName =
        comparing(zip -> zip.fileName,
                  reverseOrder());

    Comparator<ResultsView> resultsOrderedByJsonFile =
        comparing(results -> results.json,
                  nullsLast(jsonFilesOrderedByFileName));

    Comparator<ResultsView> resultsOrderedByZipFile =
        comparing(results -> results.zip,
                  nullsLast(zipFilesOrderedByFileName));

    RESULTS_COMPARATOR =
        resultsOrderedByJsonFile.thenComparing(resultsOrderedByZipFile);
  }

  /**
   * Far more entries in the view cache than we should ever need.
   */
  private static final int INSANE_NUMBER_OF_CACHE_ENTRIES = 10_000;
}
