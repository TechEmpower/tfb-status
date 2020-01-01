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
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.api.IterableProvider;
import org.glassfish.hk2.api.MultiException;
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
import tfb.status.hk2.extensions.Provides;
import tfb.status.hk2.extensions.Services;

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
   * MultiException} when there is a service of the specified type but it has
   * unsatisfied dependencies.
   */
  @Test
  public void testGetServiceWithUnsatisfiedDependencies() {
    Services services = newServices();

    assertThrows(
        MultiException.class,
        () -> services.getService(UnsatisfiedDependencies.class));
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
   * Verifies that a service with {@link Provides} annotations can also be its
   * own service that can be retrieved.
   */
  @Test
  public void testServiceThatProvidesAndIsAService() {
    Services services = newServices();

    ProvidesService service =
        services.getService(ProvidesService.class);

    assertNotNull(service);
  }

  /**
   * Verifies that a service may be registered by way of a static field
   * annotated with {@link Provides}.
   */
  @Test
  public void testStaticFieldProvides() {
    Services services = newServices();

    ProvidedByStaticField service =
        services.getService(ProvidedByStaticField.class);

    assertNotNull(service);
  }

  /**
   * Verifies that a service may be registered by way of an instance field
   * annotated with {@link Provides}.
   */
  @Test
  public void testInstanceFieldProvides() {
    Services services = newServices();

    ProvidedByInstanceField service =
        services.getService(ProvidedByInstanceField.class);

    assertNotNull(service);
  }

  /**
   * Verifies that a service may be registered by way of a static method
   * annotated with {@link Provides} when the method has zero parameters.
   */
  @Test
  public void testStaticMethodProvides() {
    Services services = newServices();

    ProvidedByStaticMethod service =
        services.getService(ProvidedByStaticMethod.class);

    assertNotNull(service);
  }

  /**
   * Verifies that a service may be registered by way of an instance method
   * annotated with {@link Provides} when the method has zero parameters.
   */
  @Test
  public void testInstanceMethodProvides() {
    Services services = newServices();

    ProvidedByInstanceMethod service =
        services.getService(ProvidedByInstanceMethod.class);

    assertNotNull(service);
  }

  /**
   * Verifies that a service may be registered by way of a static method
   * annotated with {@link Provides} when the method has parameters.
   */
  @Test
  public void testStaticMethodWithParamsProvides() {
    Services services = newServices();

    ProvidedByStaticMethodWithParams service =
        services.getService(ProvidedByStaticMethodWithParams.class);

    assertNotNull(service);
    assertNotNull(service.param1);
    assertNotNull(service.param2);
  }

  /**
   * Verifies that a service may be registered by way of an instance method
   * annotated with {@link Provides} when the method has parameters.
   */
  @Test
  public void testInstanceMethodWithParamsProvides() {
    Services services = newServices();

    ProvidedByInstanceMethodWithParams service =
        services.getService(ProvidedByInstanceMethodWithParams.class);

    assertNotNull(service);
    assertNotNull(service.param1);
    assertNotNull(service.param2);
  }

  /**
   * Verifies that when a service is registered by by of a static field
   * annotated with {@link Provides}, and the service it provides also has its
   * own {@link Provides} annotations, those other services are automatically
   * registered.
   */
  @Test
  public void testStaticFieldProvidesChain() {
    Services services = newServices();

    MiddleOfStaticFieldProvidesChain middle =
        services.getService(MiddleOfStaticFieldProvidesChain.class);

    assertNotNull(middle);

    EndOfStaticFieldProvidesChain end =
        services.getService(EndOfStaticFieldProvidesChain.class);

    assertNotNull(end);
  }

  /**
   * Verifies that when a service is registered by by of an instance field
   * annotated with {@link Provides}, and the service it provides also has its
   * own {@link Provides} annotations, those other services are automatically
   * registered.
   */
  @Test
  public void testInstanceFieldProvidesChain() {
    Services services = newServices();

    MiddleOfInstanceFieldProvidesChain middle =
        services.getService(MiddleOfInstanceFieldProvidesChain.class);

    assertNotNull(middle);

    EndOfInstanceFieldProvidesChain end =
        services.getService(EndOfInstanceFieldProvidesChain.class);

    assertNotNull(end);
  }

  /**
   * Verifies that when a service is registered by by of a static method
   * annotated with {@link Provides}, and the service it provides also has its
   * own {@link Provides} annotations, those other services are automatically
   * registered.
   */
  @Test
  public void testStaticMethodProvidesChain() {
    Services services = newServices();

    MiddleOfStaticMethodProvidesChain middle =
        services.getService(MiddleOfStaticMethodProvidesChain.class);

    assertNotNull(middle);

    EndOfStaticMethodProvidesChain end =
        services.getService(EndOfStaticMethodProvidesChain.class);

    assertNotNull(end);
  }

  /**
   * Verifies that when a service is registered by by of an instance method
   * annotated with {@link Provides}, and the service it provides also has its
   * own {@link Provides} annotations, those other services are automatically
   * registered.
   */
  @Test
  public void testInstanceMethodProvidesChain() {
    Services services = newServices();

    MiddleOfInstanceMethodProvidesChain middle =
        services.getService(MiddleOfInstanceMethodProvidesChain.class);

    assertNotNull(middle);

    EndOfInstanceMethodProvidesChain end =
        services.getService(EndOfInstanceMethodProvidesChain.class);

    assertNotNull(end);
  }

  /**
   * Verifies that a service with a generic type can be registered from a method
   * annotated with {@link Provides}.
   */
  @Test
  public void testGenericMethodProvides() {
    Services services = newServices();

    GenericFromProvidesMethod<String> service =
        services.getService(new TypeToken<GenericFromProvidesMethod<String>>() {});

    assertNotNull(service);

    assertThrows(
        NoSuchElementException.class,
        () -> services.getService(new TypeToken<GenericFromProvidesMethod<Integer>>() {}));
  }

  /**
   * Verifies that a service with a generic type can be registered from a field
   * annotated with {@link Provides}.
   */
  @Test
  public void testGenericFieldProvides() {
    Services services = newServices();

    GenericFromProvidesField<String> service =
        services.getService(new TypeToken<GenericFromProvidesField<String>>() {});

    assertNotNull(service);

    assertThrows(
        NoSuchElementException.class,
        () -> services.getService(new TypeToken<GenericFromProvidesField<Integer>>() {}));
  }

  /**
   * Verifies that a service class may provide itself by way of a static {@link
   * Provides} field even when the class has no constructors compatible with
   * injection.
   */
  @Test
  public void testServiceProvidesItselfFromField() {
    Services services = newServices();

    ProvidesSelfFromMethod self =
        services.getService(ProvidesSelfFromMethod.class);

    assertNotNull(self);
    assertNotNull(self.nonService);
    assertEquals("hi", self.nonService.message);
    assertNotNull(self.otherService1);
    assertNotNull(self.otherService2);
  }

  /**
   * Verifies that a service class may provide itself by way of a static {@link
   * Provides} method even when the class has no constructors compatible with
   * injection.
   */
  @Test
  public void testServiceProvidesItselfFromMethod() {
    Services services = newServices();

    ProvidesSelfFromMethod self =
        services.getService(ProvidesSelfFromMethod.class);

    assertNotNull(self);
    assertNotNull(self.nonService);
    assertEquals("hi", self.nonService.message);
    assertNotNull(self.otherService1);
    assertNotNull(self.otherService2);
  }

  /**
   * Verifies that the {@link PostConstruct} and {@link PreDestroy} methods of a
   * service are invoked at the expected times when the service was registered
   * from a static {@link Provides} field and the service is a singleton.
   */
  @Test
  public void testStaticFieldProvidesSingletonLifecycle() {
    Services services = newServices();

    ProvidedSingletonStaticFieldWithLifecycle service =
        services.getService(ProvidedSingletonStaticFieldWithLifecycle.class);

    assertNotNull(service);
    assertTrue(service.wasStarted());
    assertFalse(service.wasStopped());

    assertSame(
        service,
        services.getService(ProvidedSingletonStaticFieldWithLifecycle.class));

    services.shutdown();

    assertTrue(service.wasStopped());
  }

  /**
   * Verifies that the {@link PostConstruct} and {@link PreDestroy} methods of a
   * service are invoked at the expected times when the service was registered
   * from an instance {@link Provides} field and the service is a singleton.
   */
  @Test
  public void testInstanceFieldProvidesSingletonLifecycle() {
    Services services = newServices();

    ProvidedSingletonInstanceFieldWithLifecycle service =
        services.getService(ProvidedSingletonInstanceFieldWithLifecycle.class);

    assertNotNull(service);
    assertTrue(service.wasStarted());
    assertFalse(service.wasStopped());

    assertSame(
        service,
        services.getService(ProvidedSingletonInstanceFieldWithLifecycle.class));

    services.shutdown();

    assertTrue(service.wasStopped());
  }

  /**
   * Verifies that the {@link PostConstruct} and {@link PreDestroy} methods of a
   * service are invoked at the expected times when the service was registered
   * from a static {@link Provides} method and the service is a singleton.
   */
  @Test
  public void testStaticMethodProvidesSingletonLifecycle() {
    Services services = newServices();

    ProvidedSingletonStaticMethodWithLifecycle service =
        services.getService(ProvidedSingletonStaticMethodWithLifecycle.class);

    assertNotNull(service);
    assertTrue(service.wasStarted());
    assertFalse(service.wasStopped());

    assertSame(
        service,
        services.getService(ProvidedSingletonStaticMethodWithLifecycle.class));

    services.shutdown();

    assertTrue(service.wasStopped());
  }

  /**
   * Verifies that the {@link PostConstruct} and {@link PreDestroy} methods of a
   * service are invoked at the expected times when the service was registered
   * from an instance {@link Provides} method and the service is a singleton.
   */
  @Test
  public void testInstanceMethodProvidesSingletonLifecycle() {
    Services services = newServices();

    ProvidedSingletonInstanceMethodWithLifecycle service =
        services.getService(ProvidedSingletonInstanceMethodWithLifecycle.class);

    assertNotNull(service);
    assertTrue(service.wasStarted());
    assertFalse(service.wasStopped());

    assertSame(
        service,
        services.getService(ProvidedSingletonInstanceMethodWithLifecycle.class));

    services.shutdown();

    assertTrue(service.wasStopped());
  }

  /**
   * Verifies that the {@link PostConstruct} and {@link PreDestroy} methods of a
   * service are invoked at the expected times when the service was registered
   * from a static {@link Provides} field and the service is per-lookup, and the
   * service is not retrieved through a {@link ServiceHandle}.
   */
  @Test
  public void testStaticFieldProvidesPerLookupLifecycleWithoutHandle() {
    Services services = newServices();

    ProvidedPerLookupStaticFieldWithLifecycleWithoutHandle serviceWithoutHandle =
        services.getService(ProvidedPerLookupStaticFieldWithLifecycleWithoutHandle.class);

    assertNotNull(serviceWithoutHandle);
    assertTrue(serviceWithoutHandle.wasStarted());
    assertFalse(serviceWithoutHandle.wasStopped());

    // Avoid making any assumptions regarding the sameness of this instance and
    // other instances.  The static field might hold one fixed instance, or a
    // new value may be written to the field from time to time.  It doesn't
    // matter to us.

    services.shutdown();

    assertFalse(serviceWithoutHandle.wasStopped());
  }

  /**
   * Verifies that the {@link PostConstruct} and {@link PreDestroy} methods of a
   * service are invoked at the expected times when the service was registered
   * from a static {@link Provides} field and the service is per-lookup, and the
   * service is retrieved through a {@link ServiceHandle}.
   */
  @Test
  public void testStaticFieldProvidesPerLookupLifecycleWithHandle() {
    Services services = newServices();

    IterableProvider<ProvidedPerLookupStaticFieldWithLifecycleWithHandle> serviceProvider =
        services.getService(
            new TypeToken<IterableProvider<ProvidedPerLookupStaticFieldWithLifecycleWithHandle>>() {});

    ServiceHandle<ProvidedPerLookupStaticFieldWithLifecycleWithHandle> serviceHandle =
        serviceProvider.getHandle();

    ProvidedPerLookupStaticFieldWithLifecycleWithHandle serviceWithHandle =
        serviceHandle.getService();

    assertNotNull(serviceWithHandle);
    assertTrue(serviceWithHandle.wasStarted());
    assertFalse(serviceWithHandle.wasStopped());

    serviceHandle.close();

    assertTrue(serviceWithHandle.wasStopped());
  }

  /**
   * Verifies that the {@link PostConstruct} and {@link PreDestroy} methods of a
   * service are invoked at the expected times when the service was registered
   * from an instance {@link Provides} field and the service is per-lookup.
   */
  @Test
  public void testInstanceFieldProvidesPerLookupLifecycle() {
    Services services = newServices();

    ProvidedPerLookupInstanceFieldWithLifecycle serviceWithoutHandle =
        services.getService(ProvidedPerLookupInstanceFieldWithLifecycle.class);

    assertNotNull(serviceWithoutHandle);
    assertTrue(serviceWithoutHandle.wasStarted());
    assertFalse(serviceWithoutHandle.wasStopped());

    assertNotSame(
        serviceWithoutHandle,
        services.getService(ProvidedPerLookupInstanceFieldWithLifecycle.class));

    IterableProvider<ProvidedPerLookupInstanceFieldWithLifecycle> serviceProvider =
        services.getService(
            new TypeToken<IterableProvider<ProvidedPerLookupInstanceFieldWithLifecycle>>() {});

    ServiceHandle<ProvidedPerLookupInstanceFieldWithLifecycle> serviceHandle =
        serviceProvider.getHandle();

    ProvidedPerLookupInstanceFieldWithLifecycle serviceWitHandle =
        serviceHandle.getService();

    assertNotNull(serviceWitHandle);
    assertTrue(serviceWitHandle.wasStarted());
    assertFalse(serviceWitHandle.wasStopped());

    serviceHandle.close();

    assertTrue(serviceWitHandle.wasStopped());

    services.shutdown();

    assertFalse(serviceWithoutHandle.wasStopped());
  }

  /**
   * Verifies that the {@link PostConstruct} and {@link PreDestroy} methods of a
   * service are invoked at the expected times when the service was registered
   * from a static {@link Provides} method and the service is per-lookup.
   */
  @Test
  public void testStaticMethodProvidesPerLookupLifecycle() {
    Services services = newServices();

    ProvidedPerLookupStaticMethodWithLifecycle serviceWithoutHandle =
        services.getService(ProvidedPerLookupStaticMethodWithLifecycle.class);

    assertNotNull(serviceWithoutHandle);
    assertTrue(serviceWithoutHandle.wasStarted());
    assertFalse(serviceWithoutHandle.wasStopped());

    assertNotSame(
        serviceWithoutHandle,
        services.getService(ProvidedPerLookupStaticMethodWithLifecycle.class));

    IterableProvider<ProvidedPerLookupStaticMethodWithLifecycle> serviceProvider =
        services.getService(
            new TypeToken<IterableProvider<ProvidedPerLookupStaticMethodWithLifecycle>>() {});

    ServiceHandle<ProvidedPerLookupStaticMethodWithLifecycle> serviceHandle =
        serviceProvider.getHandle();

    ProvidedPerLookupStaticMethodWithLifecycle serviceWitHandle =
        serviceHandle.getService();

    assertNotNull(serviceWitHandle);
    assertTrue(serviceWitHandle.wasStarted());
    assertFalse(serviceWitHandle.wasStopped());

    serviceHandle.close();

    assertTrue(serviceWitHandle.wasStopped());

    services.shutdown();

    assertFalse(serviceWithoutHandle.wasStopped());
  }

  /**
   * Verifies that the {@link PostConstruct} and {@link PreDestroy} methods of a
   * service are invoked at the expected times when the service was registered
   * from an instance {@link Provides} method and the service is per-lookup.
   */
  @Test
  public void testInstanceMethodProvidesPerLookupLifecycle() {
    Services services = newServices();

    ProvidedPerLookupInstanceMethodWithLifecycle serviceWithoutHandle =
        services.getService(ProvidedPerLookupInstanceMethodWithLifecycle.class);

    assertNotNull(serviceWithoutHandle);
    assertTrue(serviceWithoutHandle.wasStarted());
    assertFalse(serviceWithoutHandle.wasStopped());

    assertNotSame(
        serviceWithoutHandle,
        services.getService(ProvidedPerLookupInstanceMethodWithLifecycle.class));

    IterableProvider<ProvidedPerLookupInstanceMethodWithLifecycle> serviceProvider =
        services.getService(
            new TypeToken<IterableProvider<ProvidedPerLookupInstanceMethodWithLifecycle>>() {});

    ServiceHandle<ProvidedPerLookupInstanceMethodWithLifecycle> serviceHandle =
        serviceProvider.getHandle();

    ProvidedPerLookupInstanceMethodWithLifecycle serviceWitHandle =
        serviceHandle.getService();

    assertNotNull(serviceWitHandle);
    assertTrue(serviceWitHandle.wasStarted());
    assertFalse(serviceWitHandle.wasStopped());

    serviceHandle.close();

    assertTrue(serviceWitHandle.wasStopped());

    services.shutdown();

    assertFalse(serviceWithoutHandle.wasStopped());
  }

  /**
   * Verifies that a utility class may be registered as a service when it has
   * static methods or fields annotated with {@link Provides}, and verifies that
   * the utility class itself cannot be fetched as a service.
   */
  @Test
  public void testUtilityClassProvides() {
    Services services = newServices();

    assertNotNull(services.getService(FromUtilityClassMethod.class));
    assertNotNull(services.getService(FromUtilityClassField.class));

    assertThrows(
        NoSuchElementException.class,
        () -> services.getService(UtilityClassProvides.class));
  }

  /**
   * Verifies that an abstract class may be registered as a service when it has
   * static methods or fields annotated with {@link Provides}, and verifies that
   * the abstract class itself cannot be fetched as a service.
   */
  @Test
  public void testAbstractClassProvides() {
    Services services = newServices();

    assertNotNull(services.getService(FromAbstractClassMethod.class));
    assertNotNull(services.getService(FromAbstractClassField.class));

    assertThrows(
        NoSuchElementException.class,
        () -> services.getService(AbstractClassProvides.class));
  }

  /**
   * Verifies that an interface may be registered as a service when it has
   * static methods or fields annotated with {@link Provides}, and verifies that
   * the interface itself cannot be fetched as a service.
   */
  @Test
  public void testInterfaceProvides() {
    Services services = newServices();

    assertNotNull(services.getService(FromInterfaceMethod.class));
    assertNotNull(services.getService(FromInterfaceField.class));

    assertThrows(
        NoSuchElementException.class,
        () -> services.getService(InterfaceProvides.class));
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
            addActiveDescriptor(ProvidesService.class);
            addActiveDescriptor(ProvidesSelfFromMethod.class);
            addActiveDescriptor(ProvidesSelfFromField.class);
            addActiveDescriptor(UtilityClassProvides.class);
            addActiveDescriptor(AbstractClassProvides.class);
            addActiveDescriptor(InterfaceProvides.class);
            addActiveDescriptor(UnsatisfiedDependencies.class);
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

  public static final class ProvidedByStaticField {}
  public static final class ProvidedByInstanceField {}
  public static final class ProvidedByStaticMethod {}
  public static final class ProvidedByInstanceMethod {}

  public static final class ProvidedByStaticMethodWithParams {
    public final PerLookupService param1;
    public final SingletonService param2;

    ProvidedByStaticMethodWithParams(PerLookupService param1,
                                     SingletonService param2) {
      this.param1 = Objects.requireNonNull(param1);
      this.param2 = Objects.requireNonNull(param2);
    }
  }

  public static final class ProvidedByInstanceMethodWithParams {
    public final PerLookupService param1;
    public final SingletonService param2;

    ProvidedByInstanceMethodWithParams(PerLookupService param1,
                                       SingletonService param2) {
      this.param1 = Objects.requireNonNull(param1);
      this.param2 = Objects.requireNonNull(param2);
    }
  }

  public static class ProvidedPerLookupStaticFieldWithLifecycleWithHandle extends ServiceWithLifecycle {}
  public static class ProvidedPerLookupStaticFieldWithLifecycleWithoutHandle extends ServiceWithLifecycle {}
  public static class ProvidedPerLookupInstanceFieldWithLifecycle extends ServiceWithLifecycle {}
  public static class ProvidedPerLookupStaticMethodWithLifecycle extends ServiceWithLifecycle {}
  public static class ProvidedPerLookupInstanceMethodWithLifecycle extends ServiceWithLifecycle {}

  @Singleton public static class ProvidedSingletonStaticFieldWithLifecycle extends ServiceWithLifecycle {}
  @Singleton public static class ProvidedSingletonInstanceFieldWithLifecycle extends ServiceWithLifecycle {}
  @Singleton public static class ProvidedSingletonStaticMethodWithLifecycle extends ServiceWithLifecycle {}
  @Singleton public static class ProvidedSingletonInstanceMethodWithLifecycle extends ServiceWithLifecycle {}

  public static final class ProvidesService {
    @Provides
    public static final ProvidedByStaticField staticField =
        new ProvidedByStaticField();

    @Provides
    public final ProvidedByInstanceField instanceField =
        new ProvidedByInstanceField();

    @Provides
    public static ProvidedByStaticMethod staticMethod() {
      return new ProvidedByStaticMethod();
    }

    @Provides
    public ProvidedByInstanceMethod instanceMethod() {
      return new ProvidedByInstanceMethod();
    }

    @Provides
    public static ProvidedByStaticMethodWithParams staticMethodWithParams(
        PerLookupService param1,
        SingletonService param2) {
      return new ProvidedByStaticMethodWithParams(param1, param2);
    }

    @Provides
    public ProvidedByInstanceMethodWithParams instanceMethodWithParams(
        PerLookupService param1,
        SingletonService param2) {
      return new ProvidedByInstanceMethodWithParams(param1, param2);
    }

    @Provides
    public static MiddleOfStaticMethodProvidesChain nextStaticMethod() {
      return new MiddleOfStaticMethodProvidesChain();
    }

    @Provides
    public MiddleOfInstanceMethodProvidesChain nextInstanceMethod() {
      return new MiddleOfInstanceMethodProvidesChain();
    }

    @Provides
    public static final MiddleOfStaticFieldProvidesChain nextStaticField =
        new MiddleOfStaticFieldProvidesChain();

    @Provides
    public final MiddleOfInstanceFieldProvidesChain nextInstanceField =
        new MiddleOfInstanceFieldProvidesChain();

    @Provides
    public GenericFromProvidesMethod<String> generic() {
      return new GenericFromProvidesMethod<>();
    }

    @Provides
    public final GenericFromProvidesField<String> generic =
        new GenericFromProvidesField<>();

    @Provides
    public static final ProvidedPerLookupStaticFieldWithLifecycleWithHandle perLookupStaticFieldLifecycleWithHandle =
        new ProvidedPerLookupStaticFieldWithLifecycleWithHandle();

    @Provides
    public static final ProvidedPerLookupStaticFieldWithLifecycleWithoutHandle perLookupStaticFieldLifecycleWithoutHandle =
        new ProvidedPerLookupStaticFieldWithLifecycleWithoutHandle();

    @Provides
    public final ProvidedPerLookupInstanceFieldWithLifecycle perLookupInstanceFieldLifecycle =
        new ProvidedPerLookupInstanceFieldWithLifecycle();

    @Provides
    public static ProvidedPerLookupStaticMethodWithLifecycle perLookupStaticMethodLifecycle() {
      return new ProvidedPerLookupStaticMethodWithLifecycle();
    }

    @Provides
    public ProvidedPerLookupInstanceMethodWithLifecycle perLookupInstanceMethodLifecycle() {
      return new ProvidedPerLookupInstanceMethodWithLifecycle();
    }

    @Provides
    public static final ProvidedSingletonStaticFieldWithLifecycle singletonStaticFieldLifecycle =
        new ProvidedSingletonStaticFieldWithLifecycle();

    @Provides
    public final ProvidedSingletonInstanceFieldWithLifecycle singletonInstanceFieldLifecycle =
        new ProvidedSingletonInstanceFieldWithLifecycle();

    @Provides
    public static ProvidedSingletonStaticMethodWithLifecycle singletonStaticMethodLifecycle() {
      return new ProvidedSingletonStaticMethodWithLifecycle();
    }

    @Provides
    public ProvidedSingletonInstanceMethodWithLifecycle singletonInstanceMethodLifecycle() {
      return new ProvidedSingletonInstanceMethodWithLifecycle();
    }
  }

  public static final class MiddleOfStaticMethodProvidesChain {
    @Provides
    public static EndOfStaticMethodProvidesChain next() {
      return new EndOfStaticMethodProvidesChain();
    }

    // Avoid "utility class with non-private constructor" warnings.
    public final int x = 2;
  }

  public static final class MiddleOfInstanceMethodProvidesChain {
    @Provides
    public EndOfInstanceMethodProvidesChain next() {
      return new EndOfInstanceMethodProvidesChain();
    }
  }

  public static final class MiddleOfStaticFieldProvidesChain {
    @Provides
    public static final EndOfStaticFieldProvidesChain next =
        new EndOfStaticFieldProvidesChain();

    // Avoid "utility class with non-private constructor" warnings.
    public final int x = 2;
  }

  public static final class MiddleOfInstanceFieldProvidesChain {
    @Provides
    public final EndOfInstanceFieldProvidesChain next =
        new EndOfInstanceFieldProvidesChain();
  }

  public static final class EndOfStaticFieldProvidesChain {}
  public static final class EndOfInstanceFieldProvidesChain {}
  public static final class EndOfStaticMethodProvidesChain {}
  public static final class EndOfInstanceMethodProvidesChain {}

  public static final class GenericFromProvidesMethod<T> {}

  public static final class GenericFromProvidesField<T> {}

  public static final class ProvidesSelfFromMethod {
    public final ExoticNonServiceType nonService;
    public final PerLookupService otherService1;
    public final SingletonService otherService2;

    private ProvidesSelfFromMethod(ExoticNonServiceType nonService,
                                   PerLookupService otherService1,
                                   SingletonService otherService2) {
      this.nonService = Objects.requireNonNull(nonService);
      this.otherService1 = Objects.requireNonNull(otherService1);
      this.otherService2 = Objects.requireNonNull(otherService2);
    }

    @Provides
    public static ProvidesSelfFromMethod create(PerLookupService otherService1,
                                                SingletonService otherService2) {
      return new ProvidesSelfFromMethod(
          new ExoticNonServiceType("hi"),
          otherService1,
          otherService2);
    }
  }

  public static final class ProvidesSelfFromField {
    public final ExoticNonServiceType nonService;

    private ProvidesSelfFromField(ExoticNonServiceType nonService) {
      this.nonService = Objects.requireNonNull(nonService);
    }

    @Provides
    public static final ProvidesSelfFromField instance =
      new ProvidesSelfFromField(new ExoticNonServiceType("hi"));
  }

  public static final class ExoticNonServiceType {
    public final String message;

    ExoticNonServiceType(String message) {
      this.message = Objects.requireNonNull(message);
    }
  }

  public static final class UtilityClassProvides {
    private UtilityClassProvides() {
      throw new AssertionError("This class cannot be instantiated");
    }

    @Provides
    public static FromUtilityClassMethod method() {
      return new FromUtilityClassMethod();
    }

    @Provides
    public static final FromUtilityClassField field =
        new FromUtilityClassField();
  }

  public static final class FromUtilityClassMethod {}
  public static final class FromUtilityClassField {}

  public abstract static class AbstractClassProvides {
    @Provides
    public static FromAbstractClassMethod method() {
      return new FromAbstractClassMethod();
    }

    @Provides
    public static final FromAbstractClassField field =
        new FromAbstractClassField();
  }

  public static final class FromAbstractClassMethod {}
  public static final class FromAbstractClassField {}

  public interface InterfaceProvides {
    @Provides
    static FromInterfaceMethod method() {
      return new FromInterfaceMethod();
    }

    @Provides
    FromInterfaceField field = new FromInterfaceField();
  }

  public static final class FromInterfaceMethod {}
  public static final class FromInterfaceField {}

  public static final class UnsatisfiedDependencies {
    @Inject
    public UnsatisfiedDependencies(UnregisteredService dependency) {}
  }
}
