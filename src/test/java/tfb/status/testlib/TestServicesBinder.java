package tfb.status.testlib;

import tfb.status.bootstrap.ServicesBinder;
import tfb.status.hk2.extensions.Provides;
import tfb.status.hk2.extensions.Registers;

/**
 * Registers additional service classes during tests.
 */
@Registers({
    MutableClockFactory.class,
    MutableTickerFactory.class,
    InMemoryFileSystemFactory.class,
    HttpClientFactory.class,
    TestHandler.class,
    LogTester.class,
    HttpTester.class,
    MailServer.class,
    MailDelay.class,
    MailServerDependency.class,
    ResultsTester.class
})
final class TestServicesBinder {
  private TestServicesBinder() {
    throw new AssertionError("This class cannot be instantiated");
  }

  @Provides
  public static ServicesBinder coreServices() {
    return new ServicesBinder("test_config.yml");
  }
}
