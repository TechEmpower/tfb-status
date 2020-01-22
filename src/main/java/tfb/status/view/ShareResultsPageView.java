package tfb.status.view;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import java.util.List;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A view to hold information about how to render the share results upload page.
 * Tracks the state of the upload form as well as any upload errors or successes.
 */
@Immutable
public class ShareResultsPageView {
  @Nullable public final String shareType;
  @Nullable public final String pasteResultsJson;
  @Nullable public final ImmutableList<String> errors;
  @Nullable public final ShareResultsJsonView success;
  /**
   * Contains either no entries, indicating that no shareType is selected,
   * or contains a single entry mapping the selected shareType to the value
   * "checked". This allows mustache template to simply render the radio
   * button as checked without having to do any if checks.
   */
  public final ImmutableMap<String, String> shareTypeValues;

  public ShareResultsPageView() {
    this(null, null, null, null);
  }

  public ShareResultsPageView(String error) {
    this(null, null, ImmutableList.of(Objects.requireNonNull(error)), null);
  }

  public ShareResultsPageView(@Nullable String shareType,
                              @Nullable String pasteResultsJson,
                              @Nullable List<String> errors,
                              @Nullable ShareResultsJsonView success) {
    this.shareType = shareType;
    this.pasteResultsJson = pasteResultsJson;
    this.errors = errors == null
        ? ImmutableList.of()
        : ImmutableList.copyOf(errors);
    this.success = success;

    shareTypeValues = shareType == null
        ? ImmutableMap.of()
        : ImmutableMap.of(shareType, "checked");
  }
}
