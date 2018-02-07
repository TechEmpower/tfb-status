package tfb.status.view;

import com.google.errorprone.annotations.Immutable;
import java.util.Objects;
import tfb.status.view.HomePageView.ResultsView;

/**
 * A view of the results detail page.
 */
@Immutable
public final class DetailPageView {
  public final ResultsView result;

  public DetailPageView(ResultsView result) {
    this.result = Objects.requireNonNull(result);
  }
}
