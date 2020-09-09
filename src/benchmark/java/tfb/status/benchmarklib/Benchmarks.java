package tfb.status.benchmarklib;

import com.google.common.base.Joiner;
import java.util.HashMap;
import java.util.Objects;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.profile.StackProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Utility methods for running JMH benchmarks.
 */
public final class Benchmarks {
  private Benchmarks() {
    throw new AssertionError("This class cannot be instantiated");
  }

  /**
   * Runs the JMH benchmarks defined by the specified class.
   */
  public static void runBenchmarks(Class<?> clazz) throws RunnerException {
    Objects.requireNonNull(clazz);

    var options = new OptionsBuilder();
    options.include(clazz.getName());

    if (ENABLE_GC_PROFILER)
      options.addProfiler(GCProfiler.class);

    if (ENABLE_STACK_PROFILER) {
      var stackOpts = new HashMap<String, Object>();
      stackOpts.put("lines", STACK_LINES);

      options.addProfiler(
          StackProfiler.class,
          Joiner.on(";").withKeyValueSeparator("=").join(stackOpts));
    }

    new Runner(options).run();
  }

  private static final boolean ENABLE_GC_PROFILER = false;
  private static final boolean ENABLE_STACK_PROFILER = false;
  private static final int STACK_LINES = 5;
}
