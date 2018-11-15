package tfb.status.service;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheException;
import com.github.mustachejava.MustacheNotFoundException;
import com.github.mustachejava.MustacheResolver;
import com.github.mustachejava.resolver.ClasspathResolver;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import javax.annotation.Nullable;
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

  private static MustacheRepository
  newConfiguredMustacheRepository(MustacheConfig config) {
    switch (config.mode) {
      case CLASS_PATH: {
        var resolver = new ClasspathResolver(config.root);
        var onlyFactory = new DefaultMustacheFactory(resolver);
        return fileName -> onlyFactory.compile(fileName);
      }
      case FILE_SYSTEM: {
        Path mustacheRoot = Path.of(config.root);
        var resolver = new NioFileSystemResolver(mustacheRoot);
        return fileName -> {
          var newFactory = new DefaultMustacheFactory(resolver);
          return newFactory.compile(fileName);
        };
      }
    }

    throw new AssertionError("Unknown resource mode: " + config.mode);
  }

  // Avoid using java.io.File or Path.toFile().  These are incompatible with
  // jimfs, the library that provides an in-memory file system that we use for
  // testing.
  private static final class NioFileSystemResolver implements MustacheResolver {
    private final Path root;

    NioFileSystemResolver(Path root) {
      this.root = Objects.requireNonNull(root);
    }

    @Override
    @Nullable
    public Reader getReader(String resourceName) {
      Path file;
      try {
        file = root.resolve(resourceName);
      } catch (InvalidPathException e) {
        throw new MustacheException("Invalid file path", e);
      }

      if (!file.normalize().equals(file))
        throw new MustacheException("File path must be normalized");

      if (!file.startsWith(root))
        throw new MustacheException("File path must start with the root path");

      if (!Files.isRegularFile(file))
        return null;

      try {
        return Files.newBufferedReader(file);
      } catch (IOException e) {
        throw new MustacheException("Could not open file", e);
      }
    }
  }
}
