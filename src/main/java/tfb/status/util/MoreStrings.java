package tfb.status.util;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Objects;

/**
 * Utility methods for working with strings.
 */
public final class MoreStrings {
  private MoreStrings() {
    throw new AssertionError("This class cannot be instantiated");
  }

  /**
   * Returns the list of lines contained in the provided string, where "line" is
   * defined by {@link BufferedReader#readLine()}.
   */
  public static ImmutableList<String> linesOf(String string) {
    Objects.requireNonNull(string);
    try (BufferedReader reader = new BufferedReader(new StringReader(string))) {
      return reader.lines().collect(toImmutableList());
    } catch (IOException impossible) {
      throw new AssertionError("The string is in memory", impossible);
    }
  }
}
