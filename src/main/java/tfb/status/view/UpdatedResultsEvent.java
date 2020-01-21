package tfb.status.view;

import com.google.errorprone.annotations.Immutable;
import java.util.Objects;

/**
 * An event that fires when new {@link Results} are uploaded or existing results
 * are updated.
 */
@Immutable
public final class UpdatedResultsEvent {
  /**
   * The {@link Results#uuid} of the updated results.
   */
  public final String uuid;

  public UpdatedResultsEvent(String uuid) {
    this.uuid = Objects.requireNonNull(uuid);
  }
}
