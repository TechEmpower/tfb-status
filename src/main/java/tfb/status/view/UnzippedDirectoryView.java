package tfb.status.view;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A view of one directory within a zip file.
 *
 * @param breadcrumbs The current directory and all its parents up to the root
 *        of this zip file.  The first item in this list is the root and the
 *        last is the current directory.
 * @param children The children of the current directory.  This list is sorted
 *        with directories first and files second, and with items of each type
 *        sorted by name (ignoring case).
 */
@Immutable
public record UnzippedDirectoryView(ImmutableList<FileView> breadcrumbs,
                                    ImmutableList<FileView> children) {

  public UnzippedDirectoryView {
    Objects.requireNonNull(breadcrumbs);
    Objects.requireNonNull(children);
  }

  /**
   * A view of another file or directory within the same zip file.
   *
   * @param fileName The name of the file, such as "base.log".
   * @param fullPath The full path of the file, including the name of the zip
   *        file, such as
   *        "results.2018-01-01-00-00-00-000.zip/gemini/build/gemini.log".
   * @param size The size of this file as a human-readable string, such as
   *        "4.5 kB", or {@code null} if this is a directory.
   * @param isDirectory {@code true} if this is a directory.
   * @param isSelected {@code true} if this file or directory is the one
   *        currently being viewed by the user.
   */
  @Immutable
  public record FileView(String fileName,
                         String fullPath,
                         @Nullable String size,
                         boolean isDirectory,
                         boolean isSelected) {

    public FileView {
      Objects.requireNonNull(fileName);
      Objects.requireNonNull(fullPath);
    }
  }
}
