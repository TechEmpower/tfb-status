package tfb.status.view;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;

/**
 * A view of the performance over time of a given [framework, test type]
 * combination.
 *
 * @param framework The name of the framework that was tested, such as
 *        "gemini-mysql".
 * @param testType The name of the test type that was executed, such as
 *        "plaintext".
 * @param dataPoints All the available [time, requests per second] data points
 *        representing the performance of this [framework, test type]
 *        combination over time.
 * @param testTypeOptions Information about all the test types for this
 *        framework, intended for rendering navigation links between the test
 *        types.
 * @param frameworkOptions All frameworks available for timeline view.
 */
@Immutable
public record TimelinePageView(String framework,
                               String testType,
                               ImmutableList<DataPointView> dataPoints,
                               ImmutableList<TestTypeOptionView> testTypeOptions,
                               ImmutableList<FrameworkOptionView> frameworkOptions) {

  public TimelinePageView {
    Objects.requireNonNull(framework);
    Objects.requireNonNull(testType);
    Objects.requireNonNull(dataPoints);
    Objects.requireNonNull(testTypeOptions);
    Objects.requireNonNull(frameworkOptions);
  }

  /**
   * The performance of a [framework, test type] combination during one test
   * execution.
   *
   * @param time The epoch millisecond timestamp of when the test was executed.
   * @param rps The requests per second achieved by the framework during this
   *        one test execution.
   */
  @Immutable
  public record DataPointView(long time, double rps) {}

  /**
   * Information about a test type in the context of a framework.
   *
   * @param testType The name of the test type, such as "plaintext".
   * @param isPresent {@code true} if the framework has any results to speak of
   *        for this test type.
   * @param isSelected {@code true} if the performance of the framework for this
   *        test type is currently being viewed by the user.
   */
  @Immutable
  public record TestTypeOptionView(String testType,
                                   boolean isPresent,
                                   boolean isSelected) {

    public TestTypeOptionView {
      Objects.requireNonNull(testType);
    }
  }

  /**
   * A framework available for timeline view.
   *
   * @param framework The name of the framework.
   * @param isSelected {@code true} if the framework is currently being viewed
   *        by the user.
   */
  @Immutable
  public record FrameworkOptionView(String framework, boolean isSelected) {

    public FrameworkOptionView {
      Objects.requireNonNull(framework);
    }
  }
}
