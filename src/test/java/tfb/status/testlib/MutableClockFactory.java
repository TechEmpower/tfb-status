package tfb.status.testlib;

import java.time.Clock;
import javax.inject.Singleton;
import org.glassfish.hk2.api.Rank;
import org.threeten.extra.MutableClock;
import tfb.status.hk2.extensions.Provides;

/**
 * Provides the {@link Clock} used by this application during tests, which is a
 * {@link MutableClock}.
 */
final class MutableClockFactory  {
  private MutableClockFactory() {
    throw new AssertionError("This class cannot be instantiated");
  }

  @Provides
  @Singleton
  public static MutableClock mutableClock() {
    return MutableClock.epochUTC();
  }

  @Provides
  @Singleton
  @Rank(1) // Override the default clock.
  public static Clock defaultClock(MutableClock mutableClock) {
    return mutableClock;
  }
}
