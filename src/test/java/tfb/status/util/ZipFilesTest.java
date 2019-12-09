package tfb.status.util;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.ProviderNotFoundException;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tfb.status.util.ZipFiles.ZipEntryReader;

/**
 * Tests for {@link ZipFiles}.
 */
public final class ZipFilesTest {
  private static final String PRESENT_ENTRY_PATH = "file_inside_zip.txt";
  private static final String PRESENT_ENTRY_ABSOLUTE_PATH = "/" + PRESENT_ENTRY_PATH;
  private static final String PRESENT_ENTRY_CONTENTS = "Hello!";
  private static final String DIRECTORY_ENTRY_PATH = "directory_inside_zip/";

  private static FileSystem inMemoryFs;
  private static Path zipFile;
  private static Path textFile;
  private static Path missingFile;
  private static Path directory;

  @BeforeAll
  public static void beforeAll() throws IOException {
    inMemoryFs = Jimfs.newFileSystem(Configuration.unix());
    zipFile = inMemoryFs.getPath("/zip_file.zip");

    FileSystemProvider zipFsProvider =
        FileSystemProvider
            .installedProviders()
            .stream()
            .filter(provider -> provider.getScheme().equals("jar"))
            .findAny()
            .orElseThrow(() -> new ProviderNotFoundException(
                "Could not find the zip file system provider; "
                    + "verify that the jdk.zipfs module is installed"));

    try (FileSystem zipFs =
             zipFsProvider.newFileSystem(
                 zipFile,
                 Map.of("create", "true"))) {

      Files.writeString(
          zipFs.getPath(PRESENT_ENTRY_PATH),
          PRESENT_ENTRY_CONTENTS,
          CREATE_NEW);

      Files.createDirectory(
          zipFs.getPath(DIRECTORY_ENTRY_PATH));
    }

    textFile = inMemoryFs.getPath("/file_outside_zip.txt");
    Files.writeString(
        textFile,
        "This is not a zip file",
        CREATE_NEW);

    directory = inMemoryFs.getPath("/directory_outside_zip");
    Files.createDirectory(directory);

    missingFile = inMemoryFs.getPath("/no_file_here");
  }

  @AfterAll
  public static void afterAll() throws IOException {
    inMemoryFs.close();
  }

  /**
   * Verifies that {@link ZipFiles#readZipEntry(Path, String, ZipEntryReader)}
   * is able to read a present entry from a valid zip file given a relative
   * entry path.
   */
  @Test
  public void testReadZipEntry_relativePath() throws IOException {
    assertEquals(
        PRESENT_ENTRY_CONTENTS,
        ZipFiles.readZipEntry(
            zipFile,
            PRESENT_ENTRY_PATH,
            entry -> new String(entry.readAllBytes(), UTF_8)));
  }

  /**
   * Verifies that {@link ZipFiles#readZipEntry(Path, String, ZipEntryReader)}
   * is able to read a present entry from a valid zip file given an absolute
   * entry path.
   */
  @Test
  public void testReadZipEntry_absolutePath() throws IOException {
    assertEquals(
        PRESENT_ENTRY_CONTENTS,
        ZipFiles.readZipEntry(
            zipFile,
            PRESENT_ENTRY_ABSOLUTE_PATH,
            entry -> new String(entry.readAllBytes(), UTF_8)));
  }

  /**
   * Verifies that {@link ZipFiles#readZipEntry(Path, String, ZipEntryReader)}
   * rejects a zip file that does not exist.
   */
  @Test
  public void testReadZipEntry_rejectMissingFile() {
    assertThrows(
        IOException.class,
        () -> ZipFiles.readZipEntry(
            missingFile,
            PRESENT_ENTRY_PATH,
            entry -> fail("This reader should not have been used")));
  }

  /**
   * Verifies that {@link ZipFiles#readZipEntry(Path, String, ZipEntryReader)}
   * rejects a zip file that is not actually in zip format.
   */
  @Test
  public void testReadZipEntry_rejectWrongFileFormat() {
    assertThrows(
        IOException.class,
        () -> ZipFiles.readZipEntry(
            textFile,
            PRESENT_ENTRY_PATH,
            entry -> fail("This reader should not have been used")));
  }

  /**
   * Verifies that {@link ZipFiles#readZipEntry(Path, String, ZipEntryReader)}
   * rejects a zip file that is a directory.
   */
  @Test
  public void testReadZipEntry_rejectDirectory() {
    assertThrows(
        IOException.class,
        () -> ZipFiles.readZipEntry(
            directory,
            PRESENT_ENTRY_PATH,
            entry -> fail("This reader should not have been used")));
  }

  /**
   * Verifies that {@link ZipFiles#readZipEntry(Path, String, ZipEntryReader)}
   * rejects an entry path containing invalid characters.  The exception thrown
   * should be {@link IOException} rather than {@link InvalidPathException}.
   *
   * <p>Note that zip file paths are tolerant of many characters that are
   * typically rejected for paths on the main file system.
   */
  @Test
  public void testReadZipEntry_rejectInvalidPath() {
    assertThrows(
        IOException.class,
        () -> ZipFiles.readZipEntry(
            zipFile,
            "\0",
            entry -> fail("This reader should not have been used")));
  }

  /**
   * Verifies that {@link ZipFiles#readZipEntry(Path, String, ZipEntryReader)}
   * returns {@code null} (rather than throwing an exception) when the entry
   * path refers to a directory entry in the zip file.
   */
  @Test
  public void testReadZipEntry_skipDirectoryEntry() throws IOException {
    assertNull(
        ZipFiles.readZipEntry(
            zipFile,
            DIRECTORY_ENTRY_PATH,
            entry -> fail("This reader should not have been used")));
  }

  /**
   * Verifies that {@link ZipFiles#readZipEntry(Path, String, ZipEntryReader)}
   * throws a {@link NullPointerException} when the entry reader produces a
   * {@code null} result.
   */
  @Test
  public void testReadZipEntry_rejectNullValueFromReader() {
    // Violating nullness contract on purpose.
    @SuppressWarnings({
        "NullAway" /* for the NullAway Maven plugin */,
        "ConstantConditions" /* for IntelliJ */
    })
    ZipEntryReader<Void> entryReader = entry -> null;

    assertThrows(
        NullPointerException.class,
        () -> ZipFiles.readZipEntry(
            zipFile,
            PRESENT_ENTRY_PATH,
            entryReader));
  }

  /**
   * Verifies that {@link ZipFiles#readZipEntry(Path, String, ZipEntryReader)}
   * propagates an {@link IOException} thrown by the entry reader.
   */
  @Test
  public void testReadZipEntry_ioExceptionFromReaderIsUncaught() {
    IOException e1 = new IOException();

    IOException e2 =
        assertThrows(
            IOException.class,
            () -> ZipFiles.readZipEntry(
                zipFile,
                PRESENT_ENTRY_PATH,
                entry -> { throw e1; }));

    assertSame(e1, e2);
  }

  /**
   * Verifies that {@link ZipFiles#readZipEntry(Path, String, ZipEntryReader)}
   * propagates a {@link RuntimeException} thrown by the entry reader.
   */
  @Test
  public void testReadZipEntry_runtimeExceptionFromReaderIsUncaught() {
    RuntimeException e1 = new UnsupportedOperationException();

    RuntimeException e2 =
        assertThrows(
            RuntimeException.class,
            () -> ZipFiles.readZipEntry(
                zipFile,
                PRESENT_ENTRY_PATH,
                entry -> { throw e1; }));

    assertSame(e1, e2);
  }
}
