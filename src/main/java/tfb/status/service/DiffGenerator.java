package tfb.status.service;

import static java.util.Comparator.comparing;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import tfb.status.view.DiffView;
import tfb.status.view.DiffView.DiffLineView;
import tfb.status.view.DiffView.DiffSummaryView;
import tfb.status.view.Results;

/**
 * Generates diffs between pairs of benchmark results.
 */
@Singleton
public final class DiffGenerator {
  private final MustacheRenderer mustacheRenderer;

  @Inject
  public DiffGenerator(MustacheRenderer mustacheRenderer) {
    this.mustacheRenderer = Objects.requireNonNull(mustacheRenderer);
  }

  /**
   * Generates a diff in HTML format that compares two sets of benchmark
   * results.
   *
   * @param oldResults the old benchmark results
   * @param newResults the new benchmark results
   * @return an HTML document string
   */
  public String diff(Results oldResults, Results newResults) {
    Objects.requireNonNull(oldResults);
    Objects.requireNonNull(newResults);

    var distinctFrameworks = new HashSet<String>();
    distinctFrameworks.addAll(oldResults.frameworks);
    distinctFrameworks.addAll(newResults.frameworks);

    var lines = new ArrayList<DiffLine>();
    for (String framework : distinctFrameworks) {
      for (Results.TestType testType : Results.TestType.values()) {
        double oldRps = oldResults.rps(testType, framework);
        double newRps = newResults.rps(testType, framework);
        if (oldRps != 0d || newRps != 0d) {
          lines.add(
              new DiffLine(
                  /* framework= */ framework,
                  /* testType= */ testType,
                  /* oldRps= */ oldRps,
                  /* newRps= */ newRps));
        }
      }
    }

    lines.sort(comparing(line -> line.framework,
                         String.CASE_INSENSITIVE_ORDER));

    int totalAdded = 0;
    int totalRemoved = 0;
    int totalSame = 0;
    int totalBetter = 0;
    int totalWorse = 0;

    for (DiffLine line : lines) {
      if (line.oldRps == 0d)
        totalAdded++;
      else if (line.newRps == 0d)
        totalRemoved++;

      if (line.oldRps != 0d && line.newRps != 0d) {
        double rpsChangeNumber = (line.newRps - line.oldRps) / line.oldRps;
        if (Math.abs(rpsChangeNumber) < RPS_CHANGE_THRESHOLD)
          totalSame++;
        else if (rpsChangeNumber > 0d)
          totalBetter++;
        else
          totalWorse++;
      }
    }

    int totalBothRounds = totalSame + totalBetter + totalWorse;

    NumberFormat percentFormat = NumberFormat.getPercentInstance(Locale.ROOT);
    NumberFormat integerFormat = NumberFormat.getIntegerInstance(Locale.ROOT);

    var summaryView =
        new DiffSummaryView(
            /* totalAdded= */ integerFormat.format(totalAdded),
            /* totalRemoved= */ integerFormat.format(totalRemoved),
            /* totalBothRounds= */ integerFormat.format(totalBothRounds),
            /* totalSame= */ integerFormat.format(totalSame),
            /* totalBetter= */ integerFormat.format(totalBetter),
            /* totalWorse= */ integerFormat.format(totalWorse),
            /* rpsChangeThreshold= */ percentFormat.format(RPS_CHANGE_THRESHOLD));

    var lineViews = new ImmutableList.Builder<DiffLineView>();

    for (DiffLine line : lines) {
      String addedOrRemovedText;
      if (line.oldRps == 0d)
        addedOrRemovedText = "ADDED";
      else if (line.newRps == 0d)
        addedOrRemovedText = "REMOVED";
      else
        addedOrRemovedText = "";

      String rpsChangeText;
      if (line.oldRps == 0d || line.newRps == 0d) {
        rpsChangeText = "";
      } else {
        double rpsChangeNumber = (line.newRps - line.oldRps) / line.oldRps;
        rpsChangeText = percentFormat.format(rpsChangeNumber);
      }

      lineViews.add(
          new DiffLineView(
              /* addedOrRemoved= */ addedOrRemovedText,
              /* framework= */ line.framework,
              /* testType= */ line.testType.serialize(),
              /* oldRps= */ integerFormat.format(line.oldRps),
              /* newRps= */ integerFormat.format(line.newRps),
              /* rpsChange= */ rpsChangeText));
    }

    var outerView =
        new DiffView(
            /* summary= */ summaryView,
            /* lines= */ lineViews.build());

    return mustacheRenderer.render("diff.mustache", outerView);
  }

  @Immutable
  private static final class DiffLine {
    final String framework;
    final Results.TestType testType;
    final double oldRps;
    final double newRps;

    DiffLine(String framework,
             Results.TestType testType,
             double oldRps,
             double newRps) {

      this.framework = Objects.requireNonNull(framework);
      this.testType = Objects.requireNonNull(testType);
      this.oldRps = oldRps;
      this.newRps = newRps;
    }
  }

  // TODO: Make this threshold configurable.
  private static final double RPS_CHANGE_THRESHOLD = 0.25;
}
