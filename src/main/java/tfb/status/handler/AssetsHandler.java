package tfb.status.handler;

import static com.google.common.net.MediaType.ANY_TEXT_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableSet;
import com.google.common.net.MediaType;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.server.handlers.resource.PathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.util.MimeMappings;
import jakarta.inject.Singleton;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import org.glassfish.hk2.extras.provides.Provides;
import tfb.status.config.AssetsConfig;
import tfb.status.handler.routing.Route;

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
  @Route(method = "GET", path = "/assets/{assetPath:.+}")
  public static HttpHandler assetsHandler(AssetsConfig config,
                                          FileSystem fileSystem) {
    Objects.requireNonNull(config);
    Objects.requireNonNull(fileSystem);

    HttpHandler handler = newResourceHandler(config, fileSystem);

    // Trim the "/assets" prefix from the front of the request path, since that
    // prefix would confuse the ResourceHandler.
    handler = new PathHandler().addPrefixPath("/assets", handler);

    return handler;
  }

  private static HttpHandler newResourceHandler(AssetsConfig config,
                                                FileSystem fileSystem) {
    Objects.requireNonNull(config);
    Objects.requireNonNull(fileSystem);

    return switch (config.mode()) {
      case CLASS_PATH -> {
        ClassLoader classLoader = AssetsHandler.class.getClassLoader();
        var resourceManager = new ClassPathResourceManager(classLoader, "assets");
        var resourceHandler = new ResourceHandler(resourceManager);
        resourceHandler.setMimeMappings(newMimeMappings());
        resourceHandler.setWelcomeFiles();
        yield resourceHandler;
      }
      case FILE_SYSTEM -> {
        Path assetsRoot = fileSystem.getPath("src/main/resources/assets");
        var resourceManager = new PathResourceManager(assetsRoot);
        // FIXME: ResourceHandler doesn't seem to write the expected
        //        Content-Length header in responses to HEAD requests.  Is that
        //        a bug in ResourceHandler?
        //
        //        Also, looking at that code, I suspect there is an unintended
        //        interaction between HEAD requests and
        //        resourceHandler.setContentEncodedResourceManager(...) in that
        //        if a ContentEncodedResourceManager is present, the "is this a
        //        HEAD request?" check is never performed, so perhaps content is
        //        written to the response?
        var resourceHandler = new ResourceHandler(resourceManager);
        resourceHandler.setMimeMappings(newMimeMappings());
        resourceHandler.setWelcomeFiles();
        resourceHandler.setCacheTime(0);
        yield resourceHandler;
      }
    };
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
