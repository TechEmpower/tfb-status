package tfb.status.view;

import com.google.errorprone.annotations.Immutable;
import java.util.Objects;

/**
 * Represents a successful JSON response when a user uploads a result.json
 * file via {@link tfb.status.handler.ShareResultsHandler}. This contains
 * information about the file and how to view it.
 */
@Immutable
public class ShareResultsJsonView {
  public final String fileName;
  public final String resultsUrl;
  public final String visualizeResultsUrl;

  public ShareResultsJsonView(String fileName, String resultsUrl, String visualizeResultsUrl) {
    this.fileName = Objects.requireNonNull(fileName);
    this.resultsUrl = Objects.requireNonNull(resultsUrl);
    this.visualizeResultsUrl = Objects.requireNonNull(visualizeResultsUrl);
  }
}
