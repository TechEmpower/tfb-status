package tfb.status.view;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;

/**
 * A view of a results.json file that was successfully shared by a user.
 */
@Immutable
public final class ShareSuccess {
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
  public ShareSuccess(
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
