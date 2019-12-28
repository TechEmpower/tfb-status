package tfb.status.view;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A representation of the results.json file from a TFB run.
 */
@Immutable
public final class Results {
  /**
   * The universally unique id for this set of results, or {@code null} if that
   * information is unavailable.
   *
   * <p>This field was added in March 2017 and was not present in results
   * gathered prior to that date.
   */
  public final @Nullable String uuid;

  /**
   * The informal name for this set of results, or {@code null} if that
   * information is unavailable.
   *
   * <p>This field was added in March 2017 and was not present in results
   * gathered prior to that date.
   */
  public final @Nullable String name;

  /**
   * The informal description of the environment that produced this set of
   * results, or {@code null} if that information is unavailable.
   *
   * <p>This field was added in March 2017 and was not present in results
   * gathered prior to that date.
   */
  public final @Nullable String environmentDescription;

  /**
   * The epoch millisecond timestamp of when this run started, or {@code null}
   * if that information is unavailable.
   *
   * <p>This field was added in March 2017 and was not present in results
   * gathered prior to that date.
   */
  public final @Nullable Long startTime;

  /**
   * The epoch millisecond timestamp of when this run completed, or {@code null}
   * if that information is unavailable.
   *
   * <p>This field was added in March 2017 and was not present in results
   * gathered prior to that date.
   */
  public final @Nullable Long completionTime;

  /**
   * The duration in seconds of each test.
   */
  public final long duration;

  /**
   * The names of the frameworks in the run.
   */
  public final ImmutableList<String> frameworks;

  /**
   * The mapping of framework names to the times their tests completed.
   */
  public final ImmutableMap<String, String> completed;

  /**
   * The mapping of test types to the list of frameworks that succeeded at that
   * test type in this run.
   */
  public final ImmutableListMultimap<String, String> succeeded;

  /**
   * The mapping of test types to the list of frameworks that failed at that
   * test type in this run.
   */
  public final ImmutableListMultimap<String, String> failed;

  /**
   * Maps test type names and framework names to the list of raw results for
   * that test type and framework.
   */
  public final RawData rawData;

  /**
   * The different numbers of database queries per HTTP request that were
   * tested, for the database-related tests that vary the number of queries.
   */
  public final ImmutableList<Integer> queryIntervals;

  /**
   * The different number of client-side request concurrency levels that were
   * tested.
   */
  public final ImmutableList<Integer> concurrencyLevels;

  /**
   * Information about the state of the local git repository for this run, or
   * {@code null} if the state of the git repository is unknown.
   *
   * <p>This field was added in February 2018 and was not present in results
   * gathered prior to that date.
   */
  public final @Nullable GitInfo git;

  @JsonCreator
  public Results(

      @JsonProperty(value = "uuid", required = false)
      @Nullable String uuid,

      @JsonProperty(value = "name", required = false)
      @Nullable String name,

      @JsonProperty(value = "environmentDescription", required = false)
      @Nullable String environmentDescription,

      @JsonProperty(value = "startTime", required = false)
      @Nullable Long startTime,

      @JsonProperty(value = "completionTime", required = false)
      @Nullable Long completionTime,

      @JsonProperty(value = "duration", required = true)
      long duration,

      @JsonProperty(value = "frameworks", required = true)
      ImmutableList<String> frameworks,

      @JsonProperty(value = "completed", required = true)
      ImmutableMap<String, String> completed,

      @JsonProperty(value = "succeeded", required = true)
      ImmutableListMultimap<String, String> succeeded,

      @JsonProperty(value = "failed", required = true)
      ImmutableListMultimap<String, String> failed,

      @JsonProperty(value = "rawData", required = true)
      RawData rawData,

      @JsonProperty(value = "queryIntervals", required = true)
      ImmutableList<Integer> queryIntervals,

      @JsonProperty(value = "concurrencyLevels", required = true)
      ImmutableList<Integer> concurrencyLevels,

      @JsonProperty(value = "git", required = false)
      @Nullable GitInfo git) {

    this.uuid = uuid;
    this.name = name;
    this.environmentDescription = environmentDescription;
    this.startTime = startTime;
    this.completionTime = completionTime;
    this.duration = duration;
    this.frameworks = Objects.requireNonNull(frameworks);
    this.completed = Objects.requireNonNull(completed);
    this.succeeded = Objects.requireNonNull(succeeded);
    this.failed = Objects.requireNonNull(failed);
    this.rawData = Objects.requireNonNull(rawData);
    this.queryIntervals = Objects.requireNonNull(queryIntervals);
    this.concurrencyLevels = Objects.requireNonNull(concurrencyLevels);
    this.git = git;
  }

  /**
   * Returns the total number of requests achieved by a framework in a test.
   */
  public long requests(String testType, String framework) {
    Objects.requireNonNull(testType);
    Objects.requireNonNull(framework);

    ImmutableList<SingleWrkExecution> executions =
        rawData.get(testType, framework);

    switch (testType) {
      //
      // For these test types, we vary the concurrency (simultaneous requests
      // from the client) and then use the highest total requests from any
      // concurrency level.
      //
      case "json":
      case "plaintext":
      case "db":
      case "fortune":
        return executions.stream()
                         .mapToLong(execution -> execution.successfulRequests())
                         .max()
                         .orElse(0);

      //
      // For these test types, we vary the number of database queries per
      // request and then use the total requests of the wrk execution for the
      // highest number of queries, which is the last execution in the list.
      //
      case "query":
      case "update":
      case "cached_query":
        return executions.isEmpty()
            ? 0
            : executions.get(executions.size() - 1)
                        .successfulRequests();

      default:
        return 0;
    }
  }

  /**
   * Returns the requests per second achieved by a framework in a test.
   */
  public double rps(String testType, String framework) {
    Objects.requireNonNull(testType);
    Objects.requireNonNull(framework);
    long requests = requests(testType, framework);
    return ((double) requests) / duration;
  }

  /**
   * Maps test type names and framework names to the list of raw results for
   * that test type and framework.
   */
  @Immutable
  public static final class RawData {
    public final @Nullable ImmutableListMultimap<String, SingleWrkExecution> json;
    public final @Nullable ImmutableListMultimap<String, SingleWrkExecution> plaintext;
    public final @Nullable ImmutableListMultimap<String, SingleWrkExecution> db;
    public final @Nullable ImmutableListMultimap<String, SingleWrkExecution> query;
    public final @Nullable ImmutableListMultimap<String, SingleWrkExecution> update;
    public final @Nullable ImmutableListMultimap<String, SingleWrkExecution> fortune;

    @JsonProperty("cached_query")
    public final @Nullable ImmutableListMultimap<String, SingleWrkExecution> cachedQuery;

    @JsonCreator
    public RawData(

        @JsonProperty(value = "json", required = false)
        @Nullable ImmutableListMultimap<String, SingleWrkExecution> json,

        @JsonProperty(value = "plaintext", required = false)
        @Nullable ImmutableListMultimap<String, SingleWrkExecution> plaintext,

        @JsonProperty(value = "db", required = false)
        @Nullable ImmutableListMultimap<String, SingleWrkExecution> db,

        @JsonProperty(value = "query", required = false)
        @Nullable ImmutableListMultimap<String, SingleWrkExecution> query,

        @JsonProperty(value = "update", required = false)
        @Nullable ImmutableListMultimap<String, SingleWrkExecution> update,

        @JsonProperty(value = "fortune", required = false)
        @Nullable ImmutableListMultimap<String, SingleWrkExecution> fortune,

        @JsonProperty(value = "cached_query", required = false)
        @Nullable ImmutableListMultimap<String, SingleWrkExecution> cachedQuery) {

      this.json = json;
      this.plaintext = plaintext;
      this.db = db;
      this.query = query;
      this.update = update;
      this.fortune = fortune;
      this.cachedQuery = cachedQuery;
    }

    /**
     * Extracts the raw results data for the given test type, grouping by
     * framework (the keys of the returned multimap are framework names).
     */
    ImmutableListMultimap<String, SingleWrkExecution> get(String testType) {
      ImmutableListMultimap<String, SingleWrkExecution> m;
      switch (testType) {
        case "json":         m = json; break;
        case "plaintext":    m = plaintext; break;
        case "db":           m = db; break;
        case "query":        m = query; break;
        case "update":       m = update; break;
        case "fortune":      m = fortune; break;
        case "cached_query": m = cachedQuery; break;
        default:             m = null; break;
      }
      return (m == null) ? ImmutableListMultimap.of() : m;
    }

    /**
     * Extracts the raw results data for the given test type and framework.
     */
    ImmutableList<SingleWrkExecution> get(String testType, String framework) {
      return get(testType).get(framework);
    }
  }

  /**
   * Data collected from a single execution of wrk.
   */
  @Immutable
  @JsonInclude(NON_DEFAULT)
  public static final class SingleWrkExecution {
    public final long totalRequests;
    public final @Nullable String latencyAvg;
    public final @Nullable String latencyMax;
    public final @Nullable String latencyStdev;
    @JsonProperty("5xx") public final int status5xx;
    public final int write;
    public final int read;
    public final int connect;

    @JsonCreator
    public SingleWrkExecution(

        @JsonProperty(value = "totalRequests", required = false)
        long totalRequests,

        @JsonProperty(value = "latencyAvg", required = false)
        @Nullable String latencyAvg,

        @JsonProperty(value = "latencyMax", required = false)
        @Nullable String latencyMax,

        @JsonProperty(value = "latencyStdev", required = false)
        @Nullable String latencyStdev,

        @JsonProperty(value = "5xx", required = false)
        int status5xx,

        @JsonProperty(value = "write", required = false)
        int write,

        @JsonProperty(value = "read", required = false)
        int read,

        @JsonProperty(value = "connect", required = false)
        int connect) {

      this.totalRequests = totalRequests;
      this.latencyAvg = latencyAvg;
      this.latencyMax = latencyMax;
      this.latencyStdev = latencyStdev;
      this.status5xx = status5xx;
      this.write = write;
      this.read = read;
      this.connect = connect;
    }

    /**
     * The total number of successful requests during this execution.
     */
    long successfulRequests() {
      return totalRequests - status5xx;
    }
  }

  /**
   * Information about the state of the local git repository for this run.
   */
  @Immutable
  public static final class GitInfo {
    public final String commitId;
    public final String repositoryUrl;
    public final @Nullable String branchName;

    @JsonCreator
    public GitInfo(

        @JsonProperty(value = "commitId", required = true)
        String commitId,

        @JsonProperty(value = "repositoryUrl", required = true)
        String repositoryUrl,

        @JsonProperty(value = "branchName", required = false)
        @Nullable String branchName) {

      this.commitId = Objects.requireNonNull(commitId);
      this.repositoryUrl = Objects.requireNonNull(repositoryUrl);
      this.branchName = branchName;
    }
  }

  /**
   * The set of all known test types.
   */
  public static final ImmutableSet<String> TEST_TYPES =
      ImmutableSet.of("json",
                      "plaintext",
                      "db",
                      "query",
                      "update",
                      "fortune",
                      "cached_query");
}
