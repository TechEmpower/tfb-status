package tfb.status.service;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Future;
import javax.inject.Inject;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glassfish.hk2.api.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfb.status.config.HealthCheckConfig;

/**
 * Monitors the health of this application.  See {@link #isHealthy()}.
 */
public final class HealthChecker implements PreDestroy {
  private final HealthCheckConfig config;
  private final TaskScheduler taskScheduler;
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final Object lock = new Object();

  @GuardedBy("lock")
  private boolean isHealthy;

  @GuardedBy("lock")
  private @Nullable Future<?> healthCheckTask = null;

  @Inject
  public HealthChecker(HealthCheckConfig config, TaskScheduler taskScheduler) {
    this.config = Objects.requireNonNull(config);
    this.taskScheduler = Objects.requireNonNull(taskScheduler);
  }

  @Override
  public void preDestroy() {
    shutdown();
  }

  /**
   * Cleans up resources used by this service.
   */
  public void shutdown() {
    synchronized (lock) {
      Future<?> task = this.healthCheckTask;
      if (task != null) {
        task.cancel(true);
        this.healthCheckTask = null;
      }
    }
  }

  /**
   * Returns {@code true} if this application is currently healthy.
   *
   * <p>This application is healthy when:
   * <ul>
   *   <li>There are no deadlocked threads.
   * </ul>
   *
   * <p>Health checks may be expensive, so this service performs health checks
   * in the background, and this method returns the result of the most recent
   * health check instead of running a health check immediately on the current
   * thread.
   */
  public boolean isHealthy() {
    synchronized (lock) {
      // Ensure that the background task is running.
      if (healthCheckTask == null) {
        // Ensure that the first call to isHealthy() returns the correct result.
        updateHealth();
        healthCheckTask =
            taskScheduler.repeat(
                /* task= */ () -> updateHealth(),
                /* initialDelay= */ Duration.ofSeconds(config.intervalSeconds),
                /* interval= */ Duration.ofSeconds(config.intervalSeconds));
      }

      return isHealthy;
    }
  }

  /**
   * Immediately performs a health check on the current thread and updates the
   * value of {@link #isHealthy} with the result.
   */
  private void updateHealth() {
    try {
      checkHealth();
      synchronized (lock) {
        isHealthy = true;
      }
    } catch (Exception e) {
      synchronized (lock) {
        isHealthy = false;
      }
      logger.error("Health check failed", e);
    }
  }

  /**
   * Throws an exception if this application is unhealthy.
   */
  private void checkHealth() {
    checkNoDeadlockedThreads();
    // TODO: Think about what else we should check.
  }

  /**
   * Throws an exception if there are any deadlocked threads.
   */
  private void checkNoDeadlockedThreads() {
    long[] deadlockedThreads =
        ManagementFactory.getThreadMXBean().findDeadlockedThreads();

    if (deadlockedThreads != null)
      throw new IllegalStateException(
          "Found "
              + deadlockedThreads.length
              + " deadlocked threads with ids "
              + Arrays.toString(deadlockedThreads));
  }
}
