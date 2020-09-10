package tfb.status.view;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A representation of the results.json file from a TFB run.
 *
 * <p>This class contains a subset of the fields defined in a results.json file.
 * In general, a field that is defined in results.json files will be added to
 * this class when this application uses that field, and unused fields will be
 * omitted from this class.
 *
 * <p>Instances of this class are deserialized from JSON.  Instances of this
 * class are serialized to JSON in tests only; the serialized form of this class
 * is never exposed to users.
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
  public final ImmutableSet<String> frameworks;

  /**
   * The mapping of framework names to the times their tests completed.
   */
  public final ImmutableMap<String, String> completed;

  /**
   * Maps test types to the names of the frameworks that succeeded at that test
   * type in this run.
   *
   * <p>This mapping does not take into account whether the framework achieved
   * at least one request during the test.
   */
  public final TestTypeToFrameworks succeeded;

  /**
   * Maps test types to the names of the frameworks that failed at that test
   * type in this run.
   *
   * <p>This mapping does not take into account whether the framework achieved
   * at least one request during the test.
   */
  public final TestTypeToFrameworks failed;

  /**
   * Maps test types and framework names to the list of raw results for that
   * test type and framework.
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

  /**
   * The test metadata, generated during this run (same as test_metadata.json).
   * Test metadata was not always included in the results, so this will be
   * {@code null} for old runs.
   *
   * <p>This field was added in January 2020 and was not present in results
   * gathered prior to that date.
   */
  public final @Nullable ImmutableList<TestDefinition> testMetadata;

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
      ImmutableSet<String> frameworks,

      @JsonProperty(value = "completed", required = true)
      ImmutableMap<String, String> completed,

      @JsonProperty(value = "succeeded", required = true)
      TestTypeToFrameworks succeeded,

      @JsonProperty(value = "failed", required = true)
      TestTypeToFrameworks failed,

      @JsonProperty(value = "rawData", required = true)
      RawData rawData,

      @JsonProperty(value = "queryIntervals", required = true)
      ImmutableList<Integer> queryIntervals,

      @JsonProperty(value = "concurrencyLevels", required = true)
      ImmutableList<Integer> concurrencyLevels,

      @JsonProperty(value = "git", required = false)
      @Nullable GitInfo git,

      @JsonProperty(value = "testMetadata", required = false)
      @Nullable ImmutableList<TestDefinition> testMetadata) {

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
    this.testMetadata = testMetadata;
  }

  /**
   * Returns the total number of requests achieved by a framework in a test.
   */
  public long requests(TestType testType, String framework) {
    Objects.requireNonNull(testType);
    Objects.requireNonNull(framework);

    ImmutableList<SingleWrkExecution> executions =
        rawData.get(testType, framework);

    return switch (testType) {
      //
      // For these test types, we vary the concurrency (simultaneous requests
      // from the client) and then use the highest total requests from any
      // concurrency level.
      //
      case JSON, PLAINTEXT, DB, FORTUNE ->
          executions.stream()
                    .mapToLong(execution -> execution.successfulRequests())
                    .max()
                    .orElse(0);
      //
      // For these test types, we vary the number of database queries per
      // request and then use the total requests of the wrk execution for the
      // highest number of queries, which is the last execution in the list.
      //
      case QUERY, UPDATE, CACHED_QUERY ->
          executions.isEmpty()
              ? 0
              : executions.get(executions.size() - 1)
                          .successfulRequests();
    };
  }

  /**
   * Returns the requests per second achieved by a framework in a test.
   */
  public double rps(TestType testType, String framework) {
    Objects.requireNonNull(testType);
    Objects.requireNonNull(framework);
    long requests = requests(testType, framework);
    return ((double) requests) / duration;
  }

  /**
   * Returns a {@link TestOutcome} describing what happened for the specified
   * framework in the specified test type in this run.
   */
  public TestOutcome testOutcome(TestType testType, String framework) {
    Objects.requireNonNull(testType);
    Objects.requireNonNull(framework);

    if (failed.contains(testType, framework))
      return TestOutcome.FAILED;

    if (succeeded.contains(testType, framework))
      return (requests(testType, framework) == 0)
          ? TestOutcome.FAILED
          : TestOutcome.SUCCEEDED;

    return TestOutcome.NOT_IMPLEMENTED_OR_NOT_YET_TESTED;
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (object == this) {
      return true;
    } else if (!(object instanceof Results)) {
      return false;
    } else {
      Results that = (Results) object;
      return this.duration == that.duration
          && Objects.equals(this.uuid, that.uuid)
          && Objects.equals(this.name, that.name)
          && Objects.equals(this.environmentDescription, that.environmentDescription)
          && Objects.equals(this.startTime, that.startTime)
          && Objects.equals(this.completionTime, that.completionTime)
          && this.frameworks.equals(that.frameworks)
          && this.completed.equals(that.completed)
          && this.succeeded.equals(that.succeeded)
          && this.failed.equals(that.failed)
          && this.rawData.equals(that.rawData)
          && this.queryIntervals.equals(that.queryIntervals)
          && this.concurrencyLevels.equals(that.concurrencyLevels)
          && Objects.equals(this.git, that.git)
          && Objects.equals(this.testMetadata, that.testMetadata);
    }
  }

  @Override
  public int hashCode() {
    int hash = 1;
    hash = 31 * hash + Objects.hashCode(uuid);
    hash = 31 * hash + Objects.hashCode(name);
    hash = 31 * hash + Objects.hashCode(environmentDescription);
    hash = 31 * hash + Objects.hashCode(startTime);
    hash = 31 * hash + Objects.hashCode(completionTime);
    hash = 31 * hash + Long.hashCode(duration);
    hash = 31 * hash + frameworks.hashCode();
    hash = 31 * hash + completed.hashCode();
    hash = 31 * hash + succeeded.hashCode();
    hash = 31 * hash + failed.hashCode();
    hash = 31 * hash + rawData.hashCode();
    hash = 31 * hash + queryIntervals.hashCode();
    hash = 31 * hash + concurrencyLevels.hashCode();
    hash = 31 * hash + Objects.hashCode(git);
    hash = 31 * hash + Objects.hashCode(testMetadata);
    return hash;
  }

  /**
   * A high-level description of what happened for a particular [framework, test
   * type] combination in a run.
   */
  public enum TestOutcome {
    /**
     * This framework succeeded at achieving at least one request in this test
     * type.
     */
    SUCCEEDED,

    /**
     * This framework failed to achieve at least one request in this test type.
     * Possible reasons for this failure include: the framework did not start;
     * the framework started but it failed validation for this test type; the
     * framework passed validation but did not respond to any requests during
     * the tests.
     */
    FAILED,

    /**
     * This framework did not implement this test type, or this [framework, test
     * type] combination has not yet been tested in this run.
     */
    NOT_IMPLEMENTED_OR_NOT_YET_TESTED
  }

  /**
   * A mapping of test types to names of frameworks.
   */
  @Immutable
  public static final class TestTypeToFrameworks {
    /**
     * The names of frameworks associated with the JSON serialization test in
     * this mapping.
     */
    public final @Nullable ImmutableSet<String> json;

    /**
     * The names of frameworks associated with the plaintext test in this
     * mapping.
     */
    public final @Nullable ImmutableSet<String> plaintext;

    /**
     * The names of frameworks associated with the single query test in this
     * mapping.
     */
    public final @Nullable ImmutableSet<String> db;

    /**
     * The names of frameworks associated with the multiple queries test in this
     * mapping.
     */
    public final @Nullable ImmutableSet<String> query;

    /**
     * The names of frameworks associated with the data updates test in this
     * mapping.
     */
    public final @Nullable ImmutableSet<String> update;

    /**
     * The names of frameworks associated with the fortunes test in this
     * mapping.
     */
    public final @Nullable ImmutableSet<String> fortune;

    @JsonProperty("cached_query")
    public final @Nullable ImmutableSet<String> cachedQuery;

    @JsonCreator
    public TestTypeToFrameworks(

        @JsonProperty(value = "json", required = false)
        @Nullable ImmutableSet<String> json,

        @JsonProperty(value = "plaintext", required = false)
        @Nullable ImmutableSet<String> plaintext,

        @JsonProperty(value = "db", required = false)
        @Nullable ImmutableSet<String> db,

        @JsonProperty(value = "query", required = false)
        @Nullable ImmutableSet<String> query,

        @JsonProperty(value = "update", required = false)
        @Nullable ImmutableSet<String> update,

        @JsonProperty(value = "fortune", required = false)
        @Nullable ImmutableSet<String> fortune,

        @JsonProperty(value = "cached_query", required = false)
        @JsonAlias("cached-query")
        @Nullable ImmutableSet<String> cachedQuery) {

      this.json = json;
      this.plaintext = plaintext;
      this.db = db;
      this.query = query;
      this.update = update;
      this.fortune = fortune;
      this.cachedQuery = cachedQuery;
    }

    /**
     * Returns the names of the frameworks associated with the specified test
     * type in this mapping.
     */
    ImmutableSet<String> get(TestType testType) {
      Objects.requireNonNull(testType);
      ImmutableSet<String> frameworks =
          switch (testType) {
            case JSON -> json;
            case PLAINTEXT -> plaintext;
            case DB -> db;
            case QUERY -> query;
            case UPDATE -> update;
            case FORTUNE -> fortune;
            case CACHED_QUERY -> cachedQuery;
          };
      return (frameworks == null) ? ImmutableSet.of() : frameworks;
    }

    /**
     * Returns {@code true} if the specified framework and test type are
     * associated in this mapping.
     */
    boolean contains(TestType testType, String framework) {
      Objects.requireNonNull(testType);
      Objects.requireNonNull(framework);
      return get(testType).contains(framework);
    }

    @Override
    public boolean equals(@Nullable Object object) {
      if (object == this) {
        return true;
      } else if (!(object instanceof TestTypeToFrameworks)) {
        return false;
      } else {
        TestTypeToFrameworks that = (TestTypeToFrameworks) object;
        return Objects.equals(this.json, that.json)
            && Objects.equals(this.plaintext, that.plaintext)
            && Objects.equals(this.db, that.db)
            && Objects.equals(this.query, that.query)
            && Objects.equals(this.update, that.update)
            && Objects.equals(this.fortune, that.fortune)
            && Objects.equals(this.cachedQuery, that.cachedQuery);
      }
    }

    @Override
    public int hashCode() {
      int hash = 1;
      hash = 31 * hash + Objects.hashCode(json);
      hash = 31 * hash + Objects.hashCode(plaintext);
      hash = 31 * hash + Objects.hashCode(db);
      hash = 31 * hash + Objects.hashCode(query);
      hash = 31 * hash + Objects.hashCode(update);
      hash = 31 * hash + Objects.hashCode(fortune);
      hash = 31 * hash + Objects.hashCode(cachedQuery);
      return hash;
    }
  }

  /**
   * Maps test types and framework names to the list of raw results for that
   * test type and framework.
   */
  @Immutable
  public static final class RawData {
    /**
     * Maps framework names to those frameworks' raw results in the JSON
     * serialization test.
     */
    public final @Nullable ImmutableListMultimap<String, SingleWrkExecution> json;

    /**
     * Maps framework names to those frameworks' raw results in the plaintext
     * test.
     */
    public final @Nullable ImmutableListMultimap<String, SingleWrkExecution> plaintext;

    /**
     * Maps framework names to those frameworks' raw results in the single query
     * test.
     */
    public final @Nullable ImmutableListMultimap<String, SingleWrkExecution> db;

    /**
     * Maps framework names to those frameworks' raw results in the multiple
     * queries test.
     */
    public final @Nullable ImmutableListMultimap<String, SingleWrkExecution> query;

    /**
     * Maps framework names to those frameworks' raw results in the data updates
     * test.
     */
    public final @Nullable ImmutableListMultimap<String, SingleWrkExecution> update;

    /**
     * Maps framework names to those frameworks' raw results in the fortunes
     * test.
     */
    public final @Nullable ImmutableListMultimap<String, SingleWrkExecution> fortune;

    /**
     * Maps framework names to those frameworks' raw results in the cached
     * queries test.
     */
    @JsonProperty("cached_query")
    public final @Nullable ImmutableListMultimap<String, SingleWrkExecution> cachedQuery;

    @JsonCreator
    public RawData(

        // Note: The incoming data cannot be represented as a Map<TestType, ?>
        //       because its keys are not exclusively test types.  The incoming
        //       data also contains "slocCounts" and "commitCounts" keys whose
        //       values are structured differently than the test types' values.

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
        @JsonAlias("cached-query")
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
    ImmutableListMultimap<String, SingleWrkExecution> get(TestType testType) {
      Objects.requireNonNull(testType);
      ImmutableListMultimap<String, SingleWrkExecution> m =
          switch (testType) {
            case JSON -> json;
            case PLAINTEXT -> plaintext;
            case DB -> db;
            case QUERY -> query;
            case UPDATE -> update;
            case FORTUNE -> fortune;
            case CACHED_QUERY -> cachedQuery;
          };
      return (m == null) ? ImmutableListMultimap.of() : m;
    }

    /**
     * Extracts the raw results data for the given test type and framework.
     */
    ImmutableList<SingleWrkExecution> get(TestType testType, String framework) {
      Objects.requireNonNull(testType);
      Objects.requireNonNull(framework);
      return get(testType).get(framework);
    }

    @Override
    public boolean equals(@Nullable Object object) {
      if (object == this) {
        return true;
      } else if (!(object instanceof RawData)) {
        return false;
      } else {
        RawData that = (RawData) object;
        return Objects.equals(this.json, that.json)
            && Objects.equals(this.plaintext, that.plaintext)
            && Objects.equals(this.db, that.db)
            && Objects.equals(this.query, that.query)
            && Objects.equals(this.update, that.update)
            && Objects.equals(this.fortune, that.fortune)
            && Objects.equals(this.cachedQuery, that.cachedQuery);
      }
    }

    @Override
    public int hashCode() {
      int hash = 1;
      hash = 31 * hash + Objects.hashCode(json);
      hash = 31 * hash + Objects.hashCode(plaintext);
      hash = 31 * hash + Objects.hashCode(db);
      hash = 31 * hash + Objects.hashCode(query);
      hash = 31 * hash + Objects.hashCode(update);
      hash = 31 * hash + Objects.hashCode(fortune);
      hash = 31 * hash + Objects.hashCode(cachedQuery);
      return hash;
    }
  }

  /**
   * Data collected from a single execution of wrk.
   */
  @Immutable
  @JsonInclude(NON_DEFAULT)
  public static final class SingleWrkExecution {
    /**
     * The total number of HTTP requests <em>completed</em> by wrk regardless of
     * the response status code, not including requests that failed to complete
     * due to timeouts or socket-level errors.
     *
     * <p>Subtract {@link #status5xx} from this number to determine the total
     * number of successful HTTP requests.
     */
    public final long totalRequests;

    /**
     * The total number of HTTP requests completed by wrk having response status
     * codes not in the 2xx or 3xx range.
     *
     * <p>Subtract this number from {@link #totalRequests} to determine the
     * total number of successful HTTP requests.
     */
    @JsonProperty("5xx") public final int status5xx;

    @JsonCreator
    public SingleWrkExecution(

        @JsonProperty(value = "totalRequests", required = false)
        long totalRequests,

        @JsonProperty(value = "5xx", required = false)
        int status5xx) {

      this.totalRequests = totalRequests;
      this.status5xx = status5xx;
    }

    /**
     * The total number of successful HTTP requests completed by wrk during this
     * execution.
     */
    long successfulRequests() {
      return totalRequests - status5xx;
    }

    @Override
    public boolean equals(@Nullable Object object) {
      if (object == this) {
        return true;
      } else if (!(object instanceof SingleWrkExecution)) {
        return false;
      } else {
        SingleWrkExecution that = (SingleWrkExecution) object;
        return this.totalRequests == that.totalRequests
            && this.status5xx == that.status5xx;
      }
    }

    @Override
    public int hashCode() {
      int hash = 1;
      hash = 31 * hash + Long.hashCode(totalRequests);
      hash = 31 * hash + Integer.hashCode(status5xx);
      return hash;
    }
  }

  /**
   * Information about the state of the local git repository for this run.
   */
  @Immutable
  public static final class GitInfo {
    /**
     * The current commit id of the local repository.  Equivalent to the output
     * of {@code git rev-parse HEAD}.
     */
    public final String commitId;

    /**
     * The name of the remote repository from which the local repository was
     * cloned, such as "https://github.com/TechEmpower/FrameworkBenchmarks.git".
     * Equivalent to the output of {@code git config --get remote.origin.url}.
     */
    public final String repositoryUrl;

    /**
     * The current branch name of the local repository, or {@code null} if that
     * information is unavailable.  Equivalent to the output of {@code git
     * rev-parse --abbrev-ref HEAD}.
     *
     * <p>This field was added a few weeks after the {@link #commitId} and
     * {@link #repositoryUrl} fields, so there are a few runs where this field
     * is {@code null} and those other fields are non-{@code null}.
     */
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

    @Override
    public boolean equals(@Nullable Object object) {
      if (object == this) {
        return true;
      } else if (!(object instanceof GitInfo)) {
        return false;
      } else {
        GitInfo that = (GitInfo) object;
        return this.commitId.equals(that.commitId)
            && this.repositoryUrl.equals(that.repositoryUrl)
            && Objects.equals(this.branchName, that.branchName);
      }
    }

    @Override
    public int hashCode() {
      int hash = 1;
      hash = 31 * hash + commitId.hashCode();
      hash = 31 * hash + repositoryUrl.hashCode();
      hash = 31 * hash + Objects.hashCode(branchName);
      return hash;
    }
  }

  /**
   * A type of test in TFB.  Support for each test type is hardcoded into the
   * TFB toolset and TFB website.  The set of test types in TFB changes very
   * infrequently.
   */
  // Be careful with this enum when deserializing results.json files.  This enum
  // does not provide a way to represent new/unknown test types, and we don't
  // want to throw exceptions when parsing results.json files that contain
  // new/unknown test types.
  public enum TestType {
    // These enum constants are arranged in the same order as the test type tabs
    // on the TFB website.

    /**
     * The JSON serialization test.
     */
    JSON("json"),

    /**
     * The single query test.
     */
    DB("db"),

    /**
     * The multiple queries test.
     */
    QUERY("query"),

    /**
     * The cached queries test.
     */
    // We renamed the "cached_query" test type to "cached-query" on July 2,
    // 2020.  This application will continue to support both names forever.  In
    // the timeline view, for example, we want to be able to plot "cached_query"
    // data from older results.json files alongside "cached-query" data from
    // newer results.json files.
    CACHED_QUERY("cached_query", "cached-query"),

    /**
     * The fortunes test.
     */
    FORTUNE("fortune"),

    /**
     * The data updates test.
     */
    UPDATE("update"),

    /**
     * The plaintext test.
     */
    PLAINTEXT("plaintext");

    /**
     * The name of this test type as it appears in results.json files.
     */
    private final String canonicalName;

    /**
     * Alternative or historical names for this test type, provided for
     * compatibility when this test type was or will be renamed.
     */
    private final ImmutableSet<String> aliases;

    TestType(String canonicalName, String... aliases) {
      this.canonicalName = Objects.requireNonNull(canonicalName);
      this.aliases = ImmutableSet.copyOf(aliases);
    }

    /**
     * The name of this test type as it appears in results.json files.
     */
    @JsonValue
    public String serialize() {
      return canonicalName;
    }

    /**
     * Returns the test type represented by the specified string, or {@code
     * null} if there is no such test type.  Supports alternative/historical
     * names of test types.
     *
     * @param serializedName the name of this test type as it appears in
     *        results.json files
     */
    @JsonCreator
    public static @Nullable TestType deserialize(String serializedName) {
      Objects.requireNonNull(serializedName);
      return BY_SERIALIZED_NAME.get(serializedName);
    }

    private static final ImmutableMap<String, TestType> BY_SERIALIZED_NAME;
    static {
      var builder = new ImmutableMap.Builder<String, TestType>();
      for (TestType testType : values()) {
        builder.put(testType.canonicalName, testType);
        for (String alias : testType.aliases) {
          builder.put(alias, testType);
        }
      }
      BY_SERIALIZED_NAME = builder.build();
    }
  }
}
