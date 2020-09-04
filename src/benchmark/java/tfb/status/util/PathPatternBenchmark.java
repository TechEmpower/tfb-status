package tfb.status.util;

import com.google.common.base.Joiner;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.profile.StackProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Benchmarks for {@link PathPattern}.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(1)
public class PathPatternBenchmark {
  private static final boolean ENABLE_GC_PROFILER = false;
  private static final boolean ENABLE_STACK_PROFILER = false;
  private static final int STACK_LINES = 5;

  public static void main(String[] args) throws Exception {
    var options = new OptionsBuilder();
    options.include(PathPatternBenchmark.class.getName());

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

  @Param({
      "abcdefghijklmnopqrstuvwxyz",
  })
  private String path;

  @Param({
      "{a}i{b}q{c}y{d}"
  })
  private String pattern;

  private PathPattern compiledPattern;

  @Setup
  public void setup() {
    compiledPattern = PathPattern.of(pattern);
    if (!compiledPattern.match(path).matches())
      throw new AssertionError();
  }

  @Benchmark
  public PathPattern of() {
    return PathPattern.of(pattern);
  }

  @Benchmark
  public PathPattern.MatchResult match() {
    return compiledPattern.match(path);
  }
}
