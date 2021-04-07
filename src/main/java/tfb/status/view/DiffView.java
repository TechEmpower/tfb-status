package tfb.status.view;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;

/**
 * A view of a diff between two sets of benchmark results.
 *
 * @param summary An overall summary of the diff.
 * @param lines The individual line items of the diff.
 */
@Immutable
public record DiffView(DiffSummaryView summary,
                       ImmutableList<DiffLineView> lines) {

  public DiffView {
    Objects.requireNonNull(summary);
    Objects.requireNonNull(lines);
  }

  /**
   * A view of a diff summary, including information such as how many
   * [framework, test type] combinations were added between the old results and
   * the new results.
   *
   * @param totalAdded The number of [framework, test type] combinations that
   *        were present in the new results but not the old results.
   * @param totalRemoved The number of [framework, test type] combinations that
   *        were present in the old results but not the new results.
   * @param totalBothRounds The number of [framework, test type] combinations
   *        that were present in both the old results and the new results.
   * @param totalSame The number of [framework, test type] combinations that
   *        performed roughly as well in the new results as in the old results
   *        (within some {@linkplain #rpsChangeThreshold ()threshold}.
   * @param totalBetter The number of [framework, test type] combinations that
   *        performed markedly better in the new results as compared to the old
   *        results (outside of some {@linkplain #rpsChangeThreshold()
   *        threshold}.
   * @param totalWorse The number of [framework, test type] combinations that
   *        performed markedly worse in the new results as compared to the old
   *        results (outside of some {@linkplain #rpsChangeThreshold()
   *        threshold}.
   * @param rpsChangeThreshold The percentage by which the performance of a
   *        [framework, test type] combination must change between two sets of
   *        results in order to be considered truly different.  We expect
   *        run-to-run variance, and this number is used to flag performance
   *        differences that exceed the expected variance.
   */
  @Immutable
  public record DiffSummaryView(String totalAdded,
                                String totalRemoved,
                                String totalBothRounds,
                                String totalSame,
                                String totalBetter,
                                String totalWorse,
                                String rpsChangeThreshold) {

    public DiffSummaryView {
      Objects.requireNonNull(totalAdded);
      Objects.requireNonNull(totalRemoved);
      Objects.requireNonNull(totalBothRounds);
      Objects.requireNonNull(totalSame);
      Objects.requireNonNull(totalBetter);
      Objects.requireNonNull(totalWorse);
      Objects.requireNonNull(rpsChangeThreshold);
    }
  }

  /**
   * A view of the difference in performance for a single [framework, test type]
   * combination between two sets of results.
   *
   * @param addedOrRemoved A string indicating whether this line represents a
   *        new [framework, test type] combination, a removed one, or one that
   *        existed in both the old and new results.
   * @param framework The name of the framework, such as "gemini-mysql".
   * @param testType The name of the test type, such as "plaintext".
   * @param oldRps The old requests per second for this item.
   * @param newRps The new requests per second for this item.
   * @param rpsChange The change in requests per second for this item between
   *        the old results and the new results.
   */
  @Immutable
  public record DiffLineView(String addedOrRemoved,
                             String framework,
                             String testType,
                             String oldRps,
                             String newRps,
                             String rpsChange) {

    public DiffLineView {
      Objects.requireNonNull(addedOrRemoved);
      Objects.requireNonNull(framework);
      Objects.requireNonNull(testType);
      Objects.requireNonNull(oldRps);
      Objects.requireNonNull(newRps);
      Objects.requireNonNull(rpsChange);
    }
  }
}
