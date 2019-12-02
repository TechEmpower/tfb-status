package tfb.status.service;

import static tfb.status.testlib.MoreAssertions.assertContains;
import static tfb.status.testlib.MoreAssertions.assertHtmlDocument;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.testlib.TestServicesInjector;
import tfb.status.view.Results;
import tfb.status.view.Results.RawData;
import tfb.status.view.Results.SingleWrkExecution;

/**
 * Tests for {@link DiffGenerator}.
 */
@ExtendWith(TestServicesInjector.class)
public final class DiffGeneratorTest {
  /**
   * Verifies that the {@link DiffGenerator#diff(Results, Results)} method
   * produces HTML output that reflects the input results.
   */
  @Test
  public void testDiff(DiffGenerator diffGenerator)  {
    String framework = "not_a_real_framework";
    String expectedRps = "789";

    var execution =
        new SingleWrkExecution(
            /* totalRequests= */ 789,
            /* latencyAvg= */ null,
            /* latencyMax= */ null,
            /* latencyStdev= */ null,
            /* status5xx= */ 0,
            /* write= */ 0,
            /* read= */ 0,
            /* connect= */ 0);

    var rawData =
        new RawData(
            /* json= */ ImmutableListMultimap.of(framework, execution),
            /* plaintext= */ null,
            /* db= */ null,
            /* query= */ null,
            /* update= */ null,
            /* fortune= */ null,
            /* cachedQuery= */ null);

    var results =
        new Results(
            /* uuid= */ null,
            /* name= */ null,
            /* environmentDescription= */ null,
            /* startTime= */ null,
            /* completionTime= */ null,
            /* duration= */ 1,
            /* frameworks= */ ImmutableList.of(framework),
            /* completed= */ ImmutableMap.of(),
            /* succeeded= */ ImmutableListMultimap.of(),
            /* failed= */ ImmutableListMultimap.of(),
            /* rawData= */ rawData,
            /* queryIntervals= */ ImmutableList.of(1),
            /* concurrencyLevels= */ ImmutableList.of(1),
            /* git= */ null);

    String html = diffGenerator.diff(results, results);

    assertHtmlDocument(html);
    assertContains(framework, html);
    assertContains(expectedRps, html);
  }
}
