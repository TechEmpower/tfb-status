package tfb.status.util;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.io.MoreFiles;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link FileUtils}.
 */
public final class FileUtilsTest {
  private static final String FILE_CONTENTS = "This is a text file";
  private static final int NUMBER_FILES_IN_DIRECTORY = 10;
  private static final int DIRECTORY_SIZE_BYTES =
      FILE_CONTENTS.length() * NUMBER_FILES_IN_DIRECTORY;
  private static final int NUMBER_FILES_IN_ROOT = 5;
  // The size of the text files that exist directly in the root, excluding any
  // directories' contents.
  private static final int ROOT_FILES_SIZE =
      FILE_CONTENTS.length() * NUMBER_FILES_IN_ROOT;

  private static FileSystem inMemoryFs;
  private static Path directory;
  private static Path emptyDirectory;
  private static Path textFile;
  private static Path missingFile;

  @BeforeAll
  public static void beforeAll() throws IOException {
    inMemoryFs = Jimfs.newFileSystem(Configuration.unix());

    directory = inMemoryFs.getPath("/file_utils_test_directory");
    Files.createDirectory(directory);

    emptyDirectory = inMemoryFs.getPath("/file_utils_test_empty_directory");
    Files.createDirectory(emptyDirectory);

    for (int i = 0; i < NUMBER_FILES_IN_ROOT; i++) {
      Path rootTextFile = inMemoryFs.getPath("/root_file_" + i + ".txt");
      Files.writeString(rootTextFile, FILE_CONTENTS, CREATE_NEW);
    }

    // Get a reference to one of the text files, it's not important which.
    textFile = MoreFiles.listFiles(inMemoryFs.getPath("/"))
        .stream()
        .filter(path -> "txt".equals(MoreFiles.getFileExtension(path)))
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("No text files found in root"));

    for (int i = 0; i < NUMBER_FILES_IN_DIRECTORY; i++) {
      Path textFile = directory.resolve("directory_file_" + i + ".txt");
      Files.writeString(textFile, FILE_CONTENTS, CREATE_NEW);
    }

    missingFile = inMemoryFs.getPath("/no_file_here");
  }

  @AfterAll
  public static void afterAll() throws IOException {
    inMemoryFs.close();
  }

  /**
   * Ensure that it returns the correct size for a valid directory with files.
   */
  @Test
  public void testDirectorySizeBytes_directory() throws IOException {
    assertEquals(DIRECTORY_SIZE_BYTES, FileUtils.directorySizeBytes(directory));
  }

  /**
   * Ensure that it returns {@code 0} for a valid directory with no files.
   */
  @Test
  public void testDirectorySizeBytes_emptyDirectory() throws IOException {
    assertEquals(0, FileUtils.directorySizeBytes(emptyDirectory));
  }

  /**
   * Ensure that it returns the correct size for a valid directory with files
   * and directories in it.
   */
  @Test
  public void testDirectorySizeBytes_nestedDirectory() throws IOException {
    assertEquals(
      DIRECTORY_SIZE_BYTES + ROOT_FILES_SIZE,
      FileUtils.directorySizeBytes(inMemoryFs.getPath("/")));
  }

  /**
   * Ensure that it throws an exception when given a valid path that does not
   * correspond to a directory.
   */
  @Test
  public void testDirectorySizeBytes_rejectTextFile() {
    assertThrows(
        IllegalArgumentException.class,
        () -> FileUtils.directorySizeBytes(textFile));
  }

  /**
   * Ensure that it throws an exception when given a path that does not exist.
   */
  @Test
  public void testDirectorySizeBytes_rejectMissingFile() {
    assertThrows(
        IllegalArgumentException.class,
        () -> FileUtils.directorySizeBytes(missingFile));
  }
}
