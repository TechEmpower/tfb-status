package tfb.status.testlib;

import static org.slf4j.Logger.ROOT_LOGGER_NAME;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.stream.Stream;
import org.slf4j.LoggerFactory;

/**
 * Provides an API for inspecting log messages during tests.
 */
public final class LogTester {
  /**
   * Returns the stream of all logging events that have been recorded during the
   * current execution of this application's test suite.
   */
  public Stream<ILoggingEvent> getEvents() {
    var logger = (Logger) LoggerFactory.getLogger(ROOT_LOGGER_NAME);
    var appender = (TestAppender) logger.getAppender(TEST_APPENDER_NAME);
    return appender.events().stream();
  }

  // This name is also referenced in src/test/resources/logback-test.xml
  private static final String TEST_APPENDER_NAME = "TEST_APPENDER";
}
