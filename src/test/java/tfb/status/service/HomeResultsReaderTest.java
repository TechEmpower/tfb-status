package tfb.status.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.testlib.TestServicesInjector;
import tfb.status.view.HomePageView.ResultsView;

/**
 * Tests for {@link HomeResultsReader}.
 */
@ExtendWith(TestServicesInjector.class)
public final class HomeResultsReaderTest {
  /**
   * Verifies that {@link HomeResultsReader#results()} returns the full list of
   * stored results.
   */
  @Test
  public void testResults(HomeResultsReader homeResultsReader)
      throws IOException {

    // Do not verify the size of the results list because we might have uploaded
    // new results in another test.
    ImmutableList<ResultsView> resultsList = homeResultsReader.results();
    ResultsView firstResult = resultsList.get(0);
    assertEquals("598923fe-6491-41bd-a2b6-047f70860aed", firstResult.uuid);
  }

  /**
   * Verifies that {@link HomeResultsReader#resultsByUuid(String)} returns the
   * expected content for a known-good UUID.
   */
  @Test
  public void testResultsByUuid_found(HomeResultsReader homeResultsReader)
      throws IOException {

    ResultsView results =
        homeResultsReader.resultsByUuid("598923fe-6491-41bd-a2b6-047f70860aed");

    assertNotNull(results);
    assertEquals("598923fe-6491-41bd-a2b6-047f70860aed", results.uuid);

    assertEquals("57c558b30dd57e2421b8cbaeedfa90c1a59f02fe", results.commitId);

    assertEquals("results.2019-12-11-13-21-02-404.json", results.jsonFileName);
    assertEquals(659, results.totalFrameworks);
    assertEquals(2247, results.successfulTests);
    assertEquals(229, results.failedTests);
    assertEquals(613, results.frameworksWithCleanSetup);
    assertEquals(46, results.frameworksWithSetupProblems);

    assertEquals("results.2019-12-16-03-22-48-407.zip", results.zipFileName);
    assertEquals(172, results.failures.size());

    assertEquals(
        46,
        results.failures.stream()
                        .filter(failure -> failure.hadSetupProblems)
                        .count());

    assertEquals(
        126,
        results.failures.stream()
                        .filter(failure -> !failure.failedTestTypes.isEmpty())
                        .count());
  }

  /**
   * Verifies that {@link HomeResultsReader#resultsByUuid(String)} returns
   * {@code null} for an unknown UUID.
   */
  @Test
  public void testResultsByUuid_notFound(HomeResultsReader homeResultsReader)
      throws IOException {

    assertNull(homeResultsReader.resultsByUuid("fb62bcf3-bba8-4a29-8c88-a8bcefff200e"));
  }
}
