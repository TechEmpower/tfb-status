package tfb.status.testlib;

import java.time.Clock;
import javax.inject.Singleton;
import org.glassfish.hk2.api.Factory;
import org.threeten.extra.MutableClock;

/**
 * Provides the {@link Clock} used by this application during tests, which is a
 * {@link MutableClock}.
 */
@Singleton
final class MutableClockFactory implements Factory<Clock> {
  @Override
  @Singleton
  public Clock provide() {
    return MutableClock.epochUTC();
  }

  @Override
  public void dispose(Clock instance) {
    // No cleanup required.
  }
}
