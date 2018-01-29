package tfb.status.service;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.github.mustachejava.MustacheNotFoundException;
import com.github.mustachejava.MustacheResolver;
import com.github.mustachejava.resolver.ClasspathResolver;
import com.github.mustachejava.resolver.FileSystemResolver;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
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
  public MustacheRenderer(MustacheConfig config) {
    mustacheRepository = newConfiguredMustacheRepository(config);
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
    StringWriter writer = new StringWriter();
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

  private static MustacheRepository
  newConfiguredMustacheRepository(MustacheConfig config) {
    switch (config.mode) {
      case CLASS_PATH: {
        MustacheResolver resolver = new ClasspathResolver(config.root);
        MustacheFactory onlyFactory = new DefaultMustacheFactory(resolver);
        return fileName -> onlyFactory.compile(fileName);
      }
      case FILE_SYSTEM: {
        Path mustacheRoot = Paths.get(config.root);
        MustacheResolver resolver = new FileSystemResolver(mustacheRoot.toFile());
        return fileName -> {
          MustacheFactory newFactory = new DefaultMustacheFactory(resolver);
          return newFactory.compile(fileName);
        };
      }
    }
    throw new AssertionError("Unknown resource mode: " + config.mode);
  }
}
