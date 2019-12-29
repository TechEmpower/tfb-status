package tfb.status.testlib;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import javax.inject.Singleton;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.api.Rank;

/**
 * Provides the {@link FileSystem} used by this application during tests.
 */
@Singleton
final class InMemoryFileSystemFactory implements Factory<FileSystem> {
  @Override
  @Singleton
  @Rank(1) // Override the default file system.
  public FileSystem provide() {
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
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    return fakeFileSystem;
  }

  @Override
  public void dispose(FileSystem instance) {
    try {
      instance.close();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
