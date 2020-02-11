package tfb.status.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import java.time.Duration;
import java.util.Objects;
import javax.inject.Singleton;
import org.checkerframework.checker.nullness.qual.Nullable;
import tfb.status.service.FileStore;

/**
 * The configuration for sharing results.json files.
 */
@Immutable
@Singleton
public final class SharingConfig {
  /**
   * The minimum number of seconds that must pass after sending one email before
   * sending another.  This is used to prevent accidental self-spam if there are
   * multiple requests to upload results when the share directory is full.
   */
  public final long minSecondsBetweenEmails;

  /**
   * The maximum size of the {@link FileStore#shareDirectory()} in bytes.
   */
  public final long maxDirectorySizeInBytes;

  /**
   * The maximum size of a single file (before zip compression) that can be
   * uploaded to the {@link FileStore#shareDirectory()} in bytes.
   */
  public final long maxFileSizeInBytes;

  /**
   * The <a href="https://url.spec.whatwg.org/#origin">origin</a> for this
   * application, containing the scheme and domain but no path.  Must not end
   * with a slash.
   */
  public final String tfbStatusOrigin;

  /**
   * The <a href="https://url.spec.whatwg.org/#origin">origin</a> for the main
   * TFB website, containing the scheme and domain but no path.  Must not end
   * with a slash.
   */
  public final String tfbWebsiteOrigin;

  public SharingConfig(long minSecondsBetweenEmails,
                       long maxDirectorySizeInBytes,
                       long maxFileSizeInBytes,
                       String tfbStatusOrigin,
                       String tfbWebsiteOrigin) {

    this.minSecondsBetweenEmails = minSecondsBetweenEmails;
    this.maxDirectorySizeInBytes = maxDirectorySizeInBytes;
    this.maxFileSizeInBytes = maxFileSizeInBytes;
    this.tfbStatusOrigin = Objects.requireNonNull(tfbStatusOrigin);
    this.tfbWebsiteOrigin = Objects.requireNonNull(tfbWebsiteOrigin);
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (object == this) {
      return true;
    } else if (!(object instanceof SharingConfig)) {
      return false;
    } else {
      SharingConfig that = (SharingConfig) object;
      return this.minSecondsBetweenEmails == that.minSecondsBetweenEmails
          && this.maxDirectorySizeInBytes == that.maxDirectorySizeInBytes
          && this.maxFileSizeInBytes == that.maxFileSizeInBytes
          && this.tfbStatusOrigin.equals(that.tfbStatusOrigin)
          && this.tfbWebsiteOrigin.equals(that.tfbWebsiteOrigin);
    }
  }

  @Override
  public int hashCode() {
    int hash = 1;
    hash = 31 * hash + Long.hashCode(minSecondsBetweenEmails);
    hash = 31 * hash + Long.hashCode(maxDirectorySizeInBytes);
    hash = 31 * hash + Long.hashCode(maxFileSizeInBytes);
    hash = 31 * hash + tfbStatusOrigin.hashCode();
    hash = 31 * hash + tfbWebsiteOrigin.hashCode();
    return hash;
  }

  @JsonCreator
  public static SharingConfig create(
      @JsonProperty(value = "minSecondsBetweenEmails", required = false)
      @Nullable Long minSecondsBetweenEmails,

      @JsonProperty(value = "maxDirectorySizeInBytes", required = false)
      @Nullable Long maxDirectorySizeInBytes,

      @JsonProperty(value = "maxFileSizeInBytes", required = false)
      @Nullable Long maxFileSizeInBytes,

      @JsonProperty(value = "tfbStatusOrigin", required = false)
      @Nullable String tfbStatusOrigin,

      @JsonProperty(value = "tfbWebsiteOrigin", required = false)
      @Nullable String tfbWebsiteOrigin) {

    return new SharingConfig(
        /* minSecondsBetweenEmails= */
        Objects.requireNonNullElse(
            minSecondsBetweenEmails,
            DEFAULT_MIN_SECONDS_BETWEEN_EMAILS),

        /* maxDirectorySizeInBytes= */
        Objects.requireNonNullElse(
            maxDirectorySizeInBytes,
            DEFAULT_MAX_DIRECTORY_SIZE_IN_BYTES),

        /* maxFileSizeInBytes= */
        Objects.requireNonNullElse(
            maxFileSizeInBytes,
            DEFAULT_MAX_FILE_SIZE_IN_BYTES),

        /* tfbStatusOrigin= */
        Objects.requireNonNullElse(
            tfbStatusOrigin,
            DEFAULT_TFB_STATUS_ORIGIN),

        /* tfbWebsiteOrigin= */
        Objects.requireNonNullElse(
            tfbWebsiteOrigin,
            DEFAULT_TFB_WEBSITE_ORIGIN));
  }

  public static SharingConfig defaultConfig() {
    return create(null, null, null, null, null);
  }

  private static final long DEFAULT_MIN_SECONDS_BETWEEN_EMAILS =
      Duration.ofDays(1).toSeconds();

  private static final long DEFAULT_MAX_DIRECTORY_SIZE_IN_BYTES =
      1_000_000_000; // 1GB

  private static final long DEFAULT_MAX_FILE_SIZE_IN_BYTES =
      5_000_000; // 5MB

  private static final String DEFAULT_TFB_STATUS_ORIGIN =
      "https://tfb-status.techempower.com";

  private static final String DEFAULT_TFB_WEBSITE_ORIGIN =
      "https://www.techempower.com";
}
