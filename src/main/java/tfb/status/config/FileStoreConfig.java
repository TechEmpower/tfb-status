package tfb.status.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;
import javax.inject.Singleton;
import org.checkerframework.checker.nullness.qual.Nullable;
import tfb.status.service.FileStore;

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

  /**
   * The maximum size of the {@link FileStore#shareDirectory()} in bytes.
   */
  // TODO: Move this to a ShareConfig.
  public final long maxShareDirectorySizeBytes;

  /**
   * The maximum size of a single file (before zip compression) that can be
   * uploaded to the {@link FileStore#shareDirectory()} in bytes.
   */
  // TODO: Move this to a ShareConfig.
  public final long maxShareFileSizeBytes;

  public FileStoreConfig(String root,
                         long maxShareDirectorySizeBytes,
                         long maxShareFileSizeBytes) {
    this.root = Objects.requireNonNull(root);
    this.maxShareDirectorySizeBytes = maxShareDirectorySizeBytes;
    this.maxShareFileSizeBytes = maxShareFileSizeBytes;
  }

  @Override
  public boolean equals(@Nullable Object object) {
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

  @JsonCreator
  public static FileStoreConfig create(
      @JsonProperty(value = "root", required = false)
      @Nullable String root,

      @JsonProperty(value = "maxShareDirectorySizeBytes", required = false)
      @Nullable Long maxShareDirectorySizeBytes,

      @JsonProperty(value = "maxShareFileSizeBytes", required = false)
      @Nullable Long maxShareFileSizeBytes) {

    return new FileStoreConfig(
        /* root= */
        Objects.requireNonNullElse(
            root,
            DEFAULT_ROOT),

        /* maxShareDirectorySizeBytes= */
        Objects.requireNonNullElse(
            maxShareDirectorySizeBytes,
            DEFAULT_MAX_SHARE_DIRECTORY_SIZE_BYTES),

        /* maxShareFileSizeBytes= */
        Objects.requireNonNullElse(
            maxShareFileSizeBytes,
            DEFAULT_MAX_SHARE_FILE_SIZE_BYTES));
  }

  public static FileStoreConfig defaultConfig() {
    return create(null, null, null);
  }

  private static final String DEFAULT_ROOT = "managed_files";
  private static final long DEFAULT_MAX_SHARE_DIRECTORY_SIZE_BYTES = 1_000_000_000; // 1GB
  private static final long DEFAULT_MAX_SHARE_FILE_SIZE_BYTES = 5_000_000; // 5MB
}
