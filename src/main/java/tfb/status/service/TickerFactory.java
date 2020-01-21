package tfb.status.service;

import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;
import javax.inject.Singleton;
import tfb.status.hk2.extensions.Provides;

/**
 * Provides the {@link Ticker} used by this application.
 *
 * <p>Use this ticker to measure elapsed time.  Avoid using APIs that implicitly
 * rely on the system clock.  This usage pattern allows us to swap in a
 * different ticker during tests and manipulate the apparent passage of time.
 *
 * <table>
 *   <caption>APIs to avoid and their replacements</caption>
 *   <tr>
 *   <th>Don't use this
 *   <th>Use this instead
 *   <tr>
 *   <td>{@link System#nanoTime()}
 *   <td>{@link Ticker#read()}
 *   <tr>
 *   <td>{@link Stopwatch#createStarted()}
 *   <td>{@link Stopwatch#createStarted(Ticker)}
 * </table>
 */
public final class TickerFactory {
  private TickerFactory() {
    throw new AssertionError("This class cannot be instantiated");
  }

  @Provides
  @Singleton
  public static Ticker systemTicker() {
    return Ticker.systemTicker();
  }
}
