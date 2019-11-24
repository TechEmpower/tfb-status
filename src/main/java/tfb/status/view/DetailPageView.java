package tfb.status.view;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;
import tfb.status.view.HomePageView.ResultsView;

/**
 * A view of the results detail page.
 */
@Immutable
public final class DetailPageView {
  public final ResultsView result;

  @JsonCreator
  public DetailPageView(

      @JsonProperty(value = "result", required = true)
      ResultsView result) {

    this.result = Objects.requireNonNull(result);
  }
}
