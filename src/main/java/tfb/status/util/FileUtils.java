package tfb.status.util;

import com.google.common.io.MoreFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class FileUtils {
  private FileUtils() {
    throw new AssertionError("This class cannot be instantiated");
  }

  /**
   * Calculate the size of the specified directory in bytes.
   */
  public static long directorySizeBytes(Path directory) throws IOException {
    Objects.requireNonNull(directory);

    if (!Files.isDirectory(directory)) {
      throw new IllegalArgumentException(
          "The specified File is not a directory: " + directory.toString());
    }

    long length = 0;

    for (Path path : MoreFiles.listFiles(directory)) {
      if (Files.isDirectory(path)) {
        length += directorySizeBytes(path);
      } else {
        length += Files.size(path);
      }
    }

    return length;
  }
}
