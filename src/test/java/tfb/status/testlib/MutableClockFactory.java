package tfb.status.testlib;

import java.time.Clock;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.api.Rank;
import org.threeten.extra.MutableClock;

/**
 * Provides the {@link Clock} used by this application during tests, which is a
 * {@link MutableClock}.
 */
@Singleton
final class MutableClockFactory implements Factory<MutableClock> {
  @Override
  @Singleton
  public MutableClock provide() {
    return MutableClock.epochUTC();
  }

  @Override
  public void dispose(MutableClock instance) {
    // No cleanup required.
  }

  @Singleton
  public static final class OverrideDefault implements Factory<Clock> {
    private final Provider<MutableClock> provider;

    @Inject
    public OverrideDefault(Provider<MutableClock> provider) {
      this.provider = Objects.requireNonNull(provider);
    }

    @Override
    @Singleton
    @Rank(1) // Override the default clock.
    public Clock provide() {
      return provider.get();
    }

    @Override
    public void dispose(Clock instance) {
      // No cleanup required.
    }
  }
}
