package tfb.status.handler;

import io.undertow.server.HttpHandler;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that an {@link HttpHandler} implementation handles requests whose
 * paths are prefixed by the specified string.  The {@link RootHandler} will
 * forward matching requests to that handler.
 *
 * @see RootHandler
 * @see ExactPath
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PrefixPath {
  /**
   * The path prefix string to be matched.
   */
  String value();
}
