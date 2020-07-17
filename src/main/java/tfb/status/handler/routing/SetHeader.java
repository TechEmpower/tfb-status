package tfb.status.handler.routing;

import io.undertow.server.handlers.SetHeaderHandler;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.inject.Qualifier;

/**
 * Indicates that responses from an HTTP handler will include the specified
 * response header.  Similar to {@link SetHeaderHandler}.
 *
 * <p>This annotation may only be applied to services that are annotated with at
 * least one {@link Route}.
 */
@Repeatable(SetHeaders.class)
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({
    ElementType.TYPE,
    ElementType.METHOD,
    ElementType.FIELD,
    ElementType.PARAMETER
})
public @interface SetHeader {
  /**
   * The name of the HTTP header to be included in the response.
   */
  String name();

  /**
   * The value of the HTTP header to be included in the response.
   */
  String value();
}
