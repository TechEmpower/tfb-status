package tfb.status.util;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link MoreStrings}.
 */
public final class MoreStringsTest {
  /**
   * Tests that {@link MoreStrings#linesOf(String)} deals with various inputs as
   * expected.
   */
  @Test
  public void testLinesOf() {
    assertIterableEquals(
        List.of(),
        MoreStrings.linesOf(""));

    assertIterableEquals(
        List.of("a"),
        MoreStrings.linesOf("a"));

    assertIterableEquals(
        List.of(""),
        MoreStrings.linesOf("\n"));

    assertIterableEquals(
        List.of("a"),
        MoreStrings.linesOf("a\n"));

    assertIterableEquals(
        List.of("", "a"),
        MoreStrings.linesOf("\na"));

    assertIterableEquals(
        List.of("", "a", "b", "c", ""),
        MoreStrings.linesOf("\na\rb\nc\r\n\r"));
  }
}
