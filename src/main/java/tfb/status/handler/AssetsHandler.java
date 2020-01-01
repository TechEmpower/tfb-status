package tfb.status.handler;

import static io.undertow.util.Methods.GET;

import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.server.handlers.resource.PathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Objects;
import javax.inject.Singleton;
import tfb.status.config.AssetsConfig;
import tfb.status.hk2.extensions.Provides;
import tfb.status.undertow.extensions.DefaultToUtf8Handler;
import tfb.status.undertow.extensions.HttpHandlers;
import tfb.status.undertow.extensions.MethodHandler;

/**
 * Handles HTTP requests for static assets such as JavaScript and CSS files.
 * The files are loaded from either the class path or the file system, depending
 * on this application's {@linkplain AssetsConfig assets configuration}.
 */
public final class AssetsHandler {
  private AssetsHandler() {
    throw new AssertionError("This class cannot be instantiated");
  }

  @Provides
  @Singleton
  @PrefixPath("/assets")
  public static HttpHandler assetsHandler(AssetsConfig config,
                                          FileSystem fileSystem) {
    Objects.requireNonNull(config);
    Objects.requireNonNull(fileSystem);

    return HttpHandlers.chain(
        newResourceHandler(config, fileSystem),
        handler -> new DefaultToUtf8Handler(handler),
        handler -> new MethodHandler().addMethod(GET, handler));
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
