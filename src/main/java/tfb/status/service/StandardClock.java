package tfb.status.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import javax.inject.Singleton;
import org.glassfish.hk2.api.Factory;

/**
 * Provides the standard {@link Clock} used by this application.
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
@Singleton
public final class StandardClock implements Factory<Clock> {
  @Override
  @Singleton
  public Clock provide() {
    // TODO: Consider using UTC.
    ZoneId zone = ZoneId.of("America/Los_Angeles");
    return Clock.system(zone);
  }

  @Override
  public void dispose(Clock instance) {
    // No cleanup required.
  }
}
