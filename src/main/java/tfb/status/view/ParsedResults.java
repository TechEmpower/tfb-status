package tfb.status.view;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * A representation of the results.json file from a TFB run.
 */
@Immutable
public final class ParsedResults {
  /**
   * The universally unique id for this set of results, or {@code null} if that
   * information is unavailable.
   *
   * <p>This field was added in March 2017 and was not present in results
   * gathered prior to that date.
   */
  @Nullable
  public final String uuid;

  /**
   * The informal name for this set of results, or {@code null} if that
   * information is unavailable.
   *
   * <p>This field was added in March 2017 and was not present in results
   * gathered prior to that date.
   */
  @Nullable
  public final String name;

  /**
   * The informal description of the environment that produced this set of
   * results, or {@code null} if that information is unavailable.
   *
   * <p>This field was added in March 2017 and was not present in results
   * gathered prior to that date.
   */
  @Nullable
  public final String environmentDescription;

  /**
   * The epoch millisecond timestamp of when this run started, or {@code null}
   * if that information is unavailable.
   *
   * <p>This field was added in March 2017 and was not present in results
   * gathered prior to that date.
   */
  @Nullable
  public final Long startTime;

  /**
   * The epoch millisecond timestamp of when this run completed, or {@code null}
   * if that information is unavailable.
   *
   * <p>This field was added in March 2017 and was not present in results
   * gathered prior to that date.
   */
  @Nullable
  public final Long completionTime;

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

  @JsonCreator
  public ParsedResults(

      @Nullable
      @JsonProperty(value = "uuid", required = false)
      String uuid,

      @Nullable
      @JsonProperty(value = "name", required = false)
      String name,

      @Nullable
      @JsonProperty(value = "environmentDescription", required = false)
      String environmentDescription,

      @Nullable
      @JsonProperty(value = "startTime", required = false)
      Long startTime,

      @Nullable
      @JsonProperty(value = "completionTime", required = false)
      Long completionTime,

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
      ImmutableList<Integer> concurrencyLevels) {

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
  }

  @Immutable
  public static final class RawData {
    public final ImmutableListMultimap<String, SingleWrkExecution> json;
    public final ImmutableListMultimap<String, SingleWrkExecution> plaintext;
    public final ImmutableListMultimap<String, SingleWrkExecution> db;
    public final ImmutableListMultimap<String, SingleWrkExecution> query;
    public final ImmutableListMultimap<String, SingleWrkExecution> update;
    public final ImmutableListMultimap<String, SingleWrkExecution> fortune;
    public final ImmutableListMultimap<String, SingleWrkExecution> cachedQuery;

    @JsonCreator
    public RawData(

        @Nullable
        @JsonProperty(value = "json", required = false)
        ImmutableListMultimap<String, SingleWrkExecution> json,

        @Nullable
        @JsonProperty(value = "plaintext", required = false)
        ImmutableListMultimap<String, SingleWrkExecution> plaintext,

        @Nullable
        @JsonProperty(value = "db", required = false)
        ImmutableListMultimap<String, SingleWrkExecution> db,

        @Nullable
        @JsonProperty(value = "query", required = false)
        ImmutableListMultimap<String, SingleWrkExecution> query,

        @Nullable
        @JsonProperty(value = "update", required = false)
        ImmutableListMultimap<String, SingleWrkExecution> update,

        @Nullable
        @JsonProperty(value = "fortune", required = false)
        ImmutableListMultimap<String, SingleWrkExecution> fortune,

        @Nullable
        @JsonProperty(value = "cached_query", required = false)
        ImmutableListMultimap<String, SingleWrkExecution> cachedQuery) {

      this.json =
          Objects.requireNonNullElseGet(
              json,
              ImmutableListMultimap::of);

      this.plaintext =
          Objects.requireNonNullElseGet(
              plaintext,
              ImmutableListMultimap::of);

      this.db =
          Objects.requireNonNullElseGet(
              db,
              ImmutableListMultimap::of);

      this.query =
          Objects.requireNonNullElseGet(
              query,
              ImmutableListMultimap::of);

      this.update =
          Objects.requireNonNullElseGet(
              update,
              ImmutableListMultimap::of);

      this.fortune =
          Objects.requireNonNullElseGet(
              fortune,
              ImmutableListMultimap::of);

      this.cachedQuery =
          Objects.requireNonNullElseGet(
              cachedQuery,
              ImmutableListMultimap::of);
    }
  }

  @Immutable
  @JsonInclude(NON_DEFAULT)
  public static final class SingleWrkExecution {
    public final long totalRequests;
    @Nullable public final String latencyAvg;
    @Nullable public final String latencyMax;
    @Nullable public final String latencyStdev;
    @JsonProperty("5xx") public final int status5xx;
    public final int write;
    public final int read;
    public final int connect;

    @JsonCreator
    public SingleWrkExecution(

        @JsonProperty(value = "totalRequests", required = false)
        long totalRequests,

        @Nullable
        @JsonProperty(value = "latencyAvg", required = false)
        String latencyAvg,

        @Nullable
        @JsonProperty(value = "latencyMax", required = false)
        String latencyMax,

        @Nullable
        @JsonProperty(value = "latencyStdev", required = false)
        String latencyStdev,

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
  }

  /**
   * A view of the results.json for consumption by the TFB website, which
   * contains only the subset of fields that the TFB website needs.
   */
  @Immutable
  public static final class TfbWebsiteView {
    @Nullable public final String name;
    @Nullable public final Long completionTime;
    public final long duration;
    public final ImmutableList<Integer> queryIntervals;
    public final ImmutableList<Integer> concurrencyLevels;
    public final RawData rawData;
    public final ImmutableListMultimap<String, String> failed;

    @JsonCreator
    public TfbWebsiteView(

        @Nullable
        @JsonProperty(value = "name", required = false)
        String name,

        @Nullable
        @JsonProperty(value = "completionTime", required = false)
        Long completionTime,

        @JsonProperty(value = "duration", required = true)
        long duration,

        @JsonProperty(value = "queryIntervals", required = true)
        ImmutableList<Integer> queryIntervals,

        @JsonProperty(value = "concurrencyLevels", required = true)
        ImmutableList<Integer> concurrencyLevels,

        @JsonProperty(value = "rawData", required = true)
        RawData rawData,

        @JsonProperty(value = "failed", required = true)
        ImmutableListMultimap<String, String> failed) {

      this.name = name;
      this.completionTime = completionTime;
      this.duration = duration;
      this.queryIntervals = Objects.requireNonNull(queryIntervals);
      this.concurrencyLevels = Objects.requireNonNull(concurrencyLevels);
      this.rawData = Objects.requireNonNull(rawData);
      this.failed = Objects.requireNonNull(failed);
    }
  }

  /**
   * A slimmed-down view of a results.json file that only includes its
   * (optional) {@link ParsedResults#uuid} field.
   */
  @Immutable
  public static final class UuidOnly {
    /**
     * @see ParsedResults#uuid
     */
    @Nullable
    public final String uuid;

    @JsonCreator
    public UuidOnly(@Nullable @JsonProperty("uuid") String uuid) {
      this.uuid = uuid;
    }
  }

}
