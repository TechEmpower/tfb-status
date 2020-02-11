package tfb.status.util;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.io.MoreFiles;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link FileUtils}.
 */
public final class FileUtilsTest {
  private static final String FILE_CONTENTS = "This is a text file";
  private static final int FILES_IN_ROOT_DIRECTORY = 5;
  private static final int FILES_IN_SUBDIRECTORY = 10;

  // The total size of the files that exist directly in the root, excluding
  // files in subdirectories.
  private static final int ROOT_DIRECTORY_SIZE_IN_BYTES =
      FILE_CONTENTS.length() * FILES_IN_ROOT_DIRECTORY;

  // The total size of the files within each subdirectory.
  private static final int SUBDIRECTORY_SIZE_IN_BYTES =
      FILE_CONTENTS.length() * FILES_IN_SUBDIRECTORY;

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

    for (int i = 0; i < FILES_IN_ROOT_DIRECTORY; i++) {
      Path textFile = inMemoryFs.getPath("/root_file_" + i + ".txt");
      Files.writeString(textFile, FILE_CONTENTS, CREATE_NEW);
    }

    // Get a reference to one of the text files, it's not important which.
    try (Stream<Path> files = Files.list(inMemoryFs.getPath("/"))) {
      textFile =
          files.filter(path -> MoreFiles.getFileExtension(path).equals("txt"))
               .findFirst()
               .orElseThrow(
                   () -> new IllegalArgumentException("No text files found in root"));
    }

    for (int i = 0; i < FILES_IN_SUBDIRECTORY; i++) {
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
   * Verifies that {@link FileUtils#directorySizeInBytes(Path)} returns the
   * correct size for a non-empty directory containing no subdirectories.
   */
  @Test
  public void testDirectorySizeBytes_directory() throws IOException {
    assertEquals(SUBDIRECTORY_SIZE_IN_BYTES, FileUtils.directorySizeInBytes(directory));
  }

  /**
   * Verifies that {@link FileUtils#directorySizeInBytes(Path)} returns {@code
   * 0L} for an empty directory.
   */
  @Test
  public void testDirectorySizeBytes_emptyDirectory() throws IOException {
    assertEquals(0L, FileUtils.directorySizeInBytes(emptyDirectory));
  }

  /**
   * Verifies that {@link FileUtils#directorySizeInBytes(Path)} returns the
   * correct size for a directory that contains non-empty subdirectories.
   */
  @Test
  public void testDirectorySizeBytes_nestedDirectory() throws IOException {
    assertEquals(
        SUBDIRECTORY_SIZE_IN_BYTES + ROOT_DIRECTORY_SIZE_IN_BYTES,
        FileUtils.directorySizeInBytes(inMemoryFs.getPath("/")));
  }

  /**
   * Verifies that {@link FileUtils#directorySizeInBytes(Path)} throws an
   * exception when the specified path is a file rather than a directory.
   */
  @Test
  public void testDirectorySizeBytes_rejectTextFile() {
    assertThrows(
        IllegalArgumentException.class,
        () -> FileUtils.directorySizeInBytes(textFile));
  }

  /**
   * Verifies that {@link FileUtils#directorySizeInBytes(Path)} throws an
   * exception when the specified path does not exist.
   */
  @Test
  public void testDirectorySizeBytes_rejectMissingFile() {
    assertThrows(
        IllegalArgumentException.class,
        () -> FileUtils.directorySizeInBytes(missingFile));
  }

  /**
   * Verifies that {@link FileUtils#resolveChildPath(Path, String)} returns the
   * expected non-{@code null} result when invoked with valid parameters.
   */
  @Test
  public void testResolveChildPath() {
    assertEquals(
        inMemoryFs.getPath("foo/bar"),
        FileUtils.resolveChildPath(
            inMemoryFs.getPath("foo"),
            "bar"));
    assertEquals(
        inMemoryFs.getPath("foo/bar.txt"),
        FileUtils.resolveChildPath(
            inMemoryFs.getPath("foo"),
            "bar.txt"));
  }

  /**
   * Verifies that {@link FileUtils#resolveChildPath(Path, String)} returns
   * {@code null} when the resulting path is not a child of the directory.
   */
  @Test
  public void testResolveChildPath_notChild() {
    assertNull(
        FileUtils.resolveChildPath(
            inMemoryFs.getPath("foo"),
            "bar/baz"));
  }

  /**
   * Verifies that {@link FileUtils#resolveChildPath(Path, String)} returns
   * {@code null} when the resulting path is invalid according to the file
   * system.
   */
  @Test
  public void testResolveChildPath_invalidPath() {
    assertNull(
        FileUtils.resolveChildPath(
            inMemoryFs.getPath("foo"),
            "\0"));
  }

  /**
   * Verifies that {@link FileUtils#resolveChildPath(Path, String)} returns
   * {@code null} when the resulting path is not normalized.
   */
  @Test
  public void testResolveChildPath_notNormalized() {
    assertNull(
        FileUtils.resolveChildPath(
            inMemoryFs.getPath("foo"),
            "."));
    assertNull(
        FileUtils.resolveChildPath(
            inMemoryFs.getPath("foo"),
            ".."));
    assertNull(
        FileUtils.resolveChildPath(
            inMemoryFs.getPath("foo"),
            "../foo/bar"));
    assertNull(
        FileUtils.resolveChildPath(
            inMemoryFs.getPath("foo"),
            "bar/."));
    assertNull(
        FileUtils.resolveChildPath(
            inMemoryFs.getPath("foo"),
            "bar/../baz"));
    assertNull(
        FileUtils.resolveChildPath(
            inMemoryFs.getPath("foo/."),
            "bar"));
  }
}
