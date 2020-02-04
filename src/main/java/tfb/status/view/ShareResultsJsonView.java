package tfb.status.view;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;

/**
 * Represents a successful JSON response when a user uploads a result.json
 * file via {@link tfb.status.handler.ShareResultsUploadHandler}.  This object
 * contains information about the file and how to view it.
 */
@Immutable
public final class ShareResultsJsonView {
  public final String fileName;
  public final String resultsUrl;
  public final String visualizeResultsUrl;

  @JsonCreator
  public ShareResultsJsonView(
      @JsonProperty(value = "fileName", required = true)
      String fileName,

      @JsonProperty(value = "resultsUrl", required = true)
      String resultsUrl,

      @JsonProperty(value = "visualizeResultsUrl", required = true)
      String visualizeResultsUrl) {

    this.fileName = Objects.requireNonNull(fileName);
    this.resultsUrl = Objects.requireNonNull(resultsUrl);
    this.visualizeResultsUrl = Objects.requireNonNull(visualizeResultsUrl);
  }
}
