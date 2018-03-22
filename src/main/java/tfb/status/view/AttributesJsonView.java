package tfb.status.view;

import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;

/**
 * A view of the attributes page in JSON format.
 */
@Immutable
public final class AttributesJsonView {
  public final ImmutableMap<Attribute, AttributeInfo> attributes;
  public final ImmutableMap<String, MinifiedTestDefinition> tests;

  public AttributesJsonView(ImmutableMap<Attribute, AttributeInfo> attributes,
                            ImmutableMap<String, MinifiedTestDefinition> tests) {
    this.attributes = Objects.requireNonNull(attributes);
    this.tests = Objects.requireNonNull(tests);
  }
}
