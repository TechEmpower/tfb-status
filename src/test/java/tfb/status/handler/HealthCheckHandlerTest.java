package tfb.status.handler;

import static io.undertow.util.StatusCodes.OK;
import static io.undertow.util.StatusCodes.SERVICE_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.base.Stopwatch;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tfb.status.config.HealthCheckConfig;
import tfb.status.testlib.HttpTester;
import tfb.status.testlib.TestServicesInjector;

/**
 * Tests for {@link HealthCheckHandler}.
 */
@ExtendWith(TestServicesInjector.class)
public final class HealthCheckHandlerTest {
  /**
   * Verifies that {@code GET /health} produces a {@code 200 OK} response when
   * the application is healthy and a {@code 503 Service Unavailable} response
   * when the application is unhealthy.
   */
  @Test
  public void testGet(HealthCheckConfig healthCheckConfig, HttpTester http)
      throws IOException, InterruptedException {

    Duration interval = Duration.ofSeconds(healthCheckConfig.intervalSeconds());
    Duration executionTime = Duration.ofSeconds(1); // estimated upper bound
    Duration timeUntilHealthUpdated = interval.plus(executionTime);

    HttpResponse<String> initialResponse = http.getString("/health");
    assertEquals(OK, initialResponse.statusCode());
    assertEquals("", initialResponse.body());

    var deadlock = new Deadlock();
    try {
      deadlock.start();
      Thread.sleep(timeUntilHealthUpdated.toMillis());
      HttpResponse<String> responseWhileDeadlocked = http.getString("/health");
      assertEquals(SERVICE_UNAVAILABLE, responseWhileDeadlocked.statusCode());
      assertEquals("", responseWhileDeadlocked.body());
    } finally {
      deadlock.stop();
    }

    Thread.sleep(timeUntilHealthUpdated.toMillis());
    HttpResponse<String> responseAfterDeadlockResolved = http.getString("/health");
    assertEquals(OK, responseAfterDeadlockResolved.statusCode());
    assertEquals("", responseAfterDeadlockResolved.body());
  }

  /**
   * Creates threads that become deadlocked.
   */
  private static final class Deadlock {
    private final Thread thread1;
    private final Thread thread2;

    Deadlock() {
      var lock1 = new ReentrantLock();
      var lock2 = new ReentrantLock();
      var barrier = new CyclicBarrier(2);

      thread1 =
          new Thread(
              () -> {
                try {
                  lock1.lockInterruptibly();
                  barrier.await(); // Wait until thread2 holds lock2.
                  lock2.lockInterruptibly(); // Deadlock here.
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                } catch (BrokenBarrierException impossible) {
                  throw new AssertionError(impossible);
                }
              });

      thread2 =
          new Thread(
              () -> {
                try {
                  lock2.lockInterruptibly();
                  barrier.await(); // Wait until thread1 holds lock1.
                  lock1.lockInterruptibly(); // Deadlock here.
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                } catch (BrokenBarrierException impossible) {
                  throw new AssertionError(impossible);
                }
              });
    }

    /**
     * Starts threads that become deadlocked.  When this method returns, the
     * threads are in a deadlocked state.
     */
    public void start() throws InterruptedException {
      thread1.start();
      thread2.start();
      // We assume that this is enough time for the threads to become
      // deadlocked.
      Thread.sleep(100);
    }

    /**
     * Stops the deadlocked threads.  When this method returns, the threads are
     * no longer running.
     */
    public void stop() throws InterruptedException {
      thread1.interrupt();
      thread2.interrupt();

      Duration timeout = Duration.ofSeconds(5);
      Stopwatch stopwatch = Stopwatch.createStarted();

      while ((thread1.isAlive() || thread2.isAlive())
          && stopwatch.elapsed().compareTo(timeout) < 0)
        Thread.sleep(50);

      if (thread1.isAlive() || thread2.isAlive())
        throw new AssertionError("Unable to stop the deadlocked threads");
    }
  }
}
