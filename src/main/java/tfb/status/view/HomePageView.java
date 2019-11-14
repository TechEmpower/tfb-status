package tfb.status.view;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A view of the home page.
 */
@Immutable
public final class HomePageView {
  public final ImmutableList<ResultsView> results;
  public final int skip;
  public final int limit;
  public final int next;
  public final boolean hasNext;
  public final @Nullable String announcement;

  public HomePageView(ImmutableList<ResultsView> results,
                      int skip,
                      int limit,
                      int next,
                      boolean hasNext,
                      @Nullable String announcement) {

    this.results = Objects.requireNonNull(results);
    this.skip = skip;
    this.limit = limit;
    this.next = next;
    this.hasNext = hasNext;
    this.announcement = announcement;
  }

  /**
   * A view of a single run.
   */
  @Immutable
  public static final class ResultsView {
    public final @Nullable String uuid;
    public final @Nullable String name;
    public final @Nullable String environmentDescription;
    public final int completedFrameworks;
    public final int frameworksWithCleanSetup;
    public final int frameworksWithSetupProblems;
    public final int totalFrameworks;
    public final int successfulTests;
    public final int failedTests;
    public final @Nullable String startTime;
    public final @Nullable String completionTime;
    public final @Nullable String lastUpdated;
    public final @Nullable String elapsedDuration;
    public final @Nullable String estimatedRemainingDuration;
    public final @Nullable String commitId;
    public final @Nullable String repositoryUrl;
    public final @Nullable String branchName;
    public final @Nullable String browseRepositoryUrl;
    public final @Nullable String browseCommitUrl;
    public final @Nullable String browseBranchUrl;
    public final ImmutableList<Failure> failures;
    public final @Nullable String jsonFileName;
    public final @Nullable String zipFileName;

    public ResultsView(@Nullable String uuid,
                       @Nullable String name,
                       @Nullable String environmentDescription,
                       int completedFrameworks,
                       int frameworksWithCleanSetup,
                       int frameworksWithSetupProblems,
                       int totalFrameworks,
                       int successfulTests,
                       int failedTests,
                       @Nullable String startTime,
                       @Nullable String completionTime,
                       @Nullable String lastUpdated,
                       @Nullable String elapsedDuration,
                       @Nullable String estimatedRemainingDuration,
                       @Nullable String commitId,
                       @Nullable String repositoryUrl,
                       @Nullable String branchName,
                       @Nullable String browseRepositoryUrl,
                       @Nullable String browseCommitUrl,
                       @Nullable String browseBranchUrl,
                       ImmutableList<Failure> failures,
                       @Nullable String jsonFileName,
                       @Nullable String zipFileName) {

      this.uuid = uuid;
      this.name = name;
      this.environmentDescription = environmentDescription;
      this.completedFrameworks = completedFrameworks;
      this.frameworksWithCleanSetup = frameworksWithCleanSetup;
      this.frameworksWithSetupProblems = frameworksWithSetupProblems;
      this.totalFrameworks = totalFrameworks;
      this.successfulTests = successfulTests;
      this.failedTests = failedTests;
      this.startTime = startTime;
      this.completionTime = completionTime;
      this.lastUpdated = lastUpdated;
      this.elapsedDuration = elapsedDuration;
      this.estimatedRemainingDuration = estimatedRemainingDuration;
      this.commitId = commitId;
      this.repositoryUrl = repositoryUrl;
      this.branchName = branchName;
      this.browseRepositoryUrl = browseRepositoryUrl;
      this.browseCommitUrl = browseCommitUrl;
      this.browseBranchUrl = browseBranchUrl;
      this.failures = Objects.requireNonNull(failures);
      this.jsonFileName = jsonFileName;
      this.zipFileName = zipFileName;
    }

    // Provide backwards compatibility with the TFB website, which reads the
    // JSON-serialized form of this class and expects it to have json.fileName
    // and zip.fileName properties.

    /** @deprecated use {@link #jsonFileName} instead */
    @JsonProperty("json")
    @Deprecated
    public @Nullable FileName json() {
      return (jsonFileName == null) ? null : new FileName(jsonFileName);
    }

    /** @deprecated use {@link #zipFileName} instead */
    @JsonProperty("zip")
    @Deprecated
    public @Nullable FileName zip() {
      return (zipFileName == null) ? null : new FileName(zipFileName);
    }

    @Immutable
    public static final class FileName {
      public final String fileName;

      public FileName(String fileName) {
        this.fileName = Objects.requireNonNull(fileName);
      }
    }

    /**
     * A view of a framework that failed to start or that failed at least one
     * test type.
     */
    @Immutable
    public static final class Failure {
      public final String framework;
      public final ImmutableList<String> failedTestTypes;
      public final boolean hadSetupProblems;

      public Failure(String framework,
                     ImmutableList<String> failedTestTypes,
                     boolean hadSetupProblems) {

        this.framework = Objects.requireNonNull(framework);
        this.failedTestTypes = Objects.requireNonNull(failedTestTypes);
        this.hadSetupProblems = hadSetupProblems;
      }
    }
  }
}
