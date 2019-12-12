package tfb.status.service;

import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.io.InterruptedIOException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileLockInterruptionException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
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
  private final ListeningScheduledExecutorService scheduler;
  private final ListeningExecutorService executor;
  private final Logger logger = LoggerFactory.getLogger(getClass());

  public TaskScheduler() {
    var scheduler = new ScheduledThreadPoolExecutor(1);
    scheduler.setRemoveOnCancelPolicy(true);
    scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);

    this.scheduler =
        MoreExecutors.listeningDecorator(
            Executors.unconfigurableScheduledExecutorService(scheduler));

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
        MoreExecutors.listeningDecorator(
            Executors.unconfigurableExecutorService(executor));
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

  private <T> Callable<T> exceptionLoggingTask(Callable<T> task) {
    Objects.requireNonNull(task);
    return () -> {
      try {
        return task.call();
      } catch (InterruptedException
          | ClosedByInterruptException
          | InterruptedIOException
          | FileLockInterruptionException e) {
        //
        // Do not log the various forms of InterruptedException that are thrown
        // when someone calls `future.cancel(true)` on an in-progress task.
        // Throwing those exceptions is expected behavior.
        //
        throw e;
      } catch (Throwable t) {
        logger.error("Uncaught exception from task", t);
        throw t;
      }
    };
  }

  private static Duration checkNonNegative(Duration duration, String name) {
    if (duration.isNegative())
      throw new IllegalArgumentException(
          "negative " + name + ": " + duration);

    return duration;
  }

  /**
   * Runs the specified task asynchronously as soon as possible.
   *
   * @param task the task to run
   * @return a future that completes when the task does and that may be used to
   *         cancel the task
   * @throws RejectedExecutionException if {@link #shutdown()} was called
   */
  @CanIgnoreReturnValue // failure will be logged, at least
  public ListenableFuture<?> submit(Runnable task) {
    return submit(Executors.callable(task));
  }

  /**
   * Runs the specified task asynchronously as soon as possible.
   *
   * @param task the task to run
   * @return a future that completes with the result of the task and that may be
   *         used to cancel the task
   * @throws RejectedExecutionException if {@link #shutdown()} was called
   */
  public <T> ListenableFuture<T> submit(Callable<T> task) {
    return internalSubmit(exceptionLoggingTask(task));
  }

  private <T> ListenableFuture<T> internalSubmit(Callable<T> task) {
    return executor.submit(task);
  }

  /**
   * Runs the specified task asynchronously after a delay.
   *
   * @param task the task to run
   * @param delay the amount of time to wait before running the task
   * @return a future that completes when the task does and that may be used to
   *         cancel the task
   * @throws IllegalArgumentException if {@code delay} is negative
   * @throws RejectedExecutionException if {@link #shutdown()} was called
   */
  public ListenableFuture<?> schedule(Runnable task, Duration delay) {
    return schedule(Executors.callable(task), delay);
  }

  /**
   * Runs the specified task asynchronously after a delay.
   *
   * @param task the task to run
   * @param delay the amount of time to wait before running the task
   * @return a future that completes with the result of the task and that may be
   *         used to cancel the task
   * @throws IllegalArgumentException if {@code delay} is negative
   * @throws RejectedExecutionException if {@link #shutdown()} was called
   */
  public <T> ListenableFuture<T> schedule(Callable<T> task, Duration delay) {
    return internalSchedule(
        exceptionLoggingTask(task),
        checkNonNegative(delay, "delay"));
  }

  private <T> ListenableFuture<T> internalSchedule(Callable<T> task,
                                                   Duration delay) {
    return Futures.scheduleAsync(
        () -> internalSubmit(task),
        delay,
        scheduler);
  }

  /**
   * Repeatedly runs the specified task asynchronously.
   *
   * <p>The returned future will never complete, successfully or exceptionally,
   * unless it is cancelled.  Exceptions thrown from the task's {@link
   * Runnable#run()} method are logged but not propagated to the returned
   * future, and such exceptions will not prevent the task from repeating.
   *
   * @param task the task to run
   * @param initialDelay the amount of time to wait before running the task
   *        initially
   * @param interval the amount of time to wait after the task finishes running
   *        before running the task again
   * @return a future that may be used to cancel the task
   * @throws IllegalArgumentException if {@code initialDelay} or {@code
   *         interval} is negative
   * @throws RejectedExecutionException if {@link #shutdown()} was called
   */
  public ListenableFuture<?> repeat(Runnable task,
                                    Duration initialDelay,
                                    Duration interval) {
    return repeat(Executors.callable(task), initialDelay, interval);
  }

  /**
   * Repeatedly runs the specified task asynchronously.
   *
   * <p>The returned future will never complete, successfully or exceptionally,
   * unless it is cancelled.  Exceptions thrown from the task's {@link
   * Callable#call()} method are logged but not propagated to the returned
   * future, and such exceptions will not prevent the task from repeating.
   *
   * @param task the task to run
   * @param initialDelay the amount of time to wait before running the task
   *        initially
   * @param interval the amount of time to wait after the task finishes running
   *        before running the task again
   * @return a future that may be used to cancel the task
   * @throws IllegalArgumentException if {@code initialDelay} or {@code
   *         interval} is negative
   * @throws RejectedExecutionException if {@link #shutdown()} was called
   */
  public <T> ListenableFuture<T> repeat(Callable<T> task,
                                        Duration initialDelay,
                                        Duration interval) {
    return internalRepeat(
        exceptionLoggingTask(task),
        checkNonNegative(initialDelay, "initialDelay"),
        checkNonNegative(interval, "interval"));
  }

  private <T> ListenableFuture<T> internalRepeat(Callable<T> task,
                                                 Duration initialDelay,
                                                 Duration interval) {
    return new AbstractFuture<T>() {
      final Object lock = this;

      @GuardedBy("lock")
      ListenableFuture<T> next =
          internalSchedule(
              new Callable<T>() {
                @Override
                public T call() throws Exception {
                  try {
                    return task.call();
                  } finally {
                    synchronized (lock) {
                      if (!isCancelled())
                        next = internalSchedule(this, interval);
                    }
                  }
                }
              },
              initialDelay);

      @Override
      protected void afterDone() {
        synchronized (lock) {
          if (isCancelled())
            next.cancel(wasInterrupted());
        }
      }
    };
  }
}
