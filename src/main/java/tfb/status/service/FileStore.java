package tfb.status.service;

import java.nio.file.Path;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import tfb.status.config.FileStoreConfig;

/**
 * Provides access to miscellaneous files on the file system that are managed by
 * this application.
 *
 * <p>This class should not refer to any files that are in source control.
 */
@Singleton
public final class FileStore {
  private final FileStoreConfig config;

  @Inject
  public FileStore(FileStoreConfig config) {
    this.config = Objects.requireNonNull(config);
  }

  /**
   * The root directory for uploaded results files.
   */
  public Path resultsDirectory() {
    return Path.of(config.root, "results");
  }

  /**
   * The root directory for user account files.
   */
  public Path accountsDirectory() {
    return Path.of(config.root, "accounts");
  }

  /**
   * The root directory for tfb_lookup.json files.
   */
  public Path attributesDirectory() {
    return Path.of(config.root, "attributes");
  }

  /**
   * The text file containing an announcement to be displayed on the home page.
   */
  public Path announcementFile() {
    return Path.of(config.root, "announcement.txt");
  }
}
