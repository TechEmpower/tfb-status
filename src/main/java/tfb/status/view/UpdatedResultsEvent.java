package tfb.status.view;

import com.google.errorprone.annotations.Immutable;
import java.util.Objects;

/**
 * An event that fires when new {@link Results} are uploaded or existing results
 * are updated.
 *
 * @param uuid The {@link Results#uuid()} of the updated results.
 */
@Immutable
public record UpdatedResultsEvent(String uuid) {

  public UpdatedResultsEvent {
    Objects.requireNonNull(uuid);
  }
}
