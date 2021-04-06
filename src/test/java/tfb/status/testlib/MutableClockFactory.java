package tfb.status.testlib;

import jakarta.inject.Singleton;
import java.time.Clock;
import org.glassfish.hk2.api.Rank;
import org.threeten.extra.MutableClock;
import org.glassfish.hk2.extras.provides.Provides;

/**
 * Provides the {@link Clock} used by this application during tests, which is a
 * {@link MutableClock}.
 */
final class MutableClockFactory  {
  private MutableClockFactory() {
    throw new AssertionError("This class cannot be instantiated");
  }

  @Provides(contracts = { Clock.class, MutableClock.class })
  @Singleton
  @Rank(1) // Override the default clock.
  public static MutableClock mutableClock() {
    return MutableClock.epochUTC();
  }
}
