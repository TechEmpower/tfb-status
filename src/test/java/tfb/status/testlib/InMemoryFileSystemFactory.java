package tfb.status.testlib;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.glassfish.hk2.api.Rank;
import tfb.status.hk2.extensions.Provides;

/**
 * Provides the {@link FileSystem} used by this application during tests.
 */
final class InMemoryFileSystemFactory {
  private InMemoryFileSystemFactory() {
    throw new AssertionError("This class cannot be instantiated");
  }

  @Provides(disposeMethod = "close")
  @Singleton
  @Rank(1) // Override the default file system.
  public static FileSystem inMemoryFileSystem() throws IOException {
    FileSystem realFileSystem = FileSystems.getDefault();
    FileSystem fakeFileSystem = Jimfs.newFileSystem(Configuration.unix());

    Path sourceRoot = realFileSystem.getPath("src/test/resources");
    Path targetRoot = fakeFileSystem.getPath("");

    try (Stream<Path> sources = Files.walk(sourceRoot)) {
      sources.forEach(
          (Path source) -> {
            Path target = targetRoot;
            for (Path part : sourceRoot.relativize(source))
              target = target.resolve(part.toString());

            if (Files.exists(target))
              return;

            try {
              Files.copy(source, target);
            } catch (IOException e) {
              throw new UncheckedIOException(e);
            }
          });
    }

    return fakeFileSystem;
  }
}
