package tfb.status.testlib;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Singleton;
import tfb.status.service.FileStore;
import tfb.status.util.ZipFiles;
import tfb.status.view.Results;

/**
 * Provides an API for generating fake results and saving those results to disk
 * during tests.
 */
@Singleton
public final class ResultsTester {
  private final FileStore fileStore;
  private final FileSystem fileSystem;
  private final ObjectMapper objectMapper;

  @Inject
  public ResultsTester(FileStore fileStore,
                       FileSystem fileSystem,
                       ObjectMapper objectMapper) {

    this.fileStore = Objects.requireNonNull(fileStore);
    this.fileSystem = Objects.requireNonNull(fileSystem);
    this.objectMapper = Objects.requireNonNull(objectMapper);
  }

  /**
   * Generates new results with a unique and non-null {@link Results#uuid},
   * {@link Results#name}, and {@link Results#environmentDescription}.
   */
  public Results newResults() throws IOException {
    Path oldJsonFile =
        fileStore.resultsDirectory()
                 .resolve("results.2019-12-11-13-21-02-404.json");

    Results oldResults;
    try (InputStream inputStream = Files.newInputStream(oldJsonFile)) {
      oldResults = objectMapper.readValue(inputStream, Results.class);
    }

    String newUuid = UUID.randomUUID().toString();
    String newName = "test_run_" + newUuid;
    String newEnvironment = "test_environment_" + newUuid;

    return new Results(
        /* uuid= */ newUuid,
        /* name= */ newName,
        /* environmentDescription= */ newEnvironment,
        /* startTime= */ oldResults.startTime,
        /* completionTime= */ oldResults.completionTime,
        /* duration= */ oldResults.duration,
        /* frameworks= */ oldResults.frameworks,
        /* completed= */ oldResults.completed,
        /* succeeded= */ oldResults.succeeded,
        /* failed= */ oldResults.failed,
        /* rawData= */ oldResults.rawData,
        /* queryIntervals= */ oldResults.queryIntervals,
        /* concurrencyLevels= */ oldResults.concurrencyLevels,
        /* git= */ oldResults.git);
  }

  /**
   * Saves the results.json file for these results to the {@link
   * FileStore#resultsDirectory()}.
   */
  public void saveJsonToResultsDirectory(Results results) throws IOException {
    Objects.requireNonNull(results);

    String fileName =
        "fake_results_"
            + ((results.uuid == null) ? "" : results.uuid)
            + ".json";

    Path file = fileStore.resultsDirectory().resolve(fileName);
    saveJsonToFile(results, file);
  }

  /**
   * Saves the results.json file for these results to the specified file.
   */
  public void saveJsonToFile(Results results, Path file) throws IOException {
    Objects.requireNonNull(results);
    Objects.requireNonNull(file);

    try (BufferedWriter writer = Files.newBufferedWriter(file)) {
      objectMapper.writeValue(writer, results);
    }
  }

  /**
   * Saves the results.zip file for these results to the {@link
   * FileStore#resultsDirectory()}.
   */
  public void saveZipToResultsDirectory(Results results) throws IOException {
    Objects.requireNonNull(results);

    String fileName =
        "fake_results_"
            + ((results.uuid == null) ? "" : results.uuid)
            + ".zip";

    Path file = fileStore.resultsDirectory().resolve(fileName);
    saveZipToFile(results, file);
  }

  /**
   * Saves the results.zip file for these results to the specified file.
   */
  public void saveZipToFile(Results results, Path file) throws IOException {
    Objects.requireNonNull(results);
    Objects.requireNonNull(file);

    Path oldZipFile =
        fileStore.resultsDirectory()
                 .resolve("results.2019-12-16-03-22-48-407.zip");

    Path tempZipFile =
        fileSystem.getPath(
            "temp_results_" + UUID.randomUUID().toString() + ".zip");

    Files.copy(oldZipFile, tempZipFile);

    ZipFiles.findZipEntry(
        /* zipFile= */
        tempZipFile,

        /* entryPath= */
        "results.json",

        /* ifPresent= */
        (Path zipEntry) -> {
          try (BufferedWriter writer = Files.newBufferedWriter(zipEntry)) {
            objectMapper.writeValue(writer, results);
          }
        },

        /* ifAbsent= */
        () -> fail("The results.zip file should include a results.json"));

    Files.move(tempZipFile, file, REPLACE_EXISTING);
  }
}
