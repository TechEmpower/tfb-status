package tfb.status.handler;

import static io.undertow.util.Methods.GET;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.server.handlers.resource.PathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.server.handlers.resource.ResourceManager;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.inject.Inject;
import javax.inject.Singleton;
import tfb.status.config.AssetsConfig;
import tfb.status.undertow.extensions.DefaultToUtf8Handler;
import tfb.status.undertow.extensions.MethodHandler;

/**
 * Handles HTTP requests for static assets such as JavaScript and CSS files.
 * The files are loaded from either the class path or the file system, depending
 * on the application's {@linkplain AssetsConfig assets configuration}.
 */
@Singleton
public final class AssetsHandler implements HttpHandler {
  private final HttpHandler delegate;

  @Inject
  public AssetsHandler(AssetsConfig config) {
    HttpHandler handler = newConfiguredResourceHandler(config);
    handler = new DefaultToUtf8Handler(handler);
    handler = new MethodHandler().addMethod(GET, handler);
    delegate = handler;
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    delegate.handleRequest(exchange);
  }

  private static HttpHandler newConfiguredResourceHandler(AssetsConfig config) {
    switch (config.mode) {
      case CLASS_PATH: {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        ResourceManager resourceManager =
            new ClassPathResourceManager(classLoader, config.root);
        return new ResourceHandler(resourceManager);
      }
      case FILE_SYSTEM: {
        Path assetsRoot = Paths.get(config.root);
        ResourceManager resourceManager = new PathResourceManager(assetsRoot);
        ResourceHandler resourceHandler = new ResourceHandler(resourceManager);
        resourceHandler.setCacheTime(0);
        return resourceHandler;
      }
    }
    throw new AssertionError("Unknown resource mode: " + config.mode);
  }
}
