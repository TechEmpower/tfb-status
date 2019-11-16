package tfb.status.testlib;

import com.google.common.base.Ticker;
import javax.inject.Singleton;
import org.glassfish.hk2.api.Factory;

/**
 * Provides the {@link Ticker} used by this application during tests, which is a
 * {@link MutableTicker}.
 */
@Singleton
final class MutableTickerFactory implements Factory<Ticker> {
  @Override
  @Singleton
  public Ticker provide() {
    return new MutableTicker();
  }

  @Override
  public void dispose(Ticker instance) {
    // No cleanup required.
  }
}
