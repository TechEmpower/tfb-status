package tfb.status.util;

import static java.util.Comparator.comparing;
import static java.util.Comparator.comparingInt;

import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A pattern that matches HTTP request paths.  {@link PathPattern} objects are
 * created from input strings using {@link PathPattern#of(String)}.  To
 * determine whether a path pattern matches a given request path, use {@link
 * PathPattern#match(String)} and {@link MatchResult#matches()}.
 *
 * <p>Path patterns may contain variables in the form of
 * <code>{variableName}</code> or <code>{variableName:valuePattern}</code>.
 *
 * <ul>
 * <li><code>{variableName}</code> - A variable matching one or more non-slash
 *     characters.  The path pattern <code>/users/{userId}/settings</code>
 *     matches the request paths {@code /users/123/settings} and {@code
 *     /users/abc/settings} but not {@code /users/123/settings/privacy}.
 * <li><code>{variableName:valuePattern}</code> - A variable matching values
 *     according to the regular expression {@code valuePattern}, which is
 *     compiled with {@link Pattern#compile(String)}.  The path pattern
 *     <code>/users/{userId:[\\d]+}/settings</code> matches {@code
 *     /users/123/settings} but not {@code /users/abc/settings}.  Variables of
 *     this form can match values containing slashes when the regular expression
 *     permits slashes.  The path pattern <code>/files/{filePath:.+}</code>
 *     matches {@code /files/a.txt} and {@code /files/subdirectory/b.txt}.
 * </ul>
 *
 * <p>Variable values can be retrieved from the {@link MatchResult} for a
 * request path with {@link MatchResult#variables()}.
 *
 * <p>Variable names must only contain letters, digits, '$', and '_', and they
 * must begin with a non-digit character.  The variable names in a given path
 * pattern must be unique within that pattern.
 *
 * <p>Leading and trailing slashes are not normalized in either {@link
 * PathPattern#of(String)} or {@link PathPattern#match(String)}.  The path
 * pattern {@code /hello} matches the request path {@code /hello}, but that path
 * pattern does not match {@code hello} or {@code /hello/}.
 */
@Immutable
public final class PathPattern {
  private final String source;
  private final Pattern pattern;
  private final ImmutableMap<String, Integer> variableNameToGroupNumber;
  private final int literalCharacterCount;
  private final boolean isLiteral;
  private final String literalPrefix;

  private PathPattern(String source,
                      Pattern pattern,
                      ImmutableMap<String, Integer> variableNameToGroupNumber,
                      int literalCharacterCount,
                      boolean isLiteral,
                      String literalPrefix) {

    this.source = Objects.requireNonNull(source);
    this.pattern = Objects.requireNonNull(pattern);
    this.variableNameToGroupNumber = Objects.requireNonNull(variableNameToGroupNumber);
    this.literalCharacterCount = literalCharacterCount;
    this.isLiteral = isLiteral;
    this.literalPrefix = Objects.requireNonNull(literalPrefix);
  }

  /**
   * Returns the path pattern represented by the specified input string.
   *
   * @param input the string representation of the path pattern
   * @throws IllegalArgumentException if the input string is not a valid
   *         representation of a path pattern
   */
  public static PathPattern of(String input) {
    Objects.requireNonNull(input);

    var tokens = new Tokens(input);

    // The index of the first character that has not been consumed.
    int consumedToIndex = 0;

    while (consumedToIndex < input.length()) {
      // Find the start of the next non-literal token.
      int fromIndex = input.indexOf('{', consumedToIndex);

      if (fromIndex == -1) {
        // There are no more non-literal tokens.
        tokens.addLiteral(consumedToIndex, input.length());
        break;
      }

      // Check if this '{' is escaped with a backslash, which would mean it is
      // part of a literal token.
      if (fromIndex > consumedToIndex && input.charAt(fromIndex - 1) == '\\') {
        tokens.addLiteral(consumedToIndex, fromIndex - 1);
        tokens.addLiteral(fromIndex, fromIndex + 1);
        consumedToIndex = fromIndex + 1;
        continue;
      }

      // Consume the literal characters that we skipped.
      tokens.addLiteral(consumedToIndex, fromIndex);

      // Consume the variable declaration.
      int toIndex = getVariableToIndex(input, fromIndex);
      tokens.addVariable(fromIndex, toIndex);
      consumedToIndex = toIndex;
    }

    return tokens.build();
  }

  // Returns the index *after* the closing '}' character after fromIndex, where
  // fromIndex is the index of the opening '{' character.
  private static int getVariableToIndex(String input, int fromIndex) {
    Objects.requireNonNull(input);
    Objects.checkIndex(fromIndex, input.length());

    int nestedOpeningBraces = 0;

    for (int i = fromIndex + 1; i < input.length(); i++) {
      switch (input.charAt(i)) {
        case '}' -> {
          if (nestedOpeningBraces == 0) return i + 1;
          nestedOpeningBraces--;
        }
        case '{' -> nestedOpeningBraces++;
        case '\\' -> i++; // Skip the next character.
        default -> {} // Do nothing.
      }
    }

    throw new IllegalArgumentException(
        "Invalid path pattern, "
            + "unclosed path variable declaration starting at index "
            + fromIndex
            + " in input string "
            + input);
  }

  /**
   * A growable list of literal and variable tokens extracted from a path
   * pattern string.  These are assembled into a {@link PathPattern} instance
   * using {@link #build()}.
   */
  private static final class Tokens {
    private final String input;
    private final List<Token> tokens = new ArrayList<>();

    Tokens(String input) {
      this.input = Objects.requireNonNull(input);
    }

    void addLiteral(int fromIndex, int toIndex) {
      Objects.checkFromToIndex(fromIndex, toIndex, input.length());
      if (toIndex == fromIndex) return;
      tokens.add(new Token(fromIndex, toIndex, TokenType.LITERAL));
    }

    void addVariable(int fromIndex, int toIndex) {
      Objects.checkFromToIndex(fromIndex, toIndex, input.length());
      tokens.add(new Token(fromIndex, toIndex, TokenType.VARIABLE));
    }

    PathPattern build() {
      // Maintaining insertion order is essential.
      var variableNameToGroupNumber = new LinkedHashMap<String, Integer>();
      int groupNumber = 1;

      int literalCharacterCount = 0;

      // Joins adjacent literal substrings.
      StringBuilder literal = null;

      var literalPrefix = new StringBuilder();
      boolean isLiteral = true;

      var pattern = new StringBuilder();

      // TODO: Are the starting '^' and trailing '$' necessary?
      //       If not necessary, are they beneficial?
      pattern.append("^");

      for (Token substring : tokens) {
        isLiteral = isLiteral && substring.type() == TokenType.LITERAL;

        if (literal != null && substring.type() != TokenType.LITERAL) {
          pattern.append(Pattern.quote(literal.toString()));
          literal = null;
        }

        int fromIndex = substring.fromIndex();
        int toIndex = substring.toIndex();

        // Use a switch expression for exhaustiveness even though we don't need
        // the yielded value.
        int ignored = switch (substring.type()) {
          case LITERAL -> {
            literalCharacterCount += toIndex - fromIndex;

            if (literal == null)
              literal = new StringBuilder();

            literal.append(input, fromIndex, toIndex);

            if (isLiteral)
              literalPrefix.append(input, fromIndex, toIndex);

            yield 1;
          }
          case VARIABLE -> {
            String variableString = input.substring(fromIndex, toIndex);

            Matcher variableMatcher =
                VARIABLE_DECLARATION_PATTERN.matcher(variableString);

            if (!variableMatcher.matches())
              throw new IllegalArgumentException(
                  "Invalid path pattern variable " + variableString);

            String variableName = variableMatcher.group("name");

            if (variableNameToGroupNumber.containsKey(variableName))
              throw new IllegalArgumentException(
                  "Invalid path pattern, "
                      + "duplicate variable name  "
                      + variableName
                      + " in input string "
                      + input);

            variableNameToGroupNumber.put(variableName, groupNumber);

            String valuePatternString = variableMatcher.group("valuePattern");
            Pattern valuePattern;
            int valuePatternGroupCount;

            if (valuePatternString == null) {
              valuePattern = DEFAULT_VARIABLE_VALUE_PATTERN;
              valuePatternGroupCount = 0;
            } else {
              valuePattern = Pattern.compile(valuePatternString);
              valuePatternGroupCount = valuePattern.matcher("").groupCount();
            }

            pattern.append("(")
                   .append(valuePattern)
                   .append(")");

            groupNumber += 1 + valuePatternGroupCount;

            yield 0;
          }
        };
      }

      if (literal != null)
        pattern.append(Pattern.quote(literal.toString()));

      pattern.append("$");

      return new PathPattern(
          input,
          Pattern.compile(pattern.toString()),
          ImmutableMap.copyOf(variableNameToGroupNumber),
          literalCharacterCount,
          isLiteral,
          literalPrefix.toString());
    }

    @Immutable
    private record Token(int fromIndex, int toIndex, TokenType type) {

      Token {
        Objects.requireNonNull(type);
      }
    }

    private enum TokenType { LITERAL, VARIABLE }

    // Matches {foo}, {bar:[\\d]+}, {baz:[a-z]{3}}, ...
    private static final Pattern VARIABLE_DECLARATION_PATTERN =
        Pattern.compile(
            "^\\{(?<name>[$_\\p{L}][$_\\p{LD}]*)(:(?<valuePattern>.+))?}$");

    // Matches one or more non-slash characters greedily.
    private static final Pattern DEFAULT_VARIABLE_VALUE_PATTERN =
        Pattern.compile("[^/]+");
  }

  /**
   * Returns the result of matching this path pattern against the specified
   * request path.
   *
   * @param path the request path to be matched
   */
  public MatchResult match(String path) {
    Objects.requireNonNull(path);

    Matcher matcher = pattern.matcher(path);
    if (!matcher.matches())
      return MatchResult.NEGATIVE;

    if (variableNameToGroupNumber.isEmpty())
      return MatchResult.POSITIVE_NO_VARIABLES;

    ImmutableMap.Builder<String, String> variables =
        ImmutableMap.builderWithExpectedSize(
            variableNameToGroupNumber.size());

    variableNameToGroupNumber.forEach(
        (String variableName, Integer groupNumber) -> {
          String variableValue = matcher.group(groupNumber);
          variables.put(variableName, variableValue);
        });

    return MatchResult.positive(variables.build());
  }

  /**
   * Returns the input string used as the argument to {@link
   * PathPattern#of(String)} to construct this path pattern.
   */
  public String source() {
    return source;
  }

  /**
   * Returns {@code true} if this path pattern contains no variables.
   */
  // TODO: Make this public?
  boolean isLiteral() {
    return isLiteral;
  }

  /**
   * Returns the literal characters in this path pattern preceding the first
   * variable.  If this path pattern starts with a variable, then this method
   * returns the empty string.  If this path pattern contains no variables, then
   * this method returns the literal request path that is matched by this path
   * pattern's {@link #match(String)} method.
   */
  // TODO: Make this public?
  String literalPrefix() {
    return literalPrefix;
  }

  /**
   * Orders path patterns by increasing specificity.
   *
   * <p>More precisely, this comparator performs the following comparisons:
   *
   * <ul>
   * <li>First, path patterns are compared by the number of variables they
   *     contain, where containing more variables is less specific than
   *     containing fewer variables.
   * <li>Then, path patterns are compared by the number of literal characters
   *     they contain, where containing fewer literal characters is less
   *     specific than containing more literal characters.  Literal characters
   *     are the characters in the path pattern {@link #source()} string that
   *     are not part of variable declarations.  The escape sequence
   *     <code>\{</code> is counted as one literal character.
   * </ul>
   */
  public static final Comparator<PathPattern> SPECIFICITY_COMPARATOR =
      comparingInt((PathPattern pattern) -> pattern.variableNameToGroupNumber.size())
          .reversed()
          .thenComparingInt((PathPattern pattern) -> pattern.literalCharacterCount);

  /**
   * A comparator that considers two path patterns equal when it is able to
   * determine that they match the same set of paths, and which imposes some
   * unspecified but consistent ordering otherwise.
   *
   * <p>For example, the path patterns <code>/users/{id}</code> and
   * <code>/users/{name}</code> are equal according to this comparator.  One
   * contains a variable named "id" and the other contains a variable named
   * "name", but those variables appear in the same position and match the same
   * values at that position, and the path patterns are identical otherwise.
   *
   * <p>This comparator should only be used to detect bugs.  In situations where
   * having two path patterns that match identical paths would be a mistake,
   * this comparator can help detect that mistake, but it is not guaranteed to
   * do so.
   *
   * <p>For example, the path patterns <code>/door/{name:[ab]}</code> and
   * <code>/door/{name:[ba]}</code> are <em>not equal</em> according to the
   * current implementation this comparator, even though those two path patterns
   * match the same set of paths &mdash; {@code /door/a} and {@code /door/b}.
   * The contract of this comparator allows its implementation to be improved in
   * the future such that it recognizes those path patterns (and others in
   * similar situations) as equal.
   */
  public static final Comparator<PathPattern> MATCHES_SAME_PATHS_COMPARATOR =
      comparing((PathPattern pattern) -> pattern.pattern.pattern());

  /**
   * Returns {@code true} if the specified object is a {@link PathPattern} whose
   * {@link #source()} is equal to this path pattern's.
   */
  @Override
  public boolean equals(@Nullable Object object) {
    return object instanceof PathPattern that
        && source.equals(that.source);
  }

  @Override
  public int hashCode() {
    return source.hashCode();
  }

  /**
   * Returns the {@link #source()} of this path pattern.
   */
  @Override
  public String toString() {
    return source;
  }

  /**
   * The result of matching a path pattern against a request path.
   */
  @Immutable
  public static final class MatchResult {
    private final boolean matches;
    private final ImmutableMap<String, String> variables;

    private MatchResult(boolean matches,
                        ImmutableMap<String, String> variables) {

      this.matches = matches;
      this.variables = Objects.requireNonNull(variables);
    }

    static MatchResult positive(ImmutableMap<String, String> variables) {
      Objects.requireNonNull(variables);

      if (variables.isEmpty())
        return POSITIVE_NO_VARIABLES;

      return new MatchResult(
          /* matches= */ true,
          /* variables= */ variables);
    }

    static final MatchResult NEGATIVE =
        new MatchResult(
            /* matches= */ false,
            /* variables= */ ImmutableMap.of());

    static final MatchResult POSITIVE_NO_VARIABLES =
        new MatchResult(
            /* matches= */ true,
            /* variables= */ ImmutableMap.of());

    /**
     * Returns {@code true} if the path pattern matches the request path.
     */
    public boolean matches() {
      return matches;
    }

    /**
     * When {@link #matches()} is {@code true}, returns the mapping of variable
     * names in the path pattern to their associated values in the request path.
     * The returned map is empty when {@link #matches()} is {@code false} or
     * when {@link #matches()} is {@code true} but the path pattern contains no
     * variables.
     */
    public ImmutableMap<String, String> variables() {
      return variables;
    }

    /**
     * Returns {@code true} if the specified object is a {@link MatchResult}
     * whose {@link #matches()} and {@link #variables()} are equal to this
     * result's.
     */
    @Override
    public boolean equals(@Nullable Object object) {
      return object instanceof MatchResult that
          && this.matches == that.matches
          && this.variables.equals(that.variables);
    }

    @Override
    public int hashCode() {
      int hash = 1;
      hash = 31 * hash + Boolean.hashCode(matches);
      hash = 31 * hash + variables.hashCode();
      return hash;
    }

    @Override
    public String toString() {
      return getClass().getSimpleName()
          + "[matches=" + matches + ", variables=" + variables + "]";
    }
  }
}
