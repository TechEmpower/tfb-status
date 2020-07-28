package tfb.status.undertow.extensions;

import static java.util.Comparator.comparing;

import com.google.common.net.MediaType;
import com.google.common.primitives.Booleans;
import java.util.Comparator;

/**
 * Utilities for working with media types.
 */
final class MediaTypes {
  private MediaTypes() {
    throw new AssertionError("This class cannot be instantiated");
  }

  /**
   * Orders media types by increasing specificity.
   *
   * <p>For example, the following media types are ordered by increasing
   * specificity: <code>*&#47;*</code>, {@code text/*}, {@code text/plain},
   * {@code text/plain;charset=utf-8}, {@code
   * text/plain;charset=utf-8;format=flowed}.
   *
   * <p>More precisely, this comparator performs the following comparisons:
   *
   * <ul>
   * <li>First, media types are compared by whether they have a wildcard as
   *     their {@link MediaType#type()}, where the wildcard type
   *     <code>*&#47;*</code> is less specific than a non-wildcard type such as
   *     {@code text/*}.
   * <li>Then, media types are compared by whether they have a wildcard as their
   *     {@link MediaType#subtype()}, where a wildcard subtype such as {@code
   *     text/*} is less specific than a non-wildcard subtype such as {@code
   *     text/plain}.
   * <li>Finally, media types are compared by the total count of their {@link
   *     MediaType#parameters()}, where having fewer parameters is less specific
   *     than having more parameters.
   * </ul>
   */
  public static final Comparator<MediaType> SPECIFICITY_ORDER =
      comparing((MediaType mediaType) -> mediaType.type().equals("*"),
                Booleans.trueFirst())
          .thenComparing(mediaType -> mediaType.subtype().equals("*"),
                         Booleans.trueFirst())
          .thenComparingInt(mediaType -> mediaType.parameters().size());
}
