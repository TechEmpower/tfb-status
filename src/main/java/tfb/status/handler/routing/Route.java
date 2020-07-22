package tfb.status.handler.routing;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
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
 * method}, {@linkplain HttpServerExchange#getRelativePath() path}, and media
 * type.
 *
 * <p>A single HTTP handler may be annotated with multiple routes to indicate
 * that it accepts requests matching any of those routes.
 *
 * <p>Multiple routes may refer to the same path as long as they refer to
 * different methods or media types.  It is an error to declare two identical
 * routes.
 *
 * <p>Support for OPTIONS requests is added automatically to all paths.  Support
 * for HEAD requests is added automatically to all paths that have a GET {@link
 * #method()} declared.  These default handlers can be overridden on a
 * path-specific basis by declaring a route whose {@link #method()} is OPTIONS
 * or HEAD.
 *
 * <p>Routing is implemented in this order:
 * <ol>
 * <li>By request path.  If there is no route with a matching {@link #path()},
 *     then the response is {@code 404 Not Found}.
 * <li>By request method.  If there is no route with that {@link #method()}, and
 *     the method is not one of the automatically-supported methods, then the
 *     response is {@code 405 Method Not Allowed}.
 * <li>By request media type, which is determined from the {@code Content-Type}
 *     header of the request.  If there is no route that {@link #consumes()}
 *     that media type, then the response is {@code 415 Unsupported Media Type}.
 * </ol>
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
   * The {@linkplain HttpServerExchange#getRequestMethod() request method}
   * matched by this route, such as GET or POST.
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

  /**
   * The request media type matched by this route.  The default value
   * <code>*&#47;*</code> matches all requests, including requests that do not
   * include a {@code Content-Type} header.
   */
  String consumes() default "*/*";

  /**
   * The key for an {@linkplain HttpServerExchange#getAttachment(AttachmentKey)
   * attachment} added to all requests that match some route, where the value of
   * the attachment is the matching {@link Route} annotation.
   *
   * <p>This may be useful when an HTTP handler annotated with multiple routes
   * should behave differently depending on which route is matched.
   */
  AttachmentKey<Route> MATCHED_ROUTE = AttachmentKey.create(Route.class);
}
