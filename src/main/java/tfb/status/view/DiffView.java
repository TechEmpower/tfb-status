package tfb.status.view;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;

/**
 * A view of a diff between two sets of benchmark results.
 */
@Immutable
public final class DiffView {
  /**
   * An overall summary of the diff.
   */
  public final DiffSummaryView summary;

  /**
   * The individual line items of the diff.
   */
  public final ImmutableList<DiffLineView> lines;

  public DiffView(DiffSummaryView summary, ImmutableList<DiffLineView> lines) {
    this.summary = Objects.requireNonNull(summary);
    this.lines = Objects.requireNonNull(lines);
  }

  /**
   * A view of a diff summary, including information such as how many
   * [framework, test type] combinations were added between the old results and
   * the new results.
   */
  @Immutable
  public static final class DiffSummaryView {
    /**
     * The number of [framework, test type] combinations that were present in
     * the new results but not the old results.
     */
    public final String totalAdded;

    /**
     * The number of [framework, test type] combinations that were present in
     * the old results but not the new results.
     */
    public final String totalRemoved;

    /**
     * The number of [framework, test type] combinations that were present in
     * both the old results and the new results.
     */
    public final String totalBothRounds;

    /**
     * The number of [framework, test type] combinations that performed roughly
     * as well in the new results as in the old results (within some {@linkplain
     * #rpsChangeThreshold threshold}.
     */
    public final String totalSame;

    /**
     * The number of [framework, test type] combinations that performed markedly
     * better in the new results as compared to the old results (outside of some
     * {@linkplain #rpsChangeThreshold threshold}.
     */
    public final String totalBetter;

    /**
     * The number of [framework, test type] combinations that performed markedly
     * worse in the new results as compared to the old results (outside of some
     * {@linkplain #rpsChangeThreshold threshold}.
     */
    public final String totalWorse;

    /**
     * The percentage by which the performance of a [framework, test type]
     * combination must change between two sets of results in order to be
     * considered truly different.  We expect run-to-run variance, and this
     * number is used to flag performance differences that exceed the expected
     * variance.
     */
    public final String rpsChangeThreshold;

    public DiffSummaryView(String totalAdded,
                           String totalRemoved,
                           String totalBothRounds,
                           String totalSame,
                           String totalBetter,
                           String totalWorse,
                           String rpsChangeThreshold) {
      this.totalAdded = Objects.requireNonNull(totalAdded);
      this.totalRemoved = Objects.requireNonNull(totalRemoved);
      this.totalBothRounds = Objects.requireNonNull(totalBothRounds);
      this.totalSame = Objects.requireNonNull(totalSame);
      this.totalBetter = Objects.requireNonNull(totalBetter);
      this.totalWorse = Objects.requireNonNull(totalWorse);
      this.rpsChangeThreshold = Objects.requireNonNull(rpsChangeThreshold);
    }
  }

  /**
   * A view of the difference in performance for a single [framework, test type]
   * combination between two sets of results.
   */
  @Immutable
  public static final class DiffLineView {
    /**
     * A string indicating whether this line represents a new [framework, test
     * type] combination, a removed one, or one that existed in both the old and
     * new results.
     */
    public final String addedOrRemoved;

    /**
     * The name of the framework, such as "gemini-mysql".
     */
    public final String framework;

    /**
     * The name of the test type, such as "plaintext".
     */
    public final String testType;

    /**
     * The old requests per second for this item.
     */
    public final String oldRps;

    /**
     * The new requests per second for this item.
     */
    public final String newRps;

    /**
     * The change in requests per second for this item between the old results
     * and the new results.
     */
    public final String rpsChange;

    public DiffLineView(String addedOrRemoved,
                        String framework,
                        String testType,
                        String oldRps,
                        String newRps,
                        String rpsChange) {
      this.addedOrRemoved = Objects.requireNonNull(addedOrRemoved);
      this.framework = Objects.requireNonNull(framework);
      this.testType = Objects.requireNonNull(testType);
      this.oldRps = Objects.requireNonNull(oldRps);
      this.newRps = Objects.requireNonNull(newRps);
      this.rpsChange = Objects.requireNonNull(rpsChange);
    }
  }
}
