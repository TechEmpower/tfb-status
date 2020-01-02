package tfb.status.hk2.extensions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation indicating that additional service classes should be registered
 * when the annotated class is registered.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Registers {
  /**
   * The service classes to be registered.
   */
  Class<?>[] value();
}
