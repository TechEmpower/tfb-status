package tfb.status.view;

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
   * A combo view of a results.json file and a results.zip file for the same
   * run.
   */
  @Immutable
  public static final class ResultsView {
    @Nullable public final ResultsJsonView json;
    @Nullable public final ResultsZipView zip;
    @Nullable public final String uuid;
    @Nullable public final ResultsGitView git;

    public ResultsView(@Nullable ResultsJsonView json,
                       @Nullable ResultsZipView zip) {

      this.json = json;
      this.zip = zip;

      if (json != null && json.uuid != null) {
        this.uuid = json.uuid;
      } else if (zip != null && zip.uuid != null) {
        this.uuid = zip.uuid;
      } else {
        this.uuid = null;
      }

      if (json != null && json.git != null) {
        this.git = json.git;
      } else if (zip != null && zip.git != null) {
        this.git = zip.git;
      } else {
        this.git = null;
      }
    }
  }

  /**
   * A view of a single results.json file.
   */
  @Immutable
  public static final class ResultsJsonView {
    @Nullable public final String uuid;
    @Nullable public final ResultsGitView git;
    public final String fileName;
    @Nullable public final String name;
    @Nullable public final String environmentDescription;
    @Nullable public final String startTime;
    @Nullable public final String completionTime;
    @Nullable public final String elapsedDuration;
    @Nullable public final String estimatedRemainingDuration;
    public final int completedFrameworks;
    public final int frameworksWithCleanSetup;
    public final int frameworksWithSetupProblems;
    public final int totalFrameworks;
    public final int successfulTests;
    public final int failedTests;
    @Nullable public final String lastUpdated;

    public ResultsJsonView(@Nullable String uuid,
                           @Nullable ResultsGitView git,
                           String fileName,
                           @Nullable String name,
                           @Nullable String environmentDescription,
                           @Nullable String startTime,
                           @Nullable String completionTime,
                           int completedFrameworks,
                           int frameworksWithCleanSetup,
                           int frameworksWithSetupProblems,
                           int totalFrameworks,
                           int successfulTests,
                           int failedTests,
                           @Nullable String lastUpdated,
                           @Nullable String elapsedDuration,
                           @Nullable String estimatedRemainingDuration) {
      this.uuid = uuid;
      this.git = git;
      this.fileName = Objects.requireNonNull(fileName);
      this.name = name;
      this.environmentDescription = environmentDescription;
      this.startTime = startTime;
      this.completionTime = completionTime;
      this.completedFrameworks = completedFrameworks;
      this.frameworksWithCleanSetup = frameworksWithCleanSetup;
      this.frameworksWithSetupProblems = frameworksWithSetupProblems;
      this.totalFrameworks = totalFrameworks;
      this.successfulTests = successfulTests;
      this.failedTests = failedTests;
      this.lastUpdated = lastUpdated;
      this.elapsedDuration = elapsedDuration;
      this.estimatedRemainingDuration = estimatedRemainingDuration;
    }
  }

  /**
   * A view of a single results.zip file.
   */
  @Immutable
  public static final class ResultsZipView {
    @Nullable public final String uuid;
    @Nullable public final ResultsGitView git;
    public final String fileName;
    public final ImmutableList<Failure> failures;

    public ResultsZipView(@Nullable String uuid,
                          @Nullable ResultsGitView git,
                          String fileName,
                          ImmutableList<Failure> failures) {
      this.uuid = uuid;
      this.git = git;
      this.fileName = Objects.requireNonNull(fileName);
      this.failures = Objects.requireNonNull(failures);
    }

    /**
     * A view of a framework that failed at least one test type.
     */
    @Immutable
    public static final class Failure {
      public final String framework;
      public final ImmutableList<String> failedTestTypes;

      public Failure(String framework,
                     ImmutableList<String> failedTestTypes) {
        this.framework = Objects.requireNonNull(framework);
        this.failedTestTypes = Objects.requireNonNull(failedTestTypes);
      }
    }
  }

  /**
   * A view of the state of the TFB git repository where this run was executed.
   */
  @Immutable
  public static final class ResultsGitView {
    public final String commitId;
    @Nullable public final String repositoryUrl;
    @Nullable public final String branchName;
    @Nullable public final String commitUrl;

    public ResultsGitView(String commitId,
                          @Nullable String repositoryUrl,
                          @Nullable String branchName) {
      this.commitId = Objects.requireNonNull(commitId);
      this.repositoryUrl = repositoryUrl;
      this.branchName = branchName;
      if (this.repositoryUrl != null) {
        this.commitUrl = repositoryUrl.replace(".git$", "")
                + "/commit/" + this.commitId;
      } else {
        this.commitUrl = null;
      }
    }
  }
}
