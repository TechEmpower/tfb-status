package tfb.status.service;

import static java.util.Comparator.comparing;
import static java.util.Comparator.nullsLast;
import static java.util.Comparator.reverseOrder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.io.MoreFiles;
import com.google.errorprone.annotations.Immutable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.config.FileStoreConfig;
import tfb.status.util.ZipFiles;
import tfb.status.view.HomePageView.ResultsJsonView;
import tfb.status.view.HomePageView.ResultsView;
import tfb.status.view.HomePageView.ResultsZipView;
import tfb.status.view.HomePageView.ResultsZipView.Failure;
import tfb.status.view.ParsedResults;

/**
 * Loads previously-uploaded results for display on the home page.
 */
@Singleton
public final class HomeResultsReader {
  private final Path resultsDirectory;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final Logger logger = LoggerFactory.getLogger(getClass());

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
   */
  public ImmutableList<ResultsView> results()  {
    RawFiles raw = rawFiles(resultsDirectory);
    JsonFilesByUuid jsonFiles = jsonFilesByUuid(raw.json, resultsDirectory);
    ZipFilesByUuid zipFiles = zipFilesByUuid(raw.zip, resultsDirectory);
    return combine(jsonFiles, zipFiles);
  }

  /**
   * Returns a view of the only previously-uploaded results having the given
   * UUID, suitable for rendering on the home page, or {@code null} if there are
   * no results with the given UUID.
   *
   * @param resultsUuid the UUID of the results to be viewed
   * @return a view of the results, or {@code null} if there are no matching
   *         results
   */
  @Nullable
  public ResultsView resultsByUuid(String resultsUuid) {
    Objects.requireNonNull(resultsUuid);
    return results().stream()
                    .filter(results -> resultsUuid.equals(results.uuid))
                    .findFirst()
                    .orElse(null);
  }

  /**
   * Returns the list of the results.json and results.zip files from the given
   * directory.  The search is non-recursive.  If the given path does not
   * represent a directory, then the returned list is empty.
   *
   * @param directory the directory to be read
   * @return the list of results files
   */
  private RawFiles rawFiles(Path directory) {
    Objects.requireNonNull(directory);

    ImmutableList.Builder<Path> json = ImmutableList.builder();
    ImmutableList.Builder<Path> zip = ImmutableList.builder();

    if (Files.isDirectory(directory)) {
      try (DirectoryStream<Path> resultsFiles =
               Files.newDirectoryStream(directory, "results*.{json,zip}")) {

        for (Path file : resultsFiles) {
          switch (MoreFiles.getFileExtension(file)) {
            case "json": json.add(file); break;
            case "zip":  zip.add(file);  break;

            default:
              throw new AssertionError(
                  "We limited our search to .json and .zip files only, "
                      + "but we found a file \""
                      + file
                      + "\" with extension \""
                      + MoreFiles.getFileExtension(file)
                      + "\"");
          }
        }

      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    return new RawFiles(json.build(), zip.build());
  }

  @Immutable
  private static final class RawFiles {
    /**
     * The results.json files in this directory in no particular order.
     */
    final ImmutableList<Path> json;

    /**
     * The results.zip files in this directory in no particular order.
     */
    final ImmutableList<Path> zip;

    RawFiles(ImmutableList<Path> json, ImmutableList<Path> zip) {
      this.json = Objects.requireNonNull(json);
      this.zip = Objects.requireNonNull(zip);
    }
  }

  /**
   * Given a list of raw results.json files, produces a view of that file
   * suitable for display on the home page, then puts the views into buckets
   * based on the uuid of each set of results.
   */
  private JsonFilesByUuid jsonFilesByUuid(List<Path> jsonFiles, Path directory) {
    Objects.requireNonNull(jsonFiles);
    Objects.requireNonNull(directory);

    Map<String, ResultsJsonView> byUuid = new HashMap<>();
    ImmutableList.Builder<ResultsJsonView> noUuid = ImmutableList.builder();

    for (Path jsonFile : jsonFiles) {
      ResultsJsonView view = viewJsonFileMaybeFromCache(jsonFile, directory);

      if (view == null)
        continue;

      if (view.uuid == null)
        noUuid.add(view);

      else
        byUuid.merge(
            view.uuid,
            view,
            (v1, v2) -> {
              logger.warn(
                  "Ignoring results.json file {}, which has the same uuid ({}) "
                      + "as another results.json file.",
                  jsonFile, view.uuid);
              return v1;
            });
    }

    return new JsonFilesByUuid(
        /* byUuid= */ ImmutableMap.copyOf(byUuid),
        /* noUuid= */ noUuid.build());
  }

  @Nullable
  private ResultsJsonView viewJsonFileMaybeFromCache(Path jsonFile, Path directory) {
    Objects.requireNonNull(jsonFile);
    Objects.requireNonNull(directory);
    ViewCacheKey key = new ViewCacheKey(jsonFile, directory);
    return jsonViewCache.get(key);
  }

  private final LoadingCache<ViewCacheKey, ResultsJsonView> jsonViewCache =
      Caffeine.newBuilder()
              .maximumSize(INSANE_NUMBER_OF_CACHE_ENTRIES)
              .build(key -> viewJsonFileNotCached(key.file, key.directory));

  @Nullable
  private ResultsJsonView viewJsonFileNotCached(Path jsonFile, Path directory) {
    Objects.requireNonNull(jsonFile);
    Objects.requireNonNull(directory);

    ParsedResults results;
    try {
      results = objectMapper.readValue(jsonFile.toFile(), ParsedResults.class);
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
    // TODO: Use something about the content of the results.json file to
    //       determine last updated time.  Unfortunately the datetime strings
    //       in the "completed" object do not specify their time zone even
    //       though they depend on the time zone of that particular TFB
    //       server.
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

    Path relativePath = directory.relativize(jsonFile);
    String fileName = Joiner.on('/').join(relativePath);

    return new ResultsJsonView(
        /* uuid= */ uuid,
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

  @Immutable
  private static final class JsonFilesByUuid {
    final ImmutableMap<String, ResultsJsonView> byUuid;
    final ImmutableList<ResultsJsonView> noUuid;

    JsonFilesByUuid(ImmutableMap<String, ResultsJsonView> byUuid,
                    ImmutableList<ResultsJsonView> noUuid) {
      this.byUuid = Objects.requireNonNull(byUuid);
      this.noUuid = Objects.requireNonNull(noUuid);
    }
  }

  /**
   * Given a list of raw results.zip files, produces a view of that file
   * suitable for display on the home page, then puts the views into buckets
   * based on the uuid of each set of results.
   */
  private ZipFilesByUuid zipFilesByUuid(List<Path> zipFiles, Path directory) {
    Objects.requireNonNull(zipFiles);
    Objects.requireNonNull(directory);

    Map<String, ResultsZipView> byUuid = new HashMap<>();
    ImmutableList.Builder<ResultsZipView> noUuid = ImmutableList.builder();

    for (Path zipFile : zipFiles) {
      ResultsZipView view = viewZipFileMaybeFromCache(zipFile, directory);

      if (view == null)
        continue;

      if (view.uuid == null)
        noUuid.add(view);

      else
        byUuid.merge(
            view.uuid,
            view,
            (v1, v2) -> {
              logger.warn(
                  "Ignoring results.zip file {}, which has the same "
                      + "uuid ({}) as another results.zip file.",
                  zipFile, view.uuid);
              return v1;
            });
    }

    return new ZipFilesByUuid(
        /* byUuid= */ ImmutableMap.copyOf(byUuid),
        /* noUuid= */ noUuid.build());
  }

  @Nullable
  private ResultsZipView viewZipFileMaybeFromCache(Path zipFile, Path directory) {
    Objects.requireNonNull(zipFile);
    Objects.requireNonNull(directory);
    ViewCacheKey key = new ViewCacheKey(zipFile, directory);
    return zipViewCache.get(key);
  }

  private final LoadingCache<ViewCacheKey, ResultsZipView> zipViewCache =
      Caffeine.newBuilder()
              .maximumSize(INSANE_NUMBER_OF_CACHE_ENTRIES)
              .build(key -> viewZipFileNotCached(key.file, key.directory));

  @Nullable
  private ResultsZipView viewZipFileNotCached(Path zipFile, Path directory) {
    Objects.requireNonNull(zipFile);
    Objects.requireNonNull(directory);

    //
    // Considering that these zip files may contain thousands of files,
    // iteration seems like a pretty inefficient way to find the results.json
    // file.  And we can't use ZipFile.getEntry(name) because we don't know
    // the full path (including directories) of the results.json file.
    // Fortunately, in practice the results.json file is one of the first
    // entries, so iteration finds it quickly.
    //
    ParsedResults results;
    try {
      results =
          ZipFiles.readZipEntry(
              /* zipFile= */ zipFile,
              /* entryPath= */ "results.json",
              /* entryReader= */ in -> objectMapper.readValue(in, ParsedResults.class));
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
    Path relativePath = directory.relativize(zipFile);
    String fileName = Joiner.on('/').join(relativePath);

    List<Failure> failures = new ArrayList<>();

    results.failed.inverse().asMap().forEach(
        (framework, failedTestTypes) ->
            failures.add(
                new ResultsZipView.Failure(
                    /* framework= */ framework,
                    /* failedTestTypes= */ ImmutableList.sortedCopyOf(failedTestTypes),
                    /* logFileName= */ fileName + "/" + framework + "/out.txt")));

    results.completed.forEach(
        (framework, message) -> {
          if (!results.failed.inverse().containsKey(framework)
              && !isCompletedTimestamp(message)) {
            failures.add(
                new ResultsZipView.Failure(
                    /* framework= */ framework,
                    /* failedTestTypes= */ ImmutableList.of(),
                    /* logFileName= */ fileName + "/" + framework + "/out.txt"));
          }
        });

    failures.sort(comparing(failure -> failure.framework,
                            String.CASE_INSENSITIVE_ORDER));

    return new ResultsZipView(
        /* uuid= */ uuid,
        /* fileName= */ fileName,
        /* failures= */ ImmutableList.copyOf(failures));
  }

  @Immutable
  private static final class ZipFilesByUuid {
    final ImmutableMap<String, ResultsZipView> byUuid;
    final ImmutableList<ResultsZipView> noUuid;

    ZipFilesByUuid(ImmutableMap<String, ResultsZipView> byUuid,
                   ImmutableList<ResultsZipView> noUuid) {
      this.byUuid = Objects.requireNonNull(byUuid);
      this.noUuid = Objects.requireNonNull(noUuid);
    }
  }

  @Immutable
  private static final class ViewCacheKey {
    final Path file;
    final Path directory;

    // When the file is modified, this cache key becomes unreachable.
    final FileTime lastModifiedTime;

    ViewCacheKey(Path file, Path directory) {
      this.file = Objects.requireNonNull(file);
      this.directory = Objects.requireNonNull(directory);
      try {
        this.lastModifiedTime = Files.getLastModifiedTime(file);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    @Override
    public boolean equals(@Nullable Object object) {
      if (object == this)
        return true;

      if (!(object instanceof ViewCacheKey))
        return false;

      ViewCacheKey that = (ViewCacheKey) object;
      return this.file.equals(that.file)
          && this.directory.equals(that.directory)
          && this.lastModifiedTime.equals(that.lastModifiedTime);
    }

    @Override
    public int hashCode() {
      int hash = 1;
      hash = 31 * hash + file.hashCode();
      hash = 31 * hash + directory.hashCode();
      hash = 31 * hash + lastModifiedTime.hashCode();
      return hash;
    }
  }

  /**
   * Attempts to merge results.json views and results.zip views together by
   * comparing their uuids.  Views without uuids cannot be merged, so the merged
   * views in those cases contain only one of the file types.
   */
  private ImmutableList<ResultsView> combine(JsonFilesByUuid jsonFiles,
                                             ZipFilesByUuid zipFiles) {
    Objects.requireNonNull(jsonFiles);
    Objects.requireNonNull(zipFiles);
    List<ResultsView> results = new ArrayList<>();

    Set<String> uuids =
        Sets.union(
            jsonFiles.byUuid.keySet(),
            zipFiles.byUuid.keySet());

    for (String uuid : uuids) {
      ResultsJsonView json = jsonFiles.byUuid.get(uuid);
      ResultsZipView zip = zipFiles.byUuid.get(uuid);
      results.add(new ResultsView(uuid, json, zip));
    }

    for (ResultsJsonView json : jsonFiles.noUuid)
      results.add(
          new ResultsView(
              /* uuid= */ null,
              /* json= */ json,
              /* zip= */ null));

    for (ResultsZipView zip : zipFiles.noUuid)
      results.add(
          new ResultsView(
              /* uuid= */ null,
              /* json= */ null,
              /* zip= */ zip));

    return ImmutableList.sortedCopyOf(RESULTS_COMPARATOR, results);
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
   * ParsedResults#completed} map.
   *
   * @param message a value from the {@link ParsedResults#completed} map
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
