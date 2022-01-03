package tfb.status.util;

import static java.util.Comparator.comparing;
import static java.util.Comparator.comparingInt;
import static java.util.Comparator.reverseOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link PathRouter}.
 */
public final class PathRouterTest {
  /**
   * Verifies that {@link PathRouter#find(String)} returns {@code null} for
   * every path when the router contains no endpoints.
   */
  @Test
  public void testNoPaths() {
    PathRouter<String> router = PathRouter.<String>builder().build();

    List<String> paths =
        List.of(
            "",
            "/",
            "/a",
            "/b",
            "/a.b",
            "/a/b",
            "/a/b/");

    for (String path : paths)
      assertNotFound(router, path);
  }

  /**
   * Verifies that {@link PathRouter#find(String)} never returns {@code null}
   * when the router contains an endpoint matching all paths.
   */
  @Test
  public void testAnyPath() {
    PathRouter<String> router =
        PathRouter
            .<String>builder()
            .add("{path:.*}", "foo")
            .build();

    List<String> paths =
        List.of(
            "",
            "/",
            "/a",
            "/b",
            "/a.b",
            "/a/b",
            "/a/b/");

    for (String path : paths)
      assertFound(router, path, "foo", Map.of("path", path));
  }

  /**
   * Verifies that {@link PathRouter#find(String)} chooses the most specific
   * matching endpoint when the path patterns for each endpoint are overlapping.
   */
  @Test
  public void testMostSpecificPath() {
    PathRouter<String> router =
        PathRouter
            .<String>builder()
            // Intentional ordering of .add(...) calls.
            .add(
                "/{a}/",
                "two_literals_one_variable")
            // Add a more specific path.
            .add(
                "/a/",
                "three_literals")
            // Add a less specific path.
            .add(
                "/{a}/{b}",
                "two_literals_two_variables")
            .build();

    assertFound(
        router,
        "/a/",
        "three_literals",
        Map.of());

    assertFound(
        router,
        "/x/",
        "two_literals_one_variable",
        Map.of("a", "x"));

    assertFound(
        router,
        "/x/y",
        "two_literals_two_variables",
        Map.of("a", "x", "b", "y"));
  }

  /**
   * Verifies that {@link PathRouter.Builder#add(String, Object)} throws an
   * exception for an already-added path.
   */
  @Test
  public void testDuplicatePathRejected() {
    PathRouter.Builder<String> builder =
        PathRouter
            .<String>builder()
            .add("/a/{b}", "foo")
            .add("/z/{y:[a-z]}", "bar");

    assertThrows(
        IllegalStateException.class,
        () -> builder.add("/a/{b}", "baz"));

    assertThrows(
        IllegalStateException.class,
        () -> builder.add("/a/{c}", "baz"));

    assertThrows(
        IllegalStateException.class,
        () -> builder.add("/z/{x:[a-z]}", "baz"));
  }

  /**
   * Verifies that a {@link PathRouter} applies variable value patterns
   * correctly when routing, and that the values of the path variables can be
   * retrieved using {@link PathRouter.MatchingEndpoint#variables()}.
   */
  @Test
  public void testPathVariables() {
    PathRouter<String> router =
        PathRouter
            .<String>builder()
            .add(
                "/{x}/{y:[a-z]+}/{z:.*}",
                "lowercase_letters")
            .add(
                "/{x}/{y:[A-Z]+}/{z:.*}",
                "capital_letters")
            .build();

    assertFound(
        router,
        "/foo/bar/baz",
        "lowercase_letters",
        Map.of("x", "foo", "y", "bar", "z", "baz"));

    assertFound(
        router,
        "/foo/bar/baz/qux",
        "lowercase_letters",
        Map.of("x", "foo", "y", "bar", "z", "baz/qux"));

    assertFound(
        router,
        "/foo/bar/",
        "lowercase_letters",
        Map.of("x", "foo", "y", "bar", "z", ""));

    assertFound(
        router,
        "/foo/BAR/baz",
        "capital_letters",
        Map.of("x", "foo", "y", "BAR", "z", "baz"));

    assertNotFound(router, "//bar/baz");
    assertNotFound(router, "/foo//baz");
    assertNotFound(router, "/foo/123/baz");
    assertNotFound(router, "/foo/bar");
  }

  /**
   * Verifies that a {@link PathRouter} can have an endpoint whose path pattern
   * is the literal empty path, and that a {@link PathRouter} can have an
   * endpoint whose path pattern starts with a non-literal element, meaning its
   * literal path prefix is the empty string.
   */
  @Test
  public void testEmptyPath() {
    PathRouter.Builder<String> builder =
        PathRouter
            .<String>builder()
            .add("{path:.*}", "any");

    PathRouter<String> router1 = builder.build();

    assertFound(router1, "", "any", Map.of("path", ""));
    assertFound(router1, "/", "any", Map.of("path", "/"));

    PathRouter<String> router2 =
        builder
            .add("", "empty")
            .build();

    assertFound(router2, "", "empty", Map.of());
    assertFound(router2, "/", "any", Map.of("path", "/"));
  }

  /**
   * Verifies that a {@link PathRouter} may contain endpoints whose path
   * patterns contain emoji, and request paths may contain emoji.
   */
  @Test
  public void testEmoji() {
    String seeNoEvil = "\uD83D\uDE48";
    String hearNoEvil = "\uD83D\uDE49";
    String speakNoEvil = "\uD83D\uDE4A";
    String leadingSurrogate = "\uD83D";

    PathRouter.Builder<String> builder =
        PathRouter
            .<String>builder()
            .add(seeNoEvil, "see_exact")
            .add(hearNoEvil, "hear_exact")
            .add(speakNoEvil, "speak_exact")
            .add(seeNoEvil + "{suffix:.*}", "see_prefix")
            .add(hearNoEvil + "{suffix:.*}", "hear_prefix")
            .add(speakNoEvil + "{suffix:.*}", "speak_prefix");

    PathRouter<String> router1 = builder.build();

    assertFound(router1, seeNoEvil, "see_exact", Map.of());
    assertFound(router1, hearNoEvil, "hear_exact", Map.of());
    assertFound(router1, speakNoEvil, "speak_exact", Map.of());
    assertFound(router1, seeNoEvil + "x", "see_prefix", Map.of("suffix", "x"));
    assertFound(router1, hearNoEvil + "x", "hear_prefix", Map.of("suffix", "x"));
    assertFound(router1, speakNoEvil + "x", "speak_prefix", Map.of("suffix", "x"));

    assertNull(router1.find(leadingSurrogate));
    assertNull(router1.find(leadingSurrogate + ".txt"));

    // Try to create a situation where the prefix trie has to split the emoji
    // strings down the middle of their surrogate pairs, and verify that nothing
    // bad happens as a result.

    PathRouter<String> router2 =
        builder
            .add(leadingSurrogate, "leading_exact")
            // This endpoint should cause the aforementioned split.
            .add(leadingSurrogate + "{suffix:.*}", "leading_prefix")
            .build();

    assertFound(router2, seeNoEvil, "see_exact", Map.of());
    assertFound(router2, hearNoEvil, "hear_exact", Map.of());
    assertFound(router2, speakNoEvil, "speak_exact", Map.of());
    assertFound(router2, seeNoEvil + "x", "see_prefix", Map.of("suffix", "x"));
    assertFound(router2, hearNoEvil + "x", "hear_prefix", Map.of("suffix", "x"));
    assertFound(router2, speakNoEvil + "x", "speak_prefix", Map.of("suffix", "x"));

    assertFound(router2, leadingSurrogate, "leading_exact", Map.of());
    assertFound(router2, leadingSurrogate + "x", "leading_prefix", Map.of("suffix", "x"));
  }

  /**
   * Verifies that having more than 64 endpoints with non-literal path patterns
   * (containing at least one variable) does not break routing.
   *
   * <p>As of this writing, a different {@link PathRouter} implementation is
   * used depending on whether there are more than 64 such endpoints.
   */
  @Test
  public void testMoreThan64PrefixPaths() {
    // Intentionally use overlapping prefixes, causing the prefix trie to be
    // very deep.  At the extreme end, make the trie deep enough that
    // StackOverflowException will be thrown if the implementation uses
    // recursion to copy the nodes of the trie builder into the trie.
    //
    // Use enough endpoints that performance problems in the trie builder are
    // obvious and punishing.
    //
    // Test near the maximum capacity of the smaller trie in case something bad
    // happens there.

    for (int endpointCount : List.of(63, 64, 65, 1000)) {
      PathRouter.Builder<String> builder = PathRouter.builder();

      for (int i = 0; i < endpointCount; i++)
        builder.add(
            "a".repeat(i) + "{suffix:.*}",
            "e" + endpointCount + "i" + i);

      PathRouter<String> router = builder.build();

      for (int i = 0; i < endpointCount; i++)
        assertFound(
            router,
            "a".repeat(i) + "b",
            "e" + endpointCount + "i" + i,
            Map.of("suffix", "b"));
    }
  }

  /**
   * Verifies that providing a custom {@link Comparator} to {@link
   * PathRouter.Builder#build(Comparator)} affects which endpoint is returned by
   * {@link PathRouter#find(String)} when multiple endpoints match the request
   * path.
   */
  @Test
  public void testCustomEndpointComparator() {
    PathRouter.Builder<Integer> builder =
        PathRouter
            .<Integer>builder()
            .add("{letter:[a-zA-Z]}x", 1)
            .add("{letters:[a-z]{2}}", 2);

    // The path "ax" matches both of these endpoints, but the default comparator
    // will choose the endpoint with a literal 'x' in its path pattern because
    // it is more specific than the other path pattern.  Verify this assumption
    // here.

    PathRouter<Integer> router1 = builder.build();

    assertFound(router1, "ax", 1, Map.of("letter", "a"));
    assertFound(router1, "Ax", 1, Map.of("letter", "A"));
    assertFound(router1, "ab", 2, Map.of("letters", "ab"));

    // Now, verify that a custom comparator can affect the result for the path
    // "ax".  The other two paths, "Ax" and "ab", only match one endpoint each,
    // so they should be unaffected by the comparator.

    PathRouter<Integer> router2 =
        builder.build(
            comparing(endpoint -> endpoint.value(),
                      reverseOrder()));

    assertFound(router2, "ax", 2, Map.of("letters", "ax"));
    assertFound(router2, "Ax", 1, Map.of("letter", "A"));
    assertFound(router2, "ab", 2, Map.of("letters", "ab"));
  }

  /**
   * Verifies that {@link PathRouter#findAll(String)} returns all the expected
   * endpoints in the correct order.
   */
  @Test
  public void testFindAll() {
    PathRouter<Integer> emptyRouter =
        PathRouter.<Integer>builder().build();

    PathRouter.Builder<Integer> routerBuilder =
        PathRouter.<Integer>builder()
                  // These are written in decreasing specificity, which is the
                  // default ordering.
                  .add("help.txt", 1)
                  .add("help.{extension}", 2)
                  .add("{filename}.txt", 3)
                  .add("{filename}.{extension}", 4);

    PathRouter<Integer> routerDefaultOrdering = routerBuilder.build();

    PathRouter<Integer> routerCustomOrdering =
        routerBuilder.build(
            // Order by integer value, descending.
            comparingInt((PathRouter.Endpoint<Integer> endpoint) -> endpoint.value())
                .reversed());

    assertFindAllResults(
        emptyRouter,
        "help.txt",
        List.of(),
        List.of());

    assertFindAllResults(
        routerDefaultOrdering,
        "not_found",
        List.of(),
        List.of());

    assertFindAllResults(
        routerDefaultOrdering,
        "help.txt",
        List.of(1, 2, 3, 4),
        List.of(
            Map.of(),
            Map.of("extension", "txt"),
            Map.of("filename", "help"),
            Map.of("filename", "help", "extension", "txt")));

    assertFindAllResults(
        routerDefaultOrdering,
        "help.pdf",
        List.of(2, 4),
        List.of(
            Map.of("extension", "pdf"),
            Map.of("filename", "help", "extension", "pdf")));

    assertFindAllResults(
        routerCustomOrdering,
        "help.txt",
        // The exact match is still first even though the custom comparator
        // considers it last.
        List.of(1, 4, 3, 2),
        List.of(
            Map.of(),
            Map.of("filename", "help", "extension", "txt"),
            Map.of("filename", "help"),
            Map.of("extension", "txt")));
  }

  private static <V> void assertFound(
      PathRouter<V> router,
      String path,
      V expectedValue,
      Map<String, String> expectedVariables) {

    Objects.requireNonNull(router);
    Objects.requireNonNull(path);
    Objects.requireNonNull(expectedValue);
    Objects.requireNonNull(expectedVariables);

    PathRouter.MatchingEndpoint<V> match = router.find(path);

    assertNotNull(
        match,
        "Expected endpoint for path "
            + path
            + " to be found, but the endpoint was not found");

    assertEquals(
        expectedValue,
        match.value(),
        "Expected endpoint value for path "
            + path
            + " to be "
            + expectedValue
            + ", but the actual value was " + match.value());

    assertEquals(
        expectedVariables,
        match.variables(),
        "Expected endpoint variables for path "
            + path
            + " to be "
            + expectedVariables
            + ", but the actual variables were " + match.variables());
  }

  private static <V> void assertNotFound(
      PathRouter<V> router,
      String path) {

    Objects.requireNonNull(router);
    Objects.requireNonNull(path);

    PathRouter.MatchingEndpoint<V> match = router.find(path);

    assertNull(
        match,
        "Expected no matching endpoint for path "
            + path
            + ", but instead found "
            + match);
  }

  private static <V> void assertFindAllResults(
      PathRouter<V> router,
      String path,
      List<V> expectedValueByIndex,
      List<Map<String, String>> expectedVariablesByIndex) {

    Objects.requireNonNull(router);
    Objects.requireNonNull(path);
    Objects.requireNonNull(expectedValueByIndex);
    Objects.requireNonNull(expectedVariablesByIndex);

    if (expectedValueByIndex.size() != expectedVariablesByIndex.size())
      throw new IllegalArgumentException(
          "The sizes of expectedValueByIndex and expectedVariablesByIndex "
              + "must match");

    List<PathRouter.MatchingEndpoint<V>> matches =
        router.findAll(path).toList();

    assertEquals(
        expectedValueByIndex.size(),
        matches.size(),
        "Expected to find "
            + expectedValueByIndex.size()
            + " matches for path "
            + path
            + ", but instead found "
            + matches.size()
            + " matches");

    for (int i = 0; i < matches.size(); i++) {
      PathRouter.MatchingEndpoint<V> match = matches.get(i);
      V expectedValue = expectedValueByIndex.get(i);
      Map<String, String> expectedVariables = expectedVariablesByIndex.get(i);

      assertEquals(
          expectedValue,
          match.value(),
          "Expected endpoint value for path "
              + path
              + " to be "
              + expectedValue
              + ", but the actual value was " + match.value());

      assertEquals(
          expectedVariables,
          match.variables(),
          "Expected endpoint variables for path "
              + path
              + " to be "
              + expectedVariables
              + ", but the actual variables were " + match.variables());
    }
  }
}
