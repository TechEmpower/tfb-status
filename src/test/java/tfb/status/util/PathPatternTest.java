package tfb.status.util;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link PathPattern}.
 */
public final class PathPatternTest {
  /**
   * Verifies that {@link PathPattern#of(String)} accepts input strings
   * containing no variables, and the resulting path patterns only match paths
   * identical to the input.
   */
  @Test
  public void testLiteralPatterns() {
    List<String> literals =
        List.of(
            "",
            "/",
            "a",
            "/a",
            "/a/",
            "a.b",
            "/a.b",
            "a/b",
            "/a/b");

    for (String pattern : literals)
      for (String path : literals)
        if (pattern.equals(path))
          assertTrue(
              PathPattern.of(pattern).match(path).matches(),
              "Pattern " + pattern + " should match itself");
        else
          assertFalse(
              PathPattern.of(pattern).match(path).matches(),
              "Pattern " + pattern + " should not match " + path);
  }

  /**
   * Verifies that {@link PathPattern#of(String)} accepts input strings
   * containing variables, and the values of those variables can be retrieved
   * from {@link PathPattern.MatchResult#variables()}.
   */
  @Test
  public void testVariablePatterns() {
    assertFalse(PathPattern.of("{a}").match("").matches());
    assertFalse(PathPattern.of("{a}").match("/").matches());
    assertFalse(PathPattern.of("/{a}").match("/").matches());
    assertFalse(PathPattern.of("/{a}").match("//").matches());
    assertFalse(PathPattern.of("{a:.+}").match("").matches());
    assertFalse(PathPattern.of("{a:[\\d]+}").match("ab").matches());
    assertFalse(PathPattern.of("{a:[\\d]+}").match("1a").matches());
    assertFalse(PathPattern.of("{a:[\\d]+}").match("a1").matches());
    assertFalse(PathPattern.of("{a:[\\d]{3}}").match("12").matches());

    assertEquals(
        Map.of("a", "foo"),
        PathPattern.of("{a}").match("foo").variables());

    assertEquals(
        Map.of("a", "foo"),
        PathPattern.of("/{a}").match("/foo").variables());

    assertEquals(
        Map.of("a", "foo"),
        PathPattern.of("/{a}/b").match("/foo/b").variables());

    assertEquals(
        Map.of("a", "foo"),
        PathPattern.of("/b/{a}").match("/b/foo").variables());

    assertEquals(
        Map.of("a", "foo"),
        PathPattern.of("/{a}.b").match("/foo.b").variables());

    assertEquals(
        Map.of("a", "foo"),
        PathPattern.of("/b.{a}").match("/b.foo").variables());

    assertEquals(
        Map.of("a", "foo", "b", "bar"),
        PathPattern.of("/{a}/{b}").match("/foo/bar").variables());

    assertEquals(
        Map.of("a", "foo", "b", "bar"),
        PathPattern.of("/{a}.{b}").match("/foo.bar").variables());

    assertEquals(
        Map.of("a", ""),
        PathPattern.of("{a:.*}").match("").variables());

    assertEquals(
        Map.of("a", "foo/bar"),
        PathPattern.of("{a:.*}").match("foo/bar").variables());

    assertEquals(
        Map.of("a", "123"),
        PathPattern.of("{a:[\\d]+}").match("123").variables());

    assertEquals(
        Map.of("a", "123"),
        PathPattern.of("{a:[\\d]{3}}").match("123").variables());

    assertEquals(
        Map.of("a", "}{"),
        PathPattern.of("{a:[\\}\\{]+}").match("}{").variables());
  }

  /**
   * Verifies that {@link PathPattern#of(String)} throws {@link
   * IllegalArgumentException} for invalid inputs.
   */
  @Test
  public void testInvalidPatterns() {
    List<String> invalidInputs =
        List.of(
            "{missingClosingBrace",
            "{tooManyOpeningBraces:[{]}",
            "{tooManyClosingBraces:[}]}",
            "{invalid-variable-name}",
            "{invalidRegex:\\q}",
            "{duplicateVariable}/{duplicateVariable}");

    for (String input : invalidInputs)
      assertThrows(
          IllegalArgumentException.class,
          () -> PathPattern.of(input),
          "Parsing this input string should throw: " + input);
  }

  /**
   * Verifies that {@link PathPattern#SPECIFICITY_COMPARATOR} orders {@link
   * PathPattern} instances as advertised.
   */
  @Test
  public void testSpecificityComparator() {
    PathPattern a = PathPattern.of("/a/{x}/{y}");
    PathPattern b = PathPattern.of("/a/{x}/");
    PathPattern c = PathPattern.of("/a/");
    PathPattern d = PathPattern.of("/a/b/");

    assertEquals(
        List.of(a, b, c, d),
        List.of(d, c, b, a)
            .stream()
            .sorted(PathPattern.SPECIFICITY_COMPARATOR)
            .collect(toList()));
  }

  /**
   * Verifies that a path pattern variable can match text equal to literal text
   * that follows the variable.
   *
   * <p>An earlier implementation of {@link PathPattern} failed this test.
   */
  @Test
  public void testVariableContainsFollowingLiteral() {
    assertEquals(
        Map.of("filename", ".txt"),
        PathPattern.of("{filename}.txt").match(".txt.txt").variables());

    assertEquals(
        Map.of("filename", ".txt.txt"),
        PathPattern.of("{filename}.txt").match(".txt.txt.txt").variables());
  }

  /**
   * Verifies that the default variable value pattern is greedy, matching the
   * longest possible value.
   */
  @Test
  public void testVariablesAreGreedyByDefault() {
    // Note the explicit greedy quantifier in "b".
    assertEquals(
        Map.of("a", "1.2", "b", "3"),
        PathPattern.of("{a}.{b:.+}").match("1.2.3").variables());
  }

  /**
   * Verifies that a path pattern can contain two variables side by side.
   *
   * <p>An earlier implementation of {@link PathPattern} failed this test.
   */
  @Test
  public void testSideBySideVariables() {
    assertEquals(
        Map.of("countryCode", "us", "officeNumber", "123"),
        PathPattern.of("/offices/{countryCode:[a-z]{2}}{officeNumber}")
                   .match("/offices/us123")
                   .variables());
  }

  /**
   * Verifies that the value pattern of a path pattern variable can contain
   * capturing groups, and this does not interfere with the path pattern's
   * ability to extract the values of path variables.
   */
  @Test
  public void testVariableWithCapturingGroups() {
    assertEquals(
        Map.of(
            "a", "0",
            "b", "123",
            "c", "1233312323323",
            "d", "9"),
        PathPattern.of("/{a}/{b:(1(2(3)+)+)+}/{c:(?<named>1(2(3)+)+)+}/{d}")
                   .match("/0/123/1233312323323/9")
                   .variables());
  }

  /**
   * Verifies that the '{' character can be escaped in a path pattern as
   * <code>\{</code> to prevent it from being interpreted as the beginning of a
   * variable declaration.
   */
  @Test
  public void testBackslashEscapesForOpeningCurlyBraces() {
    assertTrue(PathPattern.of("/a\\{").match("/a{").matches());
    assertTrue(PathPattern.of("/a\\{b}").match("/a{b}").matches());
    assertTrue(PathPattern.of("/a\\{b}\\{c}").match("/a{b}{c}").matches());
    assertTrue(PathPattern.of("/a\\{b").match("/a{b").matches());
    assertTrue(PathPattern.of("/a\\{\\{").match("/a{{").matches());
    assertTrue(PathPattern.of("\\{").match("{").matches());
    assertTrue(PathPattern.of("\\{\\{").match("{{").matches());

    assertEquals(
        Map.of("a", "foo"),
        PathPattern.of("\\{{a}").match("{foo").variables());

    assertEquals(
        Map.of("a", "foo"),
        PathPattern.of("{a}\\{").match("foo{").variables());

    assertEquals(
        Map.of("a", "foo", "b", "bar"),
        PathPattern.of("\\{{a}\\{{b}").match("{foo{bar").variables());

    assertEquals(
        Map.of("a", "foo", "b", "bar"),
        PathPattern.of("{a}\\{{b}\\{").match("foo{bar{").variables());
  }

  /**
   * Verifies that a {@link PathPattern} may contain literal emoji characters
   * and may contain emoji in variable value patterns, and request paths may
   * contain emoji.
   */
  @Test
  public void testEmoji() {
    String seeNoEvil = "\uD83D\uDE48";
    String hearNoEvil = "\uD83D\uDE49";
    String speakNoEvil = "\uD83D\uDE4A";

    assertEquals(
        Map.of("a", seeNoEvil),
        PathPattern
            .of("{a}")
            .match(seeNoEvil)
            .variables());

    assertEquals(
        Map.of("a", seeNoEvil, "b", hearNoEvil),
        PathPattern
            .of("{a}/{b}")
            .match(seeNoEvil + "/" + hearNoEvil)
            .variables());

    assertEquals(
        Map.of("a", seeNoEvil, "b", hearNoEvil, "c", speakNoEvil),
        PathPattern
            .of("{a}/{b}/{c}")
            .match(seeNoEvil + "/" + hearNoEvil + "/" + speakNoEvil)
            .variables());

    assertEquals(
        Map.of("a", seeNoEvil),
        PathPattern
            .of("{a:[" + seeNoEvil + hearNoEvil + speakNoEvil + "]+}")
            .match(seeNoEvil)
            .variables());

    assertEquals(
        Map.of("a", seeNoEvil + hearNoEvil),
        PathPattern
            .of("{a:[" + seeNoEvil + hearNoEvil + speakNoEvil + "]+}")
            .match(seeNoEvil + hearNoEvil)
            .variables());

    assertEquals(
        Map.of("a", seeNoEvil + hearNoEvil + speakNoEvil),
        PathPattern
            .of("{a:[" + seeNoEvil + hearNoEvil + speakNoEvil + "]+}")
            .match(seeNoEvil + hearNoEvil + speakNoEvil)
            .variables());

    assertTrue(
        PathPattern
            .of(seeNoEvil)
            .match(seeNoEvil)
            .matches());

    assertEquals(
        Map.of("a", "x"),
        PathPattern
            .of(seeNoEvil + "{a}")
            .match(seeNoEvil + "x")
            .variables());
  }

  /**
   * Verifies that a path pattern variable name can contain non-ascii letters
   * and digits.
   */
  @Test
  public void testNonAsciiVariableNames() {
    List<String> variableNames = List.of("Ａ１", "字१");

    for (String variableName : variableNames) {
      PathPattern pathPattern;
      try {
        pathPattern = PathPattern.of("{" + variableName + "}");
      } catch (IllegalArgumentException e) {
        throw new AssertionError(
            "Variable name \"" + variableName + "\" should be valid",
            e);
      }

      assertEquals(
          Map.of(variableName, "hello"),
          pathPattern.match("hello").variables(),
          "Unexpected match result for variable \"" + variableName + "\"");
    }
  }

  /**
   * Verifies that a flag such as {@code (?i)} (enable case insensitive
   * matching) contained in the regular expression of a path pattern variable
   * does not affect matching outside of that variable.
   */
  @Test
  public void testFlagsDoNotEscapeVariables() {
    assertFalse(PathPattern.of("{a:(?i)[a]}b").match("AB").matches());
    assertFalse(PathPattern.of("{a:(?i)[a]}b").match("aB").matches());
    assertTrue(PathPattern.of("{a:(?i)[a]}b").match("Ab").matches());
    assertTrue(PathPattern.of("{a:(?i)[a]}b").match("ab").matches());
  }
}
