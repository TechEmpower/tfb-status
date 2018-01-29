package tfb.status.service;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import java.util.Objects;
import tfb.status.view.ParsedResults;
import tfb.status.view.ParsedResults.SingleWrkExecution;

/**
 * Utility methods for working with {@link ParsedResults}.
 */
public final class ParsedResultsUtils {
  private ParsedResultsUtils() {
    throw new AssertionError("This class cannot be instantiated");
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

  /**
   * Returns the requests per second achieved by a framework in a test.
   */
  public static double getRps(ParsedResults results,
                              String testType,
                              String framework) {
    Objects.requireNonNull(results);
    Objects.requireNonNull(testType);
    Objects.requireNonNull(framework);
    long requests = getRequests(results, testType, framework);
    return ((double) requests) / results.duration;
  }

  /**
   * Returns the total number of requests achieved by a framework in a test.
   */
  public static long getRequests(ParsedResults results,
                                 String testType,
                                 String framework) {
    Objects.requireNonNull(results);
    Objects.requireNonNull(testType);
    Objects.requireNonNull(framework);

    ImmutableList<SingleWrkExecution> executions =
        executionsForTestType(results, testType).get(framework);

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
                         .mapToLong(execution -> execution.totalRequests)
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
        if (executions.isEmpty())
          return 0;
        else
          return executions.get(executions.size() - 1).totalRequests;

      default:
        return 0;
    }
  }

  /**
   * Extracts the raw results data for the given test type, grouping by
   * framework (the keys of the returned multimap are framework names).
   */
  private static ImmutableListMultimap<String, SingleWrkExecution>
  executionsForTestType(ParsedResults results, String testType) {
    Objects.requireNonNull(results);
    Objects.requireNonNull(testType);
    switch (testType) {
      case "json":         return results.rawData.json;
      case "plaintext":    return results.rawData.plaintext;
      case "db":           return results.rawData.db;
      case "query":        return results.rawData.query;
      case "update":       return results.rawData.update;
      case "fortune":      return results.rawData.fortune;
      case "cached_query": return results.rawData.cachedQuery;
      default:             return ImmutableListMultimap.of();
    }
  }
}
