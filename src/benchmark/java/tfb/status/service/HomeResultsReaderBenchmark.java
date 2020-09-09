package tfb.status.service;

import static tfb.status.benchmarklib.Benchmarks.runBenchmarks;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glassfish.hk2.api.ServiceLocator;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import tfb.status.service.HomeResultsReader.FileSummary;
import tfb.status.testlib.TestServices;
import tfb.status.view.HomePageView.ResultsView;

/**
 * Benchmarks for {@link HomeResultsReader}.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(1)
public class HomeResultsReaderBenchmark {

  public static void main(String[] args) throws Exception {
    runBenchmarks(HomeResultsReaderBenchmark.class);
  }

  private ServiceLocator locator;
  private HomeResultsReader homeResultsReader;
  private Path jsonFile;
  private Path zipFile;

  @Setup
  public void setup() throws IOException {
    locator = TestServices.createServiceLocator();
    homeResultsReader = locator.getService(HomeResultsReader.class);

    FileStore fileStore = locator.getService(FileStore.class);
    Path resultsDirectory = fileStore.resultsDirectory();
    jsonFile = resultsDirectory.resolve("results.2019-12-11-13-21-02-404.json");
    zipFile = resultsDirectory.resolve("results.2019-12-16-03-22-48-407.zip");

    if (homeResultsReader.results().isEmpty())
      throw new AssertionError();

    // Assert that no exception is thrown.
    FileSummary ignored = homeResultsReader.readJsonFile(jsonFile);

    if (homeResultsReader.readZipFile(zipFile) == null)
      throw new AssertionError();
  }

  @TearDown
  public void tearDown() {
    locator.shutdown();
  }

  @Benchmark
  public ImmutableList<ResultsView> results() throws IOException {
    return homeResultsReader.results();
  }

  @Benchmark
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  public FileSummary readJsonFile() throws IOException {
    return homeResultsReader.readJsonFile(jsonFile);
  }

  @Benchmark
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  public @Nullable FileSummary readZipFile() throws IOException {
    return homeResultsReader.readZipFile(zipFile);
  }
}
