package tfb.status.util;

import static tfb.status.benchmarklib.Benchmarks.runBenchmarks;

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

  public static void main(String[] args) throws Exception {
    runBenchmarks(PathPatternBenchmark.class);
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
