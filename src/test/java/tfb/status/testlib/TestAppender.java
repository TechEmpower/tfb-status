package tfb.status.testlib;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.read.ListAppender;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.util.ArrayList;
import java.util.List;

/**
 * A Logback {@link Appender} that collects logging events into a list.
 *
 * <p>Like {@link ListAppender} but with clearer semantics with respect to
 * thread safety.
 */
public final class TestAppender extends AppenderBase<ILoggingEvent> {
  @GuardedBy("this")
  private final List<ILoggingEvent> events = new ArrayList<ILoggingEvent>();

  @Override
  protected synchronized void append(ILoggingEvent eventObject) {
    events.add(eventObject);
  }

  /**
   * Returns the list of all logging events that have been recorded by this
   * appender.
   */
  public synchronized ImmutableList<ILoggingEvent> events() {
    return ImmutableList.copyOf(events);
  }
}
