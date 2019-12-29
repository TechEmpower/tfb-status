package tfb.status.handler;

import static io.undertow.util.Methods.GET;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.server.handlers.resource.PathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.jvnet.hk2.annotations.ContractsProvided;
import tfb.status.config.AssetsConfig;
import tfb.status.undertow.extensions.DefaultToUtf8Handler;
import tfb.status.undertow.extensions.HttpHandlers;
import tfb.status.undertow.extensions.MethodHandler;

/**
 * Handles HTTP requests for static assets such as JavaScript and CSS files.
 * The files are loaded from either the class path or the file system, depending
 * on this application's {@linkplain AssetsConfig assets configuration}.
 */
@Singleton
@ContractsProvided(HttpHandler.class)
@PrefixPath("/assets")
public final class AssetsHandler implements HttpHandler {
  private final HttpHandler delegate;

  @Inject
  public AssetsHandler(AssetsConfig config, FileSystem fileSystem) {
    Objects.requireNonNull(config);
    Objects.requireNonNull(fileSystem);

    delegate =
        HttpHandlers.chain(
            newResourceHandler(config, fileSystem),
            handler -> new DefaultToUtf8Handler(handler),
            handler -> new MethodHandler().addMethod(GET, handler));
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    delegate.handleRequest(exchange);
  }

  private static HttpHandler newResourceHandler(AssetsConfig config,
                                                FileSystem fileSystem) {
    Objects.requireNonNull(config);
    Objects.requireNonNull(fileSystem);

    switch (config.mode) {
      case CLASS_PATH: {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        var resourceManager = new ClassPathResourceManager(classLoader, "assets");
        return new ResourceHandler(resourceManager);
      }
      case FILE_SYSTEM: {
        Path assetsRoot = fileSystem.getPath("src/main/resources/assets");
        var resourceManager = new PathResourceManager(assetsRoot);
        var resourceHandler = new ResourceHandler(resourceManager);
        resourceHandler.setCacheTime(0);
        return resourceHandler;
      }
    }

    throw new AssertionError("Unknown resource mode: " + config.mode);
  }
}
