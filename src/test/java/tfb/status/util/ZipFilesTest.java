package tfb.status.util;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tfb.status.util.ZipFiles.ZipEntryReader;

/**
 * Tests for {@link ZipFiles}.
 */
public final class ZipFilesTest {
  private static final String PRESENT_ENTRY_PATH = "hello.txt";
  private static final String PRESENT_ENTRY_ABSOLUTE_PATH = "/" + PRESENT_ENTRY_PATH;
  private static final byte[] PRESENT_ENTRY_BYTES = "Hello!".getBytes(UTF_8);
  private static final String DIR_ENTRY_PATH = "dir/";

  private static final ZipEntryReader<Void> UNUSED_READER =
      inputStream -> {
        throw new AssertionError("This reader should not have been used");
      };

  private static Path zipFile;
  private static Path textFile;
  private static Path dir;
  private static Path missingFile;

  @BeforeAll
  public static void beforeAll() throws Exception {
    FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
    zipFile = fs.getPath("/archive.zip");

    try (var fos = Files.newOutputStream(zipFile, CREATE_NEW);
         var bos = new BufferedOutputStream(fos);
         var zos = new ZipOutputStream(bos)) {

      zos.putNextEntry(new ZipEntry(PRESENT_ENTRY_PATH));
      zos.write(PRESENT_ENTRY_BYTES);
      zos.closeEntry();

      zos.putNextEntry(new ZipEntry(DIR_ENTRY_PATH));
      zos.closeEntry();
    }

    textFile = fs.getPath("/file.txt");
    Files.write(textFile, List.of("This is not a zip file"), CREATE_NEW);
    dir = fs.getPath("/dir");
    Files.createDirectory(dir);
    missingFile = fs.getPath("/missing");
  }

  /**
   * Verifies that {@link ZipFiles#readZipEntry(Path, String, ZipEntryReader)}
   * is able to read a present entry from a valid zip file given a relative
   * entry path.
   */
  @Test
  public void testReadZipEntry_relativePath() throws IOException {
    assertArrayEquals(
        PRESENT_ENTRY_BYTES,
        ZipFiles.readZipEntry(zipFile,
                              PRESENT_ENTRY_PATH,
                              entry -> entry.readAllBytes()));
  }

  /**
   * Verifies that {@link ZipFiles#readZipEntry(Path, String, ZipEntryReader)}
   * is able to read a present entry from a valid zip file given an absolute
   * entry path.
   */
  @Test
  public void testReadZipEntry_absolutePath() throws IOException {
    assertArrayEquals(
        PRESENT_ENTRY_BYTES,
        ZipFiles.readZipEntry(zipFile,
                              PRESENT_ENTRY_ABSOLUTE_PATH,
                              entry -> entry.readAllBytes()));
  }

  /**
   * Verifies that {@link ZipFiles#readZipEntry(Path, String, ZipEntryReader)}
   * rejects a zip file that does not exist.
   */
  @Test
  public void testReadZipEntry_rejectMissingFile() {
    assertThrows(
        IOException.class,
        () -> ZipFiles.readZipEntry(missingFile,
                                    PRESENT_ENTRY_PATH,
                                    UNUSED_READER));
  }

  /**
   * Verifies that {@link ZipFiles#readZipEntry(Path, String, ZipEntryReader)}
   * rejects a zip file that is not actually in zip format.
   */
  @Test
  public void testReadZipEntry_rejectWrongFileFormat() {
    assertThrows(
        IOException.class,
        () -> ZipFiles.readZipEntry(textFile,
                                    PRESENT_ENTRY_PATH,
                                    UNUSED_READER));
  }

  /**
   * Verifies that {@link ZipFiles#readZipEntry(Path, String, ZipEntryReader)}
   * rejects a zip file that is a directory.
   */
  @Test
  public void testReadZipEntry_rejectDirectory() {
    assertThrows(
        IOException.class,
        () -> ZipFiles.readZipEntry(dir,
                                    PRESENT_ENTRY_PATH,
                                    UNUSED_READER));
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
        () -> ZipFiles.readZipEntry(zipFile,
                                    "\0",
                                    UNUSED_READER));
  }

  /**
   * Verifies that {@link ZipFiles#readZipEntry(Path, String, ZipEntryReader)}
   * returns {@code null} (rather than throwing an exception) when the entry
   * path refers to a directory entry in the zip file.
   */
  @Test
  public void testReadZipEntry_skipDirectoryEntry() throws IOException {
    assertNull(ZipFiles.readZipEntry(zipFile,
                                     DIR_ENTRY_PATH,
                                     UNUSED_READER));
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
    ZipEntryReader<Void> entryReader = inputStream -> null;

    assertThrows(
        NullPointerException.class,
        () -> ZipFiles.readZipEntry(zipFile,
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
            () -> ZipFiles.readZipEntry(zipFile,
                                        PRESENT_ENTRY_PATH,
                                        inputStream -> { throw e1; }));
    assertSame(e1, e2);
  }

  /**
   * Verifies that {@link ZipFiles#readZipEntry(Path, String, ZipEntryReader)}
   * propagates a {@link RuntimeException} thrown by the entry reader.
   */
  @Test
  public void testReadZipEntry_runtimeExceptionFromReaderIsUncaught() {
    RuntimeException e1 = new RuntimeException();

    RuntimeException e2 =
        assertThrows(
            RuntimeException.class,
            () -> ZipFiles.readZipEntry(zipFile,
                                        PRESENT_ENTRY_PATH,
                                        inputStream -> { throw e1; }));
    assertSame(e1, e2);
  }
}
