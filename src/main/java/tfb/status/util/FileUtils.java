package tfb.status.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

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
          "The specified path is not a directory: " + directory.toString());

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
}
