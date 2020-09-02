package tfb.status.util;

import com.google.common.base.Joiner;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.nullness.qual.Nullable;
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
 * Benchmarks for {@link PathRouter}.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(1)
public class PathRouterBenchmark {
  private static final boolean ENABLE_GC_PROFILER = false;
  private static final boolean ENABLE_STACK_PROFILER = false;
  private static final int STACK_LINES = 5;

  public static void main(String[] args) throws Exception {
    var options = new OptionsBuilder();
    options.include(PathRouterBenchmark.class.getName());

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
      "/a/b/c",
      "/a/b/c/d/e/f/g/h/i/j/k/l/m/n",
      "/a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p/q/r/s/t/u/v/w/x/y/z/1/2/3",
      "/",
      "/about",
      "/assets/js/home.js",
      "/health",
      "/last-seen-commit",
      "/raw/file.zip",
      "/results/abc123.json",
      "/results/abc123",
      "/robots.txt",
      "/share",
      "/share/download/abc123.json",
      "/share/upload",
      "/timeline/gemini/json",
      "/unzip/zipfile.zip",
      "/unzip/zipfile.zip/path/to/entry",
      "/updates",
      "/upload",
  })
  private String path;

  private PathRouter<Integer> router;

  @Setup
  public void setup() {
    List<String> patterns =
        List.of(
            "/",
            "/about",
            "/assets/{assetPath:.+}",
            "/health",
            "/last-seen-commit",
            "/raw/{resultsFileName}",
            "/results/{uuid:[\\w-]+}.json",
            "/results/{uuid:[\\w-]+}",
            "/robots.txt",
            "/share",
            "/share/download/{shareId:[\\w-]+}.json",
            "/share/upload",
            "/timeline/{framework}/{testType}",
            "/unzip/{zipFile}",
            "/unzip/{zipFile}/{entrySubPath:.+}",
            "/updates",
            "/upload",
            "/a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p/q/r/s/t/u/v/w/x/y/z/**",
            "/a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p/q/r/s/t/u/v/w/x/y/**",
            "/a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p/q/r/s/t/u/v/w/x/**",
            "/a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p/q/r/s/t/u/v/w/**",
            "/a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p/q/r/s/t/u/v/**",
            "/a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p/q/r/s/t/u/**",
            "/a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p/q/r/s/t/**",
            "/a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p/q/r/s/**",
            "/a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p/q/r/**",
            "/a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p/q/**",
            "/a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p/**",
            "/a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/**",
            "/a/b/c/d/e/f/g/h/i/j/k/l/m/n/**",
            "/a/b/c/d/e/f/g/h/i/j/k/l/m/**",
            "/a/b/c/d/e/f/g/h/i/j/k/l/**",
            "/a/b/c/d/e/f/g/h/i/j/k/**",
            "/a/b/c/d/e/f/g/h/i/j/**",
            "/a/b/c/d/e/f/g/h/i/**",
            "/a/b/c/d/e/f/g/h/**",
            "/a/b/c/d/e/f/g/**",
            "/a/b/c/d/e/f/**",
            "/a/b/c/d/e/**",
            "/a/b/c/d/**",
            "/a/b/c/**",
            "/a/b/**",
            "/a/**",
            "/**");

    PathRouter.Builder<Integer> builder = PathRouter.builder();

    for (int i = 0; i < patterns.size(); i++)
      builder.add(patterns.get(i), i);

    router = builder.build();
  }

  @Benchmark
  public PathRouter.@Nullable MatchingEndpoint<Integer> find() {
    return router.find(path);
  }
}
