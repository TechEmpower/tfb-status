package tfb.status.view;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;

/**
 * A view of the performance over time of a given framework + test type
 * combination.
 */
@Immutable
public final class TimelinePageView {
  /**
   * The name of the framework that was tested, such as "gemini-mysql".
   */
  public final String framework;

  /**
   * The name of the test type that was executed, such as "plaintext".
   */
  public final String testType;

  /**
   * All the available [time, requests per second] data points representing the
   * performance of this framework + test type combination over time.
   */
  public final ImmutableList<DataPointView> dataPoints;

  /**
   * Information about all the test types for this framework, intended for
   * rendering navigation links between the test types.
   */
  public final ImmutableList<TestTypeOptionView> testTypeOptions;

  /**
   * All frameworks available for timeline view.
   */
  public final ImmutableList<FrameworkOptionView> frameworkOptions;

  public TimelinePageView(String framework,
                          String testType,
                          ImmutableList<DataPointView> dataPoints,
                          ImmutableList<TestTypeOptionView> testTypeOptions,
                          ImmutableList<FrameworkOptionView> frameworkOptions) {
    this.framework = Objects.requireNonNull(framework);
    this.testType = Objects.requireNonNull(testType);
    this.dataPoints = Objects.requireNonNull(dataPoints);
    this.testTypeOptions = Objects.requireNonNull(testTypeOptions);
    this.frameworkOptions = Objects.requireNonNull(frameworkOptions);
  }

  /**
   * The performance of a framework + test type combination during one test
   * execution.
   */
  @Immutable
  public static final class DataPointView {
    /**
     * The epoch millisecond timestamp of when the test was executed.
     */
    public final long time;

    /**
     * The requests per second achieved by the framework during this one test
     * execution.
     */
    public final double rps;

    public DataPointView(long time, double rps) {
      this.time = time;
      this.rps = rps;
    }
  }

  /**
   * Information about a test type in the context of a framework.
   */
  @Immutable
  public static final class TestTypeOptionView {
    /**
     * The name of the test type, such as "plaintext".
     */
    public final String testType;

    /**
     * {@code true} if the framework has any results to speak of for this test
     * type.
     */
    public final boolean isPresent;

    /**
     * {@code true} if the performance of the framework for this test type is
     * currently being viewed by the user.
     */
    public final boolean isSelected;

    public TestTypeOptionView(String testType,
                              boolean isPresent,
                              boolean isSelected) {
      this.testType = Objects.requireNonNull(testType);
      this.isPresent = isPresent;
      this.isSelected = isSelected;
    }
  }

  /**
   * A framework available for timeline view.
   */
  @Immutable
  public static final class FrameworkOptionView {
    /**
     * The name of the framework.
     */
    public final String framework;

    /**
     * {@code true} if the framework is currently being viewed by the user.
     */
    public final boolean isSelected;

    public FrameworkOptionView(String framework, boolean isSelected) {
      this.framework = Objects.requireNonNull(framework);
      this.isSelected = isSelected;
    }
  }
}
