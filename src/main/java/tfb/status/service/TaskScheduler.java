package tfb.status.service;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.io.InterruptedIOException;
import java.nio.channels.ClosedByInterruptException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.inject.Singleton;
import org.glassfish.hk2.api.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs tasks asynchronously.
 */
@Singleton
public final class TaskScheduler implements PreDestroy {
  private final ScheduledExecutorService scheduler;
  private final ExecutorService executor;
  private final Logger logger = LoggerFactory.getLogger(getClass());

  public TaskScheduler() {
    var scheduler = new ScheduledThreadPoolExecutor(1);
    scheduler.setRemoveOnCancelPolicy(true);
    scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);

    this.scheduler =
        Executors.unconfigurableScheduledExecutorService(scheduler);

    ThreadFactory threadFactory =
        new ThreadFactoryBuilder()
            .setNameFormat("task-scheduler-thread-%s")
            .build();

    var executor =
        new ThreadPoolExecutor(
            /* corePoolSize= */ 0,
            /* maximumPoolSize= */ Integer.MAX_VALUE,
            /* keepAliveTime= */ 60L,
            /* unit= */ TimeUnit.SECONDS,
            /* workQueue= */ new SynchronousQueue<>(),
            /* threadFactory= */ threadFactory);

    this.executor =
        Executors.unconfigurableExecutorService(executor);
  }

  @Override
  public void preDestroy() {
    shutdown();
  }

  /**
   * Shuts down this task scheduler.
   */
  public void shutdown() {
    scheduler.shutdown();
    executor.shutdown();

    try {
      scheduler.awaitTermination(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      if (!scheduler.isTerminated()) {
        scheduler.shutdownNow();
      }
    }

    try {
      executor.awaitTermination(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      if (!executor.isTerminated()) {
        executor.shutdownNow();
      }
    }
  }

  /**
   * Runs the specified task on the current thread and logs uncaught exceptions.
   */
  private void runTaskLogExceptions(ThrowingTask task) throws Exception {
    try {
      task.run();
    } catch (InterruptedException
        | ClosedByInterruptException
        | InterruptedIOException e) {
      // Ignore various forms of InterruptedException that are thrown when
      // someone cancels an in-progress task.  Throwing those exceptions is
      // expected behavior, so we don't want to clutter up our logs with them.
      throw e;
    } catch (Exception e) {
      logger.error("Uncaught exception from task", e);
      throw e;
    }
  }

  /**
   * Runs the specified task asynchronously as soon as possible.
   *
   * @param task the task to run
   * @return a wrapper that may be used to cancel the task
   * @throws RejectedExecutionException if {@link #shutdown()} was called
   */
  @CanIgnoreReturnValue
  public CancellableTask submit(ThrowingTask task) {
    Objects.requireNonNull(task);
    return new ImmediateTask(task);
  }

  /**
   * Runs the specified task asynchronously after a delay.
   *
   * @param task the task to run
   * @param delay the amount of time to wait before running the task
   * @return a wrapper that may be used to cancel the task
   * @throws IllegalArgumentException if {@code delay} is negative
   * @throws RejectedExecutionException if {@link #shutdown()} was called
   */
  public CancellableTask schedule(ThrowingTask task, Duration delay) {
    Objects.requireNonNull(task);
    Objects.requireNonNull(delay);

    if (delay.isNegative())
      throw new IllegalArgumentException(
          "negative delay: " + delay);

    return new DelayedTask(task, delay.toNanos());
  }

  /**
   * Repeatedly runs the specified task asynchronously.
   *
   * @param task the task to run
   * @param initialDelay the amount of time to wait before running the task
   *        initially
   * @param interval the amount of time to wait after the task finishes running
   *        before running the task again
   * @return a wrapper that may be used to cancel the task
   * @throws IllegalArgumentException if {@code initialDelay} or {@code
   *         interval} is negative
   * @throws RejectedExecutionException if {@link #shutdown()} was called
   */
  public CancellableTask repeat(ThrowingTask task,
                                Duration initialDelay,
                                Duration interval) {

    Objects.requireNonNull(task);
    Objects.requireNonNull(initialDelay);
    Objects.requireNonNull(interval);

    if (initialDelay.isNegative())
      throw new IllegalArgumentException(
          "negative initialDelay: " + initialDelay);

    if (interval.isNegative())
      throw new IllegalArgumentException(
          "negative interval: " + interval);

    return new RepeatingTask(task, initialDelay.toNanos(), interval.toNanos());
  }

  /**
   * A task whose execution may throw an exception.
   */
  @FunctionalInterface
  public interface ThrowingTask {
    /**
     * Runs this task.
     */
    void run() throws Exception;
  }

  /**
   * A task that will run asynchronously and that can be cancelled.
   */
  public interface CancellableTask {
    /**
     * Prevents this task from running in the future and interrupts this task if
     * it is currently running.  If this task has already finished running and
     * it is not scheduled to run again in the future, then calling this
     * function has no effect.
     */
    void cancel();
  }

  /**
   * A task that runs once immediately.
   */
  private final class ImmediateTask implements CancellableTask {
    private final Future<?> future;

    ImmediateTask(ThrowingTask task) {
      future =
          executor.submit(
              () -> {
                runTaskLogExceptions(task);
                return null;
              });
    }

    @Override
    public void cancel() {
      future.cancel(true);
    }
  }

  /**
   * A task that runs once after a delay.
   */
  private final class DelayedTask implements CancellableTask {
    private final ThrowingTask task;

    @GuardedBy("this")
    private boolean isCancelled = false;

    @GuardedBy("this")
    private Future<?> future;

    DelayedTask(ThrowingTask task, long delayNanos) {
      this.task = Objects.requireNonNull(task);

      future =
          scheduler.schedule(
              () -> submitTask(),
              delayNanos,
              TimeUnit.NANOSECONDS);
    }

    private synchronized void submitTask() {
      if (!isCancelled) {
        future =
            executor.submit(
                () -> {
                  runTaskLogExceptions(task);
                  return null;
                });
      }
    }

    @Override
    public synchronized void cancel() {
      isCancelled = true;
      future.cancel(true);
    }
  }

  /**
   * A task that runs repeatedly.
   */
  private final class RepeatingTask implements CancellableTask {
    private final ThrowingTask task;
    private final long intervalNanos;

    @GuardedBy("this")
    private boolean isCancelled = false;

    @GuardedBy("this")
    private Future<?> future;

    RepeatingTask(ThrowingTask task,
                  long initialDelayNanos,
                  long intervalNanos) {

      this.task = Objects.requireNonNull(task);
      this.intervalNanos = intervalNanos;

      future =
          scheduler.schedule(
              () -> submitTask(),
              initialDelayNanos,
              TimeUnit.NANOSECONDS);
    }

    private synchronized void submitTask() {
      if (!isCancelled) {
        future =
            executor.submit(
                () -> {
                  try {
                    runTaskLogExceptions(task);
                    return null;
                  } finally {
                    scheduleNext();
                  }
                });
      }
    }

    private synchronized void scheduleNext() {
      if (!isCancelled) {
        future =
            scheduler.schedule(
                () -> submitTask(),
                intervalNanos,
                TimeUnit.NANOSECONDS);
      }
    }

    @Override
    public synchronized void cancel() {
      isCancelled = true;
      future.cancel(true);
    }
  }
}
