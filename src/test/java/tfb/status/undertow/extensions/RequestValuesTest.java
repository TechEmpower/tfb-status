package tfb.status.undertow.extensions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.undertow.server.HttpServerExchange;
import java.util.ArrayDeque;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link RequestValues}.
 */
public final class RequestValuesTest {
  private static final String PARAMETER_NAME = "foo";
  private static final int VALUE_IF_ABSENT = -11;
  private static final int VALUE_IF_MALFORMED = 62;
  private static final int EXPECTED_VALUE = 7;
  private static final String UNPARSEABLE_VALUE = "ten";

  /**
   * Tests that {@link
   * RequestValues#queryParameterAsInt(HttpServerExchange, String, int, int)}
   * reads a present integer value from the query string.
   */
  @Test
  public void testQueryParameterAsInt_happyPath() {
    HttpServerExchange exchange = new HttpServerExchange(null);
    exchange.addQueryParam(PARAMETER_NAME, String.valueOf(EXPECTED_VALUE));
    assertEquals(
        EXPECTED_VALUE,
        RequestValues.queryParameterAsInt(
            exchange, PARAMETER_NAME, VALUE_IF_ABSENT, VALUE_IF_MALFORMED));
  }

  /**
   * Tests that {@link
   * RequestValues#queryParameterAsInt(HttpServerExchange, String, int, int)}
   * returns the default value when there is no query parameter with the given
   * name.
   */
  @Test
  public void testQueryParameterAsInt_defaultWhenNoValue() {
    HttpServerExchange exchange = new HttpServerExchange(null);
    assertEquals(
        VALUE_IF_ABSENT,
        RequestValues.queryParameterAsInt(
            exchange, PARAMETER_NAME, VALUE_IF_ABSENT, VALUE_IF_MALFORMED));
  }

  /**
   * Tests that {@link
   * RequestValues#queryParameterAsInt(HttpServerExchange, String, int, int)}
   * returns the default value when the exchange has an empty collection of
   * values for the query parameter with the given name.
   *
   * <p>This is a slightly different scenario than when there is no value for
   * the parameter at all.  It may be that the only way to reach this state is
   * to manipulate the query parameter value collection directly.
   */
  @Test
  public void testQueryParameterAsInt_defaultWhenEmptyValueCollection() {
    HttpServerExchange exchange = new HttpServerExchange(null);
    exchange.getQueryParameters().put(PARAMETER_NAME, new ArrayDeque<>());
    assertEquals(
        VALUE_IF_ABSENT,
        RequestValues.queryParameterAsInt(
            exchange, PARAMETER_NAME, VALUE_IF_ABSENT, VALUE_IF_MALFORMED));
  }

  /**
   * Tests that {@link
   * RequestValues#queryParameterAsInt(HttpServerExchange, String, int, int)}
   * returns the default value when there are two values for the given query
   * parameter.
   */
  @Test
  public void testQueryParameterAsInt_defaultWhenTwoValues() {
    HttpServerExchange exchange = new HttpServerExchange(null);
    exchange.addQueryParam(PARAMETER_NAME, String.valueOf(EXPECTED_VALUE));
    exchange.addQueryParam(PARAMETER_NAME, String.valueOf(EXPECTED_VALUE));
    assertEquals(
        VALUE_IF_MALFORMED,
        RequestValues.queryParameterAsInt(
            exchange, PARAMETER_NAME, VALUE_IF_ABSENT, VALUE_IF_MALFORMED));
  }

  /**
   * Tests that {@link
   * RequestValues#queryParameterAsInt(HttpServerExchange, String, int, int)}
   * returns the default value when there is one value for the given query
   * parameter but it cannot be parsed as an integer.
   */
  @Test
  public void testQueryParameterAsInt_defaultWhenUnparseableValue() {
    HttpServerExchange exchange = new HttpServerExchange(null);
    exchange.addQueryParam(PARAMETER_NAME, UNPARSEABLE_VALUE);
    assertEquals(
        VALUE_IF_MALFORMED,
        RequestValues.queryParameterAsInt(
            exchange, PARAMETER_NAME, VALUE_IF_ABSENT, VALUE_IF_MALFORMED));
  }
}
