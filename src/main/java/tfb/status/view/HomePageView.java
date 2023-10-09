package tfb.status.view;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A view of the home page.
 *
 * @param results The currently visible page of results.
 * @param skip The number of results before the current page.
 * @param limit The maximum number of results on the current page.
 * @param next The {@code skip} value for the next page of results.
 * @param hasNext {@code true} unless this is the last page of results
 * @param announcement An announcement to be displayed on the home page, or
 *        {@code null} if no announcement is to be displayed.
 */
@Immutable
public record HomePageView(ImmutableList<ResultsView> results,
                           int skip,
                           int limit,
                           int next,
                           boolean hasNext,
                           @Nullable String announcement) {

  public HomePageView {
    Objects.requireNonNull(results);
  }

  /**
   * A view of a single run.
   *
   * @param uuid The unique id of this run, or {@code null} if that information
   *        is unavailable.  See {@link Results#uuid()}.
   * @param name The name of this run, or {@code null} if that information is
   *        unavailable.  See {@link Results#name()}.
   * @param environmentDescription The environment that produced this run, or
   *        {@code null} if that information is unavailable.  See {@link
   *        Results#environmentDescription()}.
   * @param completedFrameworks The number of frameworks that the toolset has
   *        finished testing in this run.  This should equal {@link
   *        #totalFrameworks()} when the run is complete.
   * @param frameworksWithCleanSetup The number of {@linkplain
   *        #completedFrameworks() completed frameworks} that started and
   *        stopped successfully.
   * @param frameworksWithSetupProblems The number of {@linkplain
   *        #completedFrameworks() completed frameworks} that had problems
   *        starting or stopping.
   * @param totalFrameworks The total number of frameworks available to be
   *        tested in this run.
   * @param successfulTests The total number of [framework, test type]
   *        combinations for which the {@linkplain Results.TestOutcome test
   *        outcome} was {@linkplain Results.TestOutcome#SUCCEEDED success}.
   * @param failedTests The total number of [framework, test type] combinations
   *        for which the {@linkplain Results.TestOutcome test outcome} was
   *        {@linkplain Results.TestOutcome#FAILED failure}.
   * @param startTime A human-readable string describing the date and time when
   *        this run started, or {@code null} if that information is
   *        unavailable.  See {@link Results#startTime()}.
   * @param completionTime A human-readable string describing the date and time
   *        when this run completed, or {@code null} if that information is
   *        unavailable.  See {@link Results#completionTime()}.
   * @param lastUpdated A human-readable string describing the date and time
   *        when the results for this run were most recently updated, or {@code
   *        null} if that information is unavailable.
   * @param elapsedDuration A human-readable string describing the amount of
   *        time elapsed between (1) the {@linkplain #startTime() start of this
   *        run} and (2) either the {@linkplain #completionTime() end of this
   *        run} or now if this run is still in progress, or {@code null} if
   *        the {@linkplain #startTime() start time} is unavailable.
   * @param estimatedRemainingDuration A human-readable string describing the
   *        estimated amount of time remaining in this run, or {@code null} if
   *        this run has completed or if there is insufficient information to
   *        produce an estimate.
   * @param commitId The current commit of the local repository, or {@code null}
   *        if that information is unavailable.  See {@link
   *        Results.GitInfo#commitId()}.
   * @param repositoryUrl The name of the remote repository from which the local
   *        repository was cloned, or {@code null} if that information is
   *        unavailable.  See {@link Results.GitInfo#repositoryUrl()}.
   * @param branchName The current branch name of the local repository, or
   *        {@code null} if that information is unavailable.  See {@link
   *        Results.GitInfo#branchName()}.
   * @param browseRepositoryUrl The URL of the web page for the {@linkplain
   *        #repositoryUrl() repository} used in this run, suitable for viewing
   *        in a web browser, or {@code null} if the repository is unknown or if
   *        this application cannot determine the web page for that repository.
   * @param browseCommitUrl The URL of the web page for the {@linkplain
   *        #commitId() commit} used in this run, suitable for viewing in a web
   *        browser, or {@code null} if the commit is unknown or if this
   *        application cannot determine the web page for that commit.
   * @param browseBranchUrl The URL of the web page for the {@linkplain
   *        #branchName() branch} used in this run, suitable for viewing in a
   *        web browser, or {@code null} if the branch is unknown or if this
   *        application cannot determine the web page for that branch.
   * @param failures The list of frameworks that failed to start or stop or that
   *        had at least one test failure.
   * @param jsonFileName The file name of the results.json file for this run, or
   *        {@code null} if this run has no results.json file.
   * @param zipFileName The file name of the results.zip file for this run, or
   *        {@code null} if this run has no results.zip file.
   * @param visualizeResultsUrl The TFB website URL for visualizing the results
   *        of this run, or {@code null} if this application does not currently
   *        have sufficient test metadata for this run to be visualized.
   * @param lastCompletedFramework The name of the framework that most recently
   *        completed testing, or {@code null} if no framework has completed
   *        testing.
   */
  @Immutable
  public record ResultsView(

      @JsonProperty(value = "uuid", required = true)
      @Nullable String uuid,

      @JsonProperty(value = "name", required = true)
      @Nullable String name,

      @JsonProperty(value = "environmentDescription", required = true)
      @Nullable String environmentDescription,

      @JsonProperty(value = "completedFrameworks", required = true)
      int completedFrameworks,

      @JsonProperty(value = "frameworksWithCleanSetup", required = true)
      int frameworksWithCleanSetup,

      @JsonProperty(value = "frameworksWithSetupProblems", required = true)
      int frameworksWithSetupProblems,

      @JsonProperty(value = "totalFrameworks", required = true)
      int totalFrameworks,

      @JsonProperty(value = "successfulTests", required = true)
      int successfulTests,

      @JsonProperty(value = "failedTests", required = true)
      int failedTests,

      @JsonProperty(value = "startTime", required = true)
      @Nullable String startTime,

      @JsonProperty(value = "completionTime", required = true)
      @Nullable String completionTime,

      @JsonProperty(value = "lastUpdated", required = true)
      @Nullable String lastUpdated,

      @JsonProperty(value = "elapsedDuration", required = true)
      @Nullable String elapsedDuration,

      @JsonProperty(value = "estimatedRemainingDuration", required = true)
      @Nullable String estimatedRemainingDuration,

      @JsonProperty(value = "commitId", required = true)
      @Nullable String commitId,

      @JsonProperty(value = "repositoryUrl", required = true)
      @Nullable String repositoryUrl,

      @JsonProperty(value = "branchName", required = true)
      @Nullable String branchName,

      @JsonProperty(value = "browseRepositoryUrl", required = true)
      @Nullable String browseRepositoryUrl,

      @JsonProperty(value = "browseCommitUrl", required = true)
      @Nullable String browseCommitUrl,

      @JsonProperty(value = "browseBranchUrl", required = true)
      @Nullable String browseBranchUrl,

      @JsonProperty(value = "failures", required = true)
      ImmutableList<Failure> failures,

      @JsonProperty(value = "jsonFileName", required = true)
      @Nullable String jsonFileName,

      @JsonProperty(value = "zipFileName", required = true)
      @Nullable String zipFileName,

      @JsonProperty(value = "visualizeResultsUrl", required = true)
      @Nullable String visualizeResultsUrl,

      @JsonProperty(value = "lastCompletedFramework", required = true)
      @Nullable String lastCompletedFramework) {

    @JsonCreator
    public ResultsView {
      Objects.requireNonNull(failures);
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
    public record FileName(String fileName) {
      public FileName {
        Objects.requireNonNull(fileName);
      }
    }

    /**
     * A view of a framework that failed to start or stop or that failed at
     * least one test type.
     *
     * @param framework The name of the framework, such as "gemini-mysql".
     * @param failedTestTypes The set of test types in which the {@linkplain
     *        Results#testOutcome(Results.TestType, String) test outcome} for
     *        this framework was {@linkplain Results.TestOutcome#FAILED
     *        failure}.
     * @param hadSetupProblems {@code true} if this framework failed to start or
     *        stop.
     */
    @Immutable
    public record Failure (

        @JsonProperty(value = "framework", required = true)
        String framework,

        @JsonProperty(value = "failedTestTypes", required = true)
        ImmutableList<String> failedTestTypes,

        @JsonProperty(value = "hadSetupProblems", required = true)
        boolean hadSetupProblems) {

      @JsonCreator
      public Failure {
        Objects.requireNonNull(framework);
        Objects.requireNonNull(failedTestTypes);
      }
    }
  }
}
