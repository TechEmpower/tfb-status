package tfb.status.bootstrap;

import com.google.common.base.Ticker;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Locale;
import java.util.TimeZone;
import org.glassfish.hk2.api.ServiceLocator;
import tfb.status.config.ApplicationConfig;

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
   * <p>If there are zero arguments, then a {@linkplain
   * ApplicationConfig#defaultConfig() default configuration} will be used.  If
   * there is one argument, then that argument specifies the path to this
   * application's {@linkplain ApplicationConfig#readYamlFile(Path) YAML
   * configuration file}.
   *
   * @param args the command line arguments
   * @throws IllegalArgumentException if there is one argument and the
   *         configuration file it specifies cannot be read, or if there are two
   *         or more arguments
   */
  public static void main(String[] args) {
    // TODO: Consider using UTC.
    ZoneId zone = ZoneId.of("America/Los_Angeles");
    Clock clock = Clock.system(zone);
    Ticker ticker = Ticker.systemTicker();
    FileSystem fileSystem = FileSystems.getDefault();

    // We try to avoid using the default locale or time zone, but we set the
    // defaults here anyway.  In case we accidentally use one of the defaults,
    // it'll be nice if the behavior is consistent across environments.
    Locale.setDefault(Locale.ROOT);
    TimeZone.setDefault(TimeZone.getTimeZone(zone));

    ApplicationConfig config;
    switch (args.length) {
      case 0:
        config = ApplicationConfig.defaultConfig();
        break;

      case 1:
        Path yamlFile = fileSystem.getPath(args[0]);
        config = ApplicationConfig.readYamlFile(yamlFile);
        break;

      default:
        throw new IllegalArgumentException(
            "Expected zero or one arguments, received "
                + args.length
                + " arguments instead");
    }

    ServiceLocator serviceLocator =
        Services.newServiceLocator(config, clock, ticker, fileSystem);

    HttpServer httpServer = serviceLocator.getService(HttpServer.class);
    httpServer.start();
  }
}
