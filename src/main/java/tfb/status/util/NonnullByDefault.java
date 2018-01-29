package tfb.status.util;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.meta.TypeQualifierDefault;

/**
 * Declares the <em>intent</em> that all fields, method parameters, and method
 * return values declared in the annotated package cannot be {@code null}.
 * Individual elements that can be {@code null} must be annotated with
 * {@link Nullable}.
 *
 * <p>This annotation does not affect runtime behavior.  It is only a hint for
 * IDEs, other static analysis tools, and programmers.
 */
@Documented
@Nonnull
@Target(ElementType.PACKAGE)
@TypeQualifierDefault({
    ElementType.FIELD,
    ElementType.METHOD,
    ElementType.PARAMETER
})
@Retention(RetentionPolicy.RUNTIME)
public @interface NonnullByDefault {}
