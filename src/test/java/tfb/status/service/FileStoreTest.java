package tfb.status.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.testlib.TestServicesInjector;

/**
 * Tests for {@link FileStore}.
 */
@ExtendWith(TestServicesInjector.class)
public final class FileStoreTest {
  /**
   * Verifies that {@link FileStore#resultsDirectory()} returns a directory.
   */
  @Test
  public void testResultsDirectory(FileStore fileStore) {
    assertTrue(Files.isDirectory(fileStore.resultsDirectory()));
  }

  /**
   * Verifies that {@link FileStore#accountsDirectory()} returns a directory.
   */
  @Test
  public void testAccountsDirectory(FileStore fileStore) {
    assertTrue(Files.isDirectory(fileStore.accountsDirectory()));
  }

  /**
   * Verifies that {@link FileStore#attributesDirectory()} returns a directory.
   */
  @Test
  public void testAttributesDirectory(FileStore fileStore) {
    assertTrue(Files.isDirectory(fileStore.attributesDirectory()));
  }

  /**
   * Verifies that {@link FileStore#announcementFile()} returns a file.
   */
  @Test
  public void testAnnouncementFile(FileStore fileStore) {
    assertTrue(Files.isRegularFile(fileStore.announcementFile()));
  }
}
