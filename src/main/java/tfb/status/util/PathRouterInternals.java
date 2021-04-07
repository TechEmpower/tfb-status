package tfb.status.util;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.Comparator.comparing;
import static java.util.Comparator.comparingInt;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.graph.Traverser;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import org.checkerframework.checker.nullness.qual.Nullable;
import tfb.status.util.PathRouter.Endpoint;
import tfb.status.util.PathRouter.MatchingEndpoint;

/**
 * Non-public classes and constants used by {@link PathRouter}, which can't
 * declare its own non-public classes and constants because it's an interface.
 */
final class PathRouterInternals {
  private PathRouterInternals() {
    throw new AssertionError("This class cannot be instantiated");
  }

  /**
   * The comparator used by {@link PathRouter.Builder#build()}.
   */
  static final Comparator<Endpoint<?>> DEFAULT_ENDPOINT_COMPARATOR =
      comparing((Endpoint<?> endpoint) -> endpoint.pathPattern(),
                PathPattern.SPECIFICITY_COMPARATOR
                    .reversed()
                    .thenComparing(PathPattern.MATCHES_SAME_PATHS_COMPARATOR));

  /**
   * The implementation of {@link MatchingEndpoint} returned by the default
   * {@link PathRouter} implementations.
   */
  private record Match<V>(PathPattern pathPattern,
                          V value,
                          ImmutableMap<String, String> variables)
      implements MatchingEndpoint<V> {

    Match {
      Objects.requireNonNull(pathPattern);
      Objects.requireNonNull(value);
      Objects.requireNonNull(variables);
    }

    Match<V> withVariables(ImmutableMap<String, String> variables) {
      Objects.requireNonNull(variables);

      return variables.equals(variables())
          ? this
          : new Match<>(pathPattern(), value(), variables);
    }
  }

  /**
   * The implementation of {@link PathRouter.Builder} returned by {@link
   * PathRouter#builder()}.
   */
  static final class DefaultRouterBuilder<V> implements PathRouter.Builder<V> {
    // TODO: Is it worthwhile to have this lock?
    //       It makes this builder thread-safe, which is unusual for builders.
    private final Object lock = new Object();

    // TODO: Let users customize this comparator, which detects conflicts?
    @GuardedBy("lock")
    private final TreeSet<PathPattern> pathPatterns =
        new TreeSet<>(PathPattern.MATCHES_SAME_PATHS_COMPARATOR);

    @GuardedBy("lock")
    private final ImmutableMap.Builder<String, Match<V>> exactMatches =
        ImmutableMap.builder();

    @GuardedBy("lock")
    private final List<Match<V>> prefixMatches = new ArrayList<>();

    @Override
    @CanIgnoreReturnValue
    public DefaultRouterBuilder<V> add(PathPattern pathPattern, V value) {
      Objects.requireNonNull(pathPattern);
      Objects.requireNonNull(value);

      synchronized (lock) {
        if (!pathPatterns.add(pathPattern)) {
          PathPattern conflictingKey =
              Iterables.getOnlyElement(
                  pathPatterns.subSet(
                      /* fromElement= */ pathPattern,
                      /* fromInclusive= */ true,
                      /* toElement= */ pathPattern,
                      /* toInclusive= */ true));

          if (pathPattern.equals(conflictingKey))
            throw new IllegalStateException(
                "There is already an endpoint whose path pattern is "
                    + pathPattern);
          else
            throw new IllegalStateException(
                "There is already an endpoint whose path pattern is "
                    + conflictingKey
                    + ", which conflicts with the specified path pattern "
                    + pathPattern);
        }

        Match<V> match = new Match<>(pathPattern, value, ImmutableMap.of());
        if (pathPattern.isLiteral())
          exactMatches.put(pathPattern.literalPrefix(), match);
        else
          prefixMatches.add(match);
      }

      return this;
    }

    @Override
    public PathRouter<V> build(Comparator<? super Endpoint<V>> comparator) {
      Objects.requireNonNull(comparator);

      ImmutableMap<String, Match<V>> exactMatches;
      ImmutableList<Match<V>> prefixMatches;

      synchronized (lock) {
        exactMatches = this.exactMatches.build();

        prefixMatches =
            ImmutableList.sortedCopyOf(comparator, this.prefixMatches);
      }

      return (prefixMatches.size() <= 64)
          ? new SmallTrieRouter<>(exactMatches, prefixMatches)
          : new LargeTrieRouter<>(exactMatches, prefixMatches);
    }
  }

  /**
   * An implementation of {@link PathRouter} returned by {@link
   * DefaultRouterBuilder#build(Comparator)} when there are 64 or fewer
   * endpoints containing variables.
   */
  private static final class SmallTrieRouter<V> implements PathRouter<V> {
    private final ImmutableMap<String, Match<V>> exactMatches;
    private final ImmutableList<Match<V>> prefixMatches;
    private final SmallTrie trie;

    SmallTrieRouter(ImmutableMap<String, Match<V>> exactMatches,
                    ImmutableList<Match<V>> prefixMatches) {

      this.exactMatches = Objects.requireNonNull(exactMatches);
      this.prefixMatches = Objects.requireNonNull(prefixMatches);
      this.trie = new SmallTrie(new TrieBuilder(prefixMatches));
    }

    @Override
    public @Nullable MatchingEndpoint<V> find(String path) {
      Objects.requireNonNull(path);

      Match<V> exactMatch = exactMatches.get(path);
      if (exactMatch != null)
        return exactMatch;

      for (long values = trie.values(path);
           values != 0;
           values -= Long.lowestOneBit(values)) {

        int i = Long.numberOfTrailingZeros(values);
        Match<V> prefixMatch = prefixMatches.get(i);

        PathPattern.MatchResult matchResult =
            prefixMatch.pathPattern().match(path);

        if (matchResult.matches())
          return prefixMatch.withVariables(matchResult.variables());
      }

      return null;
    }
  }

  /**
   * An implementation of {@link PathRouter} returned by {@link
   * DefaultRouterBuilder#build(Comparator)} when there are more than 64
   * endpoints containing variables.
   */
  private static final class LargeTrieRouter<V> implements PathRouter<V> {
    private final ImmutableMap<String, Match<V>> exactMatches;
    private final ImmutableList<Match<V>> prefixMatches;
    private final LargeTrie trie;

    LargeTrieRouter(ImmutableMap<String, Match<V>> exactMatches,
                    ImmutableList<Match<V>> prefixMatches) {

      this.exactMatches = Objects.requireNonNull(exactMatches);
      this.prefixMatches = Objects.requireNonNull(prefixMatches);
      this.trie = new LargeTrie(new TrieBuilder(prefixMatches));
    }

    @Override
    public @Nullable MatchingEndpoint<V> find(String path) {
      Objects.requireNonNull(path);

      Match<V> exactMatch = exactMatches.get(path);
      if (exactMatch != null)
        return exactMatch;

      BitSet values = trie.values(path);

      for (int i = values.nextSetBit(0);
           i != -1;
           i = values.nextSetBit(i + 1)) {

        Match<V> prefixMatch = prefixMatches.get(i);

        PathPattern.MatchResult matchResult =
            prefixMatch.pathPattern().match(path);

        if (matchResult.matches())
          return prefixMatch.withVariables(matchResult.variables());
      }

      return null;
    }
  }

  /**
   * An intermediate form of a prefix trie that used to construct a {@link
   * SmallTrie} or {@link LargeTrie}.
   */
  private static final class TrieBuilder {
    private final Node head = new Node(0);

    TrieBuilder(List<? extends Endpoint<?>> endpoints) {
      Objects.requireNonNull(endpoints);

      var entries = new ArrayList<PrefixAndValue>();

      for (int i = 0; i < endpoints.size(); i++) {
        Endpoint<?> endpoint = endpoints.get(i);
        PathPattern pathPattern = endpoint.pathPattern();

        if (pathPattern.isLiteral())
          throw new IllegalArgumentException(
              "Fully literal path pattern "
                  + pathPattern
                  + " passed to a function expecting only "
                  + "partially literal path patterns");

        entries.add(new PrefixAndValue(pathPattern.literalPrefix(), i));
      }

      // Insert the shortest prefixes first, preventing a situation where the
      // trie has to be reshuffled.  This allows us to throw an AssertionError
      // in that situation (see below) instead of having to handle it, which
      // would be complicated and inefficient.
      entries.sort(PREFIX_LENGTH_COMPARATOR);

      for (PrefixAndValue entry : entries) {
        String prefix = entry.prefix();
        int value = entry.value();

        Node node = head;

        while (true) {
          int remainingLength = prefix.length() - node.offset;

          if (remainingLength == 0) {
            node.values.set(value);
            break;
          }

          if (node.length == 0)
            node.length = remainingLength;

          else if (node.length > remainingLength)
            throw new AssertionError(
                "The prefixes should have been inserted in an order that "
                    + "prevents having to reshuffle this trie");

          // 0 < node.length <= remainingLength;

          int nextOffset = node.offset + node.length;
          String key = prefix.substring(node.offset, nextOffset);

          node =
              node.childNodes.computeIfAbsent(
                  key,
                  k -> new Node(nextOffset));
        }
      }
    }

    /**
     * Copies the nodes of this builder into a new trie, using the specified
     * constructor to construct each node of the new trie.
     *
     * @param <N> the type of nodes in the new trie
     * @param nodeConstructor a function accepting data for a node and returning
     *        a newly-constructed node with that data
     * @return the head node of the new trie
     */
    <N> N build(NodeConstructor<N> nodeConstructor) {
      Objects.requireNonNull(nodeConstructor);

      // Avoid recursion when copying child nodes, which would throw
      // StackOverflowException for very deep tries.  Build the trie from the
      // bottom up, keeping references to the nodes built along the way.

      var builderNodeIndexes = new HashMap<Node, Integer>();
      var constructedNodes = new ArrayList<N>();

      Traverser<Node> traverser =
          Traverser.forTree(node -> node.childNodes.values());

      for (Node builderNode : traverser.depthFirstPostOrder(head)) {
        ImmutableMap<Substring, N> childNodes =
            builderNode.childNodes
                .entrySet()
                .stream()
                .collect(
                    toImmutableMap(
                        entry -> {
                          String key = entry.getKey();
                          return new Substring(key);
                        },
                        entry -> {
                          Node child = entry.getValue();
                          int i = builderNodeIndexes.get(child);
                          return constructedNodes.get(i);
                        }));

        constructedNodes.add(
            nodeConstructor.newNode(
                builderNode.offset,
                builderNode.length,
                builderNode.values,
                childNodes));

        builderNodeIndexes.put(builderNode, builderNodeIndexes.size());
      }

      return constructedNodes.get(constructedNodes.size() - 1);
    }

    @FunctionalInterface
    interface NodeConstructor<N> {
      N newNode(int offset,
                int length,
                BitSet values,
                ImmutableMap<Substring, N> childNodes);
    }

    private static final class Node {
      final int offset;
      int length;
      final BitSet values = new BitSet();
      final Map<String, Node> childNodes = new LinkedHashMap<>();

      Node(int offset) {
        this.offset = offset;
      }
    }

    private record PrefixAndValue(String prefix, int value) {

      PrefixAndValue {
        Objects.requireNonNull(prefix);
      }
    }

    private static final Comparator<PrefixAndValue> PREFIX_LENGTH_COMPARATOR =
        comparing(
            entry -> entry.prefix(),
            comparingInt(prefix -> prefix.length()));
  }

  /**
   * Maps path prefixes to unique integer index values in {@link
   * SmallTrieRouter}.
   */
  private static final class SmallTrie {
    private final Node head;

    SmallTrie(TrieBuilder builder) {
      Objects.requireNonNull(builder);

      this.head =
          builder.build(
              (offset, length, values, childNodes) ->
                  new Node(
                      offset,
                      length,
                      toLong(values),
                      childNodes));
    }

    private static long toLong(BitSet values) {
      Objects.requireNonNull(values);
      long[] array = values.toLongArray();
      return switch (array.length) {
        case 0 -> 0L;
        case 1 -> array[0];
        default ->
            throw new IllegalArgumentException(
                "This trie can't hold values >= 64, and the argument "
                    + "contained the value "
                    + (values.length() - 1));
      };
    }

    /**
     * Finds all prefixes matching the specified path, and returns the set of
     * integer index values associated with those prefixes as a {@code long}
     * bitmask.
     */
    long values(String path) {
      Objects.requireNonNull(path);

      Node node = head;
      var key = new Substring(path);
      long values = 0;

      do {
        values |= node.values();
        if (node.length() == 0 || node.offset() + node.length() > key.capacity())
          break;
        key.setPosition(node.offset(), node.length());
        node = node.childNodes().get(key);
      } while (node != null);

      return values;
    }

    private record Node(int offset,
                        int length,
                        long values,
                        ImmutableMap<Substring, Node> childNodes) {
      Node {
        Objects.requireNonNull(childNodes);
      }
    }
  }

  /**
   * Maps path prefixes to unique integer index values in {@link
   * LargeTrieRouter}.
   */
  private static final class LargeTrie {
    private final Node head;

    LargeTrie(TrieBuilder builder) {
      Objects.requireNonNull(builder);

      this.head =
          builder.build(
              (offset, length, values, childNodes) ->
                  new Node(
                      offset,
                      length,
                      values.stream().toArray(),
                      childNodes));
    }

    /**
     * Finds all prefixes matching the specified path, and returns the set of
     * integer index values associated with those prefixes as a {@link BitSet}.
     */
    BitSet values(String path) {
      Objects.requireNonNull(path);

      Node node = head;
      var key = new Substring(path);
      var values = new BitSet();

      do {
        for (int i : node.values())
          values.set(i);
        if (node.length() == 0 || node.offset() + node.length() > key.capacity())
          break;
        key.setPosition(node.offset(), node.length());
        node = node.childNodes().get(key);
      } while (node != null);

      return values;
    }

    private record Node(int offset,
                        int length,
                        int[] values,
                        ImmutableMap<Substring, Node> childNodes) {

      Node {
        Objects.requireNonNull(values);
        Objects.requireNonNull(childNodes);
      }
    }
  }

  /**
   * Represents a substring.  Like {@link String#substring(int, int)}, but this
   * class is a view of the backing string instead of a copy, and this class is
   * {@linkplain #setPosition(int, int) mutable}.
   */
  private static final class Substring {
    // Q: Why use this class instead of String.substring?
    //
    // A: To improve performance by allocating fewer objects per request.
    //    Finding the values in the trie associated with a given request path
    //    may involve looking up multiple substrings of that path.  This class
    //    being mutable allows a single Substring instance to be allocated per
    //    request and then reused for each of those lookups.  If
    //    String.substring was used instead of this class, then each of those
    //    lookups would allocate a new String instance.

    private final String string;
    private int offset;
    private int length;

    Substring(String string) {
      this(string, 0, string.length());
    }

    Substring(String string, int offset, int length) {
      Objects.requireNonNull(string);
      Objects.checkFromIndexSize(offset, length, string.length());
      this.string = string;
      this.offset = offset;
      this.length = length;
    }

    /**
     * The length of the backing string.
     */
    int capacity() {
      return string.length();
    }

    /**
     * Mutates this instance to be a different substring of the same backing
     * string.
     */
    void setPosition(int offset, int length) {
      Objects.checkFromIndexSize(offset, length, string.length());
      this.offset = offset;
      this.length = length;
    }

    @Override
    public boolean equals(@Nullable Object object) {
      if (!(object instanceof Substring))
        return false;

      Substring that = (Substring) object;
      return this.string.regionMatches(
          this.offset, that.string, that.offset, length);
    }

    @Override
    public int hashCode() {
      // This doesn't need to be consistent with String.hashCode, so we can use
      // a slightly better hash algorithm that has fewer collisions for short
      // strings of ASCII characters.  See:
      // https://vanilla-java.github.io/2018/08/12/Why-do-I-think-Stringhash-Code-is-poor.html
      int hash = 0;
      for (int i = offset; i < offset + length; i++)
        hash = hash * 109 + string.charAt(i);

      return hash;
    }

    @Override
    public String toString() {
      return string.substring(offset, offset + length);
    }
  }
}
