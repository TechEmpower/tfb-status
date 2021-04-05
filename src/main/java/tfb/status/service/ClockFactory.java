package tfb.status.service;

import jakarta.inject.Singleton;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import tfb.status.hk2.extensions.Provides;

/**
 * Provides the {@link Clock} used by this application.
 *
 * <p>Use this clock to get the current date, time, or time-zone.  Avoid using
 * APIs that implicitly rely on the system clock or the default time-zone.  This
 * usage pattern allows us to swap in a different clock during tests and
 * manipulate the apparent date and time.
 *
 * <table>
 *   <caption>APIs to avoid and their replacements</caption>
 *   <tr>
 *   <th>Don't use this
 *   <th>Use this instead
 *   <tr>
 *   <td>{@link LocalDateTime#now()}
 *   <td>{@link LocalDateTime#now(Clock)}
 *   <tr>
 *   <td>{@link ZonedDateTime#now()}
 *   <td>{@link ZonedDateTime#now(Clock)}
 *   <tr>
 *   <td>{@link Instant#now()}
 *   <td>{@link Clock#instant()}
 *   <tr>
 *   <td>{@link System#currentTimeMillis()}
 *   <td>{@link Clock#millis()}
 * </table>
 */
public final class ClockFactory {
  private ClockFactory() {
    throw new AssertionError("This class cannot be instantiated");
  }

  @Provides
  @Singleton
  public static Clock systemClock() {
    // TODO: Consider using UTC.
    ZoneId zone = ZoneId.of("America/Los_Angeles");
    return Clock.system(zone);
  }
}
