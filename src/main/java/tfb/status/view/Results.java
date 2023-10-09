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
 *
 * @param uuid The universally unique id for this set of results, or {@code
 *        null} if that information is unavailable.  This field was added in
 *        March 2017 and was not present in results gathered prior to that date.
 * @param name The informal name for this set of results, or {@code null} if
 *        that information is unavailable.  This field was added in March 2017
 *        and was not present in results gathered prior to that date.
 * @param environmentDescription The informal description of the environment
 *        that produced this set of results, or {@code null} if that information
 *        is unavailable.  This field was added in March 2017 and was not
 *        present in results gathered prior to that date.
 * @param startTime The epoch millisecond timestamp of when this run started, or
 *        {@code null} if that information is unavailable.  This field was added
 *        in March 2017 and was not present in results gathered prior to that
 *        date.
 * @param completionTime The epoch millisecond timestamp of when this run
 *        completed, or {@code null} if that information is unavailable.  This
 *        field was added in March 2017 and was not present in results gathered
 *        prior to that date.
 * @param duration The duration in seconds of each test.
 * @param frameworks The names of the frameworks in the run.
 * @param completed The mapping of framework names to either (a) the epoch
 *        millisecond timestamp when their tests completed, assuming their tests
 *        started successfully, or (b) an error message describing why the tests
 *        did not start.
 * @param succeeded Maps test types to the names of the frameworks that
 *        succeeded at that test type in this run.  This mapping does not take
 *        into account whether the framework achieved at least one request
 *        during the test.
 * @param failed Maps test types to the names of the frameworks that failed at
 *        that test type in this run.  This mapping does not take into account
 *        whether the framework achieved at least one request during the test.
 * @param rawData Maps test types and framework names to the list of raw results
 *        for that test type and framework.
 * @param queryIntervals The different numbers of database queries per HTTP
 *        request that were tested, for the database-related tests that vary the
 *        number of queries.
 * @param concurrencyLevels The different number of client-side request
 *        concurrency levels that were tested.
 * @param git Information about the state of the local git repository for this
 *        run, or {@code null} if the state of the git repository is unknown.
 *        This field was added in February 2018 and was not present in results
 *        gathered prior to that date.
 * @param testMetadata The test metadata, generated during this run (same as
 *        test_metadata.json).  Test metadata was not always included in the
 *        results, so this will be {@code null} for old runs.  This field was
 *        added in January 2020 and was not present in results gathered prior to
 *        that date.
 */
@Immutable
public record Results(

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

  @JsonCreator
  public Results {
    Objects.requireNonNull(frameworks);
    Objects.requireNonNull(completed);
    Objects.requireNonNull(succeeded);
    Objects.requireNonNull(failed);
    Objects.requireNonNull(rawData);
    Objects.requireNonNull(queryIntervals);
    Objects.requireNonNull(concurrencyLevels);
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
   *
   * @param json The names of frameworks associated with the JSON serialization
   *        test in this mapping.
   * @param plaintext The names of frameworks associated with the plaintext test
   *        in this mapping.
   * @param db The names of frameworks associated with the single query test in
   *        this mapping.
   * @param query The names of frameworks associated with the multiple queries
   *        test in this mapping.
   * @param update The names of frameworks associated with the data updates test
   *        in this mapping.
   * @param fortune The names of frameworks associated with the fortunes test in
   *        this mapping.
   * @param cachedQuery The names of frameworks associated with the cached
   *        queries test in this mapping.
   */
  @Immutable
  public record TestTypeToFrameworks(

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

    @JsonCreator
    public TestTypeToFrameworks {}

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
  }

  /**
   * Maps test types and framework names to the list of raw results for that
   * test type and framework.
   *
   * @param json Maps framework names to those frameworks' raw results in the
   *        JSON serialization test.
   * @param plaintext Maps framework names to those frameworks' raw results in
   *        the plaintext test.
   * @param db Maps framework names to those frameworks' raw results in the
   *        single query test.
   * @param query Maps framework names to those frameworks' raw results in the
   *        multiple queries test.
   * @param update Maps framework names to those frameworks' raw results in the
   *        data updates test.
   * @param fortune Maps framework names to those frameworks' raw results in the
   *        fortunes test.
   * @param cachedQuery Maps framework names to those frameworks' raw results in
   *        the cached queries test.
   */
  @Immutable
  public record RawData(

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

    @JsonCreator
    public RawData {}

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
  }

  /**
   * Data collected from a single execution of wrk.
   *
   * @param totalRequests The total number of HTTP requests <em>completed</em>
   *        by wrk regardless of the response status code, not including
   *        requests that failed to complete due to timeouts or socket-level
   *        errors.  Subtract {@link #status5xx()} from this number to determine
   *        the total number of successful HTTP requests.
   * @param status5xx The total number of HTTP requests completed by wrk having
   *        response status codes not in the 2xx or 3xx range.  Subtract this
   *        number from {@link #totalRequests()} to determine the total number
   *        of successful HTTP requests.
   */
  @Immutable
  @JsonInclude(NON_DEFAULT)
  public record SingleWrkExecution(

      @JsonProperty(value = "totalRequests", required = false)
      long totalRequests,

      @JsonProperty(value = "5xx", required = false)
      int status5xx) {

    @JsonCreator
    public SingleWrkExecution {}

    /**
     * The total number of successful HTTP requests completed by wrk during this
     * execution.
     */
    long successfulRequests() {
      return totalRequests - status5xx;
    }
  }

  /**
   * Information about the state of the local git repository for this run.
   *
   * @param commitId The current commit id of the local repository.  Equivalent
   *        to the output of {@code git rev-parse HEAD}.
   * @param repositoryUrl The name of the remote repository from which the local
   *        repository was cloned, such as
   *        "https://github.com/TechEmpower/FrameworkBenchmarks.git".
   *        Equivalent to the output of {@code git config --get
   *        remote.origin.url}.
   * @param branchName The current branch name of the local repository, or
   *        {@code null} if that information is unavailable.  Equivalent to the
   *        output of {@code git rev-parse --abbrev-ref HEAD}.  This field was
   *        added a few weeks after the {@link #commitId()} and {@link
   *        #repositoryUrl()} fields, so there are a few runs where this field
   *        is {@code null} and those other fields are non-{@code null}.
   */
  @Immutable
  public record GitInfo(

      @JsonProperty(value = "commitId", required = true)
      String commitId,

      @JsonProperty(value = "repositoryUrl", required = true)
      String repositoryUrl,

      @JsonProperty(value = "branchName", required = false)
      @Nullable String branchName) {

    @JsonCreator
    public GitInfo {
      Objects.requireNonNull(commitId);
      Objects.requireNonNull(repositoryUrl);
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
