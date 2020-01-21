package tfb.status.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;
import javax.inject.Singleton;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * The configuration for miscellaneous files managed by this application.
 */
@Immutable
@Singleton
public final class FileStoreConfig {
  /**
   * The root directory for miscellaneous files managed by this application.
   */
  public final String root;

  public final long maxShareDirectorySizeBytes;

  public final long maxShareFileSizeBytes;

  @JsonCreator
  public FileStoreConfig(

      @JsonProperty(value = "root", required = false)
      @Nullable String root,

      @JsonProperty(value = "max_share_directory_size_bytes", required = false)
      @Nullable Long maxShareDirectorySizeBytes,

      @JsonProperty(value = "max_share_file_size_bytes", required = false)
      @Nullable Long maxShareFileSizeBytes) {

    this.root =
        Objects.requireNonNullElse(
            root,
            DEFAULT_ROOT);

    this.maxShareDirectorySizeBytes =
        Objects.requireNonNullElse(
            maxShareDirectorySizeBytes,
            DEFAULT_MAX_SHARE_DIRECTORY_SIZE_BYTES
        );

    this.maxShareFileSizeBytes =
        Objects.requireNonNullElse(
            maxShareFileSizeBytes,
            DEFAULT_MAX_SHARE_FILE_SIZE_BYTES
        );
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) {
      return true;
    } else if (!(object instanceof FileStoreConfig)) {
      return false;
    } else {
      FileStoreConfig that = (FileStoreConfig) object;
      return this.root.equals(that.root)
          && this.maxShareDirectorySizeBytes == that.maxShareDirectorySizeBytes
          && this.maxShareFileSizeBytes == that.maxShareFileSizeBytes;
    }
  }

  @Override
  public int hashCode() {
    int hash = 1;
    hash = 31 * hash + root.hashCode();
    hash = 31 * hash + Long.hashCode(maxShareDirectorySizeBytes);
    hash = 31 * hash + Long.hashCode(maxShareFileSizeBytes);
    return hash;
  }

  private static final String DEFAULT_ROOT = "managed_files";
  private static final long DEFAULT_MAX_SHARE_DIRECTORY_SIZE_BYTES = 1_000_000_000; // 1GB
  private static final long DEFAULT_MAX_SHARE_FILE_SIZE_BYTES = 5_000_000; // 5MB
}
