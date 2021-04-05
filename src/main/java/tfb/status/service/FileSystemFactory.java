package tfb.status.service;

import jakarta.inject.Singleton;
import java.io.File;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import tfb.status.hk2.extensions.Provides;

/**
 * Provides the {@link FileSystem} used by this application.
 *
 * <p>Use this file system to resolve paths to files.  Avoid using APIs that
 * implicitly rely on the platform's default file system.  This usage pattern
 * allows us to swap in a different file system during tests, which we can
 * discard when the tests complete.
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
 * These APIs are ok to use even though they always write to the platform's
 * default file system:
 *
 * <ul>
 * <li>{@link Files#createTempFile(String, String, FileAttribute[])}
 * <li>{@link Files#createTempDirectory(String, FileAttribute[])}
 * </ul>
 */
public final class FileSystemFactory {
  private FileSystemFactory() {
    throw new AssertionError("This class cannot be instantiated");
  }

  @Provides
  @Singleton
  public static FileSystem defaultFileSystem() {
    return FileSystems.getDefault();
  }
}
