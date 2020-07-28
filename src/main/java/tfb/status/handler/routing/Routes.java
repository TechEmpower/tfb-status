package tfb.status.handler.routing;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.inject.Qualifier;

/**
 * Allows multiple {@link Route} annotations to be applied to one HTTP handler.
 *
 * <p>Do not use this annotation directly.  Use {@link Route} instead.
 *
 * @see Route
 */
@Qualifier
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({
    ElementType.TYPE,
    ElementType.METHOD,
    ElementType.FIELD,
    ElementType.PARAMETER
})
public @interface Routes {
  /**
   * The {@link Route} annotations for this HTTP handler.
   */
  Route[] value();
}
