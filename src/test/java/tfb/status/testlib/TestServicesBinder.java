package tfb.status.testlib;

import com.google.common.base.Ticker;
import io.undertow.server.HttpHandler;
import java.net.http.HttpClient;
import java.nio.file.FileSystem;
import java.time.Clock;
import javax.inject.Singleton;
import org.glassfish.hk2.api.InstanceLifecycleListener;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.threeten.extra.MutableClock;

/**
 * Registers additional service classes during tests.
 */
final class TestServicesBinder extends AbstractBinder {
  @Override
  protected void configure() {
    bindFactory(MutableClockFactory.class, Singleton.class)
        .to(Clock.class)
        .to(MutableClock.class)
        .in(Singleton.class)
        .ranked(1); // override the default clock

    bindFactory(MutableTickerFactory.class, Singleton.class)
        .to(Ticker.class)
        .to(MutableTicker.class)
        .in(Singleton.class)
        .ranked(1); // override the default ticker

    bindFactory(InMemoryFileSystemFactory.class, Singleton.class)
        .to(FileSystem.class)
        .in(Singleton.class)
        .ranked(1); // override the default file system

    bindFactory(HttpClientFactory.class, Singleton.class)
        .to(HttpClient.class)
        .in(Singleton.class);

    bind(TestRouter.class)
        .to(HttpHandler.class)
        .to(TestRouter.class)
        .in(Singleton.class)
        .ranked(1); // override the default router

    bindAsContract(HttpTester.class).in(Singleton.class);

    bindAsContract(MailServer.class).in(Singleton.class);

    bind(MailServerDependency.class)
        .to(InstanceLifecycleListener.class)
        .in(Singleton.class);
  }
}
