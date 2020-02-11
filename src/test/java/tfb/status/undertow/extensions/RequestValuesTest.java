package tfb.status.undertow.extensions;

import static io.undertow.util.Headers.CONTENT_TYPE;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.common.net.MediaType;
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
  private static final int EXPECTED_VALUE_AS_INT = 7;
  private static final String EXPECTED_VALUE_AS_STRING = "7";
  private static final String UNPARSEABLE_VALUE = "ten";

  /**
   * Verifies that {@link
   * RequestValues#queryParameter(HttpServerExchange, String)} reads a present
   * value from the query string.
   */
  @Test
  public void testQueryParameter_happyPath() {
    var exchange = new HttpServerExchange(null);
    exchange.addQueryParam(PARAMETER_NAME, EXPECTED_VALUE_AS_STRING);
    assertEquals(
        EXPECTED_VALUE_AS_STRING,
        RequestValues.queryParameter(exchange, PARAMETER_NAME));
  }

  /**
   * Verifies that {@link
   * RequestValues#queryParameter(HttpServerExchange, String)} returns {@code
   * null} when there is no query parameter with the given name.
   */
  @Test
  public void testQueryParameter_nullWhenNoValue() {
    var exchange = new HttpServerExchange(null);
    assertNull(RequestValues.queryParameter(exchange, PARAMETER_NAME));
  }

  /**
   * Verifies that {@link
   * RequestValues#queryParameter(HttpServerExchange, String)} returns {@code
   * null} when the exchange has an empty collection of values for the query
   * parameter with the given name.
   *
   * <p>This is a slightly different scenario than when there is no value for
   * the parameter at all.  It may be that the only way to reach this state is
   * to manipulate the query parameter value collection directly.
   */
  @Test
  public void testQueryParameter_nullWhenEmptyValueCollection() {
    var exchange = new HttpServerExchange(null);
    exchange.getQueryParameters().put(PARAMETER_NAME, new ArrayDeque<>());
    assertNull(RequestValues.queryParameter(exchange, PARAMETER_NAME));
  }

  /**
   * Verifies that {@link
   * RequestValues#queryParameter(HttpServerExchange, String)} returns {@code
   * null} when there are two values for the given query parameter.
   */
  @Test
  public void testQueryParameter_nullWhenTwoValues() {
    var exchange = new HttpServerExchange(null);
    exchange.addQueryParam(PARAMETER_NAME, EXPECTED_VALUE_AS_STRING);
    exchange.addQueryParam(PARAMETER_NAME, EXPECTED_VALUE_AS_STRING);
    assertNull(RequestValues.queryParameter(exchange, PARAMETER_NAME));
  }

  /**
   * Verifies that {@link
   * RequestValues#queryParameterAsInt(HttpServerExchange, String, int, int)}
   * reads a present integer value from the query string.
   */
  @Test
  public void testQueryParameterAsInt_happyPath() {
    var exchange = new HttpServerExchange(null);
    exchange.addQueryParam(PARAMETER_NAME, EXPECTED_VALUE_AS_STRING);
    assertEquals(
        EXPECTED_VALUE_AS_INT,
        RequestValues.queryParameterAsInt(
            exchange, PARAMETER_NAME, VALUE_IF_ABSENT, VALUE_IF_MALFORMED));
  }

  /**
   * Verifies that {@link
   * RequestValues#queryParameterAsInt(HttpServerExchange, String, int, int)}
   * returns the default value when there is no query parameter with the given
   * name.
   */
  @Test
  public void testQueryParameterAsInt_defaultWhenNoValue() {
    var exchange = new HttpServerExchange(null);
    assertEquals(
        VALUE_IF_ABSENT,
        RequestValues.queryParameterAsInt(
            exchange, PARAMETER_NAME, VALUE_IF_ABSENT, VALUE_IF_MALFORMED));
  }

  /**
   * Verifies that {@link
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
    var exchange = new HttpServerExchange(null);
    exchange.getQueryParameters().put(PARAMETER_NAME, new ArrayDeque<>());
    assertEquals(
        VALUE_IF_ABSENT,
        RequestValues.queryParameterAsInt(
            exchange, PARAMETER_NAME, VALUE_IF_ABSENT, VALUE_IF_MALFORMED));
  }

  /**
   * Verifies that {@link
   * RequestValues#queryParameterAsInt(HttpServerExchange, String, int, int)}
   * returns the default value when there are two values for the given query
   * parameter.
   */
  @Test
  public void testQueryParameterAsInt_defaultWhenTwoValues() {
    var exchange = new HttpServerExchange(null);
    exchange.addQueryParam(PARAMETER_NAME, EXPECTED_VALUE_AS_STRING);
    exchange.addQueryParam(PARAMETER_NAME, EXPECTED_VALUE_AS_STRING);
    assertEquals(
        VALUE_IF_MALFORMED,
        RequestValues.queryParameterAsInt(
            exchange, PARAMETER_NAME, VALUE_IF_ABSENT, VALUE_IF_MALFORMED));
  }

  /**
   * Verifies that {@link
   * RequestValues#queryParameterAsInt(HttpServerExchange, String, int, int)}
   * returns the default value when there is one value for the given query
   * parameter but it cannot be parsed as an integer.
   */
  @Test
  public void testQueryParameterAsInt_defaultWhenUnparseableValue() {
    var exchange = new HttpServerExchange(null);
    exchange.addQueryParam(PARAMETER_NAME, UNPARSEABLE_VALUE);
    assertEquals(
        VALUE_IF_MALFORMED,
        RequestValues.queryParameterAsInt(
            exchange, PARAMETER_NAME, VALUE_IF_ABSENT, VALUE_IF_MALFORMED));
  }

  /**
   * Verifies that {@link RequestValues#detectMediaType(HttpServerExchange)}
   * returns {@link MediaType#ANY_TYPE} when the request has no {@code
   * Content-Type} header.
   */
  @Test
  public void testDetectMediaType_missingHeader() {
    var exchange = new HttpServerExchange(null);
    assertEquals(
        MediaType.ANY_TYPE,
        RequestValues.detectMediaType(exchange));
  }

  /**
   * Verifies that {@link RequestValues#detectMediaType(HttpServerExchange)}
   * returns {@link MediaType#ANY_TYPE} when the request has an invalid {@code
   * Content-Type} header.
   */
  @Test
  public void testDetectMediaType_invalidHeader() {
    var exchange = new HttpServerExchange(null);
    exchange.getRequestHeaders().put(CONTENT_TYPE, "invalid/content/type");
    assertEquals(
        MediaType.ANY_TYPE,
        RequestValues.detectMediaType(exchange));

  }

  /**
   * Verifies that {@link RequestValues#detectMediaType(HttpServerExchange)}
   * returns {@link MediaType#ANY_TYPE} when the request has a valid {@code
   * Content-Type} header.
   */
  @Test
  public void testDetectMediaType_validHeader() {
    var exchange = new HttpServerExchange(null);
    exchange.getRequestHeaders().put(
        CONTENT_TYPE,
        "valid/type; charset=us-ascii; p1=v1; p2=v2");
    assertEquals(
        MediaType.create("valid", "type")
                 .withCharset(US_ASCII)
                 .withParameter("p1", "v1")
                 .withParameter("p2", "v2"),
        RequestValues.detectMediaType(exchange));
  }
}
