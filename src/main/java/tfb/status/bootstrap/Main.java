package tfb.status.bootstrap;

import com.google.common.base.Ticker;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Locale;
import java.util.TimeZone;
import org.glassfish.hk2.api.ServiceLocator;
import tfb.status.config.ApplicationConfig;
import tfb.status.config.ApplicationConfig.InvalidConfigFileException;

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
   * application's {@linkplain ApplicationConfig#readYamlFile(String) YAML
   * configuration file}.
   *
   * @param args the command line arguments
   * @throws InvalidConfigFileException if there is one argument and the YAML
   *         configuration file specified by that argument is invalid
   * @throws IllegalArgumentException if there are two or more arguments
   */
  public static void main(String[] args) {
    // TODO: Consider using UTC.
    ZoneId zone = ZoneId.of("America/Los_Angeles");
    Clock clock = Clock.system(zone);
    Ticker ticker = Ticker.systemTicker();

    //
    // We try to avoid using the default locale or time zone, but we set the
    // defaults here anyway.  In case we accidentally use one of the defaults,
    // it'll be nice if the behavior is consistent across environments.
    //
    Locale.setDefault(Locale.ROOT);
    TimeZone.setDefault(TimeZone.getTimeZone(zone));

    ApplicationConfig config;
    switch (args.length) {
      case 0:
        config = ApplicationConfig.defaultConfig();
        break;
      case 1:
        config = ApplicationConfig.readYamlFile(/* yamlFilePath= */ args[0]);
        break;
      default:
        throw new IllegalArgumentException(
            "Expected zero or one arguments, received "
                + args.length
                + " arguments instead");
    }

    ServiceLocator serviceLocator =
        Services.newServiceLocator(config, clock, ticker);

    HttpServer httpServer = serviceLocator.getService(HttpServer.class);
    httpServer.start();
  }
}
