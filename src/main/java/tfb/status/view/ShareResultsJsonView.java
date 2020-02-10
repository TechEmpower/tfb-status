package tfb.status.view;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;

/**
 * Represents a successful JSON response when a user uploads a result.json file
 * via {@link tfb.status.handler.ShareResultsUploadHandler}.  This object
 * contains information about the file and how to view it.
 */
@Immutable
public final class ShareResultsJsonView {
  /**
   * The unique id for these shared results.
   */
  public final String shareId;

  /**
   * The absolute URL for viewing these shared results as JSON on this website.
   */
  public final String resultsUrl;

  /**
   * The absolute URL for visualizing these shared results on the TFB website.
   */
  public final String visualizeResultsUrl;

  @JsonCreator
  public ShareResultsJsonView(
      @JsonProperty(value = "shareId", required = true)
      String shareId,

      @JsonProperty(value = "resultsUrl", required = true)
      String resultsUrl,

      @JsonProperty(value = "visualizeResultsUrl", required = true)
      String visualizeResultsUrl) {

    this.shareId = Objects.requireNonNull(shareId);
    this.resultsUrl = Objects.requireNonNull(resultsUrl);
    this.visualizeResultsUrl = Objects.requireNonNull(visualizeResultsUrl);
  }
}
