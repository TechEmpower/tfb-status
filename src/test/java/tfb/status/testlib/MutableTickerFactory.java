package tfb.status.testlib;

import com.google.common.base.Ticker;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.api.Rank;

/**
 * Provides the {@link Ticker} used by this application during tests, which is a
 * {@link MutableTicker}.
 */
@Singleton
final class MutableTickerFactory implements Factory<MutableTicker> {
  @Override
  @Singleton
  public MutableTicker provide() {
    return new MutableTicker();
  }

  @Override
  public void dispose(MutableTicker instance) {
    // No cleanup required.
  }

  @Singleton
  public static final class OverrideDefault implements Factory<Ticker> {
    private final Provider<MutableTicker> provider;

    @Inject
    public OverrideDefault(Provider<MutableTicker> provider) {
      this.provider = Objects.requireNonNull(provider);
    }

    @Override
    @Singleton
    @Rank(1) // Override the default ticker.
    public Ticker provide() {
      return provider.get();
    }

    @Override
    public void dispose(Ticker instance) {
      // No cleanup required.
    }
  }
}
