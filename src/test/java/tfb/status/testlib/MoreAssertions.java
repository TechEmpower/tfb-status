package tfb.status.testlib;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import com.google.common.net.MediaType;
import java.io.BufferedReader;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Utility methods for making assertions in tests.
 */
public final class MoreAssertions {
  private MoreAssertions() {
    throw new AssertionError("This class cannot be instantiated");
  }

  /**
   * Asserts that the actual media type is {@linkplain MediaType#is(MediaType)
   * compatible} with the expected media type.
   */
  public static void assertMediaType(MediaType expected,
                                     @Nullable String actual) {
    Objects.requireNonNull(expected);

    if (actual == null)
      throw new AssertionError(
          "expected media type compatible with \""
              + expected
              + "\", actual value was null");

    MediaType parsed;
    try {
      parsed = MediaType.parse(actual);
    } catch (IllegalArgumentException e) {
      throw new AssertionError(
          "expected media type compatible with \""
              + expected
              + "\", actual value could not be parsed as a media type, was \""
              + actual
              + "\"",
          e);
    }

    if (!parsed.is(expected))
      throw new AssertionError(
          "expected media type compatible with \""
              + expected
              + "\", actual value was \""
              + actual
              + "\"");
  }

  /**
   * Asserts that the actual object is an instance of the expected class,
   * returns the object casted to that class.
   */
  public static <T> T assertInstanceOf(Class<T> expected,
                                       @Nullable Object actual) {
    Objects.requireNonNull(expected);

    if (actual == null)
      throw new AssertionError(
          "expected instance of "
              + expected
              + ", actual value was null");

    if (!expected.isInstance(actual))
      throw new AssertionError(
          "expected instance of "
              + expected
              + ", actual value was instance of "
              + actual.getClass());

    return expected.cast(actual);
  }

  /**
   * Asserts that the actual string contains the expected string.
   */
  public static void assertContains(String expected,
                                    @Nullable String actual) {
    Objects.requireNonNull(expected);

    if (actual == null)
      throw new AssertionError(
          "expected string to contain \""
              + expected
              + "\", actual value was null");

    if (!actual.contains(expected))
      throw new AssertionError(
          "expected string to contain \""
              + expected
              + "\", actual value was \""
              + actual
              + "\"");
  }

  /**
   * Asserts that the actual string starts with the expected string.
   */
  public static void assertStartsWith(String expected,
                                      @Nullable String actual) {
    Objects.requireNonNull(expected);

    if (actual == null)
      throw new AssertionError(
          "expected string to start with \""
              + expected
              + "\", actual value was null");

    if (!actual.startsWith(expected))
      throw new AssertionError(
          "expected string to start with \""
              + expected
              + "\", actual value was \""
              + actual
              + "\"");
  }

  /**
   * Asserts that the lines of the actual string are equal to the expected
   * lines, where "line" is defined by {@link BufferedReader#readLine()}.
   */
  public static void assertLinesEqual(List<String> expected,
                                      @Nullable String actual) {
    Objects.requireNonNull(expected);

    if (actual == null)
      throw new AssertionError(
          "expected string to contain "
              + expected.size()
              + " lines, actual value was null");

    assertIterableEquals(
        expected,
        actual.lines().collect(toList()));
  }

  /**
   * Asserts that the provided string is an HTML document.
   */
  public static void assertHtmlDocument(@Nullable String actual) {
    if (actual == null)
      throw new AssertionError(
          "expected string to be an HTML document, actual value was null");

    // TODO: Use an HTML parser?
    assertStartsWith("<!DOCTYPE html>", actual);
  }
}
