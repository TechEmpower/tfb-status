package tfb.status.service;

import static java.util.Comparator.comparing;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import tfb.status.view.DiffView;
import tfb.status.view.DiffView.DiffLineView;
import tfb.status.view.DiffView.DiffSummaryView;
import tfb.status.view.ParsedResults;

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
  public String diff(ParsedResults oldResults, ParsedResults newResults) {
    return render(lines(oldResults, newResults));
  }

  @Immutable
  private static final class DiffLine {
    final String framework;
    final String testType;
    final double oldRps;
    final double newRps;

    DiffLine(String framework, String testType, double oldRps, double newRps) {
      this.framework = Objects.requireNonNull(framework);
      this.testType = Objects.requireNonNull(testType);
      this.oldRps = oldRps;
      this.newRps = newRps;
    }
  }

  /**
   * Generates a logical diff between the two sets of results.
   */
  private ImmutableList<DiffLine> lines(ParsedResults oldResults,
                                        ParsedResults newResults) {
    Objects.requireNonNull(oldResults);
    Objects.requireNonNull(newResults);

    Set<String> distinctFrameworks = new HashSet<>();
    distinctFrameworks.addAll(oldResults.frameworks);
    distinctFrameworks.addAll(newResults.frameworks);

    List<DiffLine> lines = new ArrayList<>();
    for (String framework : distinctFrameworks) {
      for (String testType : ParsedResults.TEST_TYPES) {
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

    return ImmutableList.sortedCopyOf(
        comparing(line -> line.framework,
                  String.CASE_INSENSITIVE_ORDER),
        lines);
  }

  /**
   * Accepts a logical diff between two sets of results, returns the rendering
   * of that diff as HTML.
   */
  private String render(Iterable<DiffLine> lines) {
    Objects.requireNonNull(lines);
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

    DiffSummaryView summaryView =
        new DiffSummaryView(
            /* totalAdded= */ integerFormat.format(totalAdded),
            /* totalRemoved= */ integerFormat.format(totalRemoved),
            /* totalBothRounds= */ integerFormat.format(totalBothRounds),
            /* totalSame= */ integerFormat.format(totalSame),
            /* totalBetter= */ integerFormat.format(totalBetter),
            /* totalWorse= */ integerFormat.format(totalWorse),
            /* rpsChangeThreshold= */ percentFormat.format(RPS_CHANGE_THRESHOLD));

    ImmutableList.Builder<DiffLineView> lineViews = ImmutableList.builder();

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
              /* testType= */ line.testType,
              /* oldRps= */ integerFormat.format(line.oldRps),
              /* newRps= */ integerFormat.format(line.newRps),
              /* rpsChange= */ rpsChangeText));
    }

    DiffView outerView =
        new DiffView(
            /* summary= */ summaryView,
            /* lines= */ lineViews.build());

    return mustacheRenderer.render("diff.mustache", outerView);
  }

  // TODO: Make this threshold configurable.
  private static final double RPS_CHANGE_THRESHOLD = 0.25;
}
