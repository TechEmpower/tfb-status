package tfb.status.testlib;

import org.glassfish.hk2.utilities.binding.AbstractBinder;
import tfb.status.bootstrap.ServicesBinder;

/**
 * Registers additional service classes during tests.
 */
final class TestServicesBinder extends AbstractBinder {
  @Override
  protected void configure() {
    install(new ServicesBinder("test_config.yml"));
    addActiveDescriptor(MutableClockFactory.class);
    addActiveDescriptor(MutableTickerFactory.class);
    addActiveDescriptor(InMemoryFileSystemFactory.class);
    addActiveDescriptor(HttpClientFactory.class);
    addActiveDescriptor(TestHandler.class);
    addActiveDescriptor(LogTester.class);
    addActiveDescriptor(HttpTester.class);
    addActiveDescriptor(MailServer.class);
    addActiveDescriptor(MailDelay.class);
    addActiveDescriptor(MailServerDependency.class);
    addActiveDescriptor(ResultsTester.class);
  }
}
