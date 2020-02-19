package tfb.status.handler;

import static com.google.common.net.MediaType.ANY_TEXT_TYPE;
import static io.undertow.util.Methods.GET;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableSet;
import com.google.common.net.MediaType;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.server.handlers.resource.PathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.util.MimeMappings;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import javax.inject.Singleton;
import tfb.status.config.AssetsConfig;
import tfb.status.handler.routing.PrefixPath;
import tfb.status.hk2.extensions.Provides;
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
        var resourceHandler = new ResourceHandler(resourceManager);
        resourceHandler.setMimeMappings(newMimeMappings());
        return resourceHandler;
      }
      case FILE_SYSTEM: {
        Path assetsRoot = fileSystem.getPath("src/main/resources/assets");
        var resourceManager = new PathResourceManager(assetsRoot);
        var resourceHandler = new ResourceHandler(resourceManager);
        resourceHandler.setMimeMappings(newMimeMappings());
        resourceHandler.setCacheTime(0);
        return resourceHandler;
      }
    }

    throw new AssertionError("Unknown resource mode: " + config.mode);
  }

  private static MimeMappings newMimeMappings() {
    MimeMappings.Builder builder = MimeMappings.builder(false);

    // Use the default mappings, but add "charset=utf-8" to all text types.
    for (Map.Entry<String, String> entry
        : MimeMappings.DEFAULT_MIME_MAPPINGS.entrySet()) {

      MediaType mediaType = MediaType.parse(entry.getValue());
      if (!mediaType.charset().isPresent() && isTextType(mediaType))
        mediaType = mediaType.withCharset(UTF_8);

      builder.addMapping(
          /* extension= */ entry.getKey(),
          /* contentType= */ mediaType.toString());
    }

    return builder.build();
  }

  private static boolean isTextType(MediaType mediaType) {
    for (MediaType textType : KNOWN_TEXT_TYPES)
      if (mediaType.is(textType))
        return true;

    return false;
  }

  private static final ImmutableSet<MediaType> KNOWN_TEXT_TYPES =
      ImmutableSet.of(
          ANY_TEXT_TYPE,
          MediaType.create("application", "javascript"));
}
