package tfb.status.testlib;

import org.glassfish.hk2.utilities.binding.AbstractBinder;

/**
 * Registers additional service classes during tests.
 */
final class TestServicesBinder extends AbstractBinder {
  @Override
  protected void configure() {
    addActiveFactoryDescriptor(MutableClockFactory.class);
    addActiveFactoryDescriptor(MutableClockFactory.OverrideDefault.class);
    addActiveFactoryDescriptor(MutableTickerFactory.class);
    addActiveFactoryDescriptor(MutableTickerFactory.OverrideDefault.class);
    addActiveFactoryDescriptor(InMemoryFileSystemFactory.class);
    addActiveFactoryDescriptor(HttpClientFactory.class);

    addActiveDescriptor(TestHandler.class);
    addActiveDescriptor(LogTester.class);
    addActiveDescriptor(HttpTester.class);
    addActiveDescriptor(MailServer.class);
    addActiveDescriptor(MailDelay.class);
    addActiveDescriptor(MailServerDependency.class);
  }
}
