package tfb.status.service;

import static io.undertow.UndertowOptions.ENABLE_HTTP2;
import static io.undertow.UndertowOptions.RECORD_REQUEST_START_TIME;
import static io.undertow.UndertowOptions.SHUTDOWN_TIMEOUT;

import com.google.common.collect.Iterables;
import com.google.common.io.MoreFiles;
import com.google.common.reflect.TypeToken;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import io.undertow.Undertow;
import io.undertow.server.DefaultResponseListener;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.AttachmentHandler;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.DisableCacheHandler;
import io.undertow.server.handlers.GracefulShutdownHandler;
import io.undertow.server.handlers.SetHeaderHandler;
import io.undertow.server.handlers.accesslog.AccessLogHandler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.InetSocketAddress;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.net.ssl.SSLContext;
import org.glassfish.hk2.api.ActiveDescriptor;
import org.glassfish.hk2.api.Filter;
import org.glassfish.hk2.api.PerLookup;
import org.glassfish.hk2.api.PreDestroy;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.ServiceLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.config.HttpServerConfig;
import tfb.status.handler.routing.DisableCache;
import tfb.status.handler.routing.Route;
import tfb.status.handler.routing.Routes;
import tfb.status.handler.routing.SetHeader;
import tfb.status.handler.routing.SetHeaders;
import tfb.status.undertow.extensions.AcceptHandler;
import tfb.status.undertow.extensions.MediaTypeHandler;
import tfb.status.undertow.extensions.MethodHandler;
import tfb.status.undertow.extensions.PathPatternHandler;
import tfb.status.util.KeyStores;

/**
 * The HTTP server for this application.
 *
 * <p>This HTTP server does not start automatically.  Call {@link #start()} to
 * begin listening for incoming HTTP requests.
 */
@Singleton
public final class HttpServer implements PreDestroy {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final String serverInfo;
  private final RootHandler handler;

  @GuardedBy("this") private final Undertow server;
  @GuardedBy("this") private boolean isRunning;

  @Inject
  public HttpServer(HttpServerConfig config,
                    FileSystem fileSystem,
                    ServiceLocator locator) {

    Objects.requireNonNull(config);
    Objects.requireNonNull(fileSystem);
    Objects.requireNonNull(locator);

    handler = new RootHandler(config, locator);

    Undertow.Builder builder = Undertow.builder();
    builder.setHandler(handler);
    builder.setServerOption(RECORD_REQUEST_START_TIME, true);

    // Without this shutdown timeout, stopping this HTTP server would block the
    // current thread until all in-progress HTTP requests complete naturally.
    // That would mean a single misbehaving HTTP request could delay shutdown
    // indefinitely.
    builder.setServerOption(
        SHUTDOWN_TIMEOUT,
        config.forcefulShutdownTimeoutMillis);

    if (config.keyStore == null)
      builder.addHttpListener(config.port, config.host);

    else {
      Path keyStoreFile = fileSystem.getPath(config.keyStore.path);

      SSLContext sslContext =
          KeyStores.readServerSslContext(
              /* keyStoreBytes= */ MoreFiles.asByteSource(keyStoreFile),
              /* password= */ config.keyStore.password.toCharArray());

      builder.addHttpsListener(config.port, config.host, sslContext);
      builder.setServerOption(ENABLE_HTTP2, true);
    }

    serverInfo =
        "host=" + config.host
            + ", port=" + config.port
            + ", encrypted=" + (config.keyStore != null);

    server = builder.build();
  }

  @Override
  public void preDestroy() {
    stop();
  }

  /**
   * Starts this HTTP server if it is currently stopped.
   */
  public synchronized void start() {
    // TODO: Throw if this server was already stopped?
    if (isRunning) return;

    server.start();
    isRunning = true;
    logger.info("running [{}]", serverInfo);
  }

  /**
   * Stops this HTTP server if it is currently running.
   *
   * <p>It is not necessarily the case that all HTTP request-handling threads
   * have stopped when this method returns.  This method will wait
   * {@link HttpServerConfig#forcefulShutdownTimeoutMillis} for the threads to
   * stop, but if some threads are still running after that amount of time, this
   * method will return anyway.
   */
  public synchronized void stop() {
    if (!isRunning) return;

    // Blocks this thread for up to config.gracefulShutdownTimeoutMillis.
    handler.shutdown();

    // Blocks this thread for up to config.forcefulShutdownTimeoutMillis.
    server.stop();

    isRunning = false;
    logger.info("stopped [{}]", serverInfo);
  }

  /**
   * Returns the port number that has been assigned to this server.
   *
   * <p>When the {@linkplain HttpServerConfig#port configured port number} is
   * non-zero, the assigned port number will equal the configured port number.
   * Otherwise, when the configured port number is zero, the host system will
   * dynamically assign an ephemeral port for this server, and this method
   * returns that dynamically assigned port number.
   *
   * @throws IllegalStateException if this server is not running
   */
  public synchronized int assignedPort() {
    if (!isRunning)
      throw new IllegalStateException("This server is not running");

    Undertow.ListenerInfo listener =
        Iterables.getOnlyElement(server.getListenerInfo());

    InetSocketAddress address = (InetSocketAddress) listener.getAddress();

    return address.getPort();
  }

  /**
   * Handles every incoming HTTP request, routing to other handlers based on
   * method, path, and media type.
   *
   * <p>This handler provides the following features in addition to routing:
   *
   * <ul>
   * <li>Incoming HTTP requests are logged.
   * <li>Exceptions thrown from other handlers are logged.
   * <li>Incoming HTTP requests are {@linkplain
   *     HttpServerExchange#startBlocking() blocking}.  Other handlers are
   *     permitted to perform blocking operations such as {@link
   *     HttpServerExchange#getInputStream()} and {@link
   *     HttpServerExchange#getOutputStream()}.
   * <li>{@linkplain #shutdown() Shutdown} is handled gracefully.
   * </ul>
   */
  private static final class RootHandler implements HttpHandler {
    private final HttpServerConfig config;
    private final GracefulShutdownHandler shutdownHandler;
    private final HttpHandler delegateHandler;
    private final Logger logger = LoggerFactory.getLogger("http");

    RootHandler(HttpServerConfig config, ServiceLocator locator) {
      this.config = Objects.requireNonNull(config);
      Objects.requireNonNull(locator);

      HttpHandler handler = newRoutingHandler(locator);
      handler = shutdownHandler = new GracefulShutdownHandler(handler);
      handler = newAccessLoggingHandler(handler, logger);
      handler = new ExceptionLoggingHandler(handler, logger);
      handler = new BlockingHandler(handler);

      delegateHandler = handler;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
      delegateHandler.handleRequest(exchange);
    }

    /**
     * Shuts down this root handler, allowing already in-progress HTTP requests
     * to complete and rejecting new HTTP requests with {@code 503 Service
     * Unavailable}.
     *
     * <p>It is not necessarily the case that all HTTP requests have completed
     * or have been terminated when this method returns.  This method will wait
     * {@link HttpServerConfig#gracefulShutdownTimeoutMillis} for the requests
     * to complete naturally, but if the requests haven't completed after that
     * amount of time, this method will return anyway.
     */
    public void shutdown() {
      shutdownHandler.shutdown();
      boolean allRequestsComplete;
      try {
        allRequestsComplete =
            shutdownHandler.awaitShutdown(config.gracefulShutdownTimeoutMillis);
      } catch (InterruptedException e) {
        logger.warn(
            "Shutdown was interrupted before all in-progress HTTP requests "
                + "could complete",
            e);
        Thread.currentThread().interrupt();
        return;
      }
      if (!allRequestsComplete)
        logger.warn(
            "Not all in-progress HTTP requests could complete within a "
                + "reasonable amount of time; shutting down anyway");
    }
  }

  /**
   * An HTTP handler that forwards requests to the other handlers based on their
   * {@link Route} annotations.  Discovers the {@link Route}-annotated handlers
   * using a {@link ServiceLocator}.  Initializes those other handlers lazily,
   * instantiating each handler when a request matching that handler's {@link
   * Route} annotation is received.  Modifies outgoing responses according to
   * the handlers' {@link DisableCache} and {@link SetHeader} annotations.
   *
   * @throws InvalidRouteException if any of the discovered {@link Route}
   *         annotations are invalid
   */
  private static HttpHandler newRoutingHandler(ServiceLocator locator) {
    Objects.requireNonNull(locator);

    // path -> method -> consumes media type -> produces media type -> handler
    var pathMap = new HashMap<String, Map<String, Map<String, Map<String, HttpHandler>>>>();

    Filter routesFilter =
        descriptor ->
            descriptor.getQualifiers().contains(Route.class.getName())
                || descriptor.getQualifiers().contains(Routes.class.getName());

    for (ActiveDescriptor<?> nonReifiedDescriptor
        : locator.getDescriptors(routesFilter)) {

      ActiveDescriptor<?> untypedDescriptor =
          locator.reifyDescriptor(nonReifiedDescriptor);

      boolean isHttpHandler =
          untypedDescriptor
              .getContractTypes()
              .stream()
              .anyMatch(
                  type -> TypeToken.of(type).isSubtypeOf(HttpHandler.class));

      if (!isHttpHandler)
        throw new InvalidRouteException(
            "@"
                + Route.class.getSimpleName()
                + " annotation found on service with implementation class "
                + untypedDescriptor.getImplementationClass()
                + " where the service does not include a subtype of "
                + HttpHandler.class.getSimpleName()
                + " in its contracts; the service's contracts are: "
                + untypedDescriptor.getContractTypes());

      // This unchecked cast is safe because we confirmed that this descriptor
      // advertises some subtype of HttpHandler in its contracts, meaning it
      // must produce an instance that is some subtype of HttpHandler.
      @SuppressWarnings("unchecked")
      ActiveDescriptor<? extends HttpHandler> typedDescriptor =
          (ActiveDescriptor<? extends HttpHandler>) untypedDescriptor;

      var routes = new ArrayList<Route>();
      var setHeaders = new ArrayList<SetHeader>();
      DisableCache disableCache = null;

      for (Annotation annotation : typedDescriptor.getQualifierAnnotations()) {
        if (annotation.annotationType() == Route.class)
          routes.add((Route) annotation);
        else if (annotation.annotationType() == Routes.class)
          routes.addAll(Arrays.asList(((Routes) annotation).value()));
        else if (annotation.annotationType() == SetHeader.class)
          setHeaders.add((SetHeader) annotation);
        else if (annotation.annotationType() == SetHeaders.class)
          setHeaders.addAll(Arrays.asList(((SetHeaders) annotation).value()));
        else if (annotation.annotationType() == DisableCache.class)
          disableCache = (DisableCache) annotation;
      }

      if (routes.isEmpty())
        // This should only be possible when the handler is annotated with
        // @Routes({}).  Solution: don't do that.
        throw new InvalidRouteException(
            "@"
                + Routes.class.getSimpleName()
                + " annotation in implementation class "
                + untypedDescriptor.getImplementationClass()
                + " is empty; avoid using @"
                + Routes.class.getSimpleName()
                + " directly and use @"
                + Route.class.getSimpleName()
                + " instead");

      HttpHandler handler =
          new LazyHandler(locator, typedDescriptor);

      if (disableCache != null)
        handler = new DisableCacheHandler(handler);

      for (SetHeader setHeader : setHeaders)
        handler =
            new SetHeaderHandler(
                /* next= */ handler,
                /* header= */ setHeader.name(),
                /* value= */ setHeader.value());

      for (Route route : routes)
        pathMap
            .computeIfAbsent(route.path(), path -> new HashMap<>())
            .computeIfAbsent(route.method(), method -> new HashMap<>())
            .computeIfAbsent(route.consumes(), consumes -> new HashMap<>())
            .merge(
                route.produces(),
                new AttachmentHandler<>(Route.MATCHED_ROUTE, handler, route),
                (handler1, handler2) -> {
                  throw new InvalidRouteException(
                      "There are multiple @"
                          + Route.class.getSimpleName()
                          + " annotations with path \""
                          + route.path()
                          + "\", method \""
                          + route.method()
                          + "\", consumes \""
                          + route.consumes()
                          + "\", and produces \""
                          + route.produces()
                          + "\"; the combination of these fields must be "
                          + "globally unique");
                });
    }

    PathPatternHandler.Builder pathsBuilder = PathPatternHandler.builder();

    pathMap.forEach(
        (String path, Map<String, Map<String, Map<String, HttpHandler>>> methodMap) -> {
          MethodHandler.Builder methodsBuilder = MethodHandler.builder();

          methodMap.forEach(
              (String method, Map<String, Map<String, HttpHandler>> consumesMap) -> {
                MediaTypeHandler.Builder consumesBuilder = MediaTypeHandler.builder();

                consumesMap.forEach(
                    (String consumes, Map<String, HttpHandler> producesMap) -> {
                      AcceptHandler.Builder producesBuilder = AcceptHandler.builder();

                      producesMap.forEach(
                          (String produces, HttpHandler handler) -> {
                            producesBuilder.add(produces, handler);
                          });

                      AcceptHandler producesHandler = producesBuilder.build();
                      consumesBuilder.add(consumes, producesHandler);
                    });

                MediaTypeHandler consumesHandler = consumesBuilder.build();
                methodsBuilder.add(method, consumesHandler);
              });

          MethodHandler methodHandler = methodsBuilder.build();
          try {
            pathsBuilder.add(path, methodHandler);
          } catch (IllegalStateException e) {
            throw new InvalidRouteException(
                "@"
                    + Route.class.getSimpleName()
                    + " annotation with path \""
                    + path
                    + "\" conflicts with another "
                    + Route.class.getSimpleName()
                    + " annotation with a differently-spelled but functionally "
                    + "equivalent path; if they are meant to have the same "
                    + "path, then check that each path string uses the same "
                    + "spelling for each variable at each position",
                e);
          }
        });

    return pathsBuilder.build();
  }

  /**
   * An exception thrown from {@link #newRoutingHandler(ServiceLocator)} when a
   * particular {@link Route} annotation appears to be invalid.
   */
  private static final class InvalidRouteException
      extends RuntimeException {

    InvalidRouteException(String message) {
      super(Objects.requireNonNull(message));
    }

    InvalidRouteException(String message, Throwable cause) {
      super(Objects.requireNonNull(message),
            Objects.requireNonNull(cause));
    }

    private static final long serialVersionUID = 0;
  }

  /**
   * An HTTP handler that is lazily loaded from a {@link ServiceLocator}.  For
   * each incoming request, the handler will be obtained from its {@link
   * ServiceLocator#getServiceHandle(ActiveDescriptor)}, that handler will
   * handle the request, and then {@link ServiceHandle#close()} will be called
   * if the handler has {@link PerLookup} scope.
   */
  private static final class LazyHandler implements HttpHandler {
    private final ServiceLocator locator;
    private final ActiveDescriptor<? extends HttpHandler> descriptor;

    LazyHandler(ServiceLocator locator,
                ActiveDescriptor<? extends HttpHandler> descriptor) {

      this.locator = Objects.requireNonNull(locator);
      this.descriptor = Objects.requireNonNull(descriptor);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
      ServiceHandle<? extends HttpHandler> serviceHandle =
          locator.getServiceHandle(descriptor);

      if (descriptor.getScopeAnnotation() == PerLookup.class)
        exchange.addExchangeCompleteListener(
            new CloseServiceHandleListener(serviceHandle));

      HttpHandler handler = serviceHandle.getService();
      handler.handleRequest(exchange);
    }

    private static final class CloseServiceHandleListener
        implements ExchangeCompletionListener {

      private final ServiceHandle<?> serviceHandle;

      CloseServiceHandleListener(ServiceHandle<?> serviceHandle) {
        this.serviceHandle = Objects.requireNonNull(serviceHandle);
      }

      @Override
      public void exchangeEvent(HttpServerExchange exchange,
                                NextListener nextListener) {

        // TODO: What happens when serviceHandle.close() throws?
        try {
          serviceHandle.close();
        } finally {
          nextListener.proceed();
        }
      }
    }
  }

  /**
   * An HTTP handler that logs all incoming requests and that delegates to a
   * caller-supplied HTTP handler.
   */
  private static HttpHandler newAccessLoggingHandler(HttpHandler handler,
                                                     Logger logger) {
    Objects.requireNonNull(handler);
    Objects.requireNonNull(logger);

    String formatString =
        String.join(
            " :: ",
            "%{REQUEST_LINE}",
            "%{RESPONSE_CODE} %{RESPONSE_REASON_PHRASE}",
            "%{BYTES_SENT} bytes",
            "%{RESPONSE_TIME} ms");

    ClassLoader classLoader = HttpServer.class.getClassLoader();

    return new AccessLogHandler(
        /* next= */ handler,
        /* accessLogReceiver= */ message -> logger.info(message),
        /* formatString= */ formatString,
        /* classLoader= */ classLoader);
  }

  /**
   * An HTTP handler that ensures that <em>all</em> uncaught exceptions from a
   * caller-supplied HTTP handler are logged.
   *
   * <p>By default, Undertow only logs <em>some</em> uncaught exceptions.  In
   * particular, it does not log uncaught {@link IOException}s.  This class
   * fixes that problem.
   */
  // TODO: Figure out how to disable Undertow's default exception logging.
  private static final class ExceptionLoggingHandler implements HttpHandler {
    private final HttpHandler handler;
    private final ExchangeCompletionListener listener;

    ExceptionLoggingHandler(HttpHandler handler, Logger logger) {
      this.handler = Objects.requireNonNull(handler);
      this.listener = new ExceptionLoggingListener(logger);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
      exchange.addExchangeCompleteListener(listener);
      handler.handleRequest(exchange);
    }

    private static final class ExceptionLoggingListener
        implements ExchangeCompletionListener {

      private final Logger logger;

      ExceptionLoggingListener(Logger logger) {
        this.logger = Objects.requireNonNull(logger);
      }

      @Override
      public void exchangeEvent(HttpServerExchange exchange,
                                NextListener nextListener) {
        Throwable exception =
            exchange.getAttachment(DefaultResponseListener.EXCEPTION);

        if (exception != null)
          logger.error(
              "Uncaught exception from HTTP handler {} {}",
              exchange.getRequestMethod(),
              exchange.getRequestURI(),
              exception);

        nextListener.proceed();
      }
    }
  }
}
