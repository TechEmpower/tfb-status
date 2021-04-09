package tfb.status.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Utility methods for working with the file system.
 */
public final class FileUtils {
  private FileUtils() {
    throw new AssertionError("This class cannot be instantiated");
  }

  /**
   * Returns the size of the specified directory in bytes.  Includes files
   * contained in subdirectories.
   *
   * @throws IllegalArgumentException if the specified path is not a directory
   */
  public static long directorySizeInBytes(Path directory) throws IOException {
    Objects.requireNonNull(directory);

    if (!Files.isDirectory(directory))
      throw new IllegalArgumentException(
          "The specified path is not a directory: " + directory);

    try (Stream<Path> stream = Files.walk(directory)) {
      return stream
          .mapToLong(
              path -> {
                if (!Files.isRegularFile(path))
                  return 0;

                try {
                  return Files.size(path);
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              })
          .sum();
    }
  }

  /**
   * Returns the {@link Path} object for the child of the specified directory
   * where the child has the specified {@linkplain Path#getFileName() file
   * name}.  Returns {@code null} if the file name is invalid according to the
   * directory's file system, or if the resulting path is not {@linkplain
   * Path#normalize() normalized} (the path contains ".." or "." elements), or
   * if the resulting path would not refer to a direct child of the directory.
   * This method does not check if the file or directory exists in the file
   * system.
   *
   * @param directory the parent path
   * @param fileName the file name of the child path
   */
  public static @Nullable Path resolveChildPath(Path directory,
                                                String fileName) {
    Objects.requireNonNull(directory);
    Objects.requireNonNull(fileName);

    Path child;
    try {
      child = directory.resolve(fileName);
    } catch (InvalidPathException ignored) {
      return null;
    }

    if (!child.equals(child.normalize()))
      return null;

    if (!directory.equals(child.getParent()))
      return null;

    return child;
  }
}
