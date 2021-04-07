package tfb.status.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.testlib.ResultsTester;
import tfb.status.testlib.TestServicesInjector;
import tfb.status.util.ZipFiles;
import tfb.status.view.HomePageView.ResultsView;
import tfb.status.view.Results;

/**
 * Tests for {@link HomeResultsReader}.
 */
@ExtendWith(TestServicesInjector.class)
public final class HomeResultsReaderTest {
  /**
   * Verifies that {@link HomeResultsReader#results()} returns the full list of
   * stored results.
   */
  @Test
  public void testResults(HomeResultsReader homeResultsReader)
      throws IOException {

    // Do not verify the size of the results list because we might have uploaded
    // new results in another test.
    ImmutableList<ResultsView> resultsList = homeResultsReader.results();
    ResultsView firstResult = resultsList.get(0);
    assertEquals("598923fe-6491-41bd-a2b6-047f70860aed", firstResult.uuid());
  }

  /**
   * Verifies that {@link HomeResultsReader#resultsByUuid(String)} returns the
   * expected content for a known-good UUID.
   */
  @Test
  public void testResultsByUuid_found(HomeResultsReader homeResultsReader,
                                      FileStore fileStore,
                                      Clock clock)
      throws IOException {

    ResultsView results =
        homeResultsReader.resultsByUuid("598923fe-6491-41bd-a2b6-047f70860aed");

    assertNotNull(results);

    assertEquals("598923fe-6491-41bd-a2b6-047f70860aed", results.uuid());
    assertEquals("Continuous Benchmarking Run 2019-12-11 21:15:18", results.name());
    assertEquals("Citrine", results.environmentDescription());

    assertEquals("results.2019-12-11-13-21-02-404.json", results.jsonFileName());
    assertEquals("results.2019-12-16-03-22-48-407.zip", results.zipFileName());

    assertEquals(659, results.totalFrameworks());
    assertEquals(659, results.completedFrameworks());
    assertEquals(2247, results.successfulTests());
    assertEquals(229, results.failedTests());
    assertEquals(613, results.frameworksWithCleanSetup());
    assertEquals(46, results.frameworksWithSetupProblems());

    assertEquals("2019-12-11 at 9:15 PM", results.startTime());
    assertEquals("2019-12-16 at 11:22 AM", results.completionTime());

    // TODO: Avoid using the last modified time of the file on disk, which may
    //       change for reasons completely unrelated to the run itself, and use
    //       something from the results.json file to give us a last modified
    //       time instead.
    assertNotNull(results.zipFileName());
    Path zipFile = fileStore.resultsDirectory().resolve(results.zipFileName());
    assertEquals(
        Files.getLastModifiedTime(zipFile)
             .toInstant()
             .atZone(clock.getZone())
             .toLocalDateTime()
             .format(HomeResultsReader.DISPLAYED_TIME_FORMATTER),
        results.lastUpdated());

    assertEquals("~110 hours", results.elapsedDuration());
    assertNull(results.estimatedRemainingDuration());

    assertEquals(
        "https://github.com/TechEmpower/FrameworkBenchmarks.git",
        results.repositoryUrl());

    assertEquals("master", results.branchName());

    assertEquals(
        "57c558b30dd57e2421b8cbaeedfa90c1a59f02fe",
        results.commitId());

    assertEquals(
        "https://github.com/TechEmpower/FrameworkBenchmarks",
        results.browseRepositoryUrl());

    assertEquals(
        "https://github.com/TechEmpower/FrameworkBenchmarks/tree/master",
        results.browseBranchUrl());

    assertEquals(
        "https://github.com/TechEmpower/FrameworkBenchmarks/tree/57c558b30dd57e2421b8cbaeedfa90c1a59f02fe",
        results.browseCommitUrl());

    assertEquals(
        "https://www.techempower.com/benchmarks/#section=test&runid=598923fe-6491-41bd-a2b6-047f70860aed",
        results.visualizeResultsUrl());

    assertEquals(172, results.failures().size());

    assertEquals(
        46,
        results.failures().stream()
                          .filter(failure -> failure.hadSetupProblems())
                          .count());

    assertEquals(
        126,
        results.failures().stream()
                          .filter(failure -> !failure.failedTestTypes().isEmpty())
                          .count());
  }

  /**
   * Verifies that {@link HomeResultsReader#resultsByUuid(String)} returns
   * {@code null} for an unknown UUID.
   */
  @Test
  public void testResultsByUuid_notFound(HomeResultsReader homeResultsReader)
      throws IOException {

    assertNull(homeResultsReader.resultsByUuid("fb62bcf3-bba8-4a29-8c88-a8bcefff200e"));
  }

  /**
   * Verifies that results may be visualized when we only have a results.json
   * file and that file contains embedded test metadata.
   */
  @Test
  public void testVisualize_metadataInResultsJson(HomeResultsReader homeResultsReader,
                                                  ResultsTester resultsTester)
      throws IOException {

    Results results = resultsTester.newResults();
    assertNotNull(results.uuid());
    assertNotNull(results.testMetadata());

    resultsTester.saveJsonToResultsDirectory(results);

    ResultsView view = homeResultsReader.resultsByUuid(results.uuid());
    assertNotNull(view);
    assertNotNull(view.visualizeResultsUrl());
  }

  /**
   * Verifies that results cannot be visualized when we only have a results.json
   * file and that file does not contain embedded test metadata.
   */
  @Test
  public void testVisualize_noMetadataInResultsJson(HomeResultsReader homeResultsReader,
                                                    ResultsTester resultsTester)
      throws IOException {

    Results results = newResultsWithoutMetadata(resultsTester);
    assertNotNull(results.uuid());
    assertNull(results.testMetadata());

    resultsTester.saveJsonToResultsDirectory(results);

    ResultsView view = homeResultsReader.resultsByUuid(results.uuid());
    assertNotNull(view);
    assertNull(view.visualizeResultsUrl());
  }

  /**
   * Verifies that results may be visualized when we only have a results.zip
   * file, and that zip file does not contain a test_metadata.json file, but the
   * results.json within the zip does contain embedded test metadata.
   */
  @Test
  public void testVisualize_metadataInResultsJsonInResultsZip(HomeResultsReader homeResultsReader,
                                                              ResultsTester resultsTester)
      throws IOException {

    Results results = resultsTester.newResults();
    assertNotNull(results.uuid());
    assertNotNull(results.testMetadata());

    Path zipFile = resultsTester.saveZipToResultsDirectory(results);

    ZipFiles.findZipEntry(
        /* zipFile= */
        zipFile,

        /* entryPath= */
        "test_metadata.json",

        /* ifPresent= */
        metadataFile -> Files.delete(metadataFile),

        /* ifAbsent= */
        () -> fail("test_metadata.json file must be present"));

    ResultsView view = homeResultsReader.resultsByUuid(results.uuid());
    assertNotNull(view);
    assertNotNull(view.visualizeResultsUrl());
  }

  /**
   * Verifies that results may be visualized when we only have a results.zip
   * file, and the results.json within the zip not does contain embedded test
   * metadata, but that zip file does contain a test_metadata.json file.
   */
  @Test
  public void testVisualize_metadataFileInResultsZip(HomeResultsReader homeResultsReader,
                                                     ResultsTester resultsTester)
      throws IOException {

    Results results = newResultsWithoutMetadata(resultsTester);
    assertNotNull(results.uuid());
    assertNull(results.testMetadata());

    Path zipFile = resultsTester.saveZipToResultsDirectory(results);

    ZipFiles.findZipEntry(
        /* zipFile= */
        zipFile,

        /* entryPath= */
        "test_metadata.json",

        /* ifPresent= */
        metadataFile -> {}, // Do nothing.

        /* ifAbsent= */
        () -> fail("test_metadata.json file must be present"));

    ResultsView view = homeResultsReader.resultsByUuid(results.uuid());
    assertNotNull(view);
    assertNotNull(view.visualizeResultsUrl());
  }

  /**
   * Verifies that results cannot be visualized when we only have a results.zip
   * file, and that zip file does not contain a test_metadata.json file, and
   * the results.json within the zip does not contain embedded test metadata.
   */
  @Test
  public void testVisualize_noMetadataInResultsZip(HomeResultsReader homeResultsReader,
                                                   ResultsTester resultsTester)
      throws IOException {

    Results results = newResultsWithoutMetadata(resultsTester);
    assertNotNull(results.uuid());
    assertNull(results.testMetadata());

    Path zipFile = resultsTester.saveZipToResultsDirectory(results);

    ZipFiles.findZipEntry(
        /* zipFile= */
        zipFile,

        /* entryPath= */
        "test_metadata.json",

        /* ifPresent= */
        metadataFile -> Files.delete(metadataFile),

        /* ifAbsent= */
        () -> fail("test_metadata.json file must be present"));

    ResultsView view = homeResultsReader.resultsByUuid(results.uuid());
    assertNotNull(view);
    assertNull(view.visualizeResultsUrl());
  }

  private static Results newResultsWithoutMetadata(ResultsTester resultsTester)
      throws IOException {

    Results template = resultsTester.newResults();

    return new Results(
        /* uuid= */ template.uuid(),
        /* name= */ template.name(),
        /* environmentDescription= */ template.environmentDescription(),
        /* startTime= */ template.startTime(),
        /* completionTime= */ template.completionTime(),
        /* duration= */ template.duration(),
        /* frameworks= */ template.frameworks(),
        /* completed= */ template.completed(),
        /* succeeded= */ template.succeeded(),
        /* failed= */ template.failed(),
        /* rawData= */ template.rawData(),
        /* queryIntervals= */ template.queryIntervals(),
        /* concurrencyLevels= */ template.concurrencyLevels(),
        /* git= */ template.git(),
        /* testMetadata= */ null);
  }
}
