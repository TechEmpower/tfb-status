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
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.NoSuchElementException;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.api.IterableProvider;
import org.glassfish.hk2.api.PerLookup;
import org.glassfish.hk2.api.PreDestroy;
import org.glassfish.hk2.api.TypeLiteral;
import org.glassfish.hk2.utilities.Binder;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.junit.jupiter.api.Test;
import tfb.status.bootstrap.Services.InvalidServiceException;

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
    var binder =
        new AbstractBinder() {
          @Override
          protected void configure() {
            bindAsContract(SimpleService.class).in(Singleton.class);
          }
        };

    var services = new Services(binder);

    SimpleService service1 = services.getService(SimpleService.class);
    SimpleService service2 = services.getService(SimpleService.class);

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
    var binder =
        new AbstractBinder() {
          @Override
          protected void configure() {
            bindAsContract(SimpleService.class);
          }
        };

    var services = new Services(binder);

    SimpleService service1 = services.getService(SimpleService.class);
    SimpleService service2 = services.getService(SimpleService.class);

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
    var binder =
        new AbstractBinder() {
          @Override
          protected void configure() {}
        };

    var services = new Services(binder);

    assertThrows(
        NoSuchElementException.class,
        () -> services.getService(UnregisteredService.class));
  }

  /**
   * Verifies that {@link Services#shutdown()} invokes the {@link
   * PreDestroy#preDestroy()} methods of registered singleton services.
   */
  @Test
  public void testShutdownService() {
    var binder =
        new AbstractBinder() {
          @Override
          protected void configure() {
            bindAsContract(ServiceWithShutdown.class).in(Singleton.class);
          }
        };

    var services = new Services(binder);

    var service = services.getService(ServiceWithShutdown.class);

    assertFalse(service.isShutdown());

    services.shutdown();

    assertTrue(service.isShutdown());
  }

  /**
   * Verifies that {@link Services#shutdown()} invokes the {@link
   * Factory#dispose(Object)} methods of registered singleton factories.
   */
  @Test
  public void testShutdownFactory() {
    var binder =
        new AbstractBinder() {
          @Override
          protected void configure() {
            bindFactory(FactoryWithShutdown.class, Singleton.class)
                .to(ServiceWithShutdown.class)
                .in(Singleton.class);
          }
        };

    var services = new Services(binder);

    var service = services.getService(ServiceWithShutdown.class);

    assertFalse(service.isShutdown());

    services.shutdown();

    assertTrue(service.isShutdown());
  }

  /**
   * Verifies that {@link Services#getService(TypeToken)} can produce a {@link
   * Provider} of a registered service type, and that the returned provider's
   * {@link Provider#get()} method provides an instance of that service.
   */
  @Test
  public void testGetProvider() {
    var binder =
        new AbstractBinder() {
          @Override
          protected void configure() {
            bindAsContract(SimpleService.class);
          }
        };

    var services = new Services(binder);

    Provider<SimpleService> provider =
        services.getService(new TypeToken<Provider<SimpleService>>() {});

    SimpleService service = provider.get();

    assertNotNull(service);
  }

  /**
   * Verifies that {@link Services#getService(TypeToken)} can produce a {@link
   * Provider} even when the service type is unregistered, and that the returned
   * provider's {@link Provider#get()} method returns {@code null}.
   */
  @Test
  public void testGetUnregisteredProvider() {
    var binder =
        new AbstractBinder() {
          @Override
          protected void configure() {}
        };

    var services = new Services(binder);

    Provider<UnregisteredService> provider =
        services.getService(new TypeToken<Provider<UnregisteredService>>() {});

    UnregisteredService service = provider.get();

    assertNull(service);
  }

  /**
   * Verifies that {@link Services#getService(TypeToken)} can produce an {@link
   * IterableProvider} of a registered contract type, and that the returned
   * provider's {@link IterableProvider#iterator()} contains one element for
   * each service registered with that contract.
   */
  @Test
  public void testGetIterableProvider() {
    var binder =
        new AbstractBinder() {
          @Override
          protected void configure() {
            bind(ServiceWithContract1.class).to(SimpleContract.class);
            bind(ServiceWithContract2.class).to(SimpleContract.class);
          }
        };

    var services = new Services(binder);

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
    var binder =
        new AbstractBinder() {
          @Override
          protected void configure() {}
        };

    var services = new Services(binder);

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
    var binder =
        new AbstractBinder() {
          @Override
          protected void configure() {
            bind(ServiceWithGenericContract1.class)
                .to(new TypeLiteral<GenericContract<Integer>>() {});

            bind(ServiceWithGenericContract2.class)
                .to(new TypeLiteral<GenericContract<String>>() {});
          }
        };

    var services = new Services(binder);

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
   * Verifies that {@link Services#hasService(Type)} works as expected.
   */
  @Test
  public void testHasService() {
    var binder =
        new AbstractBinder() {
          @Override
          protected void configure() {
            bindAsContract(SimpleService.class);

            bind(ServiceWithContract1.class).to(SimpleContract.class);
            bind(ServiceWithContract2.class).to(SimpleContract.class);

            bind(ServiceWithGenericContract1.class)
                .to(new TypeLiteral<GenericContract<Integer>>() {});

            bind(ServiceWithGenericContract2.class)
                .to(new TypeLiteral<GenericContract<String>>() {});
          }
        };

    var services = new Services(binder);

    assertTrue(services.hasService(SimpleService.class));
    assertFalse(services.hasService(UnregisteredService.class));
    assertTrue(services.hasService(SimpleContract.class));
    assertFalse(services.hasService(ServiceWithContract1.class));
    assertFalse(services.hasService(ServiceWithContract2.class));
    assertTrue(services.hasService(new TypeToken<GenericContract<Integer>>() {}.getType()));
    assertTrue(services.hasService(new TypeToken<GenericContract<String>>() {}.getType()));
    assertFalse(services.hasService(new TypeToken<GenericContract<Double>>() {}.getType()));
    assertFalse(services.hasService(ServiceWithGenericContract1.class));
    assertFalse(services.hasService(ServiceWithGenericContract2.class));
    assertTrue(services.hasService(new TypeToken<Provider<SimpleService>>() {}.getType()));
    assertTrue(services.hasService(new TypeToken<IterableProvider<SimpleService>>() {}.getType()));
    assertTrue(services.hasService(new TypeToken<Provider<UnregisteredService>>() {}.getType()));
    assertTrue(services.hasService(new TypeToken<IterableProvider<UnregisteredService>>() {}.getType()));
  }

  /**
   * Verifies that {@link Services#Services(Binder...)} throws {@link
   * InvalidServiceException} when registering a {@link PerLookup} service that
   * has a {@link PreDestroy#preDestroy()} method.
   *
   * <p>This exception is thrown in order to warn the caller that the {@link
   * PreDestroy#preDestroy()} method of their service will never be called.
   */
  @Test
  public void testRejectPerLookupServiceWithShutdown() {
    var binder =
        new AbstractBinder() {
          @Override
          protected void configure() {
            bindAsContract(ServiceWithShutdown.class);
          }
        };

    assertThrows(
        Services.InvalidServiceException.class,
        () -> new Services(binder));
  }

  /**
   * Verifies that {@link Services#Services(Binder...)} throws {@link
   * InvalidServiceException} when registering a {@link PerLookup} factory.
   *
   * <p>This exception is thrown in order to warn the caller that the {@link
   * Factory#dispose(Object)} method of their factory will never be called.
   */
  @Test
  public void testRejectPerLookupFactory() {
    var binder =
        new AbstractBinder() {
          @Override
          protected void configure() {
            bindFactory(FactoryWithShutdown.class)
                .to(SimpleService.class)
                .in(Singleton.class);
          }
        };

    assertThrows(
        Services.InvalidServiceException.class,
        () -> new Services(binder));
  }

  public static final class SimpleService {}

  public interface SimpleContract {}

  public static final class ServiceWithContract1 implements SimpleContract {}

  public static final class ServiceWithContract2 implements SimpleContract {}

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

  public static final class ServiceWithShutdown implements PreDestroy {
    @GuardedBy("this")
    private boolean isShutdown = false;

    public synchronized boolean isShutdown() {
      return isShutdown;
    }

    public synchronized void shutdown() {
      isShutdown = true;
    }

    @Override
    public synchronized void preDestroy() {
      shutdown();
    }
  }

  public static final class FactoryWithShutdown
      implements Factory<ServiceWithShutdown> {

    @Override
    public ServiceWithShutdown provide() {
      return new ServiceWithShutdown();
    }

    @Override
    public void dispose(ServiceWithShutdown instance) {
      instance.shutdown();
    }
  }

  public static final class UnregisteredService {}
}
