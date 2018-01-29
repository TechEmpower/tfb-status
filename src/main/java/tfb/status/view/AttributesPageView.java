package tfb.status.view;

import com.google.errorprone.annotations.Immutable;
import java.util.Objects;

/**
 * A view of the attributes page.
 */
@Immutable
public final class AttributesPageView {
  public final String attributes;
  public final String tests;
  public final String fileName;

  public AttributesPageView(String attributes, String tests, String fileName) {
    this.attributes = Objects.requireNonNull(attributes);
    this.tests = Objects.requireNonNull(tests);
    this.fileName = Objects.requireNonNull(fileName);
  }
}
