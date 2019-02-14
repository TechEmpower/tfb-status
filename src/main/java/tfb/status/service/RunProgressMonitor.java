package tfb.status.service;

import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Complains over email when a benchmarking environment has stopped sending
 * updates.
 */
public final class RunProgressMonitor {
  private final EmailSender emailSender;
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @GuardedBy("this")
  @Nullable
  private ScheduledThreadPoolExecutor taskScheduler;

  @GuardedBy("this")
  private final Map<String, ScheduledFuture<?>> environmentToTask = new HashMap<>();

  @Inject
  public RunProgressMonitor(EmailSender emailSender) {
    this.emailSender = Objects.requireNonNull(emailSender);
  }

  /**
   * Initializes resources used by this service.
   */
  @PostConstruct
  public synchronized void start() {
    taskScheduler = new ScheduledThreadPoolExecutor(1);
    taskScheduler.setRemoveOnCancelPolicy(true);
    taskScheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    taskScheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
  }

  /**
   * Cleans up resources used by this service.
   */
  @PreDestroy
  public synchronized void stop() {
    for (ScheduledFuture<?> task : environmentToTask.values())
      task.cancel(false);

    environmentToTask.clear();

    ScheduledThreadPoolExecutor scheduler = this.taskScheduler;
    if (scheduler != null) {
      scheduler.shutdownNow();
      this.taskScheduler = null;
    }
  }

  /**
   * Notifies this service that the given environment has made progress on a
   * run.
   *
   * @param environment the name of the environment
   * @param expectMore {@code true} if this service should expect more progress
   *        to be made in this same environment
   */
  public synchronized void recordProgress(String environment,
                                          boolean expectMore) {
    Objects.requireNonNull(environment);

    ScheduledThreadPoolExecutor scheduler = this.taskScheduler;
    if (scheduler == null)
      throw new IllegalStateException("This service is not running");

    if (environmentToTask.size() >= MAX_ENVIRONMENTS
        && !environmentToTask.containsKey(environment)) {
      logger.warn(
          "Ignoring progress from environment {} because there are "
              + "already {} environments running concurrently, "
              + "which is more than expected",
          environment,
          environmentToTask.size());
      return;
    }

    environmentToTask.compute(
        environment,
        (String env, ScheduledFuture<?> oldTask) -> {

          if (oldTask != null)
            oldTask.cancel(false);

          if (!expectMore)
            return null;

          return scheduler.schedule(
              /* command= */ () -> {

                synchronized (this) {
                  environmentToTask.remove(environment);
                }

                complain(environment);
              },
              /* delay= */ COMPLAINT_DELAY.toMillis(),
              /* unit= */ TimeUnit.MILLISECONDS);
        });
  }

  private static final Duration COMPLAINT_DELAY = Duration.ofHours(6);
  private static final int MAX_ENVIRONMENTS = 5;

  private void complain(String environment) {
    Objects.requireNonNull(environment);

    String subject =
        "<tfb> <auto> Benchmarking environment \""
            + environment
            + "\" crashed?";

    String textContent =
        "tfb-status hasn't received any new data from "
            + "the benchmarking environment \""
            + environment
            + "\" in a while.  Is everything ok?";

    try {
      emailSender.sendEmail(
          /* subject= */ subject,
          /* textContent= */ textContent,
          /* attachments= */ List.of());
    } catch (MessagingException e) {
      logger.warn(
          "Error sending email regarding lack of progress in environment {}",
          environment, e);
    }
  }
}
