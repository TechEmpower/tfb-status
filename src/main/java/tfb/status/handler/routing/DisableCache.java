package tfb.status.handler.routing;

import io.undertow.server.handlers.DisableCacheHandler;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.inject.Qualifier;

/**
 * Indicates that responses from an HTTP handler must not be cached by clients.
 * Similar to {@link DisableCacheHandler}.
 *
 * <p>This annotation may only be applied to services that are annotated with at
 * least one {@link Route}.
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({
    ElementType.TYPE,
    ElementType.METHOD,
    ElementType.FIELD,
    ElementType.PARAMETER
})
public @interface DisableCache {}
