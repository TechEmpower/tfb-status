package tfb.status.service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import javax.inject.Singleton;
import org.glassfish.hk2.api.Factory;

/**
 * Provides the standard {@link FileSystem} used by this application.
 *
 * <p>Use this file system to resolve paths to files.  Avoid using APIs that
 * implicitly rely on the default file system.  This usage pattern allows us to
 * swap in a "fake" file system during tests, which we can throw away entirely
 * when the tests complete.
 *
 * <table>
 *   <caption>APIs to avoid and their replacements</caption>
 *   <tr>
 *   <th>Don't use this
 *   <th>Use this instead
 *   <tr>
 *   <td>{@link File}
 *   <td>{@link Path}
 *   <tr>
 *   <td>{@link Path#of(String, String[])} or {@link Paths#get(String, String[])}
 *   <td>{@link FileSystem#getPath(String, String...)}
 *   <tr>
 *   <td>{@link File#separator} or {@link File#separatorChar}
 *   <td>{@link FileSystem#getSeparator()}
 *   <tr>
 *   <td>{@link Path#toFile()}
 *   <td>No replacement.  Just don't call that method.
 * </table>
 *
 * <p>It is not necessary to use the provided file system for temporary files.
 * These APIs are ok to use even though they always write to the default file
 * system:
 *
 * <ul>
 * <li>{@link Files#createTempFile(String, String, FileAttribute[])}
 * <li>{@link Files#createTempDirectory(String, FileAttribute[])}
 * </ul>
 */
@Singleton
public final class StandardFileSystem implements Factory<FileSystem> {
  @Override
  @Singleton
  public FileSystem provide() {
    return FileSystems.getDefault();
  }

  @Override
  public void dispose(FileSystem instance) {
    // No cleanup required.
  }
}
