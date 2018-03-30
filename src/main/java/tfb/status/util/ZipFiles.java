package tfb.status.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.ProviderNotFoundException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * Utility methods for working with zip files.
 */
public final class ZipFiles {
  private ZipFiles() {
    throw new AssertionError("This class cannot be instantiated");
  }

  /**
   * Opens the provided zip file, then reads the entry whose path {@linkplain
   * Path#endsWith(String) ends with} the provided entry path.  Returns the
   * non-{@code null} result of reading the entry or {@code null} if the entry
   * is not found.
   *
   * <p>If the zip file contains multiple matching entries, then this method
   * will choose one of those entries.  The way it makes this choice is
   * unspecified, possibly non-deterministic, and subject to change.
   *
   * <p>If the entry is found but the reader throws an exception while reading
   * it, that exception is propagated as-is to the caller of this method.
   *
   * <p>The time complexity of this method is {@code O(n)} with respect to the
   * total number of entries in the zip file.
   *
   * @param <T> the type of value to be read from the entry
   * @param zipFile the zip file to be searched
   * @param entryPath the path of the entry to be read
   * @param entryReader the reader to be used upon finding the entry
   * @return the non-{@code null} result of reading the entry or {@code null} if
   *         the entry is not found
   * @throws IOException if an I/O error occurs
   */
  @Nullable
  public static <T> T readZipEntry(Path zipFile,
                                   String entryPath,
                                   ZipEntryReader<T> entryReader)
      throws IOException {

    Objects.requireNonNull(zipFile, "zipFile");
    Objects.requireNonNull(entryPath, "entryPath");
    Objects.requireNonNull(entryReader, "entryReader");

    var resultHolder = new AtomicReference<T>();

    findZipEntry(
        /* zipFile= */ zipFile,
        /* entryPath= */ entryPath,
        /* ifPresent= */
        zipEntry -> {
          if (Files.isRegularFile(zipEntry)) {
            T result;
            try (InputStream in = Files.newInputStream(zipEntry)) {
              result = entryReader.read(in);
            }
            Objects.requireNonNull(result, "result");
            resultHolder.set(result);
          }
        },
        /* ifAbsent= */ () -> {});

    return resultHolder.get();
  }

  /**
   * A function that accepts the contents of a zip entry as stream of bytes and
   * produces a non-{@code null} value as a result.
   *
   * <p>It is not necessary for implementations of this interface to close the
   * stream.
   *
   * <p>This interface should only be used by callers of {@link
   * #readZipEntry(Path, String, ZipEntryReader)}.
   *
   * @param <T> the type of value contained (as bytes) in the stream
   */
  @FunctionalInterface
  public interface ZipEntryReader<T> {
    /**
     * Reads the value contained in the zip entry.
     *
     * @param in the (unzipped) contents of the entry as a stream of bytes
     * @return the non-{@code null} result of reading the entry
     * @throws IOException if an I/O error occurs while reading the entry
     */
    T read(InputStream in) throws IOException;
  }

  /**
   * Opens the provided zip file, then finds the entry whose path {@linkplain
   * Path#endsWith(String) ends with} the provided entry path.  If the entry is
   * found, this method invokes the provided {@code ifPresent} action.
   * Otherwise, this method invokes the provided {@code ifAbsent} action.
   *
   * <p>If the zip file contains multiple matching entries, then this method
   * will choose one of those entries.  The way it makes this choice is
   * unspecified, possibly non-deterministic, and subject to change.
   *
   * <p>If the entry is found but the entry handler throws an exception when
   * invoked, that exception is propagated as-is to the caller of this method.
   *
   * <p>The time complexity of this method is {@code O(n)} with respect to the
   * total number of entries in the zip file.
   *
   * @param zipFile the zip file to be searched
   * @param entryPath the path of the entry to be read
   * @param ifPresent the action to be invoked if the entry is found
   * @param ifAbsent the action to be invoked if the entry is not found
   * @throws IOException if an I/O error occurs
   */
  public static void findZipEntry(Path zipFile,
                                  String entryPath,
                                  ZipEntryConsumer ifPresent,
                                  Runnable ifAbsent)
      throws IOException {

    Objects.requireNonNull(zipFile, "zipFile");
    Objects.requireNonNull(entryPath, "entryPath");
    Objects.requireNonNull(ifPresent, "ifPresent");
    Objects.requireNonNull(ifAbsent, "ifAbsent");

    FileSystem zipFileSystem;
    try {
      zipFileSystem = FileSystems.newFileSystem(zipFile, null);
    } catch (ProviderNotFoundException | FileSystemNotFoundException e) {
      throw new IOException(e);
    }

    try (zipFileSystem) {
      Path validatedEntryPath;
      try {
        validatedEntryPath = zipFileSystem.getPath(entryPath);
      } catch (InvalidPathException e) {
        throw new IOException(e);
      }

      if (validatedEntryPath.isAbsolute()) {
        if (Files.exists(validatedEntryPath)) {
          ifPresent.accept(validatedEntryPath);
          return;
        }
      } else {
        for (Path root : zipFileSystem.getRootDirectories()) {
          Path matchingEntry;
          try (Stream<Path> entries = Files.walk(root)) {
            matchingEntry =
                entries.filter(Files::isDirectory)
                       .map(directory -> directory.resolve(validatedEntryPath))
                       .filter(Files::exists)
                       .findAny()
                       .orElse(null);
          }

          if (matchingEntry != null) {
            ifPresent.accept(matchingEntry);
            return;
          }
        }
      }
    }

    ifAbsent.run();
  }

  /**
   * An action performed on a zip entry, where the zip entry is represented as a
   * {@link Path}.
   *
   * <p>The action should process the {@link Path} immediately and not save a
   * reference to it for later use, as the {@linkplain Path#getFileSystem() file
   * system} will be closed once this action returns.
   *
   * <p>This interface should only be used by callers of {@link
   * #findZipEntry(Path, String, ZipEntryConsumer, Runnable)}.
   */
  @FunctionalInterface
  public interface ZipEntryConsumer {
    /**
     * Performs some action on a zip entry.
     *
     * @param zipEntry the zip entry
     * @throws IOException if an I/O error occurs while performing the action
     */
    void accept(Path zipEntry) throws IOException;
  }
}
