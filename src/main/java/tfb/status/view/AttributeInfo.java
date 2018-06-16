package tfb.status.view;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * An entry in the attribute definition for tfb_lookup.json.
 */
@Immutable
public final class AttributeInfo {
  /**
   * The minified code used in the test definitions.  These are listed in {@link
   * Attribute}.
   */
  public final String code;

  /**
   * The full list of possible values for this attribute.  These are copied from
   * the test metadata.
   */
  public final ImmutableList<String> list;

  /**
   * This field seems to be unused, and it's unclear what it's for.
   */
  public final ImmutableSet<String> v;

  @JsonCreator
  public AttributeInfo(

      @Nullable
      @JsonProperty(value="code", required = true)
      String code,

      @Nullable
      @JsonProperty(value="list", required = true)
      ImmutableList<String> list,

      @Nullable
      @JsonProperty(value="v", required = true)
      ImmutableSet<String> v) {

    this.code = Objects.requireNonNull(code);
    this.list = Objects.requireNonNull(list);
    this.v = Objects.requireNonNull(v);
  }
}
