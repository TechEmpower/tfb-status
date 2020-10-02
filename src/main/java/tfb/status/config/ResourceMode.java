package tfb.status.config;

import java.net.URL;

/**
 * Controls how resource files are loaded and cached.
 */
public enum ResourceMode {
  /**
   * Resource files are loaded from the class path and cached.  This mode is
   * intended for production environments.
   */
  CLASS_PATH,

  /**
   * Resource files are loaded from the file system and not cached.  This mode
   * is intended for development environments.
   */
  FILE_SYSTEM;

  /**
   * Guesses the preferred mode for this environment.
   */
  static ResourceMode defaultMode() {
    return DefaultModeHolder.VALUE;
  }

  private static final class DefaultModeHolder {
    static final ResourceMode VALUE;
    static {
      var thisClass = DefaultModeHolder.class;
      String classFilePath = thisClass.getName().replace('.', '/') + ".class";

      ClassLoader classLoader = thisClass.getClassLoader();
      URL classFileUrl = classLoader.getResource(classFilePath);

      if (classFileUrl == null)
        throw new AssertionError("The class file for this class must exist");

      VALUE =
          classFileUrl.getProtocol().equals("jar")
              ? CLASS_PATH
              : FILE_SYSTEM;
    }
  }
}
