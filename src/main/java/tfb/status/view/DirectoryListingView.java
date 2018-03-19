package tfb.status.view;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;
import javax.annotation.Nullable;

@Immutable
public final class DirectoryListingView {
  public final String zipFileName;
  public final String directory;
  public final ImmutableList<FileView> files;

  public DirectoryListingView(String zipFileName,
                              String directory,
                              ImmutableList<FileView> files) {
    this.zipFileName = Objects.requireNonNull(zipFileName);
    this.directory = Objects.requireNonNull(directory);
    this.files = Objects.requireNonNull(files);
  }

  @Immutable
  public static final class FileView {
    public final String fileName;
    @Nullable public final String size;
    public final boolean isDirectory;

    public FileView(String fileName,
                    @Nullable String size,
                    boolean isDirectory) {
      this.fileName = Objects.requireNonNull(fileName);
      this.size = size;
      this.isDirectory = isDirectory;
    }
  }
}
