package tfb.status.util;

import com.google.common.collect.ImmutableList;
import com.google.common.io.MoreFiles;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Utility methods for working with files.  Complements {@link Files} and {@link
 * MoreFiles}.
 */
public final class OtherFiles {
  private OtherFiles() {
    throw new AssertionError("This class cannot be instantiated");
  }

  /**
   * Returns the files in the given directory that match the given filename
   * pattern.  Does not search subdirectories.  If the specified directory is
   * not a directory, the empty list is returned.
   */
  public static ImmutableList<Path> listFiles(Path directory, String glob)
      throws IOException {

    Objects.requireNonNull(directory);
    Objects.requireNonNull(glob);

    if (!Files.isDirectory(directory))
      return ImmutableList.of();

    try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, glob)) {
      return ImmutableList.copyOf(files);
    }
  }
}
