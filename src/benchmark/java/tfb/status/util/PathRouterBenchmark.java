package tfb.status.util;

import static tfb.status.benchmarklib.Benchmarks.runBenchmarks;

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

  public static void main(String[] args) throws Exception {
    runBenchmarks(PathRouterBenchmark.class);
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
            "/a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p/q/r/s/t/u/v/w/x/y/z/{rest:.*}",
            "/a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p/q/r/s/t/u/v/w/x/y/{rest:.*}",
            "/a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p/q/r/s/t/u/v/w/x/{rest:.*}",
            "/a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p/q/r/s/t/u/v/w/{rest:.*}",
            "/a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p/q/r/s/t/u/v/{rest:.*}",
            "/a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p/q/r/s/t/u/{rest:.*}",
            "/a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p/q/r/s/t/{rest:.*}",
            "/a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p/q/r/s/{rest:.*}",
            "/a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p/q/r/{rest:.*}",
            "/a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p/q/{rest:.*}",
            "/a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p/{rest:.*}",
            "/a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/{rest:.*}",
            "/a/b/c/d/e/f/g/h/i/j/k/l/m/n/{rest:.*}",
            "/a/b/c/d/e/f/g/h/i/j/k/l/m/{rest:.*}",
            "/a/b/c/d/e/f/g/h/i/j/k/l/{rest:.*}",
            "/a/b/c/d/e/f/g/h/i/j/k/{rest:.*}",
            "/a/b/c/d/e/f/g/h/i/j/{rest:.*}",
            "/a/b/c/d/e/f/g/h/i/{rest:.*}",
            "/a/b/c/d/e/f/g/h/{rest:.*}",
            "/a/b/c/d/e/f/g/{rest:.*}",
            "/a/b/c/d/e/f/{rest:.*}",
            "/a/b/c/d/e/{rest:.*}",
            "/a/b/c/d/{rest:.*}",
            "/a/b/c/{rest:.*}",
            "/a/b/{rest:.*}",
            "/a/{rest:.*}",
            "/{rest:.*}");

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
