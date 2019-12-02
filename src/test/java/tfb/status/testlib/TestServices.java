package tfb.status.testlib;

import com.google.common.base.Ticker;
import java.time.Clock;
import org.threeten.extra.MutableClock;
import tfb.status.bootstrap.HttpServer;
import tfb.status.bootstrap.Services;
import tfb.status.bootstrap.ServicesBinder;

/**
 * Manages the instances of HTTP handlers and service classes within this
 * application during tests.
 *
 * <p>Use {@link #getService(Class)} to retrieve instances of service classes.
 * For example, <code>getService(HttpServer.class)</code> returns an instance
 * of {@link HttpServer}.
 *
 * <p><strong>Important:</strong> Call {@link #shutdown()} once the tests are
 * complete.
 *
 * @see #getService(Class)
 * @see #shutdown()
 */
public final class TestServices extends Services {
  /**
   * Constructs the interface for managing this application's services during
   * tests.
   */
  public TestServices() {
    super(new ServicesBinder("test_config.yml"),
          new TestServicesBinder());
  }

  /**
   * The {@link Clock} used by every service that needs to read the current wall
   * clock time.  This clock can be adjusted manually.
   */
  public MutableClock clock() {
    // TODO: Make `getService(MutableClock.class)` return this same clock,
    //       meaning this method would be unnecessary.
    return (MutableClock) getService(Clock.class);
  }

  /**
   * The {@link Ticker} used by every service that needs to measure elapsed
   * time.  This ticker can be adjusted manually.
   */
  public MutableTicker ticker() {
    // TODO: Make `getService(MutableTicker.class)` return this same ticker,
    //       meaning this method would be unnecessary.
    return (MutableTicker) getService(Ticker.class);
  }
}
