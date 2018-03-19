package tfb.status.util;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link MoreStrings}.
 */
public final class MoreStringsTest {
  @Test
  public void testLinesOf_empty() {
    assertIterableEquals(
        List.of(),
        MoreStrings.linesOf(""));
  }

  @Test
  public void testLinesOf_letter() {
    assertIterableEquals(
        List.of("a"),
        MoreStrings.linesOf("a"));
  }

  @Test
  public void testLinesOf_lf() {
    assertIterableEquals(
        List.of(""),
        MoreStrings.linesOf("\n"));
  }

  @Test
  public void testLinesOf_cr() {
    assertIterableEquals(
        List.of(""),
        MoreStrings.linesOf("\r"));
  }

  @Test
  public void testLinesOf_cr_lf() {
    assertIterableEquals(
        List.of(""),
        MoreStrings.linesOf("\r\n"));
  }

  @Test
  public void testLinesOf_lf_cr() {
    assertIterableEquals(
        List.of("", ""),
        MoreStrings.linesOf("\n\r"));
  }

  @Test
  public void testLinesOf_letter_lf() {
    assertIterableEquals(
        List.of("a"),
        MoreStrings.linesOf("a\n"));
  }

  @Test
  public void testLinesOf_lf_letter() {
    assertIterableEquals(
        List.of("", "a"),
        MoreStrings.linesOf("\na"));
  }

  @Test
  public void testLinesOf_complex() {
    assertIterableEquals(
        List.of("", "a", "b", "c", "", ""),
        MoreStrings.linesOf("\na\rb\nc\r\n\n\r"));
  }

  @Test
  public void testLinesOf_unrecognizedLineSeparators() {
    assertIterableEquals(
        List.of("\u2028\u2029\u0085"),
        MoreStrings.linesOf("\u2028\u2029\u0085"));
  }
}
