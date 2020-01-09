package tfb.status.hk2.extensions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.util.Iterator;
import java.util.NoSuchElementException;
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
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.api.TypeLiteral;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.junit.jupiter.api.Test;
import org.jvnet.hk2.annotations.Contract;

/**
 * Tests for {@link InjectUtils}.
 *
 * These tests focus on the {@link InjectUtils#getService(ServiceLocator,
 * TypeLiteral)} method since we rely on that method heavily on other tests.
 */
public final class InjectUtilsTest {
  /**
   * Verifies that every invocation of {@link
   * InjectUtils#getService(ServiceLocator, TypeLiteral)} returns the same
   * instance when the service has the {@link Singleton} scope.
   */
  @Test
  public void testGetSingletonService() {
    ServiceLocator locator = newServiceLocator();

    SingletonService service1 =
        InjectUtils.getService(
            locator,
            new TypeLiteral<SingletonService>() {});

    SingletonService service2 =
        InjectUtils.getService(
            locator,
            new TypeLiteral<SingletonService>() {});

    assertNotNull(service1);
    assertNotNull(service2);
    assertSame(service1, service2);
  }

  /**
   * Verifies that every invocation of {@link
   * InjectUtils#getService(ServiceLocator, TypeLiteral)} returns a new instance
   * when the service has the {@link PerLookup} scope, which is the default
   * scope.
   */
  @Test
  public void testGetPerLookupService() {
    ServiceLocator locator = newServiceLocator();

    PerLookupService service1 =
        InjectUtils.getService(
            locator,
            new TypeLiteral<PerLookupService>() {});

    PerLookupService service2 =
        InjectUtils.getService(
            locator,
            new TypeLiteral<PerLookupService>() {});

    assertNotNull(service1);
    assertNotNull(service2);
    assertNotSame(service1, service2);
  }

  /**
   * Verifies that {@link InjectUtils#getService(ServiceLocator, TypeLiteral)}
   * throws {@link NoSuchElementException} when there is no service of the
   * specified type.
   */
  @Test
  public void testGetUnregisteredService() {
    ServiceLocator locator = newServiceLocator();

    assertThrows(
        NoSuchElementException.class,
        () ->
            InjectUtils.getService(
                locator,
                new TypeLiteral<UnregisteredService>() {}));

    Iterable<UnregisteredService> iterable =
        InjectUtils.getService(
            locator,
            new TypeLiteral<Iterable<UnregisteredService>>() {});

    Iterator<UnregisteredService> iterator = iterable.iterator();

    assertFalse(iterator.hasNext());
  }

  /**
   * Verifies that {@link InjectUtils#getService(ServiceLocator, TypeLiteral)}
   * throws {@link MultiException} when there is a service of the specified type
   * but it has unsatisfied dependencies.
   */
  @Test
  public void testGetServiceWithUnsatisfiedDependencies() {
    ServiceLocator locator = newServiceLocator();

    assertThrows(
        MultiException.class,
        () ->
            InjectUtils.getService(
                locator,
                new TypeLiteral<UnsatisfiedDependencies>() {}));
  }

  /**
   * Verifies that {@link InjectUtils#getService(ServiceLocator, TypeLiteral)}
   * throws {@link NoSuchElementException} when there is a service of the
   * specified type but its provider provided {@code null}.
   */
  @Test
  public void testGetNullService() {
    ServiceLocator locator = newServiceLocator();

    assertThrows(
        NoSuchElementException.class,
        () ->
            InjectUtils.getService(
                locator,
                new TypeLiteral<NullService>() {}));

    // Assert that there is one "instance" of the service, but it's null.
    Iterable<NullService> iterable =
        InjectUtils.getService(
            locator,
            new TypeLiteral<Iterable<NullService>>() {});

    Iterator<NullService> iterator = iterable.iterator();

    assertTrue(iterator.hasNext());
    assertNull(iterator.next());
    assertFalse(iterator.hasNext());
  }

  /**
   * Verifies that {@link InjectUtils#getService(ServiceLocator, TypeLiteral)}
   * can produce an {@link Optional} of a registered service type, and that the
   * returned optional contains an instance of that service.
   */
  @Test
  public void testGetOptional() {
    ServiceLocator locator = newServiceLocator();

    Optional<PerLookupService> optional =
        InjectUtils.getService(
            locator,
            new TypeLiteral<Optional<PerLookupService>>() {});

    assertTrue(optional.isPresent());
  }

  /**
   * Verifies that {@link InjectUtils#getService(ServiceLocator, TypeLiteral)}
   * can produce an {@link Optional} even when the service type is unregistered,
   * and that the returned optional is empty.
   */
  @Test
  public void testGetUnregisteredOptional() {
    ServiceLocator locator = newServiceLocator();

    Optional<UnregisteredService> optional =
        InjectUtils.getService(
            locator,
            new TypeLiteral<Optional<UnregisteredService>>() {});

    assertTrue(optional.isEmpty());
  }

  /**
   * Verifies that {@link InjectUtils#getService(ServiceLocator, TypeLiteral)}
   * can produce a {@link Provider} of a registered service type, and that the
   * returned provider's {@link Provider#get()} method provides an instance of
   * that service.
   */
  @Test
  public void testGetProvider() {
    ServiceLocator locator = newServiceLocator();

    Provider<PerLookupService> provider =
        InjectUtils.getService(
            locator,
            new TypeLiteral<Provider<PerLookupService>>() {});

    PerLookupService service = provider.get();

    assertNotNull(service);
  }

  /**
   * Verifies that {@link InjectUtils#getService(ServiceLocator, TypeLiteral)}
   * can produce a {@link Provider} even when the service type is unregistered,
   * and that the returned provider's {@link Provider#get()} method returns
   * {@code null}.
   */
  @Test
  public void testGetUnregisteredProvider() {
    ServiceLocator locator = newServiceLocator();

    Provider<UnregisteredService> provider =
        InjectUtils.getService(
            locator,
            new TypeLiteral<Provider<UnregisteredService>>() {});

    UnregisteredService service = provider.get();

    assertNull(service);
  }

  /**
   * Verifies that {@link InjectUtils#getService(ServiceLocator, TypeLiteral)}
   * can produce an {@link Iterable} of a registered contract type, and that the
   * returned iterable contains one element for each service registered with
   * that contract.
   */
  @Test
  public void testGetIterable() {
    ServiceLocator locator = newServiceLocator();

    Iterable<SimpleContract> provider =
        InjectUtils.getService(
            locator,
            new TypeLiteral<Iterable<SimpleContract>>() {});

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
   * Verifies that {@link InjectUtils#getService(ServiceLocator, TypeLiteral)}
   * can produce an {@link Iterable} even when the service type is unregistered,
   * and that the returned iterable is empty.
   */
  @Test
  public void testGetUnregisteredIterable() {
    ServiceLocator locator = newServiceLocator();

    Iterable<UnregisteredService> provider =
        InjectUtils.getService(
            locator,
            new TypeLiteral<Iterable<UnregisteredService>>() {});

    assertFalse(provider.iterator().hasNext());
  }

  /**
   * Verifies that {@link InjectUtils#getService(ServiceLocator, TypeLiteral)}
   * can produce an {@link IterableProvider} of a registered contract type, and
   * that the returned provider's {@link IterableProvider#iterator()} contains
   * one element for each service registered with that contract.
   */
  @Test
  public void testGetIterableProvider() {
    ServiceLocator locator = newServiceLocator();

    IterableProvider<SimpleContract> provider =
        InjectUtils.getService(
            locator,
            new TypeLiteral<IterableProvider<SimpleContract>>() {});

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
   * Verifies that {@link InjectUtils#getService(ServiceLocator, TypeLiteral)}
   * can produce an {@link IterableProvider} even when the service type is
   * unregistered, and that the returned provider's {@link
   * IterableProvider#iterator()} is empty.
   */
  @Test
  public void testGetUnregisteredIterableProvider() {
    ServiceLocator locator = newServiceLocator();

    IterableProvider<UnregisteredService> provider =
        InjectUtils.getService(
            locator,
            new TypeLiteral<IterableProvider<UnregisteredService>>() {});

    assertFalse(provider.iterator().hasNext());
  }

  /**
   * Verifies that {@link InjectUtils#getService(ServiceLocator, TypeLiteral)}
   * can retrieve services bound to generic contract types, and that it
   * correctly distinguishes these services from each other based on the generic
   * type arguments of the contract.
   */
  @Test
  public void testGetGenericService() {
    ServiceLocator locator = newServiceLocator();

    GenericContract<Integer> service1 =
        InjectUtils.getService(
            locator,
            new TypeLiteral<GenericContract<Integer>>() {});

    GenericContract<String> service2 =
        InjectUtils.getService(
            locator,
            new TypeLiteral<GenericContract<String>>() {});

    assertThrows(
        NoSuchElementException.class,
        () ->
            InjectUtils.getService(
                locator,
                new TypeLiteral<GenericContract<Double>>() {}));

    assertNotNull(service1);
    assertNotNull(service2);
    assertNotSame(service1, service2);
    assertEquals(1, service1.method());
    assertEquals("hello", service2.method());
  }

  /**
   * Verifies that {@link ServiceLocator#shutdown()} invokes the {@link
   * PreDestroy#preDestroy()} methods of registered singleton services.
   */
  @Test
  public void testShutdownSingletonService() {
    ServiceLocator locator = newServiceLocator();

    SingletonServiceWithShutdown service =
        InjectUtils.getService(
            locator,
            new TypeLiteral<SingletonServiceWithShutdown>() {});

    assertFalse(service.wasStopped());

    locator.shutdown();

    assertTrue(service.wasStopped());
  }

  /**
   * Verifies that {@link ServiceLocator#shutdown()} invokes the {@link
   * Factory#dispose(Object)} methods of registered factories that produce
   * singleton services.
   */
  @Test
  public void testShutdownSingletonServiceFromFactory() {
    ServiceLocator locator = newServiceLocator();

    SingletonServiceWithShutdownFromFactory service =
        InjectUtils.getService(
            locator,
            new TypeLiteral<SingletonServiceWithShutdownFromFactory>() {});

    assertFalse(service.wasStopped());

    locator.shutdown();

    assertTrue(service.wasStopped());
  }

  /**
   * Verifies that {@link ServiceHandle#close()} invokes the {@link
   * PreDestroy#preDestroy()} methods of registered per-lookup services.
   */
  @Test
  public void testShutdownPerLookupService() {
    ServiceLocator locator = newServiceLocator();

    IterableProvider<ServiceWithLifecycle> providers =
        InjectUtils.getService(
            locator,
            new TypeLiteral<IterableProvider<ServiceWithLifecycle>>() {});

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
    ServiceLocator locator = newServiceLocator();

    IterableProvider<ServiceWithShutdownFromFactory> providers =
        InjectUtils.getService(
            locator,
            new TypeLiteral<IterableProvider<ServiceWithShutdownFromFactory>>() {});

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
   * Constructs a new set of services to be used in one test.
   */
  private ServiceLocator newServiceLocator() {
    ServiceLocator locator =
        ServiceLocatorUtilities.createAndPopulateServiceLocator();

    ServiceLocatorUtilities.bind(locator, new InjectUtilsTestBinder());
    return locator;
  }

  public static final class InjectUtilsTestBinder extends AbstractBinder {
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
      addActiveDescriptor(UnsatisfiedDependencies.class);
    }
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

  public static final class UnsatisfiedDependencies {
    @Inject
    public UnsatisfiedDependencies(UnregisteredService dependency) {}
  }
}
