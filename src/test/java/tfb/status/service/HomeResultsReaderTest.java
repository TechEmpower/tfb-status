package tfb.status.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tfb.status.bootstrap.TestServices;
import tfb.status.view.HomePageView.ResultsView;

/**
 * Tests for {@link HomeResultsReader}.
 */
public final class HomeResultsReaderTest {
  private static TestServices services;
  private static HomeResultsReader homeResultsReader;

  @BeforeAll
  public static void beforeAll() {
    services = new TestServices();
    homeResultsReader = services.serviceLocator().getService(HomeResultsReader.class);
  }

  @AfterAll
  public static void afterAll() {
    services.shutdown();
  }

  /**
   * Verifies that {@link HomeResultsReader#results()} returns the full list of
   * stored results.
   */
  @Test
  public void testResults() throws IOException {
    ImmutableList<ResultsView> resultsList = homeResultsReader.results();
    assertEquals(1, resultsList.size());
    ResultsView onlyResult = resultsList.get(0);
    assertEquals("03da6340-d56c-4584-9ef2-702106203809", onlyResult.uuid);
  }

  /**
   * Verifies that {@link HomeResultsReader#resultsByUuid(String)} returns the
   * expected content for a known-good UUID.
   */
  @Test
  public void testResultsByUuid_found() throws IOException {
    ResultsView results =
        homeResultsReader.resultsByUuid("03da6340-d56c-4584-9ef2-702106203809");

    assertNotNull(results);
    assertEquals("03da6340-d56c-4584-9ef2-702106203809", results.uuid);

    assertNotNull(results.git);
    assertEquals("ef202bb6ef535086ccf94d0a4064548fe41b4ca8", results.git.commitId);

    assertNotNull(results.json);
    assertEquals("results.2017-12-26-05-07-14-321.json", results.json.fileName);
    assertEquals(459, results.json.totalFrameworks);
    assertEquals(1652, results.json.successfulTests);
    assertEquals(117, results.json.failedTests);
    assertEquals(373, results.json.frameworksWithCleanSetup);
    assertEquals(45, results.json.frameworksWithSetupProblems);

    assertNotNull(results.zip);
    assertEquals("results.2017-12-29-23-04-02-541.zip", results.zip.fileName);
    assertEquals(76, results.zip.failures.size());

    assertEquals(
        45,
        results.zip.failures.stream()
                            .filter(failure -> failure.hadSetupProblems)
                            .count());

    assertEquals(
        37,
        results.zip.failures.stream()
                            .filter(failure -> !failure.failedTestTypes.isEmpty())
                            .count());
  }

  /**
   * Verifies that {@link HomeResultsReader#resultsByUuid(String)} returns
   * {@code null} for an unknown UUID.
   */
  @Test
  public void testResultsByUuid_notFound() throws IOException {
    assertNull(homeResultsReader.resultsByUuid("fb62bcf3-bba8-4a29-8c88-a8bcefff200e"));
  }
}
