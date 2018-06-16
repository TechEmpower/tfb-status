package tfb.status.view;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;

/**
 * A representation of the tfb_lookup.json file.
 */
@Immutable
public final class AttributeLookup {
  @JsonProperty("attributes")
  public final ImmutableMap<Attribute, AttributeInfo> attributes;

  @JsonProperty("tests")
  public final ImmutableMap<String, MinifiedTestDefinition> minifiedTests;

  @JsonCreator
  public AttributeLookup(

      @JsonProperty(value = "attributes", required = true)
      ImmutableMap<Attribute, AttributeInfo> attributes,

      @JsonProperty(value = "tests", required = true)
      ImmutableMap<String, MinifiedTestDefinition> minifiedTests) {

    this.attributes = Objects.requireNonNull(attributes);
    this.minifiedTests = Objects.requireNonNull(minifiedTests);
  }
}
