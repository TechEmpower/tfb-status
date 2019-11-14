package tfb.status.service;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.comparing;
import static java.util.Comparator.reverseOrder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.base.Joiner;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.io.MoreFiles;
import com.google.errorprone.annotations.Immutable;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.util.ZipFiles;
import tfb.status.view.HomePageView.ResultsView;
import tfb.status.view.HomePageView.ResultsView.Failure;
import tfb.status.view.Results;

/**
 * Loads previously-uploaded results for display on the home page.
 */
@Singleton
public final class HomeResultsReader {
  private final FileStore fileStore;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final LoadingCache<FileKey, FileSummary> fileCache =
      Caffeine.newBuilder()
              .maximumSize(FILE_CACHE_MAX_SIZE)
              .build(key -> readFile(key.file));

  // This number should be greater than the total number of results files we'll
  // ever have on disk at once.
  private static final int FILE_CACHE_MAX_SIZE = 10_000;

  @GuardedBy("this")
  private @Nullable ScheduledThreadPoolExecutor purgeScheduler;

  @GuardedBy("this")
  private @Nullable ScheduledFuture<?> purgeTask;

  @Inject
  public HomeResultsReader(FileStore fileStore,
                           ObjectMapper objectMapper,
                           Clock clock) {

    this.fileStore = Objects.requireNonNull(fileStore);
    this.objectMapper = Objects.requireNonNull(objectMapper);
    this.clock = Objects.requireNonNull(clock);
  }

  /**
   * Initializes resources used by this service.
   */
  @PostConstruct
  public synchronized void start() {
    purgeScheduler = new ScheduledThreadPoolExecutor(1);
    purgeScheduler.setRemoveOnCancelPolicy(true);
    purgeScheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    purgeScheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);

    purgeTask =
        purgeScheduler.scheduleWithFixedDelay(
            /* command= */ () -> {
              try {
                purgeUnreachableCacheKeys();
              } catch (RuntimeException e) {
                // An uncaught exception would de-schedule this task.
                logger.error("Error purging unreachable cache keys", e);
              }
            },
            /* initialDelay= */ 1,
            /* delay= */ 1,
            /* unit= */ TimeUnit.HOURS);
  }

  /**
   * Cleans up resources used by this service.
   */
  @PreDestroy
  public synchronized void stop() {
    ScheduledFuture<?> task = this.purgeTask;
    if (task != null) {
      task.cancel(false);
      this.purgeTask = null;
    }

    ScheduledThreadPoolExecutor scheduler = this.purgeScheduler;
    if (scheduler != null) {
      scheduler.shutdownNow();
      this.purgeScheduler = null;
    }
  }

  /**
   * Returns a view of all the previously-uploaded results, suitable for
   * rendering on the home page.
   *
   * @return a view of the results
   * @throws IOException if an I/O error occurs while reading the results
   */
  public ImmutableList<ResultsView> results() throws IOException {
    var noUuid = new ArrayList<FileSummary>();
    var byUuid = new HashMap<String, List<FileSummary>>();

    readAllFiles().forEach(
        (FileSummary summary) -> {
          if (summary.uuid == null)
            noUuid.add(summary);
          else
            byUuid.computeIfAbsent(summary.uuid, uuid -> new ArrayList<>())
                  .add(summary);
        });

    var results = new ArrayList<ResultsView>();

    for (FileSummary summary : noUuid)
      results.add(newResultsView(List.of(summary)));

    for (List<FileSummary> summaries : byUuid.values())
      results.add(newResultsView(summaries));

    return ImmutableList.sortedCopyOf(RESULTS_COMPARATOR, results);
  }

  /**
   * Returns a view of the only previously-uploaded results having the given
   * UUID, suitable for rendering on the home page, or {@code null} if there are
   * no results with the given UUID.
   *
   * @param uuid the UUID of the results to be viewed
   * @return a view of the results, or {@code null} if there are no matching
   *         results
   * @throws IOException if an I/O error occurs while reading the results
   */
  public @Nullable ResultsView resultsByUuid(String uuid) throws IOException {
    Objects.requireNonNull(uuid);

    ImmutableList<FileSummary> summaries =
        readAllFiles()
            .filter(summary -> uuid.equals(summary.uuid))
            .collect(toImmutableList());

    return summaries.isEmpty()
        ? null
        : newResultsView(summaries);
  }

  private Stream<FileSummary> readAllFiles() throws IOException {
    Stream.Builder<FileKey> keys = Stream.builder();

    try (DirectoryStream<Path> files =
             Files.newDirectoryStream(fileStore.resultsDirectory(),
                                      "*.{json,zip}")) {

      for (Path file : files)
        keys.add(new FileKey(file));
    }

    return keys.build()
               .map(key -> fileCache.get(key))
               .filter(summary -> summary != null);
  }

  private @Nullable FileSummary readFile(Path file) {
    Objects.requireNonNull(file);
    switch (MoreFiles.getFileExtension(file)) {
      case "json":
        try {
          return readJsonFile(file);
        } catch (IOException e) {
          logger.warn("Exception reading results.json file {}", file, e);
          return null;
        }
      case "zip":
        try {
          return readZipFile(file);
        } catch (IOException e) {
          logger.warn("Exception reading results.zip file {}", file, e);
          return null;
        }
      default:
        logger.warn("Unknown format for results file {}", file);
        return null;
    }
  }

  private FileSummary readJsonFile(Path jsonFile) throws IOException {
    Objects.requireNonNull(jsonFile);

    Results results;
    try (InputStream inputStream = Files.newInputStream(jsonFile)) {
      results = objectMapper.readValue(inputStream, Results.class);
    }

    // TODO: Avoid using the last modified time of the file on disk, which may
    //       change for reasons completely unrelated to the run itself, and use
    //       something from the results.json file to give us a last modified
    //       time instead.
    FileTime lastModifiedTime  = Files.getLastModifiedTime(jsonFile);

    Path relativePath = fileStore.resultsDirectory().relativize(jsonFile);
    String fileName = Joiner.on('/').join(relativePath);

    return summarizeResults(
        /* results= */ results,
        /* fileName= */ fileName,
        /* lastUpdated= */ lastModifiedTime.toInstant(),
        /* backupCommitId= */ null);
  }

  private @Nullable FileSummary readZipFile(Path zipFile) throws IOException {
    Objects.requireNonNull(zipFile);

    Results results =
        ZipFiles.readZipEntry(
            /* zipFile= */ zipFile,
            /* entryPath= */ "results.json",
            /* entryReader= */ inputStream ->
                                   objectMapper.readValue(inputStream,
                                                          Results.class));

    if (results == null) {
      logger.warn(
          "results.zip file {} does not contain a results.json file",
          zipFile);

      // If the zip doesn't contain a results.json at all, then we have nothing
      // useful to say to users about it, and we want to pretend it doesn't
      // exist.
      return null;
    }

    // TODO: Avoid using the last modified time of the file on disk, which may
    //       change for reasons completely unrelated to the run itself, and use
    //       something from the results.json file to give us a last modified
    //       time instead.
    FileTime lastModifiedTime = Files.getLastModifiedTime(zipFile);

    Path relativePath = fileStore.resultsDirectory().relativize(zipFile);
    String fileName = Joiner.on('/').join(relativePath);

    // If the results.json doesn't tell us the commit id, then search for a
    // commit_id.txt file.  We used to capture the git commit id in its own file
    // before we added it to results.json.
    String backupCommitId;
    if (results.git != null)
      backupCommitId = null;
    else {
      backupCommitId =
          ZipFiles.readZipEntry(
              /* zipFile= */ zipFile,
              /* entryPath= */ "commit_id.txt",
              /* entryReader= */
              inputStream -> {
                try (var isr = new InputStreamReader(inputStream, UTF_8);
                     var br = new BufferedReader(isr)) {
                  return br.readLine();
                }
              });
    }

    return summarizeResults(
        /* results= */ results,
        /* fileName= */ fileName,
        /* lastUpdated= */ lastModifiedTime.toInstant(),
        /* backupCommitId= */ backupCommitId);
  }

  private FileSummary summarizeResults(Results results,
                                       String fileName,
                                       Instant lastUpdated,
                                       @Nullable String backupCommitId) {

    Objects.requireNonNull(results);
    Objects.requireNonNull(fileName);
    Objects.requireNonNull(lastUpdated);

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

    for (String message : results.completed.values()) {
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

    for (String testType : Results.TEST_TYPES) {
      for (String framework : results.frameworks) {
        if (results.succeeded.containsEntry(testType, framework)
            && results.requests(testType, framework) == 0) {
          successfulTests--;
          failedTests++;
        }
      }
    }

    Instant startTime =
        (results.startTime == null)
            ? null
            : Instant.ofEpochMilli(results.startTime);

    Instant completionTime =
        (results.completionTime == null)
            ? null
            : Instant.ofEpochMilli(results.completionTime);

    String commitId;
    String repositoryUrl;
    String branchName;

    if (results.git == null) {
      commitId = backupCommitId;
      repositoryUrl = null;
      branchName = null;
    } else {
      commitId = results.git.commitId;
      repositoryUrl = results.git.repositoryUrl;
      branchName = results.git.branchName;
    }

    var failures = new ArrayList<Failure>();

    SetMultimap<String, String> frameworkToFailedTestTypes =
        HashMultimap.create(results.failed.inverse());

    for (String testType : Results.TEST_TYPES) {
      for (String framework : results.frameworks) {
        if (results.succeeded.containsEntry(testType, framework)
            && results.requests(testType, framework) == 0) {
          frameworkToFailedTestTypes.put(framework, testType);
        }
      }
    }

    var frameworksWithSetupIssues = new HashSet<String>();

    results.completed.forEach(
        (String framework, String message) -> {
          if (!isCompletedTimestamp(message)) {
            frameworksWithSetupIssues.add(framework);
          }
        });

    for (String framework : Sets.union(frameworkToFailedTestTypes.keySet(),
                                       frameworksWithSetupIssues)) {

      Set<String> failedTestTypes = frameworkToFailedTestTypes.get(framework);
      boolean hadSetupProblems = frameworksWithSetupIssues.contains(framework);

      failures.add(
          new Failure(
              /* framework= */ framework,
              /* failedTestTypes= */ ImmutableList.sortedCopyOf(failedTestTypes),
              /* hadSetupProblems= */ hadSetupProblems));
    }

    failures.sort(comparing(failure -> failure.framework,
                            String.CASE_INSENSITIVE_ORDER));

    return new FileSummary(
        /* fileName= */ fileName,
        /* uuid= */ uuid,
        /* commitId= */ commitId,
        /* repositoryUrl= */ repositoryUrl,
        /* branchName= */ branchName,
        /* name= */ name,
        /* environmentDescription= */ environmentDescription,
        /* startTime= */ startTime,
        /* completionTime= */ completionTime,
        /* lastUpdated= */ lastUpdated,
        /* completedFrameworks= */ completedFrameworks,
        /* frameworksWithCleanSetup= */ frameworksWithCleanSetup,
        /* frameworksWithSetupProblems= */ frameworksWithSetupProblems,
        /* totalFrameworks= */ totalFrameworks,
        /* successfulTests= */ successfulTests,
        /* failedTests= */ failedTests,
        /* failures= */ ImmutableList.copyOf(failures));
  }

  private ResultsView newResultsView(Iterable<FileSummary> summaries) {
    Objects.requireNonNull(summaries);

    FileSummary mostRecentJson = null;
    FileSummary mostRecentZip = null;

    for (FileSummary summary : summaries) {
      if (summary.fileName.endsWith(".json")) {
        if (mostRecentJson == null
            || summary.lastUpdated.isAfter(mostRecentJson.lastUpdated)) {
          mostRecentJson = summary;
        }
      } else if (summary.fileName.endsWith(".zip")) {
        if (mostRecentZip == null
            || summary.lastUpdated.isAfter(mostRecentZip.lastUpdated)) {
          mostRecentZip = summary;
        }
      }
    }

    FileSummary summary;
    // Prefer the results.zip file.  The zip file is uploaded at the end of the
    // run and should contain the complete, final results.json.
    if (mostRecentZip != null)
      summary = mostRecentZip;
    else if (mostRecentJson != null)
      summary = mostRecentJson;
    else
      throw new IllegalArgumentException(
          "There must be at least one results file");

    String uuid = summary.uuid;
    String name = summary.name;
    String environmentDescription = summary.environmentDescription;
    int frameworksWithCleanSetup = summary.frameworksWithCleanSetup;
    int frameworksWithSetupProblems = summary.frameworksWithSetupProblems;
    int successfulTests = summary.successfulTests;
    int failedTests = summary.failedTests;
    ImmutableList<Failure> failures = summary.failures;
    int completedFrameworks = summary.completedFrameworks;
    int totalFrameworks = summary.totalFrameworks;
    Instant startTime = summary.startTime;
    Instant completionTime = summary.completionTime;
    Instant lastUpdated = summary.lastUpdated;
    String commitId = summary.commitId;
    String repositoryUrl = summary.repositoryUrl;
    String branchName = summary.branchName;

    Duration elapsedDuration;
    Duration estimatedRemainingDuration;

    if (startTime == null)
      elapsedDuration = null;

    else {
      Instant endTime =
          (completionTime == null)
              // TODO: Use lastUpdated here instead of now?
              ? clock.instant()
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

    DateTimeFormatter displayedTimeFormatter =
        DateTimeFormatter.ofPattern(
            "yyyy-MM-dd 'at' h:mm a",
            Locale.ROOT);

    String startTimeString =
        (startTime == null)
            ? null
            : startTime.atZone(clock.getZone())
                       .toLocalDateTime()
                       .format(displayedTimeFormatter);

    String completionTimeString =
        (completionTime == null)
            ? null
            : completionTime.atZone(clock.getZone())
                            .toLocalDateTime()
                            .format(displayedTimeFormatter);

    String lastUpdatedString =
        lastUpdated.atZone(clock.getZone())
                   .toLocalDateTime()
                   .format(displayedTimeFormatter);

    // TODO: Don't display huge durations when it looks like the run is defunct.

    String elapsedDurationString =
        (elapsedDuration == null)
            ? null
            : formatDuration(elapsedDuration);

    String estimatedRemainingDurationString =
        (estimatedRemainingDuration == null)
            ? null
            : formatDuration(estimatedRemainingDuration);

    String browseRepositoryUrl;
    String browseCommitUrl;
    String browseBranchUrl;

    if (repositoryUrl == null) {
      browseRepositoryUrl = null;
      browseCommitUrl = null;
      browseBranchUrl = null;
    } else {
      Matcher githubMatcher = GITHUB_REPOSITORY_PATTERN.matcher(repositoryUrl);
      if (githubMatcher.matches()) {

        browseRepositoryUrl =
            "https://github.com" + githubMatcher.group("path");

        browseCommitUrl =
            browseRepositoryUrl + "/tree/" + commitId;

        browseBranchUrl =
            (branchName == null)
                ? null
                : browseRepositoryUrl + "/tree/" + branchName;

      } else {
        browseRepositoryUrl = null;
        browseCommitUrl = null;
        browseBranchUrl = null;
      }
    }

    String jsonFileName =
        (mostRecentJson == null)
            ? null
            : mostRecentJson.fileName;

    String zipFileName =
        (mostRecentZip == null)
            ? null
            : mostRecentZip.fileName;

    return new ResultsView(
        /* uuid= */ uuid,
        /* name= */ name,
        /* environmentDescription= */ environmentDescription,
        /* completedFrameworks= */ completedFrameworks,
        /* frameworksWithCleanSetup= */ frameworksWithCleanSetup,
        /* frameworksWithSetupProblems= */ frameworksWithSetupProblems,
        /* totalFrameworks= */ totalFrameworks,
        /* successfulTests= */ successfulTests,
        /* failedTests= */ failedTests,
        /* startTime= */ startTimeString,
        /* completionTime= */ completionTimeString,
        /* lastUpdated= */ lastUpdatedString,
        /* elapsedDuration= */ elapsedDurationString,
        /* estimatedRemainingDuration= */ estimatedRemainingDurationString,
        /* commitId= */ commitId,
        /* repositoryUrl= */ repositoryUrl,
        /* branchName= */ branchName,
        /* browseRepositoryUrl= */ browseRepositoryUrl,
        /* browseCommitUrl= */ browseCommitUrl,
        /* browseBranchUrl= */ browseBranchUrl,
        /* failures= */ failures,
        /* jsonFileName= */ jsonFileName,
        /* zipFileName= */ zipFileName);
  }

  /**
   * Trims the internal cache, removing entries that are "dead" because they
   * have {@linkplain FileKey#isUnreachable() unreachable} keys.
   */
  private void purgeUnreachableCacheKeys() {
    ImmutableSet<FileKey> unreachableKeys =
        fileCache.asMap()
                 .keySet()
                 .stream()
                 .filter(key -> key.isUnreachable())
                 .collect(toImmutableSet());

    fileCache.invalidateAll(unreachableKeys);
  }

  /**
   * A cache key pointing to a results.json or results.zip file on disk.
   */
  @Immutable
  private static final class FileKey {
    final Path file;

    // When the file is modified, this cache key becomes unreachable.
    final FileTime lastModifiedTime;

    FileKey(Path file) throws IOException {
      this.file = Objects.requireNonNull(file);
      this.lastModifiedTime = Files.getLastModifiedTime(file);
    }

    @Override
    public boolean equals(@Nullable Object object) {
      if (object == this)
        return true;

      if (!(object instanceof FileKey))
        return false;

      var that = (FileKey) object;
      return this.file.equals(that.file)
          && this.lastModifiedTime.equals(that.lastModifiedTime);
    }

    @Override
    public int hashCode() {
      return file.hashCode() ^ lastModifiedTime.hashCode();
    }

    /**
     * Returns {@code true} if the file has been modified since this cache key
     * was created, meaning this cache key is effectively unreachable.
     */
    boolean isUnreachable() {
      try {
        return !lastModifiedTime.equals(Files.getLastModifiedTime(file));
      } catch (IOException ignored) {
        //
        // This would happen if the file is deleted, for example.
        //
        // Since an exception here implies that constructing a new key for this
        // file would also throw an exception, this key is unreachable.
        //
        return true;
      }
    }
  }

  /**
   * Information extracted from a results.json or results.zip file.
   */
  @Immutable
  private static final class FileSummary {
    final String fileName;
    final @Nullable String uuid;
    final @Nullable String commitId;
    final @Nullable String repositoryUrl;
    final @Nullable String branchName;
    final @Nullable String name;
    final @Nullable String environmentDescription;
    final @Nullable Instant startTime;
    final @Nullable Instant completionTime;
    final Instant lastUpdated;
    final int completedFrameworks;
    final int frameworksWithCleanSetup;
    final int frameworksWithSetupProblems;
    final int totalFrameworks;
    final int successfulTests;
    final int failedTests;
    // TODO: Avoid sharing the Failure data type with HomePageView?
    final ImmutableList<Failure> failures;

    FileSummary(String fileName,
                @Nullable String uuid,
                @Nullable String commitId,
                @Nullable String repositoryUrl,
                @Nullable String branchName,
                @Nullable String name,
                @Nullable String environmentDescription,
                @Nullable Instant startTime,
                @Nullable Instant completionTime,
                Instant lastUpdated,
                int completedFrameworks,
                int frameworksWithCleanSetup,
                int frameworksWithSetupProblems,
                int totalFrameworks,
                int successfulTests,
                int failedTests,
                ImmutableList<Failure> failures) {

      this.fileName = Objects.requireNonNull(fileName);
      this.uuid = uuid;
      this.commitId = commitId;
      this.repositoryUrl = repositoryUrl;
      this.branchName = branchName;
      this.name = name;
      this.environmentDescription = environmentDescription;
      this.startTime = startTime;
      this.completionTime = completionTime;
      this.lastUpdated = Objects.requireNonNull(lastUpdated);
      this.completedFrameworks = completedFrameworks;
      this.frameworksWithCleanSetup = frameworksWithCleanSetup;
      this.frameworksWithSetupProblems = frameworksWithSetupProblems;
      this.totalFrameworks = totalFrameworks;
      this.successfulTests = successfulTests;
      this.failedTests = failedTests;
      this.failures = Objects.requireNonNull(failures);
    }
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
  //
  // In practice, the results files are named like this:
  //
  //   results.{uploaded_at_date_time}.{json|zip}
  //
  // where {uploaded_at_date_time} is in the format "yyyy-MM-dd-HH-mm-ss-SSS".
  //
  // Therefore, sorting by file name effectively sorts the results by when they
  // were uploaded, and this comparator puts the most recently uploaded results
  // first.
  //
  private static final Comparator<ResultsView> RESULTS_COMPARATOR =
      comparing(
          results -> {
            // The JSON file name is a better sort key because it should remain
            // constant throughout the whole run, whereas the zip file name is
            // expected to change at the end of the run (from null to non-null).
            if (results.jsonFileName != null)
              return results.jsonFileName;

            else if (results.zipFileName != null)
              return results.zipFileName;

            else
              return "";
          },
          reverseOrder());

  private static final Pattern GITHUB_REPOSITORY_PATTERN =
      Pattern.compile("^(https|git)://github\\.com(?<path>/.*)\\.git$");
}
