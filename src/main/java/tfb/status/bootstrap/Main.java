package tfb.status.bootstrap;

import com.google.common.base.Ticker;
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
   * @param args the command line arguments, which must consist of a single
   *        argument, and that one argument must specify the path to this
   *        application's YAML configuration file
   */
  public static void main(String[] args) {
    if (args.length != 1)
      throw new IllegalArgumentException(
          "Expected one argument: the path to the YAML configuration file "
              + "(received " + args.length + " arguments instead)");

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

    ApplicationConfig config =
        ApplicationConfig.readYamlFile(/* yamlFilePath= */ args[0]);

    ServiceLocator serviceLocator =
        Services.newServiceLocator(config, clock, ticker);

    HttpServer httpServer = serviceLocator.getService(HttpServer.class);
    httpServer.start();
  }
}
