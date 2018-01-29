package tfb.status.util;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static tfb.status.util.MoreStrings.linesOf;

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
        linesOf(""));

    assertIterableEquals(
        List.of("a"),
        linesOf("a"));

    assertIterableEquals(
        List.of(""),
        linesOf("\n"));

    assertIterableEquals(
        List.of("a"),
        linesOf("a\n"));

    assertIterableEquals(
        List.of("", "a"),
        linesOf("\na"));

    assertIterableEquals(
        List.of("", "a", "b", "c", ""),
        linesOf("\na\rb\nc\r\n\r"));
  }
}
