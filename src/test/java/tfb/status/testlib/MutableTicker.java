package tfb.status.testlib;

import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.time.Duration;

/**
 * A {@link Ticker} that does not advance on its own and that must be adjusted
 * manually.  Use this to test components that measure elapsed time via a {@link
 * Ticker} or {@link Stopwatch}.
 */
//
// We're not using the FakeTicker class from guava-testlib because guava-testlib
// depends on JUnit 4 and we use JUnit 5.
//
public final class MutableTicker extends Ticker {
  @GuardedBy("this")
  private long nanos;

  @Override
  public synchronized long read() {
    return nanos;
  }

  /**
   * Adds the specified amount of time to this ticker.
   *
   * @param amountToAdd the amount of time to add
   */
  public synchronized void add(Duration amountToAdd) {
    nanos += amountToAdd.toNanos();
  }
}
