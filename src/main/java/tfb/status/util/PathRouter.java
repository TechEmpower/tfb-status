package tfb.status.util;

import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Implements path-based routing.  A {@link PathRouter} is a set of {@linkplain
 * Endpoint endpoints}, mapping {@linkplain PathPattern path patterns} to
 * values.  The best matching endpoint for a given request path can be found
 * using {@link #find(String)}.
 *
 * <p>To begin construction of a new {@link PathRouter}, use {@link #builder()}.
 *
 * @param <V> the type of values associated with endpoints in this router
 */
public interface PathRouter<V> {
  /**
   * Returns the best matching endpoint for the specified request path, or
   * {@code null} if there is no matching endpoint.
   *
   * @param path the request path to be matched
   */
  @Nullable MatchingEndpoint<V> find(String path);

  /**
   * Returns all the matching endpoints for the specified request path ordered
   * from best match to worst.
   *
   * @param path the request path to be matched
   */
  Stream<MatchingEndpoint<V>> findAll(String path);

  /**
   * An endpoint mapping a {@link PathPattern} to a value.
   *
   * @param <V> the type of the value associated with this endpoint
   */
  interface Endpoint<V> {
    /**
     * The path pattern associated with this endpoint.
     */
    PathPattern pathPattern();

    /**
     * The value associated with this endpoint.
     */
    V value();
  }

  /**
   * An endpoint matching a request path; a positive match returned from {@link
   * PathRouter#find(String)}.
   */
  interface MatchingEndpoint<V> extends Endpoint<V> {
    /**
     * The mapping of variable names in this endpoint's path pattern to their
     * associated values in the request path.  See {@link
     * PathPattern.MatchResult#variables()}.
     */
    ImmutableMap<String, String> variables();
  }

  /**
   * Returns a new, initially-empty {@link Builder} instance.
   *
   * <p>The returned {@link Builder} uses {@link
   * PathPattern#MATCHES_SAME_PATHS_COMPARATOR} to detect duplicate path
   * patterns in its implementation of {@link Builder#add(PathPattern, Object)}.
   */
  static <V> Builder<V> builder() {
    return new PathRouterInternals.DefaultRouterBuilder<>();
  }

  /**
   * A mutable builder class used to construct {@link PathRouter} instances.
   *
   * @param <V> the type of values associated with endpoints in this builder
   */
  interface Builder<V> {
    /**
     * Shortcut for {@link #add(PathPattern, Object)}.
     *
     * @throws IllegalArgumentException if the input is not a valid path pattern
     *         (see {@link PathPattern#of(String)})
     */
    @CanIgnoreReturnValue
    default Builder<V> add(String pathPattern, V value) {
      Objects.requireNonNull(pathPattern);
      Objects.requireNonNull(value);

      return add(PathPattern.of(pathPattern), value);
    }

    /**
     * Adds an endpoint to this builder.
     *
     * @param pathPattern the path pattern associated with this endpoint
     * @param value the value associated with this endpoint
     * @return this {@link Builder} instance (for chaining)
     * @throws IllegalStateException if an existing endpoint has the same path
     *         pattern as this endpoint
     */
    @CanIgnoreReturnValue
    Builder<V> add(PathPattern pathPattern, V value);

    /**
     * Returns a new {@link PathRouter} instance containing the endpoints that
     * have been added to this {@link Builder}.  Subsequent modifications to
     * this {@link Builder} do not affect previously-returned {@link PathRouter}
     * instances.
     *
     * <p>In calls to {@link PathRouter#find(String)} where multiple endpoints
     * match the request path, the returned {@link PathRouter} selects the
     * endpoint with the {@linkplain PathPattern#SPECIFICITY_COMPARATOR most
     * specific} path pattern.
     */
    default PathRouter<V> build() {
      return build(PathRouterInternals.DEFAULT_ENDPOINT_COMPARATOR);
    }

    /**
     * Returns a new {@link PathRouter} instance containing the endpoints that
     * have been added to this {@link Builder}.  Subsequent modifications to
     * this {@link Builder} do not affect previously-returned {@link PathRouter}
     * instances.
     *
     * <p>In calls to {@link PathRouter#find(String)} where multiple endpoints
     * match the request path, the returned {@link PathRouter} selects the
     * <b>first</b> of the matching endpoints, where the ordering of endpoints
     * is determined by the specified comparator.  If any of the matching
     * endpoints is an exact literal match for the request path &mdash; the
     * matching endpoint's path pattern contains no variables &mdash; then that
     * endpoint is selected regardless of where the specified comparator ranks
     * that endpoint in comparison to the others.
     */
    PathRouter<V> build(Comparator<? super Endpoint<V>> comparator);
  }
}
