package tfb.status.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.reflect.TypeToken;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.api.IterableProvider;
import org.glassfish.hk2.api.PerLookup;
import org.glassfish.hk2.api.PostConstruct;
import org.glassfish.hk2.api.PreDestroy;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.messaging.MessageReceiver;
import org.glassfish.hk2.api.messaging.SubscribeTo;
import org.glassfish.hk2.api.messaging.Topic;
import org.glassfish.hk2.api.messaging.TopicDistributionService;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.junit.jupiter.api.Test;
import org.jvnet.hk2.annotations.Contract;

/**
 * Tests for {@link Services}.
 */
public final class ServicesTest {
  /**
   * Verifies that every invocation of {@link Services#getService(Class)}
   * returns the same instance when the service has the {@link Singleton} scope.
   */
  @Test
  public void testGetSingletonService() {
    Services services = newServices();

    SingletonService service1 = services.getService(SingletonService.class);
    SingletonService service2 = services.getService(SingletonService.class);

    assertNotNull(service1);
    assertNotNull(service2);
    assertSame(service1, service2);
  }

  /**
   * Verifies that every invocation of {@link Services#getService(Class)}
   * returns a new instance when the service has the {@link PerLookup} scope,
   * which is the default scope.
   */
  @Test
  public void testGetPerLookupService() {
    Services services = newServices();

    PerLookupService service1 = services.getService(PerLookupService.class);
    PerLookupService service2 = services.getService(PerLookupService.class);

    assertNotNull(service1);
    assertNotNull(service2);
    assertNotSame(service1, service2);
  }

  /**
   * Verifies that {@link Services#getService(Class)} throws {@link
   * NoSuchElementException} when there is no service of the specified type.
   */
  @Test
  public void testGetUnregisteredService() {
    Services services = newServices();

    assertThrows(
        NoSuchElementException.class,
        () -> services.getService(UnregisteredService.class));

    Iterable<UnregisteredService> iterable =
        services.getService(
            new TypeToken<Iterable<UnregisteredService>>() {});

    Iterator<UnregisteredService> iterator = iterable.iterator();

    assertFalse(iterator.hasNext());
  }

  /**
   * Verifies that {@link Services#getService(Class)} throws {@link
   * NoSuchElementException} when there is a service of the specified type but
   * its provider provided {@code null}.
   */
  @Test
  public void testGetNullService() {
    Services services = newServices();

    assertThrows(
        NoSuchElementException.class,
        () -> services.getService(NullService.class));

    // Assert that there is one "instance" of the service, but it's null.
    Iterable<NullService> iterable =
        services.getService(
            new TypeToken<Iterable<NullService>>() {});

    Iterator<NullService> iterator = iterable.iterator();

    assertTrue(iterator.hasNext());
    assertNull(iterator.next());
    assertFalse(iterator.hasNext());
  }

  /**
   * Verifies that {@link Services#getService(TypeToken)} can produce an {@link
   * Optional} of a registered service type, and that the returned optional
   * contains an instance of that service.
   */
  @Test
  public void testGetOptional() {
    Services services = newServices();

    Optional<PerLookupService> optional =
        services.getService(new TypeToken<Optional<PerLookupService>>() {});

    assertTrue(optional.isPresent());
  }

  /**
   * Verifies that {@link Services#getService(TypeToken)} can produce an {@link
   * Optional} even when the service type is unregistered, and that the returned
   * optional is empty.
   */
  @Test
  public void testGetUnregisteredOptional() {
    Services services = newServices();

    Optional<UnregisteredService> optional =
        services.getService(new TypeToken<Optional<UnregisteredService>>() {});

    assertTrue(optional.isEmpty());
  }

  /**
   * Verifies that {@link Services#getService(TypeToken)} can produce a {@link
   * Provider} of a registered service type, and that the returned provider's
   * {@link Provider#get()} method provides an instance of that service.
   */
  @Test
  public void testGetProvider() {
    Services services = newServices();

    Provider<PerLookupService> provider =
        services.getService(new TypeToken<Provider<PerLookupService>>() {});

    PerLookupService service = provider.get();

    assertNotNull(service);
  }

  /**
   * Verifies that {@link Services#getService(TypeToken)} can produce a {@link
   * Provider} even when the service type is unregistered, and that the returned
   * provider's {@link Provider#get()} method returns {@code null}.
   */
  @Test
  public void testGetUnregisteredProvider() {
    Services services = newServices();

    Provider<UnregisteredService> provider =
        services.getService(new TypeToken<Provider<UnregisteredService>>() {});

    UnregisteredService service = provider.get();

    assertNull(service);
  }

  /**
   * Verifies that {@link Services#getService(TypeToken)} can produce an {@link
   * Iterable} of a registered contract type, and that the returned iterable
   * contains one element for each service registered with that contract.
   */
  @Test
  public void testGetIterable() {
    Services services = newServices();

    Iterable<SimpleContract> provider =
        services.getService(
            new TypeToken<Iterable<SimpleContract>>() {});

    Iterator<SimpleContract> iterator = provider.iterator();

    assertTrue(iterator.hasNext());

    SimpleContract service1 = iterator.next();

    assertTrue(iterator.hasNext());

    SimpleContract service2 = iterator.next();

    assertFalse(iterator.hasNext());

    assertNotNull(service1);
    assertNotNull(service2);
    assertNotSame(service1, service2);
  }

  /**
   * Verifies that {@link Services#getService(TypeToken)} can produce an {@link
   * Iterable} even when the service type is unregistered, and that the returned
   * iterable is empty.
   */
  @Test
  public void testGetUnregisteredIterable() {
    Services services = newServices();

    Iterable<UnregisteredService> provider =
        services.getService(
            new TypeToken<Iterable<UnregisteredService>>() {});

    assertFalse(provider.iterator().hasNext());
  }

  /**
   * Verifies that {@link Services#getService(TypeToken)} can produce an {@link
   * IterableProvider} of a registered contract type, and that the returned
   * provider's {@link IterableProvider#iterator()} contains one element for
   * each service registered with that contract.
   */
  @Test
  public void testGetIterableProvider() {
    Services services = newServices();

    IterableProvider<SimpleContract> provider =
        services.getService(
            new TypeToken<IterableProvider<SimpleContract>>() {});

    Iterator<SimpleContract> iterator = provider.iterator();

    assertTrue(iterator.hasNext());

    SimpleContract service1 = iterator.next();

    assertTrue(iterator.hasNext());

    SimpleContract service2 = iterator.next();

    assertFalse(iterator.hasNext());

    assertNotNull(service1);
    assertNotNull(service2);
    assertNotSame(service1, service2);
  }

  /**
   * Verifies that {@link Services#getService(TypeToken)} can produce an {@link
   * IterableProvider} even when the service type is unregistered, and that the
   * returned provider's {@link IterableProvider#iterator()} is empty.
   */
  @Test
  public void testGetUnregisteredIterableProvider() {
    Services services = newServices();

    IterableProvider<UnregisteredService> provider =
        services.getService(
            new TypeToken<IterableProvider<UnregisteredService>>() {});

    assertFalse(provider.iterator().hasNext());
  }

  /**
   * Verifies that {@link Services#getService(TypeToken)} can retrieve services
   * bound to generic contract types, and that it correctly distinguishes these
   * services from each other based on the generic type arguments of the
   * contract.
   */
  @Test
  public void testGetGenericService() {
    Services services = newServices();

    GenericContract<Integer> service1 =
        services.getService(new TypeToken<GenericContract<Integer>>() {});

    GenericContract<String> service2 =
        services.getService(new TypeToken<GenericContract<String>>() {});

    assertThrows(
        NoSuchElementException.class,
        () -> services.getService(new TypeToken<GenericContract<Double>>() {}));

    assertNotNull(service1);
    assertNotNull(service2);
    assertNotSame(service1, service2);
    assertEquals(1, service1.method());
    assertEquals("hello", service2.method());
  }

  /**
   * Verifies that {@link Services#shutdown()} invokes the {@link
   * PreDestroy#preDestroy()} methods of registered singleton services.
   */
  @Test
  public void testShutdownSingletonService() {
    Services services = newServices();

    SingletonServiceWithShutdown service =
        services.getService(SingletonServiceWithShutdown.class);

    assertFalse(service.wasStopped());

    services.shutdown();

    assertTrue(service.wasStopped());
  }

  /**
   * Verifies that {@link Services#shutdown()} invokes the {@link
   * Factory#dispose(Object)} methods of registered factories that produce
   * singleton services.
   */
  @Test
  public void testShutdownSingletonServiceFromFactory() {
    Services services = newServices();

    SingletonServiceWithShutdownFromFactory service =
        services.getService(SingletonServiceWithShutdownFromFactory.class);

    assertFalse(service.wasStopped());

    services.shutdown();

    assertTrue(service.wasStopped());
  }

  /**
   * Verifies that {@link ServiceHandle#close()} invokes the {@link
   * PreDestroy#preDestroy()} methods of registered per-lookup services.
   */
  @Test
  public void testShutdownPerLookupService() {
    Services services = newServices();

    IterableProvider<ServiceWithLifecycle> providers =
        services.getService(
            new TypeToken<IterableProvider<ServiceWithLifecycle>>() {});

    int loopCount = 2;
    int serviceCount = 0;

    for (int i = 0; i < loopCount; i++) {
      for (ServiceHandle<ServiceWithLifecycle> handle
          : providers.handleIterator()) {

        ServiceWithLifecycle service = handle.getService();
        assertFalse(service.wasStopped());
        handle.close();
        assertTrue(service.wasStopped());
        serviceCount++;
      }
    }

    // Assert that we saw one service during each iteration of the loop.
    assertEquals(loopCount, serviceCount);
  }

  /**
   * Verifies that {@link ServiceHandle#close()} invokes the {@link
   * Factory#dispose(Object)} methods of registered singleton factories.
   */
  @Test
  public void testShutdownPerLookupServiceFromFactory() {
    Services services = newServices();

    IterableProvider<ServiceWithShutdownFromFactory> providers =
        services.getService(
            new TypeToken<IterableProvider<ServiceWithShutdownFromFactory>>() {});

    int loopCount = 2;
    int serviceCount = 0;

    for (int i = 0; i < loopCount; i++) {
      for (ServiceHandle<ServiceWithShutdownFromFactory> handle
          : providers.handleIterator()) {

        ServiceWithLifecycle service = handle.getService();
        assertFalse(service.wasStopped());
        handle.close();
        assertTrue(service.wasStopped());
        serviceCount++;
      }
    }

    // Assert that we saw one service during each iteration of the loop.
    assertEquals(loopCount, serviceCount);
  }

  /**
   * Verifies that {@link Topic#publish(Object)} distributes the message to all
   * subscribers.  Verifies the existence of a {@link TopicDistributionService}.
   */
  @Test
  public void testTopics() {
    Services services = newServices();

    Topic<String> stringTopic =
        services.getService(new TypeToken<Topic<String>>() {});

    Topic<Integer> integerTopic = // subtype of Number, should be seen
        services.getService(new TypeToken<Topic<Integer>>() {});

    Topic<CharSequence> charSequenceTopic = // should be ignored
        services.getService(new TypeToken<Topic<CharSequence>>() {});

    stringTopic.publish("1");
    integerTopic.publish(2);
    charSequenceTopic.publish("3");

    SubscriberService service = services.getService(SubscriberService.class);

    assertEquals(
        List.of("1", 2),
        service.getMessages());

    List<ServiceWithLifecycle> service1List = service.getService1List();
    List<SingletonServiceWithShutdown> service2List = service.getService2List();

    List<Boolean> serviced1WasShutdown = service.getService1WasShutdown();
    List<Boolean> serviced2WasShutdown = service.getService2WasShutdown();

    assertEquals(2, service1List.size());
    assertEquals(2, service2List.size());

    assertEquals(2, serviced1WasShutdown.size());
    assertEquals(2, serviced2WasShutdown.size());

    assertFalse(serviced1WasShutdown.get(0));
    assertFalse(serviced1WasShutdown.get(1));
    assertTrue(service1List.get(0).wasStopped());
    assertTrue(service1List.get(1).wasStopped());
    assertNotSame(service1List.get(0), service1List.get(1));

    assertFalse(serviced2WasShutdown.get(0));
    assertFalse(serviced2WasShutdown.get(1));
    assertFalse(service2List.get(0).wasStopped());
    assertFalse(service2List.get(1).wasStopped());
    assertSame(service2List.get(0), service2List.get(1));
  }

  /**
   * Constructs a new set of services to be used in one test.
   */
  private Services newServices() {
    var binder =
        new AbstractBinder() {
          @Override
          protected void configure() {
            addActiveDescriptor(PerLookupService.class);
            addActiveDescriptor(SingletonService.class);
            addActiveFactoryDescriptor(NullFactory.class);
            addActiveDescriptor(ServiceWithContract1.class);
            addActiveDescriptor(ServiceWithContract2.class);
            addActiveDescriptor(ServiceWithGenericContract1.class);
            addActiveDescriptor(ServiceWithGenericContract2.class);
            addActiveDescriptor(ServiceWithLifecycle.class);
            addActiveDescriptor(SingletonServiceWithShutdown.class);
            addActiveFactoryDescriptor(FactoryOfServiceWithShutdown.class);
            addActiveFactoryDescriptor(FactoryOfSingletonServiceWithShutdown.class);
            addActiveDescriptor(SubscriberService.class);
          }
        };

    return new Services(binder);
  }

  public static final class PerLookupService {}

  @Singleton
  public static final class SingletonService {}

  @Contract
  public interface SimpleContract {}

  public static final class ServiceWithContract1 implements SimpleContract {}

  public static final class ServiceWithContract2 implements SimpleContract {}

  @Contract
  public interface GenericContract<T> {
    T method();
  }

  public static final class ServiceWithGenericContract1
      implements GenericContract<Integer> {

    @Override
    public Integer method() {
      return 1;
    }
  }

  public static final class ServiceWithGenericContract2
      implements GenericContract<String> {

    @Override
    public String method() {
      return "hello";
    }
  }

  public static class ServiceWithLifecycle implements PostConstruct, PreDestroy {
    @GuardedBy("this")
    private boolean wasStarted = false;

    @GuardedBy("this")
    private boolean wasStopped = false;

    public synchronized boolean wasStarted() {
      return wasStarted;
    }

    public synchronized boolean wasStopped() {
      return wasStopped;
    }

    public synchronized void start() {
      wasStarted = true;
    }

    public synchronized void stop() {
      wasStopped = true;
    }

    @Override
    public void postConstruct() {
      start();
    }

    @Override
    public void preDestroy() {
      stop();
    }
  }

  @Singleton
  public static class SingletonServiceWithShutdown
      extends ServiceWithLifecycle {}

  public static class ServiceWithShutdownFromFactory
      extends ServiceWithLifecycle {}

  @Singleton
  public static class SingletonServiceWithShutdownFromFactory
      extends ServiceWithLifecycle {}

  public static final class FactoryOfServiceWithShutdown
      implements Factory<ServiceWithShutdownFromFactory> {

    @Override
    public ServiceWithShutdownFromFactory provide() {
      return new ServiceWithShutdownFromFactory();
    }

    @Override
    public void dispose(ServiceWithShutdownFromFactory instance) {
      instance.stop();
    }
  }

  public static final class FactoryOfSingletonServiceWithShutdown
      implements Factory<SingletonServiceWithShutdownFromFactory> {

    @Override
    @Singleton
    public SingletonServiceWithShutdownFromFactory provide() {
      return new SingletonServiceWithShutdownFromFactory();
    }

    @Override
    public void dispose(SingletonServiceWithShutdownFromFactory instance) {
      instance.stop();
    }
  }

  public static final class UnregisteredService {}

  public static final class NullService {}

  public static final class NullFactory implements Factory<NullService> {
    @Override
    public @Nullable NullService provide() {
      return null;
    }

    @Override
    public void dispose(NullService instance) {
      // Do nothing.
    }
  }

  @Singleton
  @MessageReceiver({ String.class, Number.class })
  public static final class SubscriberService {
    @GuardedBy("this")
    private List<Object> messages = new ArrayList<>();

    @GuardedBy("this")
    private List<ServiceWithLifecycle> service1List = new ArrayList<>();

    @GuardedBy("this")
    private List<SingletonServiceWithShutdown> service2List = new ArrayList<>();

    @GuardedBy("this")
    private List<Boolean> service1WasShutdown = new ArrayList<>();

    @GuardedBy("this")
    private List<Boolean> service2WasShutdown = new ArrayList<>();

    public synchronized void onEvent(@SubscribeTo Object message,
                                     ServiceWithLifecycle service1,
                                     SingletonServiceWithShutdown service2) {
      messages.add(message);
      service1List.add(service1);
      service2List.add(service2);
      service1WasShutdown.add(service1.wasStopped());
      service2WasShutdown.add(service2.wasStopped());
    }

    public synchronized List<Object> getMessages() {
      return new ArrayList<>(messages);
    }

    public synchronized List<ServiceWithLifecycle> getService1List() {
      return new ArrayList<>(service1List);
    }

    public synchronized List<SingletonServiceWithShutdown> getService2List() {
      return new ArrayList<>(service2List);
    }

    public synchronized List<Boolean> getService1WasShutdown() {
      return new ArrayList<>(service1WasShutdown);
    }

    public synchronized List<Boolean> getService2WasShutdown() {
      return new ArrayList<>(service2WasShutdown);
    }
  }
}
