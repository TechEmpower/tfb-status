package tfb.status.view;

import com.google.errorprone.annotations.Immutable;
import java.util.Objects;

/**
 * Represents a JSON response to an unsuccessful request to share a results.json
 * file via {@link tfb.status.handler.ShareResultsUploadHandler}.
 */
@Immutable
public class ShareResultsErrorJsonView {
  public final String message;

  public ShareResultsErrorJsonView(String message) {
    this.message = Objects.requireNonNull(message);
  }
}
