package tfb.status.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link OtherFiles}.
 */
public final class OtherFilesTest {
  private static Path directory;
  private static Path file;
  private static Path subdirectory;
  private static Path subdirectoryFile;

  @BeforeAll
  public static void beforeAll() throws Exception {
    directory = Files.createTempDirectory("OtherFilesTest");
    file = Files.createTempFile(directory, "file", ".txt");
    subdirectory = Files.createTempDirectory(directory, "subdirectory");
    subdirectoryFile = Files.createTempFile(subdirectory, "subdirectoryFile", ".txt");
  }

  @AfterAll
  public static void afterAll() throws Exception {
    Exception thrown = null;
    for (Path file : List.of(subdirectoryFile, subdirectory, file, directory)) {
      if (file != null) {
        try {
          Files.deleteIfExists(file);
        } catch (Exception e) {
          if (thrown == null) {
            thrown = e;
          } else {
            thrown.addSuppressed(e);
          }
        }
      }
    }
    if (thrown != null) {
      throw thrown;
    }
  }

  @Test
  public void testListFiles_all() throws IOException {
    assertEquals(
        Set.of(file, subdirectory),
        Set.copyOf(OtherFiles.listFiles(directory, "*")));
  }

  @Test
  public void testListFiles_glob() throws IOException {
    assertEquals(
        Set.of(file),
        Set.copyOf(OtherFiles.listFiles(directory, "*.txt")));
  }

  @Test
  public void testListFiles_emptyWhenNotDirectory() throws IOException {
    assertEquals(
        Set.of(),
        Set.copyOf(OtherFiles.listFiles(file, "*")));
  }
}
