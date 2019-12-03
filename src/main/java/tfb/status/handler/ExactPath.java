package tfb.status.handler;

import io.undertow.server.HttpHandler;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that an {@link HttpHandler} implementation handles requests whose
 * paths are equal to the specified string.  The {@link RootHandler} will
 * forward matching requests to that handler.
 *
 * @see RootHandler
 * @see PrefixPath
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ExactPath {
  /**
   * The exact path string to be matched.
   */
  String value();
}
