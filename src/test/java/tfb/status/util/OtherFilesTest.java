package tfb.status.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
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
    FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
    directory = fs.getPath("/directory");
    Files.createDirectory(directory);
    file = directory.resolve("file.txt");
    Files.createFile(file);
    subdirectory = directory.resolve("subdirectory");
    Files.createDirectory(subdirectory);
    subdirectoryFile = subdirectory.resolve("subdirectoryFile.txt");
    Files.createFile(subdirectoryFile);
  }

  /**
   * Verifies that {@link OtherFiles#listFiles(Path, String)} returns the list
   * of all files in the directory when given the {@code "*"} pattern.
   */
  @Test
  public void testListFiles_all() throws IOException {
    assertEquals(
        Set.of(file, subdirectory),
        Set.copyOf(OtherFiles.listFiles(directory, "*")));
    assertEquals(
        Set.of(subdirectoryFile),
        Set.copyOf(OtherFiles.listFiles(subdirectory, "*")));
  }

  /**
   * Verifies that {@link OtherFiles#listFiles(Path, String)} returns only the
   * matching files in the directory when given a pattern that does not match
   * all files.
   */
  @Test
  public void testListFiles_glob() throws IOException {
    assertEquals(
        Set.of(file),
        Set.copyOf(OtherFiles.listFiles(directory, "*.txt")));
    assertEquals(
        Set.of(subdirectoryFile),
        Set.copyOf(OtherFiles.listFiles(subdirectory, "*.txt")));
  }

  /**
   * Verifies that {@link OtherFiles#listFiles(Path, String)} returns an empty
   * list when the specified directory is not a directory.
   */
  @Test
  public void testListFiles_emptyWhenNotDirectory() throws IOException {
    assertEquals(
        Set.of(),
        Set.copyOf(OtherFiles.listFiles(file, "*")));
    assertEquals(
        Set.of(),
        Set.copyOf(OtherFiles.listFiles(subdirectoryFile, "*")));
  }
}
