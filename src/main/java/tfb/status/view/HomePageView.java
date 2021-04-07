package tfb.status.view;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A view of the home page.
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
      @Nullable String visualizeResultsUrl) {

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
     * A view of a framework that failed to start or that failed at least one
     * test type.
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
