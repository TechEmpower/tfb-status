package tfb.status.view;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * A view of one directory within a zip file.
 */
@Immutable
public final class DirectoryListingView {
  /**
   * The current directory and all its parents up to the root of this zip file.
   * The first item in this list is the root and the last is the current
   * directory.
   */
  public final ImmutableList<FileView> breadcrumbs;

  /**
   * The children of the current directory.  This list is sorted with
   * directories first and files second, and with items of each type sorted by
   * name (ignoring case).
   */
  public final ImmutableList<FileView> children;

  public DirectoryListingView(ImmutableList<FileView> breadcrumbs,
                              ImmutableList<FileView> children) {
    this.breadcrumbs = Objects.requireNonNull(breadcrumbs);
    this.children = Objects.requireNonNull(children);
  }

  /**
   * A view of another file or directory within the same zip file.
   */
  @Immutable
  public static final class FileView {
    /**
     * The name of the file, such as "base.log".
     */
    public final String fileName;

    /**
     * The full path of the file, including the name of the zip file, such as
     * "results.2018-01-01-00-00-00-000.zip/gemini/build/base.log".
     */
    public final String fullPath;

    /**
     * The size of this file as a human-readable string, such as "4.5 kB", or
     * {@code null} if this is a directory.
     */
    @Nullable public final String size;

    /**
     * {@code true} if this is a directory.
     */
    public final boolean isDirectory;

    /**
     * {@code true} if this file or directory is the one currently being viewed
     * by the user.
     */
    public final boolean isSelected;

    public FileView(String fileName,
                    String fullPath,
                    @Nullable String size,
                    boolean isDirectory,
                    boolean isSelected) {
      this.fileName = Objects.requireNonNull(fileName);
      this.fullPath = Objects.requireNonNull(fullPath);
      this.size = size;
      this.isDirectory = isDirectory;
      this.isSelected = isSelected;
    }
  }
}
