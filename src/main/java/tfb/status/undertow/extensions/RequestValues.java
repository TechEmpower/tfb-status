package tfb.status.undertow.extensions;

import com.google.common.collect.Iterables;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.server.handlers.PathTemplateHandler;
import io.undertow.util.PathTemplateMatch;
import io.undertow.util.PathTemplateMatcher;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Utility methods for reading request values from an {@link HttpServerExchange}
 * instance.
 */
public final class RequestValues {
  private RequestValues() {
    throw new AssertionError("This class cannot be instantiated");
  }

  /**
   * Reads a query string parameter.
   *
   * @param exchange the HTTP request/response
   * @param parameterName the name of the query parameter
   * @return either the value of the query parameter or {@code null} if the
   *         request contains zero values or multiple values for the parameter
   */
  public static @Nullable String queryParameter(HttpServerExchange exchange,
                                                String parameterName) {
    Objects.requireNonNull(exchange);
    Objects.requireNonNull(parameterName);

    Deque<String> values = exchange.getQueryParameters().get(parameterName);

    if (values == null || values.size() != 1)
      return null;

    return Iterables.getOnlyElement(values);
  }

  /**
   * Reads a query string parameter as a primitive {@code int} in base 10.
   *
   * @param exchange the HTTP request/response
   * @param parameterName the name of the query parameter to be parsed
   * @param valueIfAbsent the default value to be returned if the request does
   *        not contain a value for the query parameter
   * @param valueIfMalformed the default value to be returned if the request
   *        contains a value for the query parameter but that value cannot be
   *        parsed as an integer, or if the request contains multiple values for
   *        the query parameter
   * @return either the parsed value or one of the default values
   */
  public static int queryParameterAsInt(HttpServerExchange exchange,
                                        String parameterName,
                                        int valueIfAbsent,
                                        int valueIfMalformed) {
    Objects.requireNonNull(exchange);
    Objects.requireNonNull(parameterName);

    Deque<String> values = exchange.getQueryParameters().get(parameterName);

    if (values == null || values.isEmpty())
      return valueIfAbsent;

    if (values.size() > 1)
      return valueIfMalformed;

    String onlyValue = Iterables.getOnlyElement(values);
    try {
      return Integer.parseInt(onlyValue);
    } catch (NumberFormatException ignored) {
      return valueIfMalformed;
    }
  }

  /**
   * Reads a path parameter from the request.
   *
   * <p>This assumes the request was processed by a {@link PathTemplateMatcher},
   * and the result of {@link PathTemplateMatcher#match(String)} on the {@link
   * HttpServerExchange#getRelativePath()} was attached to the request at {@link
   * PathTemplateMatch#ATTACHMENT_KEY}.  {@link RoutingHandler} and {@link
   * PathTemplateHandler} implement this behavior, for example.
   *
   * @param exchange the HTTP request/response
   * @param parameterName the name of the path parameter
   * @return the value of the path parameter with the specified name, or an
   *         empty optional if there is no path parameter with the specified
   *         name
   */
  public static Optional<String> pathParameter(HttpServerExchange exchange,
                                               String parameterName) {

    Objects.requireNonNull(exchange);
    Objects.requireNonNull(parameterName);

    PathTemplateMatch match =
        exchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY);

    if (match == null)
      return Optional.empty();

    String value = match.getParameters().get(parameterName);
    return Optional.ofNullable(value);
  }
}
