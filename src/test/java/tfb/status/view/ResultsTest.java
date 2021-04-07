package tfb.status.view;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static tfb.status.testlib.MoreAssertions.assertContains;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.service.FileStore;
import tfb.status.testlib.TestServicesInjector;

/**
 * Tests for {@link Results}.
 */
@ExtendWith(TestServicesInjector.class)
public final class ResultsTest {
  /**
   * Verifies that "cached_query" and "cached-query" are both considered valid
   * names for the {@linkplain Results.TestType#CACHED_QUERY cached query} test
   * type when parsing results.json files.
   */
  @Test
  public void testCachedQueryTestTypeAliases(FileStore fileStore,
                                             ObjectMapper objectMapper)
      throws IOException {

    Path jsonFile =
        fileStore.resultsDirectory()
                 .resolve("results.2019-12-11-13-21-02-404.json");

    String oldKey = "\"cached_query\"";
    String newKey = "\"cached-query\"";

    String oldJson = Files.readString(jsonFile, UTF_8);
    String newJson = oldJson.replace(oldKey, newKey);

    // Confirm that we are really replacing something.
    assertContains(oldKey, oldJson);
    assertEquals(-1, oldJson.indexOf(newKey));
    assertContains(newKey, newJson);
    assertEquals(-1, newJson.indexOf(oldKey));

    // Confirm that both names are deserialized in the same way.
    Results oldResults = objectMapper.readValue(oldJson, Results.class);
    Results newResults = objectMapper.readValue(newJson, Results.class);
    assertEquals(oldResults, newResults);

    // Confirm that we're not dealing with empty/missing data.
    assertNotNull(newResults.succeeded().cachedQuery());
    assertFalse(newResults.succeeded().cachedQuery().isEmpty());
    assertNotNull(newResults.rawData().cachedQuery());
    assertFalse(newResults.rawData().cachedQuery().isEmpty());
  }
}
