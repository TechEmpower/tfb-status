package tfb.status.testlib;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Filters events below the threshold level for the specified loggers.
 */
public final class LoggerThresholdFilter extends Filter<ILoggingEvent> {
  private Level level = Level.ALL;
  private final Set<String> loggerNames = ConcurrentHashMap.newKeySet();

  @Override
  public FilterReply decide(ILoggingEvent event) {
    Objects.requireNonNull(event);

    if (isStarted()
        && !event.getLevel().isGreaterOrEqual(level)
        && loggerNames.contains(event.getLoggerName()))
      return FilterReply.DENY;

    return FilterReply.NEUTRAL;
  }

  public void setLevel(String level) {
    Objects.requireNonNull(level);
    this.level = Level.toLevel(level);
  }

  public void addLoggerName(String loggerName) {
    Objects.requireNonNull(loggerName);
    loggerNames.add(loggerName);
  }
}
