package tfb.status.view;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;
import tfb.status.config.FileStoreConfig;

/**
 * Represents a JSON response to an unsuccessful request to share a results.json
 * file via {@link tfb.status.handler.ShareResultsUploadHandler}.
 */
@Immutable
public final class ShareResultsErrorJsonView {
  /**
   * The kind of failure that occurred.
   */
  public final ErrorKind errorKind;

  /**
   * A message describing why the results could not be shared, which may be
   * displayed directly to the user.
   */
  public final String message;

  @JsonCreator
  public ShareResultsErrorJsonView(
      @JsonProperty(value = "errorKind", required = true)
      ErrorKind errorKind,

      @JsonProperty(value = "message", required = true)
      String message) {

    this.errorKind = Objects.requireNonNull(errorKind);
    this.message = Objects.requireNonNull(message);
  }

  /**
   * A kind of failure that prevents results from being shared.
   */
  public enum ErrorKind {
    /**
     * The results cannot be shared because the share directory has reached its
     * {@linkplain FileStoreConfig#maxShareDirectorySizeBytes maximum size}.
     */
    SHARE_DIRECTORY_FULL,

    /**
     * The results cannot be shared because the results.json file exceeds the
     * {@linkplain FileStoreConfig#maxShareFileSizeBytes maximum size} for
     * individual files.
     */
    FILE_TOO_LARGE,

    /**
     * The results cannot be shared because its {@link Results#testMetadata} is
     * {@code null} or empty.
     */
    MISSING_TEST_METADATA,

    /**
     * The results cannot be shared because the results.json file isn't a valid
     * JSON encoding of {@link Results}.
     */
    INVALID_JSON
  }
}
