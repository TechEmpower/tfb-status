package tfb.status.hk2.extensions;

import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.common.reflect.TypeToken;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.lang.reflect.Type;
import java.util.EnumSet;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import javax.inject.Singleton;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.api.IterableProvider;
import org.glassfish.hk2.api.PostConstruct;
import org.glassfish.hk2.api.PreDestroy;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.hk2.utilities.reflection.ReflectionHelper;
import org.junit.jupiter.api.Test;
import org.jvnet.hk2.annotations.Contract;

/**
 * Tests for {@link Provides}, {@link NoInstancesFilter}, and supporting
 * classes.
 */
public final class ProvidesTest {
  /**
   * Verifies that a service with {@link Provides} annotations can also be its
   * own service that can be retrieved.
   */
  @Test
  public void testServiceThatProvidesAndIsAService() {
    ServiceLocator locator = newServiceLocator();

    ProvidesService service =
        locator.getService(ProvidesService.class);

    assertNotNull(service);
  }

  /**
   * Verifies that a service may be registered by way of a static field
   * annotated with {@link Provides}.
   */
  @Test
  public void testStaticFieldProvides() {
    ServiceLocator locator = newServiceLocator();

    ProvidedByStaticField service =
        locator.getService(ProvidedByStaticField.class);

    assertNotNull(service);
  }

  /**
   * Verifies that a service may be registered by way of an instance field
   * annotated with {@link Provides}.
   */
  @Test
  public void testInstanceFieldProvides() {
    ServiceLocator locator = newServiceLocator();

    ProvidedByInstanceField service =
        locator.getService(ProvidedByInstanceField.class);

    assertNotNull(service);
  }

  /**
   * Verifies that a service may be registered by way of a static method
   * annotated with {@link Provides} when the method has zero parameters.
   */
  @Test
  public void testStaticMethodProvides() {
    ServiceLocator locator = newServiceLocator();

    ProvidedByStaticMethod service =
        locator.getService(ProvidedByStaticMethod.class);

    assertNotNull(service);
  }

  /**
   * Verifies that a service may be registered by way of an instance method
   * annotated with {@link Provides} when the method has zero parameters.
   */
  @Test
  public void testInstanceMethodProvides() {
    ServiceLocator locator = newServiceLocator();

    ProvidedByInstanceMethod service =
        locator.getService(ProvidedByInstanceMethod.class);

    assertNotNull(service);
  }

  /**
   * Verifies that a service may be registered by way of a static method
   * annotated with {@link Provides} when the method has parameters.
   */
  @Test
  public void testStaticMethodWithParamsProvides() {
    ServiceLocator locator = newServiceLocator();

    ProvidedByStaticMethodWithParams service =
        locator.getService(ProvidedByStaticMethodWithParams.class);

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
    ServiceLocator locator = newServiceLocator();

    ProvidedByInstanceMethodWithParams service =
        locator.getService(ProvidedByInstanceMethodWithParams.class);

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
    ServiceLocator locator = newServiceLocator();

    MiddleOfStaticFieldProvidesChain middle =
        locator.getService(MiddleOfStaticFieldProvidesChain.class);

    assertNotNull(middle);

    EndOfStaticFieldProvidesChain end =
        locator.getService(EndOfStaticFieldProvidesChain.class);

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
    ServiceLocator locator = newServiceLocator();

    MiddleOfInstanceFieldProvidesChain middle =
        locator.getService(MiddleOfInstanceFieldProvidesChain.class);

    assertNotNull(middle);

    EndOfInstanceFieldProvidesChain end =
        locator.getService(EndOfInstanceFieldProvidesChain.class);

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
    ServiceLocator locator = newServiceLocator();

    MiddleOfStaticMethodProvidesChain middle =
        locator.getService(MiddleOfStaticMethodProvidesChain.class);

    assertNotNull(middle);

    EndOfStaticMethodProvidesChain end =
        locator.getService(EndOfStaticMethodProvidesChain.class);

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
    ServiceLocator locator = newServiceLocator();

    MiddleOfInstanceMethodProvidesChain middle =
        locator.getService(MiddleOfInstanceMethodProvidesChain.class);

    assertNotNull(middle);

    EndOfInstanceMethodProvidesChain end =
        locator.getService(EndOfInstanceMethodProvidesChain.class);

    assertNotNull(end);
  }

  /**
   * Verifies that a service with a generic type can be registered from a method
   * annotated with {@link Provides}.
   */
  @Test
  public void testGenericMethodProvides() {
    ServiceLocator locator = newServiceLocator();

    GenericFromProvidesMethod<String> service =
        InjectUtils.getService(
            locator,
            new TypeToken<GenericFromProvidesMethod<String>>() {});

    assertNotNull(service);

    assertThrows(
        NoSuchElementException.class,
        () ->
            InjectUtils.getService(
                locator,
                new TypeToken<GenericFromProvidesMethod<Integer>>() {}));
  }

  /**
   * Verifies that a service with a generic type can be registered from a field
   * annotated with {@link Provides}.
   */
  @Test
  public void testGenericFieldProvides() {
    ServiceLocator locator = newServiceLocator();

    GenericFromProvidesField<String> service =
        InjectUtils.getService(
            locator,
            new TypeToken<GenericFromProvidesField<String>>() {});

    assertNotNull(service);

    assertThrows(
        NoSuchElementException.class,
        () ->
            InjectUtils.getService(
                locator,
                new TypeToken<GenericFromProvidesField<Integer>>() {}));
  }

  /**
   * Verifies that a service class may provide itself by way of a static {@link
   * Provides} field even when the class has no constructors compatible with
   * injection.
   */
  @Test
  public void testServiceProvidesItselfFromField() {
    ServiceLocator locator = newServiceLocator();

    ProvidesSelfFromMethod self =
        locator.getService(ProvidesSelfFromMethod.class);

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
    ServiceLocator locator = newServiceLocator();

    ProvidesSelfFromMethod self =
        locator.getService(ProvidesSelfFromMethod.class);

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
    ServiceLocator locator = newServiceLocator();

    ProvidedSingletonStaticFieldWithLifecycle service =
        locator.getService(ProvidedSingletonStaticFieldWithLifecycle.class);

    assertNotNull(service);
    assertFalse(service.wasStarted());
    assertFalse(service.wasStopped());

    assertSame(
        service,
        locator.getService(ProvidedSingletonStaticFieldWithLifecycle.class));

    locator.shutdown();

    assertFalse(service.wasStopped());
  }

  /**
   * Verifies that the {@link PostConstruct} and {@link PreDestroy} methods of a
   * service are invoked at the expected times when the service was registered
   * from an instance {@link Provides} field and the service is a singleton.
   */
  @Test
  public void testInstanceFieldProvidesSingletonLifecycle() {
    ServiceLocator locator = newServiceLocator();

    ProvidedSingletonInstanceFieldWithLifecycle service =
        locator.getService(ProvidedSingletonInstanceFieldWithLifecycle.class);

    assertNotNull(service);
    assertFalse(service.wasStarted());
    assertFalse(service.wasStopped());

    assertSame(
        service,
        locator.getService(ProvidedSingletonInstanceFieldWithLifecycle.class));

    locator.shutdown();

    assertTrue(service.wasStopped());
  }

  /**
   * Verifies that the {@link PostConstruct} and {@link PreDestroy} methods of a
   * service are invoked at the expected times when the service was registered
   * from a static {@link Provides} method and the service is a singleton.
   */
  @Test
  public void testStaticMethodProvidesSingletonLifecycle() {
    ServiceLocator locator = newServiceLocator();

    ProvidedSingletonStaticMethodWithLifecycle service =
        locator.getService(ProvidedSingletonStaticMethodWithLifecycle.class);

    assertNotNull(service);
    assertFalse(service.wasStarted());
    assertFalse(service.wasStopped());

    assertSame(
        service,
        locator.getService(ProvidedSingletonStaticMethodWithLifecycle.class));

    locator.shutdown();

    assertTrue(service.wasStopped());
  }

  /**
   * Verifies that the {@link PostConstruct} and {@link PreDestroy} methods of a
   * service are invoked at the expected times when the service was registered
   * from an instance {@link Provides} method and the service is a singleton.
   */
  @Test
  public void testInstanceMethodProvidesSingletonLifecycle() {
    ServiceLocator locator = newServiceLocator();

    ProvidedSingletonInstanceMethodWithLifecycle service =
        locator.getService(ProvidedSingletonInstanceMethodWithLifecycle.class);

    assertNotNull(service);
    assertFalse(service.wasStarted());
    assertFalse(service.wasStopped());

    assertSame(
        service,
        locator.getService(ProvidedSingletonInstanceMethodWithLifecycle.class));

    locator.shutdown();

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
    ServiceLocator locator = newServiceLocator();

    ProvidedPerLookupStaticFieldWithLifecycleWithoutHandle serviceWithoutHandle =
        locator.getService(ProvidedPerLookupStaticFieldWithLifecycleWithoutHandle.class);

    assertNotNull(serviceWithoutHandle);
    assertFalse(serviceWithoutHandle.wasStarted());
    assertFalse(serviceWithoutHandle.wasStopped());

    // Avoid making any assumptions regarding the sameness of this instance and
    // other instances.  The static field might hold one fixed instance, or a
    // new value may be written to the field from time to time.  It doesn't
    // matter to us.

    locator.shutdown();

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
    ServiceLocator locator = newServiceLocator();

    IterableProvider<ProvidedPerLookupStaticFieldWithLifecycleWithHandle> serviceProvider =
        InjectUtils.getService(
            locator,
            new TypeToken<IterableProvider<ProvidedPerLookupStaticFieldWithLifecycleWithHandle>>() {});

    ServiceHandle<ProvidedPerLookupStaticFieldWithLifecycleWithHandle> serviceHandle =
        serviceProvider.getHandle();

    ProvidedPerLookupStaticFieldWithLifecycleWithHandle serviceWithHandle =
        serviceHandle.getService();

    assertNotNull(serviceWithHandle);
    assertFalse(serviceWithHandle.wasStarted());
    assertFalse(serviceWithHandle.wasStopped());

    serviceHandle.close();

    assertFalse(serviceWithHandle.wasStopped());
  }

  /**
   * Verifies that the {@link PostConstruct} and {@link PreDestroy} methods of a
   * service are invoked at the expected times when the service was registered
   * from an instance {@link Provides} field and the service is per-lookup.
   */
  @Test
  public void testInstanceFieldProvidesPerLookupLifecycle() {
    ServiceLocator locator = newServiceLocator();

    ProvidedPerLookupInstanceFieldWithLifecycle serviceWithoutHandle =
        locator.getService(ProvidedPerLookupInstanceFieldWithLifecycle.class);

    assertNotNull(serviceWithoutHandle);
    assertFalse(serviceWithoutHandle.wasStarted());
    assertFalse(serviceWithoutHandle.wasStopped());

    assertNotSame(
        serviceWithoutHandle,
        locator.getService(ProvidedPerLookupInstanceFieldWithLifecycle.class));

    IterableProvider<ProvidedPerLookupInstanceFieldWithLifecycle> serviceProvider =
        InjectUtils.getService(
            locator,
            new TypeToken<IterableProvider<ProvidedPerLookupInstanceFieldWithLifecycle>>() {});

    ServiceHandle<ProvidedPerLookupInstanceFieldWithLifecycle> serviceHandle =
        serviceProvider.getHandle();

    ProvidedPerLookupInstanceFieldWithLifecycle serviceWitHandle =
        serviceHandle.getService();

    assertNotNull(serviceWitHandle);
    assertFalse(serviceWitHandle.wasStarted());
    assertFalse(serviceWitHandle.wasStopped());

    serviceHandle.close();

    assertTrue(serviceWitHandle.wasStopped());

    locator.shutdown();

    assertFalse(serviceWithoutHandle.wasStopped());
  }

  /**
   * Verifies that the {@link PostConstruct} and {@link PreDestroy} methods of a
   * service are invoked at the expected times when the service was registered
   * from a static {@link Provides} method and the service is per-lookup.
   */
  @Test
  public void testStaticMethodProvidesPerLookupLifecycle() {
    ServiceLocator locator = newServiceLocator();

    ProvidedPerLookupStaticMethodWithLifecycle serviceWithoutHandle =
        locator.getService(ProvidedPerLookupStaticMethodWithLifecycle.class);

    assertNotNull(serviceWithoutHandle);
    assertFalse(serviceWithoutHandle.wasStarted());
    assertFalse(serviceWithoutHandle.wasStopped());

    assertNotSame(
        serviceWithoutHandle,
        locator.getService(ProvidedPerLookupStaticMethodWithLifecycle.class));

    IterableProvider<ProvidedPerLookupStaticMethodWithLifecycle> serviceProvider =
        InjectUtils.getService(
            locator,
            new TypeToken<IterableProvider<ProvidedPerLookupStaticMethodWithLifecycle>>() {});

    ServiceHandle<ProvidedPerLookupStaticMethodWithLifecycle> serviceHandle =
        serviceProvider.getHandle();

    ProvidedPerLookupStaticMethodWithLifecycle serviceWitHandle =
        serviceHandle.getService();

    assertNotNull(serviceWitHandle);
    assertFalse(serviceWitHandle.wasStarted());
    assertFalse(serviceWitHandle.wasStopped());

    serviceHandle.close();

    assertTrue(serviceWitHandle.wasStopped());

    locator.shutdown();

    assertFalse(serviceWithoutHandle.wasStopped());
  }

  /**
   * Verifies that the {@link PostConstruct} and {@link PreDestroy} methods of a
   * service are invoked at the expected times when the service was registered
   * from an instance {@link Provides} method and the service is per-lookup.
   */
  @Test
  public void testInstanceMethodProvidesPerLookupLifecycle() {
    ServiceLocator locator = newServiceLocator();

    ProvidedPerLookupInstanceMethodWithLifecycle serviceWithoutHandle =
        locator.getService(ProvidedPerLookupInstanceMethodWithLifecycle.class);

    assertNotNull(serviceWithoutHandle);
    assertFalse(serviceWithoutHandle.wasStarted());
    assertFalse(serviceWithoutHandle.wasStopped());

    assertNotSame(
        serviceWithoutHandle,
        locator.getService(ProvidedPerLookupInstanceMethodWithLifecycle.class));

    IterableProvider<ProvidedPerLookupInstanceMethodWithLifecycle> serviceProvider =
        InjectUtils.getService(
            locator,
            new TypeToken<IterableProvider<ProvidedPerLookupInstanceMethodWithLifecycle>>() {});

    ServiceHandle<ProvidedPerLookupInstanceMethodWithLifecycle> serviceHandle =
        serviceProvider.getHandle();

    ProvidedPerLookupInstanceMethodWithLifecycle serviceWitHandle =
        serviceHandle.getService();

    assertNotNull(serviceWitHandle);
    assertFalse(serviceWitHandle.wasStarted());
    assertFalse(serviceWitHandle.wasStopped());

    serviceHandle.close();

    assertTrue(serviceWitHandle.wasStopped());

    locator.shutdown();

    assertFalse(serviceWithoutHandle.wasStopped());
  }

  /**
   * Verifies that a utility class may be registered as a service when it has
   * static methods or fields annotated with {@link Provides}, and verifies that
   * the utility class itself cannot be fetched as a service.
   */
  @Test
  public void testUtilityClassProvides() {
    ServiceLocator locator = newServiceLocator();

    assertNotNull(locator.getService(FromUtilityClassMethod.class));
    assertNotNull(locator.getService(FromUtilityClassField.class));
    assertNull(locator.getService(UtilityClassProvides.class));
  }

  /**
   * Verifies that an abstract class may be registered as a service when it has
   * static methods or fields annotated with {@link Provides}, and verifies that
   * the abstract class itself cannot be fetched as a service.
   */
  @Test
  public void testAbstractClassProvides() {
    ServiceLocator locator = newServiceLocator();

    assertNotNull(locator.getService(FromAbstractClassMethod.class));
    assertNotNull(locator.getService(FromAbstractClassField.class));
    assertNull(locator.getService(AbstractClassProvides.class));
  }

  /**
   * Verifies that an interface may be registered as a service when it has
   * static methods or fields annotated with {@link Provides}, and verifies that
   * the interface itself cannot be fetched as a service.
   */
  @Test
  public void testInterfaceProvides() {
    ServiceLocator locator = newServiceLocator();

    assertNotNull(locator.getService(FromInterfaceMethod.class));
    assertNotNull(locator.getService(FromInterfaceField.class));
    assertNull(locator.getService(InterfaceProvides.class));
  }

  /**
   * Verifies that an enum class may be registered as a service when its enum
   * constants are annotated with {@link Provides}.
   */
  @Test
  public void testEnumProvides() {
    ServiceLocator locator = newServiceLocator();

    IterableProvider<EnumProvides> provider =
        InjectUtils.getService(
            locator,
            new TypeToken<IterableProvider<EnumProvides>>() {});

    assertEquals(
        EnumSet.allOf(EnumProvides.class),
        Sets.immutableEnumSet(provider));
  }

  /**
   * Verifies that contracts of enum constants annotated with {@link Provides}
   * are detected correctly.
   */
  @Test
  public void testEnumProvidesContracts() {
    ServiceLocator locator = newServiceLocator();

    IterableProvider<EnumContract> provider =
        InjectUtils.getService(
            locator,
            new TypeToken<IterableProvider<EnumContract>>() {});

    assertEquals(
        EnumSet.allOf(EnumProvidesContract.class),
        ImmutableSet.copyOf(provider));

    IterableProvider<SecondEnumContract> secondProvider =
        InjectUtils.getService(
            locator,
            new TypeToken<IterableProvider<SecondEnumContract>>() {});

    assertEquals(
        EnumSet.allOf(EnumProvidesContract.class),
        ImmutableSet.copyOf(secondProvider));
  }

  /**
   * Verifies that {@link Provides#disposeMethod()} cannot specify a method of
   * the provided type to be invoked at the end of the service's lifecycle when
   * the {@link Provides} annotation is on a static field and {@link
   * Provides#disposalHandledBy()} is {@link
   * Provides.DisposalHandledBy#PROVIDED_INSTANCE}.
   */
  @Test
  public void testProvidesCustomDisposeStaticField() {
    ServiceLocator locator = newServiceLocator();

    HasCustomDisposeMethod service =
        locator.getService(ProvidedWithCustomDisposeFromStaticField.class);

    assertFalse(service.isClosed());
    locator.shutdown();
    assertFalse(service.isClosed());
  }

  /**
   * Verifies that {@link Provides#disposeMethod()} may specify a method of the
   * provided type to be invoked at the end of the service's lifecycle when the
   * {@link Provides} annotation is on an instance field and {@link
   * Provides#disposalHandledBy()} is {@link
   * Provides.DisposalHandledBy#PROVIDED_INSTANCE}.
   */
  @Test
  public void testProvidesCustomDisposeInstanceField() {
    ServiceLocator locator = newServiceLocator();

    HasCustomDisposeMethod service =
        locator.getService(ProvidedWithCustomDisposeFromInstanceField.class);

    assertFalse(service.isClosed());
    locator.shutdown();
    assertTrue(service.isClosed());
  }

  /**
   * Verifies that {@link Provides#disposeMethod()} may specify a method of the
   * provided type to be invoked at the end of the service's lifecycle when the
   * {@link Provides} annotation is on a static method and {@link
   * Provides#disposalHandledBy()} is {@link
   * Provides.DisposalHandledBy#PROVIDED_INSTANCE}.
   */
  @Test
  public void testProvidesCustomDisposeStaticMethod() {
    ServiceLocator locator = newServiceLocator();

    HasCustomDisposeMethod service =
        locator.getService(ProvidedWithCustomDisposeFromStaticMethod.class);

    assertFalse(service.isClosed());
    locator.shutdown();
    assertTrue(service.isClosed());
  }

  /**
   * Verifies that {@link Provides#disposeMethod()} may specify a method of the
   * provided type to be invoked at the end of the service's lifecycle when the
   * {@link Provides} annotation is on an instance method and {@link
   * Provides#disposalHandledBy()} is {@link
   * Provides.DisposalHandledBy#PROVIDED_INSTANCE}.
   */
  @Test
  public void testProvidesCustomDisposeInstanceMethod() {
    ServiceLocator locator = newServiceLocator();

    HasCustomDisposeMethod service =
        locator.getService(ProvidedWithCustomDisposeFromInstanceMethod.class);

    assertFalse(service.isClosed());
    locator.shutdown();
    assertTrue(service.isClosed());
  }

  /**
   * Verifies that {@link Provides#disposeMethod()} cannot specify a method of
   * the provider type to be invoked at the end of the service's lifecycle when
   * the {@link Provides} annotation is on a static field and {@link
   * Provides#disposalHandledBy()} is {@link
   * Provides.DisposalHandledBy#PROVIDER}.
   */
  @Test
  public void testProvidesCustomDisposeStaticFieldFactoryDestroys() {
    ServiceLocator locator = newServiceLocator();

    HasCustomDisposeMethod service =
        locator.getService(ProvidedWithCustomDisposeFromStaticFieldForFactory.class);

    assertFalse(service.isClosed());
    locator.shutdown();
    assertFalse(service.isClosed());
  }

  /**
   * Verifies that {@link Provides#disposeMethod()} may specify a method of the
   * provider type to be invoked at the end of the service's lifecycle when the
   * {@link Provides} annotation is on a static field and {@link
   * Provides#disposalHandledBy()} is {@link
   * Provides.DisposalHandledBy#PROVIDER}.
   */
  @Test
  public void testProvidesCustomDisposeInstanceFieldFactoryDestroys() {
    ServiceLocator locator = newServiceLocator();

    HasCustomDisposeMethod service =
        locator.getService(ProvidedWithCustomDisposeFromInstanceFieldForFactory.class);

    assertFalse(service.isClosed());
    locator.shutdown();
    assertTrue(service.isClosed());
  }

  /**
   * Verifies that {@link Provides#disposeMethod()} may specify a method of the
   * provider type to be invoked at the end of the service's lifecycle when the
   * {@link Provides} annotation is on a static field and {@link
   * Provides#disposalHandledBy()} is {@link
   * Provides.DisposalHandledBy#PROVIDER}.
   */
  @Test
  public void testProvidesCustomDisposeStaticMethodFactoryDestroys() {
    ServiceLocator locator = newServiceLocator();

    HasCustomDisposeMethod service =
        locator.getService(ProvidedWithCustomDisposeFromStaticMethodForFactory.class);

    assertFalse(service.isClosed());
    locator.shutdown();
    assertTrue(service.isClosed());
  }

  /**
   * Verifies that {@link Provides#disposeMethod()} may specify a method of the
   * provider type to be invoked at the end of the service's lifecycle when the
   * {@link Provides} annotation is on a static field and {@link
   * Provides#disposalHandledBy()} is {@link
   * Provides.DisposalHandledBy#PROVIDER}.
   */
  @Test
  public void testProvidesCustomDisposeInstanceMethodFactoryDestroys() {
    ServiceLocator locator = newServiceLocator();

    HasCustomDisposeMethod service =
        locator.getService(ProvidedWithCustomDisposeFromInstanceMethodForFactory.class);

    assertFalse(service.isClosed());
    locator.shutdown();
    assertTrue(service.isClosed());
  }

  /**
   * Verifies that {@link Provides#contracts()} may specify a list of contracts
   * that overrides the default contracts for a static field.
   */
  @Test
  public void testProvidesExplicitContractsFromStaticField() {
    ServiceLocator locator = newServiceLocator();

    ExplicitContractInStaticField service =
        locator.getService(ExplicitContractInStaticField.class);

    assertTrue(service instanceof HasDefaultContractsInStaticField);
    assertNull(locator.getService(HasDefaultContractsInStaticField.class));
    assertNull(locator.getService(DefaultContractInStaticField.class));
  }

  /**
   * Verifies that {@link Provides#contracts()} may specify a list of contracts
   * that overrides the default contracts for an instance field.
   */
  @Test
  public void testProvidesExplicitContractsFromInstanceField() {
    ServiceLocator locator = newServiceLocator();

    ExplicitContractInInstanceField service =
        locator.getService(ExplicitContractInInstanceField.class);

    assertTrue(service instanceof HasDefaultContractsInInstanceField);
    assertNull(locator.getService(HasDefaultContractsInInstanceField.class));
    assertNull(locator.getService(DefaultContractInInstanceField.class));
  }

  /**
   * Verifies that {@link Provides#contracts()} may specify a list of contracts
   * that overrides the default contracts for a static method.
   */
  @Test
  public void testProvidesExplicitContractsFromStaticMethod() {
    ServiceLocator locator = newServiceLocator();

    ExplicitContractInStaticMethod service =
        locator.getService(ExplicitContractInStaticMethod.class);

    assertTrue(service instanceof HasDefaultContractsInStaticMethod);
    assertNull(locator.getService(HasDefaultContractsInStaticMethod.class));
    assertNull(locator.getService(DefaultContractInStaticMethod.class));
  }

  /**
   * Verifies that {@link Provides#contracts()} may specify a list of contracts
   * that overrides the default contracts for an instance method.
   */
  @Test
  public void testProvidesExplicitContractsFromInstanceMethod() {
    ServiceLocator locator = newServiceLocator();

    ExplicitContractInInstanceMethod service =
        locator.getService(ExplicitContractInInstanceMethod.class);

    assertTrue(service instanceof HasDefaultContractsInInstanceMethod);
    assertNull(locator.getService(HasDefaultContractsInInstanceMethod.class));
    assertNull(locator.getService(DefaultContractInInstanceMethod.class));
  }

  /**
   * Verifies that {@code null} may be provided from a static field annotated
   * with {@link Provides}.
   */
  @Test
  public void testProvidesNullFromStaticField() {
    ServiceLocator locator = newServiceLocator();

    IterableProvider<NullFromStaticField> provider =
        InjectUtils.getService(
            locator,
            new TypeToken<IterableProvider<NullFromStaticField>>() {});

    try (ServiceHandle<NullFromStaticField> handle = provider.getHandle()) {
      assertNull(handle.getService());
    }
  }

  /**
   * Verifies that {@code null} may be provided from an instance field annotated
   * with {@link Provides}.
   */
  @Test
  public void testProvidesNullFromInstanceField() {
    ServiceLocator locator = newServiceLocator();

    IterableProvider<NullFromInstanceField> provider =
        InjectUtils.getService(
            locator,
            new TypeToken<IterableProvider<NullFromInstanceField>>() {});

    try (ServiceHandle<NullFromInstanceField> handle = provider.getHandle()) {
      assertNull(handle.getService());
    }
  }

  /**
   * Verifies that {@code null} may be provided from a static method annotated
   * with {@link Provides}.
   */
  @Test
  public void testProvidesNullFromStaticMethod() {
    ServiceLocator locator = newServiceLocator();

    IterableProvider<NullFromStaticMethod> provider =
        InjectUtils.getService(
            locator,
            new TypeToken<IterableProvider<NullFromStaticMethod>>() {});

    try (ServiceHandle<NullFromStaticMethod> handle = provider.getHandle()) {
      assertNull(handle.getService());
    }
  }

  /**
   * Verifies that {@code null} may be provided from an instance method
   * annotated with {@link Provides}.
   */
  @Test
  public void testProvidesNullFromInstanceMethod() {
    ServiceLocator locator = newServiceLocator();

    IterableProvider<NullFromInstanceMethod> provider =
        InjectUtils.getService(
            locator,
            new TypeToken<IterableProvider<NullFromInstanceMethod>>() {});

    try (ServiceHandle<NullFromInstanceMethod> handle = provider.getHandle()) {
      assertNull(handle.getService());
    }
  }

  /**
   * Verifies the lifecycle of a service obtained from a static field that is
   * annotated with {@link Provides}.
   */
  @Test
  public void testProvidesLifecycleFromStaticField() {
    ServiceLocator locator = newServiceLocator();

    IterableProvider<ProvidesLifecycleFromStaticField> provider =
        InjectUtils.getService(
            locator,
            new TypeToken<IterableProvider<ProvidesLifecycleFromStaticField>>() {});

    ServiceHandle<ProvidesLifecycleFromStaticField> handle =
        provider.getHandle();

    ProvidesLifecycleFromStaticField root = handle.getService();

    assertFalse(root.wasStarted());
    assertFalse(root.factory.wasStarted());
    assertFalse(root.dependency.wasStarted());

    assertFalse(root.wasStopped());
    assertFalse(root.factory.wasStopped());
    assertFalse(root.dependency.wasStopped());

    handle.close();

    assertFalse(root.wasStopped());
    assertFalse(root.factory.wasStopped());
    assertFalse(root.dependency.wasStopped());
  }

  /**
   * Verifies the lifecycle a service obtained from an instance field that is
   * annotated with {@link Provides}.
   */
  @Test
  public void testProvidesLifecycleFromInstanceField() {
    ServiceLocator locator = newServiceLocator();

    IterableProvider<ProvidesLifecycleFromInstanceField> provider =
        InjectUtils.getService(
            locator,
            new TypeToken<IterableProvider<ProvidesLifecycleFromInstanceField>>() {});

    ServiceHandle<ProvidesLifecycleFromInstanceField> handle =
        provider.getHandle();

    ProvidesLifecycleFromInstanceField root = handle.getService();

    assertFalse(root.wasStarted());
    assertTrue(root.factory.wasStarted());
    assertFalse(root.dependency.wasStarted());

    assertFalse(root.wasStopped());
    assertFalse(root.factory.wasStopped());
    assertFalse(root.dependency.wasStopped());

    handle.close();

    assertTrue(root.wasStopped());
    assertTrue(root.factory.wasStopped());
    assertFalse(root.dependency.wasStopped());
  }

  /**
   * Verifies the lifecycle of a service obtained from a static method that is
   * annotated with {@link Provides}.
   */
  @Test
  public void testProvidesLifecycleFromStaticMethod() {
    ServiceLocator locator = newServiceLocator();

    IterableProvider<ProvidesLifecycleFromStaticMethod> provider =
        InjectUtils.getService(
            locator,
            new TypeToken<IterableProvider<ProvidesLifecycleFromStaticMethod>>() {});

    ServiceHandle<ProvidesLifecycleFromStaticMethod> handle =
        provider.getHandle();

    ProvidesLifecycleFromStaticMethod root = handle.getService();

    assertFalse(root.wasStarted());
    assertFalse(root.factory.wasStarted());
    assertTrue(root.dependency.wasStarted());

    assertFalse(root.wasStopped());
    assertFalse(root.factory.wasStopped());
    assertFalse(root.dependency.wasStopped());

    handle.close();

    assertTrue(root.wasStopped());
    assertFalse(root.factory.wasStopped());
    assertTrue(root.dependency.wasStopped());
  }

  /**
   * Verifies the lifecycle of a service obtained from an instance method that
   * is annotated with {@link Provides}.
   */
  @Test
  public void testProvidesLifecycleFromInstanceMethod() {
    ServiceLocator locator = newServiceLocator();

    IterableProvider<ProvidesLifecycleFromInstanceMethod> provider =
        InjectUtils.getService(
            locator,
            new TypeToken<IterableProvider<ProvidesLifecycleFromInstanceMethod>>() {});

    ServiceHandle<ProvidesLifecycleFromInstanceMethod> handle =
        provider.getHandle();

    ProvidesLifecycleFromInstanceMethod root = handle.getService();

    assertFalse(root.wasStarted());
    assertTrue(root.factory.wasStarted());
    assertTrue(root.dependency.wasStarted());

    assertFalse(root.wasStopped());
    assertFalse(root.factory.wasStopped());
    assertFalse(root.dependency.wasStopped());

    handle.close();

    assertTrue(root.wasStopped());
    assertTrue(root.factory.wasStopped());
    assertTrue(root.dependency.wasStopped());
  }

  /**
   * Verifies that generic type parameters are carried through a {@link
   * Provides} chain starting from a static field and ending with a generic
   * {@link Provides} field.
   */
  @Test
  public void testProvidesGenericTypeParametersStaticFieldToField() {
    ServiceLocator locator = newServiceLocator();

    ProvidesGenericField<Value1> providerA =
        InjectUtils.getService(
            locator,
            new TypeToken<ProvidesGenericField<Value1>>() {});

    assertNotNull(providerA);
    assertNotNull(providerA.value);
    assertEquals(Value1.class, providerA.value.getClass());

    Value1 providedA = locator.getService(Value1.class);

    assertNotNull(providedA);
    assertEquals(Value1.class, providedA.getClass());

    ProvidesGenericField<Value2> providerB =
        InjectUtils.getService(
            locator,
            new TypeToken<ProvidesGenericField<Value2>>() {});

    assertNotNull(providerB);
    assertNotNull(providerB.value);
    assertEquals(Value2.class, providerB.value.getClass());

    Value2 providedB = locator.getService(Value2.class);

    assertNotNull(providedB);
    assertEquals(Value2.class, providedB.getClass());
  }

  /**
   * Verifies that generic type parameters are carried through a {@link
   * Provides} chain starting from an instance field and ending with a generic
   * {@link Provides} field.
   */
  @Test
  public void testProvidesGenericTypeParametersInstanceFieldToField() {
    ServiceLocator locator = newServiceLocator();

    ProvidesGenericField<Value3> providerA =
        InjectUtils.getService(
            locator,
            new TypeToken<ProvidesGenericField<Value3>>() {});

    assertNotNull(providerA);
    assertNotNull(providerA.value);
    assertEquals(Value3.class, providerA.value.getClass());

    Value3 providedA = locator.getService(Value3.class);

    assertNotNull(providedA);
    assertEquals(Value3.class, providedA.getClass());

    ProvidesGenericField<Value4> providerB =
        InjectUtils.getService(
            locator,
            new TypeToken<ProvidesGenericField<Value4>>() {});

    assertNotNull(providerB);
    assertNotNull(providerB.value);
    assertEquals(Value4.class, providerB.value.getClass());

    Value4 providedB = locator.getService(Value4.class);

    assertNotNull(providedB);
    assertEquals(Value4.class, providedB.getClass());
  }

  /**
   * Verifies that generic type parameters are carried through a {@link
   * Provides} chain starting from a static method and ending with a generic
   * {@link Provides} field.
   */
  @Test
  public void testProvidesGenericTypeParametersStaticMethodToField() {
    ServiceLocator locator = newServiceLocator();

    ProvidesGenericField<Value5> providerA =
        InjectUtils.getService(
            locator,
            new TypeToken<ProvidesGenericField<Value5>>() {});

    assertNotNull(providerA);
    assertNotNull(providerA.value);
    assertEquals(Value5.class, providerA.value.getClass());

    Value5 providedA = locator.getService(Value5.class);

    assertNotNull(providedA);
    assertEquals(Value5.class, providedA.getClass());

    ProvidesGenericField<Value6> providerB =
        InjectUtils.getService(
            locator,
            new TypeToken<ProvidesGenericField<Value6>>() {});

    assertNotNull(providerB);
    assertNotNull(providerB.value);
    assertEquals(Value6.class, providerB.value.getClass());

    Value6 providedB = locator.getService(Value6.class);

    assertNotNull(providedB);
    assertEquals(Value6.class, providedB.getClass());
  }

  /**
   * Verifies that generic type parameters are carried through a {@link
   * Provides} chain starting from an instance method and ending with a generic
   * {@link Provides} field.
   */
  @Test
  public void testProvidesGenericTypeParametersInstanceMethodToField() {
    ServiceLocator locator = newServiceLocator();

    ProvidesGenericField<Value7> providerA =
        InjectUtils.getService(
            locator,
            new TypeToken<ProvidesGenericField<Value7>>() {});

    assertNotNull(providerA);
    assertNotNull(providerA.value);
    assertEquals(Value7.class, providerA.value.getClass());

    Value7 providedA = locator.getService(Value7.class);

    assertNotNull(providedA);
    assertEquals(Value7.class, providedA.getClass());

    ProvidesGenericField<Value8> providerB =
        InjectUtils.getService(
            locator,
            new TypeToken<ProvidesGenericField<Value8>>() {});

    assertNotNull(providerB);
    assertNotNull(providerB.value);
    assertEquals(Value8.class, providerB.value.getClass());

    Value8 providedB = locator.getService(Value8.class);

    assertNotNull(providedB);
    assertEquals(Value8.class, providedB.getClass());
  }

  /**
   * Verifies that generic type parameters are carried through a {@link
   * Provides} chain starting from a static field and ending with a generic
   * {@link Provides} method.
   */
  @Test
  public void testProvidesGenericTypeParametersStaticFieldToMethod() {
    ServiceLocator locator = newServiceLocator();

    ProvidesGenericMethod<Value9> providerA =
        InjectUtils.getService(
            locator,
            new TypeToken<ProvidesGenericMethod<Value9>>() {});

    assertNotNull(providerA);
    assertNotNull(providerA.getValue());
    assertEquals(Value9.class, providerA.getValue().getClass());

    Value9 providedA = locator.getService(Value9.class);

    assertNotNull(providedA);
    assertEquals(Value9.class, providedA.getClass());

    ProvidesGenericMethod<Value10> providerB =
        InjectUtils.getService(
            locator,
            new TypeToken<ProvidesGenericMethod<Value10>>() {});

    assertNotNull(providerB);
    assertNotNull(providerB.getValue());
    assertEquals(Value10.class, providerB.getValue().getClass());

    Value10 providedB = locator.getService(Value10.class);

    assertNotNull(providedB);
    assertEquals(Value10.class, providedB.getClass());
  }

  /**
   * Verifies that generic type parameters are carried through a {@link
   * Provides} chain starting from an instance field and ending with a generic
   * {@link Provides} method.
   */
  @Test
  public void testProvidesGenericTypeParametersInstanceFieldToMethod() {
    ServiceLocator locator = newServiceLocator();

    ProvidesGenericMethod<Value11> providerA =
        InjectUtils.getService(
            locator,
            new TypeToken<ProvidesGenericMethod<Value11>>() {});

    assertNotNull(providerA);
    assertNotNull(providerA.getValue());
    assertEquals(Value11.class, providerA.getValue().getClass());

    Value11 providedA = locator.getService(Value11.class);

    assertNotNull(providedA);
    assertEquals(Value11.class, providedA.getClass());

    ProvidesGenericMethod<Value12> providerB =
        InjectUtils.getService(
            locator,
            new TypeToken<ProvidesGenericMethod<Value12>>() {});

    assertNotNull(providerB);
    assertNotNull(providerB.getValue());
    assertEquals(Value12.class, providerB.getValue().getClass());

    Value12 providedB = locator.getService(Value12.class);

    assertNotNull(providedB);
    assertEquals(Value12.class, providedB.getClass());
  }

  /**
   * Verifies that generic type parameters are carried through a {@link
   * Provides} chain starting from a static method and ending with a generic
   * {@link Provides} method.
   */
  @Test
  public void testProvidesGenericTypeParametersStaticMethodToMethod() {
    ServiceLocator locator = newServiceLocator();

    ProvidesGenericMethod<Value13> providerA =
        InjectUtils.getService(
            locator,
            new TypeToken<ProvidesGenericMethod<Value13>>() {});

    assertNotNull(providerA);
    assertNotNull(providerA.getValue());
    assertEquals(Value13.class, providerA.getValue().getClass());

    Value13 providedA = locator.getService(Value13.class);

    assertNotNull(providedA);
    assertEquals(Value13.class, providedA.getClass());

    ProvidesGenericMethod<Value14> providerB =
        InjectUtils.getService(
            locator,
            new TypeToken<ProvidesGenericMethod<Value14>>() {});

    assertNotNull(providerB);
    assertNotNull(providerB.getValue());
    assertEquals(Value14.class, providerB.getValue().getClass());

    Value14 providedB = locator.getService(Value14.class);

    assertNotNull(providedB);
    assertEquals(Value14.class, providedB.getClass());
  }

  /**
   * Verifies that generic type parameters are carried through a {@link
   * Provides} chain starting from an instance method and ending with a generic
   * {@link Provides} method.
   */
  @Test
  public void testProvidesGenericTypeParametersInstanceMethodToMethod() {
    ServiceLocator locator = newServiceLocator();

    ProvidesGenericMethod<Value15> providerA =
        InjectUtils.getService(
            locator,
            new TypeToken<ProvidesGenericMethod<Value15>>() {});

    assertNotNull(providerA);
    assertNotNull(providerA.getValue());
    assertEquals(Value15.class, providerA.getValue().getClass());

    Value15 providedA = locator.getService(Value15.class);

    assertNotNull(providedA);
    assertEquals(Value15.class, providedA.getClass());

    ProvidesGenericMethod<Value16> providerB =
        InjectUtils.getService(
            locator,
            new TypeToken<ProvidesGenericMethod<Value16>>() {});

    assertNotNull(providerB);
    assertNotNull(providerB.getValue());
    assertEquals(Value16.class, providerB.getValue().getClass());

    Value16 providedB = locator.getService(Value16.class);

    assertNotNull(providedB);
    assertEquals(Value16.class, providedB.getClass());
  }

  /**
   * Verifies that generic type parameters are carried through a {@link
   * Provides} chain where a {@link Contract} interface is involved.
   */
  @Test
  public void testProvidesGenericContractTypeParameters() {
    ServiceLocator locator = newServiceLocator();

    IterableProvider<BoxFromGenericProvidesContract<String>> boxes =
        InjectUtils.getService(
            locator,
            new TypeToken<IterableProvider<BoxFromGenericProvidesContract<String>>>() {});

    assertEquals(4, boxes.getSize());

    assertEquals(
        Set.of(
            "staticField",
            "instanceField",
            "staticMethod",
            "instanceMethod"),
        Streams.stream(boxes)
               .map(box -> box.value)
               .collect(toSet()));
  }

  /**
   * Verifies that when a {@link Contract} interface has a {@link Provides}
   * method, and then a concrete service class overrides that method and the
   * overridden method is also implemented with {@link Provides}, that they are
   * considered to be the same method and not counted as distinct providers.
   */
  @Test
  public void testProvidesOverrideMethodNoDuplicates() {
    ServiceLocator locator = newServiceLocator();

    Set<String> expected =
        Set.of(
            "staticField",
            "instanceField",
            "staticMethod",
            "instanceMethod");

    IterableProvider<OverrideBoxFromGenericProvidesContract<String>> overrides =
        InjectUtils.getService(
            locator,
            new TypeToken<IterableProvider<OverrideBoxFromGenericProvidesContract<String>>>() {});

    assertEquals(expected.size(), overrides.getSize());

    assertEquals(
        expected,
        Streams.stream(overrides)
               .map(box -> box.value)
               .collect(toSet()));
  }

  /**
   * Verifies that when a generic provider {@code class Foo<T>} declares a
   * static method {@code <T> static Bar<T> bar(...)} that also uses a type
   * variable, and that static method is with {@link Provides}, and a narrower
   * type of the provider class {@code Foo<String>} has been provided somewhere,
   * that the type of service provided by the static method isn't also wrongly
   * narrowed to {@code Bar<String>}.  In that case, the static method's
   * contract type should still be {@code Bar<T>}, since its generic type
   * variable {@code T} is distinct from the {@code T} in {@code class Foo<T>}
   * even though they are both named "T".
   *
   * <p>Also verifies that the {@link Provides} analysis doesn't fail when it
   * encounters generic type variables like {@code T}.  A previous
   * implementation of {@link Provides} relied on {@link
   * ReflectionHelper#getAllTypes(Type)}, which did fail in this case.
   */
  @Test
  public void testProvidesStaticMethodGenericsNotInheritedFromClass() {
    ServiceLocator locator = newServiceLocator();

    assertNotNull(locator.getService(LinksToStaticGenericProvider.class));

    assertNotNull(
        InjectUtils.getService(
            locator,
            new TypeToken<StaticGenericProvider<String>>() {}));

    assertNotNull(
        InjectUtils.getService(
            locator,
            new TypeToken<ParamFromStaticGenericTest<String>>() {}));

    assertThrows(
        NoSuchElementException.class,
        () -> InjectUtils.getService(
            locator,
            new TypeToken<ReturnFromStaticGenericTest<String>>() {}));
  }

  /**
   * Constructs a new set of services to be used in one test.
   */
  private ServiceLocator newServiceLocator() {
    ServiceLocator locator =
        ServiceLocatorUtilities.createAndPopulateServiceLocator();

    NoInstancesFilter.enableNoInstancesFilter(locator);
    ServiceLocatorUtilities.bind(locator, new ServicesTestBinder());
    return locator;
  }

  public static final class ServicesTestBinder extends AbstractBinder {
    @Override
    protected void configure() {
      install(new ProvidesModule());
      addActiveDescriptor(PerLookupService.class);
      addActiveDescriptor(SingletonService.class);
      addActiveDescriptor(ServiceWithContract1.class);
      addActiveDescriptor(ServiceWithContract2.class);
      addActiveDescriptor(ServiceWithGenericContract1.class);
      addActiveDescriptor(ServiceWithGenericContract2.class);
      addActiveDescriptor(ServiceWithLifecycle.class);
      addActiveDescriptor(SingletonServiceWithShutdown.class);
      addActiveFactoryDescriptor(FactoryOfServiceWithShutdown.class);
      addActiveFactoryDescriptor(FactoryOfSingletonServiceWithShutdown.class);
      addActiveDescriptor(ProvidesService.class);
      addActiveDescriptor(ProvidesSelfFromMethod.class);
      addActiveDescriptor(ProvidesSelfFromField.class);
      addActiveDescriptor(UtilityClassProvides.class);
      addActiveDescriptor(AbstractClassProvides.class);
      addActiveDescriptor(InterfaceProvides.class);
      addActiveDescriptor(EnumProvides.class);
      addActiveDescriptor(EnumProvidesContract.class);
      addActiveDescriptor(ProvidesCustomDispose.class);
      addActiveDescriptor(ProvidesExplicitContracts.class);
      addActiveDescriptor(ProvidesNull.class);
      addActiveDescriptor(ProvidesLifecycleFactory.class);
      addActiveDescriptor(ProvidesLifecycleDependency.class);
      addActiveDescriptor(GenericTypeParameterChains.class);
      addActiveDescriptor(ProvidesGenericContracts.class);
      addActiveDescriptor(LinksToStaticGenericProvider.class);
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

  public enum EnumProvides {
    @Provides
    FOO,

    @Provides
    BAR {
      // Have a class body, which subclasses the enum type, so that we can
      // verify that all of the enum constants still provide the same contract.
      @Override
      public String toString() {
        return "bar";
      }
    }
  }

  @Contract
  public interface EnumContract {}

  @Contract
  public interface SecondEnumContract {}

  public enum EnumProvidesContract implements EnumContract, SecondEnumContract {
    @Provides
    FOO,

    @Provides
    BAR {
      // Have a class body, which subclasses the enum type, so that we can
      // verify that all of the enum constants still provide the same contract.
      @Override
      public String toString() {
        return "bar";
      }
    }
  }

  public static final class ProvidesCustomDispose {
    @Provides(
        disposeMethod = "customDisposeMethod",
        disposalHandledBy = Provides.DisposalHandledBy.PROVIDED_INSTANCE)
    @Singleton
    public static final ProvidedWithCustomDisposeFromStaticField staticField =
        new ProvidedWithCustomDisposeFromStaticField();

    @Provides(
        disposeMethod = "staticDestroyMethod",
        disposalHandledBy = Provides.DisposalHandledBy.PROVIDER)
    @Singleton
    public static final ProvidedWithCustomDisposeFromStaticFieldForFactory staticFieldForFactory =
        new ProvidedWithCustomDisposeFromStaticFieldForFactory();

    @Provides(
        disposeMethod = "customDisposeMethod",
        disposalHandledBy = Provides.DisposalHandledBy.PROVIDED_INSTANCE)
    @Singleton
    public final ProvidedWithCustomDisposeFromInstanceField instanceField =
        new ProvidedWithCustomDisposeFromInstanceField();

    @Provides(
        disposeMethod = "instanceDestroyMethod",
        disposalHandledBy = Provides.DisposalHandledBy.PROVIDER)
    @Singleton
    public final ProvidedWithCustomDisposeFromInstanceFieldForFactory instanceFieldForFactory =
        new ProvidedWithCustomDisposeFromInstanceFieldForFactory();

    @Provides(
        disposeMethod = "customDisposeMethod",
        disposalHandledBy = Provides.DisposalHandledBy.PROVIDED_INSTANCE)
    @Singleton
    public static ProvidedWithCustomDisposeFromStaticMethod staticMethod() {
      return new ProvidedWithCustomDisposeFromStaticMethod();
    }

    @Provides(
        disposeMethod = "staticDestroyMethod",
        disposalHandledBy = Provides.DisposalHandledBy.PROVIDER)
    @Singleton
    public static ProvidedWithCustomDisposeFromStaticMethodForFactory staticMethodForFactory() {
      return new ProvidedWithCustomDisposeFromStaticMethodForFactory();
    }

    @Provides(
        disposeMethod = "customDisposeMethod",
        disposalHandledBy = Provides.DisposalHandledBy.PROVIDED_INSTANCE)
    @Singleton
    public ProvidedWithCustomDisposeFromInstanceMethod instanceMethod() {
      return new ProvidedWithCustomDisposeFromInstanceMethod();
    }

    @Provides(
        disposeMethod = "instanceDestroyMethod",
        disposalHandledBy = Provides.DisposalHandledBy.PROVIDER)
    @Singleton
    public ProvidedWithCustomDisposeFromInstanceMethodForFactory instanceMethodForFactory() {
      return new ProvidedWithCustomDisposeFromInstanceMethodForFactory();
    }

    public static void staticDestroyMethod(HasCustomDisposeMethod instance) {
      instance.customDisposeMethod();
    }

    public void instanceDestroyMethod(HasCustomDisposeMethod instance) {
      instance.customDisposeMethod();
    }
  }

  public static class HasCustomDisposeMethod {
    @GuardedBy("this")
    private boolean isClosed;

    public synchronized boolean isClosed() {
      return isClosed;
    }

    public synchronized void customDisposeMethod() {
      isClosed = true;
    }
  }

  public static final class ProvidedWithCustomDisposeFromStaticField extends HasCustomDisposeMethod {}
  public static final class ProvidedWithCustomDisposeFromInstanceField extends HasCustomDisposeMethod {}
  public static final class ProvidedWithCustomDisposeFromStaticMethod extends HasCustomDisposeMethod {}
  public static final class ProvidedWithCustomDisposeFromInstanceMethod extends HasCustomDisposeMethod {}

  public static final class ProvidedWithCustomDisposeFromStaticFieldForFactory extends HasCustomDisposeMethod {}
  public static final class ProvidedWithCustomDisposeFromInstanceFieldForFactory extends HasCustomDisposeMethod {}
  public static final class ProvidedWithCustomDisposeFromStaticMethodForFactory extends HasCustomDisposeMethod {}
  public static final class ProvidedWithCustomDisposeFromInstanceMethodForFactory extends HasCustomDisposeMethod {}

  public static final class ProvidesExplicitContracts {
    @Provides(contracts = ExplicitContractInStaticField.class)
    public static final HasDefaultContractsInStaticField staticField =
        new HasDefaultContractsInStaticField();

    @Provides(contracts = ExplicitContractInInstanceField.class)
    public final HasDefaultContractsInInstanceField instanceField =
        new HasDefaultContractsInInstanceField();

    @Provides(contracts = ExplicitContractInStaticMethod.class)
    public static HasDefaultContractsInStaticMethod staticMethod() {
      return new HasDefaultContractsInStaticMethod();
    }

    @Provides(contracts = ExplicitContractInInstanceMethod.class)
    public HasDefaultContractsInInstanceMethod instanceMethod() {
      return new HasDefaultContractsInInstanceMethod();
    }
  }

  @Contract public interface DefaultContractInStaticField {}
  @Contract public interface DefaultContractInInstanceField {}
  @Contract public interface DefaultContractInStaticMethod {}
  @Contract public interface DefaultContractInInstanceMethod {}

  // not @Contract
  public interface ExplicitContractInStaticField {}
  public interface ExplicitContractInInstanceField {}
  public interface ExplicitContractInStaticMethod {}
  public interface ExplicitContractInInstanceMethod {}

  public static final class HasDefaultContractsInStaticField
      implements DefaultContractInStaticField, ExplicitContractInStaticField {}

  public static final class HasDefaultContractsInInstanceField
      implements DefaultContractInInstanceField, ExplicitContractInInstanceField {}

  public static final class HasDefaultContractsInStaticMethod
      implements DefaultContractInStaticMethod, ExplicitContractInStaticMethod {}

  public static final class HasDefaultContractsInInstanceMethod
      implements DefaultContractInInstanceMethod, ExplicitContractInInstanceMethod {}

  public static final class ProvidesNull {
    @Provides
    public static final @Nullable NullFromStaticField staticField = null;

    @Provides
    public final @Nullable NullFromInstanceField instanceField = null;

    @Provides
    public static @Nullable NullFromStaticMethod staticMethod() {
      return null;
    }

    @Provides
    public @Nullable NullFromInstanceMethod instanceMethod() {
      return null;
    }
  }

  public static final class NullFromStaticField {}
  public static final class NullFromInstanceField {}
  public static final class NullFromStaticMethod {}
  public static final class NullFromInstanceMethod {}

  public static final class ProvidesLifecycleFactory extends ServiceWithLifecycle {
    @Provides
    public static final ProvidesLifecycleFromStaticField staticField =
        new ProvidesLifecycleFromStaticField(new ProvidesLifecycleFactory(),
                                             new ProvidesLifecycleDependency());

    @Provides
    public final ProvidesLifecycleFromInstanceField instanceField =
        new ProvidesLifecycleFromInstanceField(this, new ProvidesLifecycleDependency());

    @Provides
    public static ProvidesLifecycleFromStaticMethod staticMethod(
        ProvidesLifecycleDependency dependency) {
      return new ProvidesLifecycleFromStaticMethod(new ProvidesLifecycleFactory(),
                                                   dependency);
    }

    @Provides
    public ProvidesLifecycleFromInstanceMethod instanceMethod(
        ProvidesLifecycleDependency dependency) {
      return new ProvidesLifecycleFromInstanceMethod(this, dependency);
    }
  }

  public abstract static class ProvidesLifecycleRoot extends ServiceWithLifecycle {
    public final ProvidesLifecycleFactory factory;
    public final ProvidesLifecycleDependency dependency;

    protected ProvidesLifecycleRoot(ProvidesLifecycleFactory factory,
                                    ProvidesLifecycleDependency dependency) {
      this.factory = Objects.requireNonNull(factory);
      this.dependency = Objects.requireNonNull(dependency);
    }
  }

  public static final class ProvidesLifecycleDependency extends ServiceWithLifecycle {}

  public static final class ProvidesLifecycleFromStaticField extends ProvidesLifecycleRoot {
    public ProvidesLifecycleFromStaticField(
        ProvidesLifecycleFactory factory,
        ProvidesLifecycleDependency dependency) {
      super(factory, dependency);
    }
  }

  public static final class ProvidesLifecycleFromInstanceField extends ProvidesLifecycleRoot {
    public ProvidesLifecycleFromInstanceField(
        ProvidesLifecycleFactory factory,
        ProvidesLifecycleDependency dependency) {
      super(factory, dependency);
    }
  }

  public static final class ProvidesLifecycleFromStaticMethod extends ProvidesLifecycleRoot {
    public ProvidesLifecycleFromStaticMethod(
        ProvidesLifecycleFactory factory,
        ProvidesLifecycleDependency dependency) {
      super(factory, dependency);
    }
  }

  public static final class ProvidesLifecycleFromInstanceMethod extends ProvidesLifecycleRoot {
    public ProvidesLifecycleFromInstanceMethod(
        ProvidesLifecycleFactory factory,
        ProvidesLifecycleDependency dependency) {
      super(factory, dependency);
    }
  }

  public static final class Value1 {}
  public static final class Value2 {}
  public static final class Value3 {}
  public static final class Value4 {}
  public static final class Value5 {}
  public static final class Value6 {}
  public static final class Value7 {}
  public static final class Value8 {}
  public static final class Value9 {}
  public static final class Value10 {}
  public static final class Value11 {}
  public static final class Value12 {}
  public static final class Value13 {}
  public static final class Value14 {}
  public static final class Value15 {}
  public static final class Value16 {}

  public static final class ProvidesGenericField<T> {
    @Provides
    public final T value;

    ProvidesGenericField(T value) {
      this.value = Objects.requireNonNull(value);
    }
  }

  public static final class ProvidesGenericMethod<T> {
    private final T value;

    ProvidesGenericMethod(T value) {
      this.value = Objects.requireNonNull(value);
    }

    @Provides
    public T getValue() {
      return value;
    }
  }

  public static final class GenericTypeParameterChains {
    @Provides
    public static ProvidesGenericField<Value1> staticFieldToFieldA =
        new ProvidesGenericField<>(new Value1());

    @Provides
    public static ProvidesGenericField<Value2> staticFieldToFieldB =
        new ProvidesGenericField<>(new Value2());

    @Provides
    public ProvidesGenericField<Value3> instanceFieldToFieldA =
        new ProvidesGenericField<>(new Value3());

    @Provides
    public ProvidesGenericField<Value4> instanceFieldToFieldB =
        new ProvidesGenericField<>(new Value4());

    @Provides
    public static ProvidesGenericField<Value5> staticMethodToFieldA() {
      return new ProvidesGenericField<>(new Value5());
    }

    @Provides
    public static ProvidesGenericField<Value6> staticMethodToFieldB() {
      return new ProvidesGenericField<>(new Value6());
    }

    @Provides
    public ProvidesGenericField<Value7> instanceMethodToFieldA() {
      return new ProvidesGenericField<>(new Value7());
    }

    @Provides
    public ProvidesGenericField<Value8> instanceMethodToFieldB() {
      return new ProvidesGenericField<>(new Value8());
    }

    @Provides
    public static ProvidesGenericMethod<Value9> staticFieldToMethodA =
        new ProvidesGenericMethod<>(new Value9());

    @Provides
    public static ProvidesGenericMethod<Value10> staticFieldToMethodB =
        new ProvidesGenericMethod<>(new Value10());

    @Provides
    public ProvidesGenericMethod<Value11> instanceFieldToMethodA =
        new ProvidesGenericMethod<>(new Value11());

    @Provides
    public ProvidesGenericMethod<Value12> instanceFieldToMethodB =
        new ProvidesGenericMethod<>(new Value12());

    @Provides
    public static ProvidesGenericMethod<Value13> staticMethodToMethodA() {
      return new ProvidesGenericMethod<>(new Value13());
    }

    @Provides
    public static ProvidesGenericMethod<Value14> staticMethodToMethodB() {
      return new ProvidesGenericMethod<>(new Value14());
    }

    @Provides
    public ProvidesGenericMethod<Value15> instanceMethodToMethodA() {
      return new ProvidesGenericMethod<>(new Value15());
    }

    @Provides
    public ProvidesGenericMethod<Value16> instanceMethodToMethodB() {
      return new ProvidesGenericMethod<>(new Value16());
    }
  }

  public static final class BoxFromGenericProvidesContract<T> {
    public final T value;

    BoxFromGenericProvidesContract(T value) {
      this.value = Objects.requireNonNull(value);
    }
  }

  public static final class OverrideBoxFromGenericProvidesContract<T> {
    public final T value;

    OverrideBoxFromGenericProvidesContract(T value) {
      this.value = Objects.requireNonNull(value);
    }
  }

  @Contract
  public interface GenericProvidesContract<T> {
    @Provides
    default BoxFromGenericProvidesContract<T> getBox() {
      return new BoxFromGenericProvidesContract<>(getValue());
    }

    @Provides
    default OverrideBoxFromGenericProvidesContract<T> getOverrideBox() {
      return new OverrideBoxFromGenericProvidesContract<>(getValue());
    }

    T getValue();
  }

  public static final class GenericProvidesClassWithContract<T>
      implements GenericProvidesContract<T> {

    private final T value;

    GenericProvidesClassWithContract(T value) {
      this.value = Objects.requireNonNull(value);
    }

    @Override
    public T getValue() {
      return value;
    }

    @Override
    @Provides
    public OverrideBoxFromGenericProvidesContract<T> getOverrideBox() {
      return new OverrideBoxFromGenericProvidesContract<>(getValue());
    }
  }

  public static final class ProvidesGenericContracts {
    @Provides
    public static GenericProvidesClassWithContract<String> staticField =
        new GenericProvidesClassWithContract<>("staticField");

    @Provides
    public GenericProvidesClassWithContract<String> instanceField =
        new GenericProvidesClassWithContract<>("instanceField");

    @Provides
    public static GenericProvidesClassWithContract<String> staticMethod() {
      return new GenericProvidesClassWithContract<>("staticMethod");
    }

    @Provides
    public GenericProvidesClassWithContract<String> instanceMethod() {
      return new GenericProvidesClassWithContract<>("instanceMethod");
    }
  }

  public static final class ReturnFromStaticGenericTest<T> {
    public final T value;

    ReturnFromStaticGenericTest(T value) {
      this.value = Objects.requireNonNull(value);
    }
  }

  public static final class ParamFromStaticGenericTest<T> {
    public final T value;

    ParamFromStaticGenericTest(T value) {
      this.value = Objects.requireNonNull(value);
    }
  }

  public static final class LinksToStaticGenericProvider {
    @Provides
    public StaticGenericProvider<String> getIt() {
      return new StaticGenericProvider<String>();
    }
  }

  public static final class StaticGenericProvider<T> {
    @Provides
    public ParamFromStaticGenericTest<String> param() {
      return new ParamFromStaticGenericTest<>("hello");
    }

    @Provides
    // not the same <T>
    public static <T> ReturnFromStaticGenericTest<T> staticMethod(
        ParamFromStaticGenericTest<T> param) {
      return new ReturnFromStaticGenericTest<>(param.value);
    }
  }
}
