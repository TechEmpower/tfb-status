package tfb.status.view;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;
import javax.annotation.Nullable;

@Immutable
public final class DirectoryListingView {
  public final ImmutableList<FileView> parents;
  public final ImmutableList<FileView> children;

  public DirectoryListingView(ImmutableList<FileView> parents,
                              ImmutableList<FileView> children) {
    this.parents = Objects.requireNonNull(parents);
    this.children = Objects.requireNonNull(children);
  }

  @Immutable
  public static final class FileView {
    public final String fileName;
    public final String fullPath;
    @Nullable public final String size;
    public final boolean isDirectory;
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
