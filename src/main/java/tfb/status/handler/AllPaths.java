package tfb.status.handler;

import io.undertow.server.HttpHandler;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.inject.Qualifier;

/**
 * Indicates that an HTTP handler handles requests to all paths, routing to
 * other handlers based on path.
 *
 * <p>This handler must advertise the {@link HttpHandler} contract.
 *
 * <p>Those other handlers must advertise the {@link HttpHandler} contract, and
 * they must be qualified with either {@link ExactPath} or {@link PrefixPath}.
 *
 * @see ExactPath
 * @see PrefixPath
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({
    ElementType.TYPE,
    ElementType.METHOD,
    ElementType.FIELD,
    ElementType.PARAMETER
})
public @interface AllPaths {}
