package tfb.status.handler.routing;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.inject.Qualifier;
import tfb.status.undertow.extensions.RequestValues;

/**
 * Indicates that the annotated service is an {@link HttpHandler} that handles
 * requests with the specified {@linkplain HttpServerExchange#getRequestMethod()
 * method} and {@linkplain HttpServerExchange#getRelativePath() path}.
 *
 * <p>A single handler may declare multiple routes to indicate that it accepts
 * requests matching any of those routes.
 *
 * <p>Multiple route declarations may share the same {@link #path()} as long as
 * they do not share the same {@link #method()}.
 *
 * <p>Support for OPTIONS requests is added automatically to all paths.  Support
 * for HEAD requests is added automatically to all paths that have a GET {@link
 * #method()} declared.  These default handlers can be overridden on a
 * path-specific basis by declaring a route whose {@link #method()} is OPTIONS
 * or HEAD.
 *
 * <p>The special {@link #method()} value {@code "*"} indicates that the
 * annotated handler will handle <em>all</em> methods, including OPTIONS and
 * HEAD.  In this case, no other route may use the same {@link #path()}.
 */
@Repeatable(Routes.class)
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({
    ElementType.TYPE,
    ElementType.METHOD,
    ElementType.FIELD,
    ElementType.PARAMETER
})
public @interface Route {
  /**
   * The specific HTTP method matched by this route, such as "GET" or "POST", or
   * "*" if this route matches all HTTP methods.
   */
  String method();

  /**
   * The {@linkplain HttpServerExchange#getRelativePath() request path} matched
   * by this route.
   *
   * <p>This path string is a template that may contain variables, as in {@code
   * /users/{userId}}.  This template may end with {@code /*} to indicate that
   * this route matches any number of trailing path parts; the template
   * {@code /users/{userId}/*} would match a request to the path {@code
   * /users/123/settings/email}, for example.  The values of these variables
   * &mdash; {@code userId} and {@code *} in the previous example &mdash; can be
   * read from a request using {@link
   * RequestValues#pathParameter(HttpServerExchange, String)}.
   */
  String path();
}
