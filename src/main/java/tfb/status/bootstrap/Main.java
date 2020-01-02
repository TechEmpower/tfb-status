package tfb.status.bootstrap;

import tfb.status.hk2.extensions.Services;
import tfb.status.service.HttpServer;

/**
 * Provides the {@code main} method for starting this application.
 */
public final class Main {
  private Main() {
    throw new AssertionError("This class cannot be instantiated");
  }

  /**
   * Starts this application.
   *
   * <p>If there are zero arguments, then a default configuration will be used.
   * If there is one argument, then that argument specifies the path to this
   * application's YAML configuration file.
   *
   * @param args the command line arguments
   * @throws IllegalArgumentException if there are two or more arguments
   */
  public static void main(String[] args) {
    String configFilePath;
    switch (args.length) {
      case 0:
        configFilePath = null;
        break;

      case 1:
        configFilePath = args[0];
        break;

      default:
        throw new IllegalArgumentException(
            "Expected zero or one arguments, received "
                + args.length
                + " arguments instead");
    }

    var services = new Services();
    services.addInstance(new ServicesBinder(configFilePath));
    HttpServer httpServer = services.getService(HttpServer.class);
    httpServer.start();
  }
}
