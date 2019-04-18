package tfb.status.view;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;
import javax.annotation.Nullable;

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
  @Nullable public final String announcement;

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
    @Nullable public final String uuid;
    @Nullable public final String name;
    @Nullable public final String environmentDescription;
    public final int completedFrameworks;
    public final int frameworksWithCleanSetup;
    public final int frameworksWithSetupProblems;
    public final int totalFrameworks;
    public final int successfulTests;
    public final int failedTests;
    @Nullable public final String startTime;
    @Nullable public final String completionTime;
    @Nullable public final String lastUpdated;
    @Nullable public final String elapsedDuration;
    @Nullable public final String estimatedRemainingDuration;
    @Nullable public final String commitId;
    @Nullable public final String repositoryUrl;
    @Nullable public final String branchName;
    @Nullable public final String browseRepositoryUrl;
    @Nullable public final String browseCommitUrl;
    @Nullable public final String browseBranchUrl;
    public final ImmutableList<Failure> failures;
    @Nullable public final String jsonFileName;
    @Nullable public final String zipFileName;

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
    @Deprecated
    @Nullable
    @JsonProperty("json")
    public FileName json() {
      return (jsonFileName == null) ? null : new FileName(jsonFileName);
    }

    /** @deprecated use {@link #zipFileName} instead */
    @Deprecated
    @Nullable
    @JsonProperty("zip")
    public FileName zip() {
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
