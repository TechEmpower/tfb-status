package tfb.status.view;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;

/**
 * A view of a results.json file that was successfully shared by a user.
 *
 * @param shareId The unique id for these shared results.
 * @param resultsUrl The absolute URL for viewing these shared results as JSON
 *        on this website.
 * @param visualizeResultsUrl The absolute URL for visualizing these shared
 *        results on the TFB website.
 */
@Immutable
public record ShareSuccess(
    @JsonProperty(value = "shareId", required = true)
    String shareId,

    @JsonProperty(value = "resultsUrl", required = true)
    String resultsUrl,

    @JsonProperty(value = "visualizeResultsUrl", required = true)
    String visualizeResultsUrl) {

  @JsonCreator
  public ShareSuccess {
    Objects.requireNonNull(shareId);
    Objects.requireNonNull(resultsUrl);
    Objects.requireNonNull(visualizeResultsUrl);
  }
}
