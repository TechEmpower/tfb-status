package tfb.status.service;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheNotFoundException;
import com.github.mustachejava.resolver.ClasspathResolver;
import com.github.mustachejava.resolver.FileSystemResolver;
import java.io.StringWriter;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import tfb.status.config.MustacheConfig;

/**
 * Renders Mustache template files as HTML.
 */
@Singleton
public final class MustacheRenderer {
  private final MustacheRepository mustacheRepository;

  @Inject
  public MustacheRenderer(MustacheConfig config, FileSystem fileSystem) {
    mustacheRepository = newMustacheRepository(config, fileSystem);
  }

  /**
   * Renders the specified Mustache template file as HTML.
   *
   * @param fileName the name of the Mustache template file
   * @param scopes (optional) the scope objects for the template, searched
   *        right-to-left for references
   * @return the rendered HTML
   * @throws MustacheNotFoundException if the Mustache template file does not
   *         exist
   */
  public String render(String fileName, Object... scopes) {
    Objects.requireNonNull(fileName);
    Objects.requireNonNull(scopes);

    Mustache mustache = mustacheRepository.get(fileName);
    var writer = new StringWriter();
    mustache.execute(writer, scopes);
    return writer.toString();
  }

  /**
   * Loads {@link Mustache} objects by file name.
   */
  @FunctionalInterface
  private interface MustacheRepository {
    /**
     * Returns the {@link Mustache} object representing a template file.
     *
     * @param fileName the path to the Mustache template file relative to the
     *        root template directory
     * @throws MustacheNotFoundException if the Mustache template file does not
     *         exist
     */
    Mustache get(String fileName);
  }

  private static MustacheRepository newMustacheRepository(MustacheConfig config,
                                                          FileSystem fileSystem) {
    Objects.requireNonNull(config);
    Objects.requireNonNull(fileSystem);

    switch (config.mode) {
      case CLASS_PATH: {
        var resolver = new ClasspathResolver(config.root);
        var onlyFactory = new DefaultMustacheFactory(resolver);
        return fileName -> onlyFactory.compile(fileName);
      }
      case FILE_SYSTEM: {
        Path mustacheRoot = fileSystem.getPath(config.root);
        // FIXME: Use a version of Mustache that lets us avoid calling toFile(),
        //        which breaks on non-default file systems.
        var resolver = new FileSystemResolver(mustacheRoot.toFile());
        return fileName -> {
          var newFactory = new DefaultMustacheFactory(resolver);
          return newFactory.compile(fileName);
        };
      }
    }

    throw new AssertionError("Unknown resource mode: " + config.mode);
  }
}
