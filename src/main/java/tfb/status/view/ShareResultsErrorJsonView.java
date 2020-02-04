package tfb.status.view;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;

/**
 * Represents a JSON response to an unsuccessful request to share a results.json
 * file via {@link tfb.status.handler.ShareResultsUploadHandler}.
 */
@Immutable
public final class ShareResultsErrorJsonView {
  public final String message;

  @JsonCreator
  public ShareResultsErrorJsonView(
      @JsonProperty(value = "message", required = true)
      String message) {

    this.message = Objects.requireNonNull(message);
  }
}
