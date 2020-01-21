package tfb.status.hk2.extensions;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.glassfish.hk2.utilities.ServiceLocatorUtilities.createAndPopulateServiceLocator;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static tfb.status.hk2.extensions.CompatibleWithJava8.listOf;
import static tfb.status.hk2.extensions.CompatibleWithJava8.setOf;

import java.lang.annotation.Retention;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.StreamSupport;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Qualifier;
import javax.inject.Singleton;
import org.glassfish.hk2.api.ActiveDescriptor;
import org.glassfish.hk2.api.Filter;
import org.glassfish.hk2.api.IterableProvider;
import org.glassfish.hk2.api.MultiException;
import org.glassfish.hk2.api.PostConstruct;
import org.glassfish.hk2.api.PreDestroy;
import org.glassfish.hk2.api.Self;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.api.TypeLiteral;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.glassfish.hk2.utilities.reflection.ReflectionHelper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.jvnet.hk2.annotations.Contract;

/**
 * Tests for {@link Provides} and {@link ProvidesListener}.
 */
public final class ProvidesTest {
  /**
   * Verifies that a service with {@link Provides} annotations can also be its
   * own service that can be retrieved.
   */
  @Test
  public void testServiceThatProvidesAndIsAService() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesString.class);

    ProvidesString service =
        locator.getService(ProvidesString.class);

    assertNotNull(service);

    String string = locator.getService(String.class);
    assertEquals("hello", string);

    ServiceHandle<String> handle = locator.getServiceHandle(String.class);
    handle.getService();
    handle.close();
  }

  /**
   * Verifies that a service may be registered by way of a static field
   * annotated with {@link Provides}.
   */
  @Test
  public void testStaticFieldProvides() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesStaticField.class);

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesInstanceField.class);

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesStaticMethod.class);

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesInstanceMethod.class);

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ParamForProvides1.class,
        ParamForProvides2.class,
        ProvidesStaticMethodWithParams.class);

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ParamForProvides1.class,
        ParamForProvides2.class,
        ProvidesInstanceMethodWithParams.class);

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesChain.class);

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesChain.class);

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesChain.class);

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesChain.class);

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesQualifiedGenericMethod.class);

    assertNotNull(
        locator.getService(
            new TypeLiteral<GenericFromProvidesMethod<String>>() {}.getType()));

    assertNull(
        locator.getService(
            new TypeLiteral<GenericFromProvidesMethod<Integer>>() {}.getType()));
  }

  /**
   * Verifies that a service with a generic type can be registered from a field
   * annotated with {@link Provides}.
   */
  @Test
  public void testGenericFieldProvides() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesQualifiedGenericField.class);

    assertNotNull(
        locator.getService(
            new TypeLiteral<GenericFromProvidesField<String>>() {}.getType()));

    assertNull(
        locator.getService(
            new TypeLiteral<GenericFromProvidesField<Integer>>() {}.getType()));
  }

  /**
   * Verifies that the {@link PostConstruct} and {@link PreDestroy} methods of a
   * service are invoked at the expected times when the service was registered
   * from a static {@link Provides} field and the service is a singleton.
   */
  @Test
  public void testStaticFieldProvidesSingletonLifecycle() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesLifecycle.class);

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesLifecycle.class);

    ProvidedSingletonInstanceFieldWithLifecycle service =
        locator.getService(ProvidedSingletonInstanceFieldWithLifecycle.class);

    assertNotNull(service);
    assertFalse(service.wasStarted());
    assertFalse(service.wasStopped());

    assertSame(
        service,
        locator.getService(ProvidedSingletonInstanceFieldWithLifecycle.class));

    locator.shutdown();

    assertFalse(service.wasStopped());
  }

  /**
   * Verifies that the {@link PostConstruct} and {@link PreDestroy} methods of a
   * service are invoked at the expected times when the service was registered
   * from a static {@link Provides} method and the service is a singleton.
   */
  @Test
  public void testStaticMethodProvidesSingletonLifecycle() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesLifecycle.class);

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesLifecycle.class);

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesLifecycle.class);

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesLifecycle.class);

    IterableProvider<ProvidedPerLookupStaticFieldWithLifecycleWithHandle> serviceProvider =
        locator.getService(
            new TypeLiteral<IterableProvider<ProvidedPerLookupStaticFieldWithLifecycleWithHandle>>() {}.getType());

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesLifecycle.class);

    ProvidedPerLookupInstanceFieldWithLifecycle serviceWithoutHandle =
        locator.getService(ProvidedPerLookupInstanceFieldWithLifecycle.class);

    assertNotNull(serviceWithoutHandle);
    assertFalse(serviceWithoutHandle.wasStarted());
    assertFalse(serviceWithoutHandle.wasStopped());

    assertNotSame(
        serviceWithoutHandle,
        locator.getService(ProvidedPerLookupInstanceFieldWithLifecycle.class));

    IterableProvider<ProvidedPerLookupInstanceFieldWithLifecycle> serviceProvider =
        locator.getService(
            new TypeLiteral<IterableProvider<ProvidedPerLookupInstanceFieldWithLifecycle>>() {}.getType());

    ServiceHandle<ProvidedPerLookupInstanceFieldWithLifecycle> serviceHandle =
        serviceProvider.getHandle();

    ProvidedPerLookupInstanceFieldWithLifecycle serviceWitHandle =
        serviceHandle.getService();

    assertNotNull(serviceWitHandle);
    assertFalse(serviceWitHandle.wasStarted());
    assertFalse(serviceWitHandle.wasStopped());

    serviceHandle.close();

    assertFalse(serviceWitHandle.wasStopped());

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesLifecycle.class);

    ProvidedPerLookupStaticMethodWithLifecycle serviceWithoutHandle =
        locator.getService(ProvidedPerLookupStaticMethodWithLifecycle.class);

    assertNotNull(serviceWithoutHandle);
    assertFalse(serviceWithoutHandle.wasStarted());
    assertFalse(serviceWithoutHandle.wasStopped());

    assertNotSame(
        serviceWithoutHandle,
        locator.getService(ProvidedPerLookupStaticMethodWithLifecycle.class));

    IterableProvider<ProvidedPerLookupStaticMethodWithLifecycle> serviceProvider =
        locator.getService(
            new TypeLiteral<IterableProvider<ProvidedPerLookupStaticMethodWithLifecycle>>() {}.getType());

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesLifecycle.class);

    ProvidedPerLookupInstanceMethodWithLifecycle serviceWithoutHandle =
        locator.getService(ProvidedPerLookupInstanceMethodWithLifecycle.class);

    assertNotNull(serviceWithoutHandle);
    assertFalse(serviceWithoutHandle.wasStarted());
    assertFalse(serviceWithoutHandle.wasStopped());

    assertNotSame(
        serviceWithoutHandle,
        locator.getService(ProvidedPerLookupInstanceMethodWithLifecycle.class));

    IterableProvider<ProvidedPerLookupInstanceMethodWithLifecycle> serviceProvider =
        locator.getService(
            new TypeLiteral<IterableProvider<ProvidedPerLookupInstanceMethodWithLifecycle>>() {}.getType());

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
   * Verifies that {@link Provides#disposeMethod()} cannot specify a method of
   * the provided type to be invoked at the end of the service's lifecycle when
   * the {@link Provides} annotation is on a static field and {@link
   * Provides#disposalHandledBy()} is {@link
   * Provides.DisposalHandledBy#PROVIDED_INSTANCE}.
   */
  @Test
  public void testProvidesCustomDisposeStaticField() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesCustomDispose.class);

    HasCustomDisposeMethod service =
        locator.getService(ProvidedWithCustomDisposeFromStaticField.class);

    assertFalse(service.isClosed());
    locator.shutdown();
    assertFalse(service.isClosed());
  }

  /**
   * Verifies that {@link Provides#disposeMethod()} cannot specify a method of
   * the provided type to be invoked at the end of the service's lifecycle when
   * the {@link Provides} annotation is on an instance field and {@link
   * Provides#disposalHandledBy()} is {@link
   * Provides.DisposalHandledBy#PROVIDED_INSTANCE}.
   */
  @Test
  public void testProvidesCustomDisposeInstanceField() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesCustomDispose.class);

    HasCustomDisposeMethod service =
        locator.getService(ProvidedWithCustomDisposeFromInstanceField.class);

    assertFalse(service.isClosed());
    locator.shutdown();
    assertFalse(service.isClosed());
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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesCustomDispose.class);

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesCustomDispose.class);

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesCustomDispose.class);

    HasCustomDisposeMethod service =
        locator.getService(ProvidedWithCustomDisposeFromStaticFieldForFactory.class);

    assertFalse(service.isClosed());
    locator.shutdown();
    assertFalse(service.isClosed());
  }

  /**
   * Verifies that {@link Provides#disposeMethod()} cannot specify a method of
   * the provider type to be invoked at the end of the service's lifecycle when
   * the {@link Provides} annotation is on an instance field and {@link
   * Provides#disposalHandledBy()} is {@link
   * Provides.DisposalHandledBy#PROVIDER}.
   */
  @Test
  public void testProvidesCustomDisposeInstanceFieldFactoryDestroys() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesCustomDispose.class);

    HasCustomDisposeMethod service =
        locator.getService(ProvidedWithCustomDisposeFromInstanceFieldForFactory.class);

    assertFalse(service.isClosed());
    locator.shutdown();
    assertFalse(service.isClosed());
  }

  /**
   * Verifies that {@link Provides#disposeMethod()} may specify a static method
   * of the provider type to be invoked at the end of the service's lifecycle
   * when the {@link Provides} annotation is on a static method and {@link
   * Provides#disposalHandledBy()} is {@link
   * Provides.DisposalHandledBy#PROVIDER}.
   */
  @Test
  public void testProvidesCustomDisposeStaticMethodToFactoryStaticMethod() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesCustomDispose.class);

    HasCustomDisposeMethod service =
        locator.getService(ProvidedWithCustomDisposeFromStaticMethodToFactoryStaticMethod.class);

    assertFalse(service.isClosed());
    locator.shutdown();
    assertTrue(service.isClosed());
  }

  /**
   * Verifies that {@link Provides#disposeMethod()} may specify an instance
   * method of the provider type to be invoked at the end of the service's
   * lifecycle when the {@link Provides} annotation is on a static method and
   * {@link Provides#disposalHandledBy()} is {@link
   * Provides.DisposalHandledBy#PROVIDER}.
   */
  @Test
  public void testProvidesCustomDisposeStaticMethodToFactoryInstanceMethod() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesCustomDispose.class);

    HasCustomDisposeMethod service =
        locator.getService(ProvidedWithCustomDisposeFromStaticMethodToFactoryInstanceMethod.class);

    assertFalse(service.isClosed());
    locator.shutdown();
    assertTrue(service.isClosed());
  }

  /**
   * Verifies that {@link Provides#disposeMethod()} may specify a static method
   * of the provider type to be invoked at the end of the service's lifecycle
   * when the {@link Provides} annotation is on an instance method and {@link
   * Provides#disposalHandledBy()} is {@link
   * Provides.DisposalHandledBy#PROVIDER}.
   */
  @Test
  public void testProvidesCustomDisposeInstanceMethodToFactoryStaticMethod() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesCustomDispose.class);

    HasCustomDisposeMethod service =
        locator.getService(ProvidedWithCustomDisposeFromInstanceMethodToFactoryStaticMethod.class);

    assertFalse(service.isClosed());
    locator.shutdown();
    assertTrue(service.isClosed());
  }

  /**
   * Verifies that {@link Provides#disposeMethod()} may specify an instance
   * method of the provider type to be invoked at the end of the service's
   * lifecycle when the {@link Provides} annotation is on an instance method and
   * {@link Provides#disposalHandledBy()} is {@link
   * Provides.DisposalHandledBy#PROVIDER}.
   */
  @Test
  public void testProvidesCustomDisposeInstanceMethodToFactoryInstanceMethod() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesCustomDispose.class);

    HasCustomDisposeMethod service =
        locator.getService(ProvidedWithCustomDisposeFromInstanceMethodToFactoryInstanceMethod.class);

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesExplicitContracts.class);

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesExplicitContracts.class);

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesExplicitContracts.class);

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesExplicitContracts.class);

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesNull.class);

    IterableProvider<NullFromStaticField> provider =
        locator.getService(
            new TypeLiteral<IterableProvider<NullFromStaticField>>() {}.getType());

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesNull.class);

    IterableProvider<NullFromInstanceField> provider =
        locator.getService(
            new TypeLiteral<IterableProvider<NullFromInstanceField>>() {}.getType());

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesNull.class);

    IterableProvider<NullFromStaticMethod> provider =
        locator.getService(
            new TypeLiteral<IterableProvider<NullFromStaticMethod>>() {}.getType());

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesNull.class);

    IterableProvider<NullFromInstanceMethod> provider =
        locator.getService(
            new TypeLiteral<IterableProvider<NullFromInstanceMethod>>() {}.getType());

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesLifecycleFactory.class,
        ProvidesLifecycleDependency.class);

    IterableProvider<ProvidesLifecycleFromStaticField> provider =
        locator.getService(
            new TypeLiteral<IterableProvider<ProvidesLifecycleFromStaticField>>() {}.getType());

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesLifecycleFactory.class,
        ProvidesLifecycleDependency.class);

    IterableProvider<ProvidesLifecycleFromInstanceField> provider =
        locator.getService(
            new TypeLiteral<IterableProvider<ProvidesLifecycleFromInstanceField>>() {}.getType());

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

    assertFalse(root.wasStopped());
    assertTrue(root.factory.wasStopped());
    assertFalse(root.dependency.wasStopped());
  }

  /**
   * Verifies the lifecycle of a service obtained from a static method that is
   * annotated with {@link Provides}.
   */
  @Test
  public void testProvidesLifecycleFromStaticMethod() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesLifecycleFactory.class,
        ProvidesLifecycleDependency.class);

    IterableProvider<ProvidesLifecycleFromStaticMethod> provider =
        locator.getService(
            new TypeLiteral<IterableProvider<ProvidesLifecycleFromStaticMethod>>() {}.getType());

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesLifecycleFactory.class,
        ProvidesLifecycleDependency.class);

    IterableProvider<ProvidesLifecycleFromInstanceMethod> provider =
        locator.getService(
            new TypeLiteral<IterableProvider<ProvidesLifecycleFromInstanceMethod>>() {}.getType());

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        GenericTypeParameterChains.class);

    ProvidesGenericField<Value1> providerA =
        locator.getService(
            new TypeLiteral<ProvidesGenericField<Value1>>() {}.getType());

    assertNotNull(providerA);
    assertNotNull(providerA.value);
    assertEquals(Value1.class, providerA.value.getClass());

    Value1 providedA = locator.getService(Value1.class);

    assertNotNull(providedA);
    assertEquals(Value1.class, providedA.getClass());

    ProvidesGenericField<Value2> providerB =
        locator.getService(
            new TypeLiteral<ProvidesGenericField<Value2>>() {}.getType());

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        GenericTypeParameterChains.class);

    ProvidesGenericField<Value3> providerA =
        locator.getService(
            new TypeLiteral<ProvidesGenericField<Value3>>() {}.getType());

    assertNotNull(providerA);
    assertNotNull(providerA.value);
    assertEquals(Value3.class, providerA.value.getClass());

    Value3 providedA = locator.getService(Value3.class);

    assertNotNull(providedA);
    assertEquals(Value3.class, providedA.getClass());

    ProvidesGenericField<Value4> providerB =
        locator.getService(
            new TypeLiteral<ProvidesGenericField<Value4>>() {}.getType());

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        GenericTypeParameterChains.class);

    ProvidesGenericField<Value5> providerA =
        locator.getService(
            new TypeLiteral<ProvidesGenericField<Value5>>() {}.getType());

    assertNotNull(providerA);
    assertNotNull(providerA.value);
    assertEquals(Value5.class, providerA.value.getClass());

    Value5 providedA = locator.getService(Value5.class);

    assertNotNull(providedA);
    assertEquals(Value5.class, providedA.getClass());

    ProvidesGenericField<Value6> providerB =
        locator.getService(
            new TypeLiteral<ProvidesGenericField<Value6>>() {}.getType());

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        GenericTypeParameterChains.class);

    ProvidesGenericField<Value7> providerA =
        locator.getService(
            new TypeLiteral<ProvidesGenericField<Value7>>() {}.getType());

    assertNotNull(providerA);
    assertNotNull(providerA.value);
    assertEquals(Value7.class, providerA.value.getClass());

    Value7 providedA = locator.getService(Value7.class);

    assertNotNull(providedA);
    assertEquals(Value7.class, providedA.getClass());

    ProvidesGenericField<Value8> providerB =
        locator.getService(
            new TypeLiteral<ProvidesGenericField<Value8>>() {}.getType());

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        GenericTypeParameterChains.class);

    ProvidesGenericMethod<Value9> providerA =
        locator.getService(
            new TypeLiteral<ProvidesGenericMethod<Value9>>() {}.getType());

    assertNotNull(providerA);
    assertNotNull(providerA.getValue());
    assertEquals(Value9.class, providerA.getValue().getClass());

    Value9 providedA = locator.getService(Value9.class);

    assertNotNull(providedA);
    assertEquals(Value9.class, providedA.getClass());

    ProvidesGenericMethod<Value10> providerB =
        locator.getService(
            new TypeLiteral<ProvidesGenericMethod<Value10>>() {}.getType());

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        GenericTypeParameterChains.class);

    ProvidesGenericMethod<Value11> providerA =
        locator.getService(
            new TypeLiteral<ProvidesGenericMethod<Value11>>() {}.getType());

    assertNotNull(providerA);
    assertNotNull(providerA.getValue());
    assertEquals(Value11.class, providerA.getValue().getClass());

    Value11 providedA = locator.getService(Value11.class);

    assertNotNull(providedA);
    assertEquals(Value11.class, providedA.getClass());

    ProvidesGenericMethod<Value12> providerB =
        locator.getService(
            new TypeLiteral<ProvidesGenericMethod<Value12>>() {}.getType());

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        GenericTypeParameterChains.class);

    ProvidesGenericMethod<Value13> providerA =
        locator.getService(
            new TypeLiteral<ProvidesGenericMethod<Value13>>() {}.getType());

    assertNotNull(providerA);
    assertNotNull(providerA.getValue());
    assertEquals(Value13.class, providerA.getValue().getClass());

    Value13 providedA = locator.getService(Value13.class);

    assertNotNull(providedA);
    assertEquals(Value13.class, providedA.getClass());

    ProvidesGenericMethod<Value14> providerB =
        locator.getService(
            new TypeLiteral<ProvidesGenericMethod<Value14>>() {}.getType());

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        GenericTypeParameterChains.class);

    ProvidesGenericMethod<Value15> providerA =
        locator.getService(
            new TypeLiteral<ProvidesGenericMethod<Value15>>() {}.getType());

    assertNotNull(providerA);
    assertNotNull(providerA.getValue());
    assertEquals(Value15.class, providerA.getValue().getClass());

    Value15 providedA = locator.getService(Value15.class);

    assertNotNull(providedA);
    assertEquals(Value15.class, providedA.getClass());

    ProvidesGenericMethod<Value16> providerB =
        locator.getService(
            new TypeLiteral<ProvidesGenericMethod<Value16>>() {}.getType());

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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesGenericContracts.class);

    IterableProvider<BoxFromGenericProvidesContract<String>> boxes =
        locator.getService(
            new TypeLiteral<IterableProvider<BoxFromGenericProvidesContract<String>>>() {}.getType());

    Set<String> expected =
        setOf(
            "staticField",
            "instanceField",
            "staticMethod",
            "instanceMethod");

    assertEquals(expected.size(), boxes.getSize());

    assertEquals(
        expected,
        StreamSupport.stream(boxes.spliterator(), false)
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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesGenericContracts.class);

    Set<String> expected =
        setOf(
            "staticField",
            "instanceField",
            "staticMethod",
            "instanceMethod");

    IterableProvider<OverrideBoxFromGenericProvidesContract<String>> overrides =
        locator.getService(
            new TypeLiteral<IterableProvider<OverrideBoxFromGenericProvidesContract<String>>>() {}.getType());

    assertEquals(expected.size(), overrides.getSize());

    assertEquals(
        expected,
        StreamSupport.stream(overrides.spliterator(), false)
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
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        LinksToStaticGenericProvider.class);

    assertNotNull(locator.getService(LinksToStaticGenericProvider.class));

    assertNotNull(
        locator.getService(
            new TypeLiteral<StaticGenericProvider<String>>() {}.getType()));

    assertNotNull(
        locator.getService(
            new TypeLiteral<ParamFromStaticGenericTest<String>>() {}.getType()));

    assertNull(
        locator.getService(
            new TypeLiteral<ReturnFromStaticGenericTest<String>>() {}.getType()));
  }

  /**
   * Verifies that if  {@link ProvidesListener#getFilter()} is not overridden,
   * then the implementation classes of all services are scanned for {@link
   * Provides} annotations.
   */
  @Test
  public void testDefaultFilter() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        FilteredProvider.class,
        UnfilteredProvider.class);
    assertNotNull(locator.getService(FilteredProvider.class));
    assertNotNull(locator.getService(UnfilteredProvider.class));
    assertNotNull(locator.getService(FromFilteredProvider.class));
    assertNotNull(locator.getService(FromUnfilteredProvider.class));
  }

  /**
   * Verifies that if {@link ProvidesListener#getFilter()} is overridden, then
   * that filter restricts the set of services whose implementation classes are
   * scanned for {@link Provides} annotations.
   */
  @Test
  public void testOverriddenFilter() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        FilteredProvidesListener.class,
        FilteredProvider.class,
        UnfilteredProvider.class);
    assertNotNull(locator.getService(FilteredProvider.class));
    assertNotNull(locator.getService(UnfilteredProvider.class));
    assertNull(locator.getService(FromFilteredProvider.class));
    assertNotNull(locator.getService(FromUnfilteredProvider.class));
  }

  /**
   * Verifies that an instance method annotated with {@link Provides} can
   * consume a parameter whose type declaration contains a type variable as long
   * as that type variable can be resolved in the context of the providing
   * service.
   */
  @Test
  public void testConsumesGenericParameter() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProviderOfConsumesGenericParameter.class);

    assertEquals("secret", locator.getService(String.class));

    FromConsumesGenericParameter<String> service =
        locator.getService(
            new TypeLiteral<FromConsumesGenericParameter<String>>() {}.getType());

    assertNotNull(service);
    assertEquals("secret", service.value);
  }

  /**
   * Verifies that if a field is annotated with {@link Provides} but its type
   * contains a top-level type variable that cannot be resolved, then no service
   * is registered for that field.
   */
  @Test
  public void testUnresolvableTypeVariableInFieldType() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        UnresolvableTypeVariableInFieldType.class);

    assertEquals(
        listOf(),
        locator.getDescriptors(
            d -> d.getQualifiers().contains(BetterNotFindMe.class.getName())));

    assertNotEquals(
        listOf(),
        locator.getDescriptors(
            d -> d.getQualifiers().contains(BetterFindMe.class.getName())));
  }

  /**
   * Verifies that if a field is annotated with {@link Provides} but its type
   * contains a type variable that cannot be resolved, where that type variable
   * is not at the top level, then no service is registered for that field.
   */
  @Test
  public void testUnresolvableTypeVariableDeepInFieldType() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        UnresolvableTypeVariableDeepInFieldType.class);

    assertEquals(
        listOf(),
        locator.getDescriptors(
            d -> d.getQualifiers().contains(BetterNotFindMe.class.getName())));

    assertNotEquals(
        listOf(),
        locator.getDescriptors(
            d -> d.getQualifiers().contains(BetterFindMe.class.getName())));
  }

  /**
   * Verifies that if a static method is annotated with {@link Provides} but its
   * return type contains a top-level type variable that cannot be resolved,
   * then no service is registered for that method.
   */
  @Test
  public void testUnresolvableTypeVariableInStaticMethodReturnType() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        UnresolvableTypeVariableInStaticMethodReturnType.class);

    assertEquals(
        listOf(),
        locator.getDescriptors(
            d -> d.getQualifiers().contains(BetterNotFindMe.class.getName())));

    assertNotEquals(
        listOf(),
        locator.getDescriptors(
            d -> d.getQualifiers().contains(BetterFindMe.class.getName())));
  }

  /**
   * Verifies that if a static method is annotated with {@link Provides} but its
   * return type contains a type variable that cannot be resolved, where that
   * type variable is not at the top level, then no service is registered for
   * that method.
   */
  @Test
  public void testUnresolvableTypeVariableDeepInStaticMethodReturnType() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        UnresolvableTypeVariableDeepInStaticMethodReturnType.class);

    assertEquals(
        listOf(),
        locator.getDescriptors(
            d -> d.getQualifiers().contains(BetterNotFindMe.class.getName())));

    assertNotEquals(
        listOf(),
        locator.getDescriptors(
            d -> d.getQualifiers().contains(BetterFindMe.class.getName())));
  }

  /**
   * Verifies that if an instance method is annotated with {@link Provides} but
   * its return type contains a top-level type variable that cannot be resolved,
   * then no service is registered for that method.
   */
  @Test
  public void testUnresolvableTypeVariableInInstanceMethodReturnType() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        UnresolvableTypeVariableInInstanceMethodReturnType.class);

    assertEquals(
        listOf(),
        locator.getDescriptors(
            d -> d.getQualifiers().contains(BetterNotFindMe.class.getName())));

    assertNotEquals(
        listOf(),
        locator.getDescriptors(
            d -> d.getQualifiers().contains(BetterFindMe.class.getName())));
  }

  /**
   * Verifies that if an instance method is annotated with {@link Provides} but
   * its return type contains a type variable that cannot be resolved, where
   * that type variable is not at the top level, then no service is registered
   * for that method.
   */
  @Test
  public void testUnresolvableTypeVariableDeepInInstanceMethodReturnType() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        UnresolvableTypeVariableDeepInInstanceMethodReturnType.class);

    assertEquals(
        listOf(),
        locator.getDescriptors(
            d -> d.getQualifiers().contains(BetterNotFindMe.class.getName())));

    assertNotEquals(
        listOf(),
        locator.getDescriptors(
            d -> d.getQualifiers().contains(BetterFindMe.class.getName())));
  }

  /**
   * Verifies that if a static method is annotated with {@link Provides} but one
   * of its parameter types contains a top-level type variable that cannot be
   * resolved, then no service is registered for that method.
   */
  @Test
  public void testUnresolvableTypeVariableInStaticMethodParameterType() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        UnresolvableTypeVariableInStaticMethodParameterType.class);

    assertEquals(
        listOf(),
        locator.getDescriptors(
            d -> d.getQualifiers().contains(BetterNotFindMe.class.getName())));

    assertNotEquals(
        listOf(),
        locator.getDescriptors(
            d -> d.getQualifiers().contains(BetterFindMe.class.getName())));
  }

  /**
   * Verifies that if a static method is annotated with {@link Provides} but one
   * of its parameter types contains a type variable that cannot be resolved,
   * where that type variable is not at the top level, then no service is
   * registered for that method.
   */
  @Test
  public void testUnresolvableTypeVariableDeepInStaticMethodParameterType() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        UnresolvableTypeVariableDeepInStaticMethodParameterType.class);

    assertEquals(
        listOf(),
        locator.getDescriptors(
            d -> d.getQualifiers().contains(BetterNotFindMe.class.getName())));

    assertNotEquals(
        listOf(),
        locator.getDescriptors(
            d -> d.getQualifiers().contains(BetterFindMe.class.getName())));
  }

  /**
   * Verifies that if an instance method is annotated with {@link Provides} but
   * one of its parameter types contains a top-level type variable that cannot
   * be resolved, then no service is registered for that method.
   */
  @Test
  public void testUnresolvableTypeVariableInInstanceMethodParameterType() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        UnresolvableTypeVariableInInstanceMethodParameterType.class);

    assertNotNull(locator.getService(BetterFindMe.class));

    assertEquals(
        listOf(),
        locator.getDescriptors(
            d -> d.getQualifiers().contains(BetterNotFindMe.class.getName())));
  }

  /**
   * Verifies that if an instance method is annotated with {@link Provides} but
   * one of its parameter types contains a type variable that cannot be
   * resolved, where that type variable is not at the top level, then no service
   * is registered for that method.
   */
  @Test
  public void testUnresolvableTypeVariableDeepInInstanceMethodParameterType() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        UnresolvableTypeVariableDeepInInstanceMethodParameterType.class);

    assertNotNull(locator.getService(BetterFindMe.class));

    assertEquals(
        listOf(),
        locator.getDescriptors(
            d -> d.getQualifiers().contains(BetterNotFindMe.class.getName())));
  }

  /**
   * Verifies that if a service comes from a {@link Provides} method or field
   * and its type defines lifecycle methods, or the {@link Provides} annotation
   * defines a custom dispose method, that no errors are thrown when the service
   * is {@code null}.
   */
  @Test
  public void testNullServicesWithLifecycle() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesNullWithLifecycle.class);

    IterableProvider<ServiceWithLifecycle> providers =
        locator.getService(
            new TypeLiteral<IterableProvider<ServiceWithLifecycle>>() {}.getType());

    assertEquals(12, providers.getSize());

    for (ServiceHandle<ServiceWithLifecycle> handle
        : providers.handleIterator()) {
      assertNull(handle.getService());
      handle.close();
    }
  }

  /**
   * Verifies that an instance method annotated with {@link Provides} may
   * provide a service of a generic array type.
   */
  @Test
  public void testProvidesGenericArray() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesStringArray.class);

    String[][] expected = { new String[] { "hey" } };
    String[][] actual = locator.getService(String[][].class);
    assertArrayEquals(expected, actual);
  }

  /**
   * Verifies that a wildcard in the generic type parameters of a service coming
   * from {@link Provides} is treated appropriately.  See the comments near
   * {@link WrongDisposeMethodParameterType#disposeBad(Object)} for details.
   */
  @Test
  public void testProvidesWildcardCapture() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesWrongDisposeMethodParameterType.class);

    assertEquals(
        listOf(),
        locator.getDescriptors(
            d -> d.getQualifiers().contains(BetterNotFindMe.class.getName())));

    assertNotEquals(
        listOf(),
        locator.getDescriptors(
            d -> d.getQualifiers().contains(BetterFindMe.class.getName())));
  }

  /**
   * Verifies that a {@link Provides} annotation may specify a dispose method on
   * the providing class that accepts multiple parameters.
   */
  @Test
  public void testStaticDisposeMultipleParameters() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        FactoryStats.class,
        PerLookupFactoryDependency.class,
        SingletonFactoryDependency.class,
        ProvidesDisposeMethodWithMultipleParameters.class);

    FactoryStats stats = locator.getService(FactoryStats.class);

    assertEquals(listOf(), stats.valuesProvided);
    assertEquals(listOf(), stats.valuesDisposed);
    assertEquals(0, stats.factoryDisposeCounter.get());
    assertEquals(0, stats.perLookupDependencyInitCounter.get());
    assertEquals(0, stats.perLookupDependencyDisposeCounter.get());
    assertEquals(0, stats.singletonDependencyInitCounter.get());
    assertEquals(0, stats.singletonDependencyDisposeCounter.get());

    try (ServiceHandle<String> handle =
             locator.getServiceHandle(String.class, "forStaticDisposeMethod")) {
      assertEquals("1", handle.getService());
      assertEquals(listOf("1"), stats.valuesProvided);
      assertEquals(listOf(), stats.valuesDisposed);
      assertEquals(0, stats.factoryDisposeCounter.get());
      assertEquals(0, stats.perLookupDependencyInitCounter.get());
      assertEquals(0, stats.perLookupDependencyDisposeCounter.get());
      assertEquals(0, stats.singletonDependencyInitCounter.get());
      assertEquals(0, stats.singletonDependencyDisposeCounter.get());
    }

    assertEquals(listOf("1"), stats.valuesProvided);
    assertEquals(listOf("1"), stats.valuesDisposed);
    assertEquals(0, stats.factoryDisposeCounter.get());
    assertEquals(1, stats.perLookupDependencyInitCounter.get());
    assertEquals(1, stats.perLookupDependencyDisposeCounter.get());
    assertEquals(1, stats.singletonDependencyInitCounter.get());
    assertEquals(0, stats.singletonDependencyDisposeCounter.get());

    try (ServiceHandle<String> handle =
             locator.getServiceHandle(String.class, "forInstanceDisposeMethod")) {
      assertEquals("2", handle.getService());
      assertEquals(listOf("1", "2"), stats.valuesProvided);
      assertEquals(listOf("1"), stats.valuesDisposed);
      assertEquals(0, stats.factoryDisposeCounter.get());
      assertEquals(1, stats.perLookupDependencyInitCounter.get());
      assertEquals(1, stats.perLookupDependencyDisposeCounter.get());
      assertEquals(1, stats.singletonDependencyInitCounter.get());
      assertEquals(0, stats.singletonDependencyDisposeCounter.get());
    }

    assertEquals(listOf("1", "2"), stats.valuesProvided);
    assertEquals(listOf("1", "2"), stats.valuesDisposed);
    assertEquals(1, stats.factoryDisposeCounter.get());
    assertEquals(2, stats.perLookupDependencyInitCounter.get());
    assertEquals(2, stats.perLookupDependencyDisposeCounter.get());
    assertEquals(1, stats.singletonDependencyInitCounter.get());
    assertEquals(0, stats.singletonDependencyDisposeCounter.get());

    locator.shutdown();

    assertEquals(listOf("1", "2"), stats.valuesProvided);
    assertEquals(listOf("1", "2"), stats.valuesDisposed);
    assertEquals(1, stats.factoryDisposeCounter.get());
    assertEquals(2, stats.perLookupDependencyInitCounter.get());
    assertEquals(2, stats.perLookupDependencyDisposeCounter.get());
    assertEquals(1, stats.singletonDependencyInitCounter.get());
    assertEquals(1, stats.singletonDependencyDisposeCounter.get());
  }

  /**
   * Verifies that {@link Self} may be used to inject the {@link
   * ActiveDescriptor} of a provided service into the method annotated with
   * {@link Provides} that provides that service or into the dispose method for
   * that service.
   */
  @Test
  public void testSelf() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        SeenByUsesSelf.class,
        UsesSelf.class);

    SeenByUsesSelf seen = locator.getService(SeenByUsesSelf.class);

    assertEquals(listOf(), seen.list);

    ServiceHandle<String> handle = locator.getServiceHandle(String.class);

    assertEquals("hello", handle.getService());

    assertEquals(
        listOf(UsesSelf.class, String.class),
        seen.list.stream()
                 .map(d -> d.getImplementationClass())
                 .collect(toList()));

    handle.close();

    assertEquals(
        listOf(UsesSelf.class, String.class, UsesSelf.class, String.class),
        seen.list.stream()
                 .map(d -> d.getImplementationClass())
                 .collect(toList()));
  }

  /**
   * Verifies that if a class declares a {@link Provides} instance method and
   * that method's return type is that same class, that only one descriptor is
   * registered for that class.
   */
  @Test
  public void testDirectMethodLoop() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        DirectMethodLoop.class);

    assertEquals(1, locator.getAllServiceHandles(DirectMethodLoop.class).size());

    // Assert that the loop is remembered across configurations.
    ServiceLocatorUtilities.addClasses(locator, ProvidesString.class);

    assertEquals(1, locator.getAllServiceHandles(DirectMethodLoop.class).size());
  }

  /**
   * Verifies that if a class declares a {@link Provides} instance method and
   * that method's return type is another service type with a {@link Provides}
   * instance method, and following this chain of {@link Provides} methods
   * eventually leads back to the original class, that only one descriptor is
   * registered for each class.
   */
  @Test
  public void testIndirectMethodLoop() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        StartsIndirectMethodLoop.class);

    assertEquals(1, locator.getAllServiceHandles(StartsIndirectMethodLoop.class).size());
    assertEquals(1, locator.getAllServiceHandles(MethodLoop1.class).size());
    assertEquals(1, locator.getAllServiceHandles(MethodLoop2.class).size());
    assertEquals(1, locator.getAllServiceHandles(MethodLoop3.class).size());

    // Assert that the loop is remembered across configurations.
    ServiceLocatorUtilities.addClasses(locator, ProvidesString.class);

    assertEquals(1, locator.getAllServiceHandles(StartsIndirectMethodLoop.class).size());
    assertEquals(1, locator.getAllServiceHandles(MethodLoop1.class).size());
    assertEquals(1, locator.getAllServiceHandles(MethodLoop2.class).size());
    assertEquals(1, locator.getAllServiceHandles(MethodLoop3.class).size());
  }

  /**
   * Like {@link #testDirectMethodLoop()}, except that each service in the chain
   * has a distinct generic type.
   */
  @Test
  public void testDirectGenericMethodLoop() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        StartsDirectGenericMethodLoop.class);

    assertEquals(1, locator.getAllServiceHandles(StartsDirectGenericMethodLoop.class).size());
    assertEquals(1, locator.getAllServiceHandles(DirectGenericMethodLoop.class).size());

    // Assert that the loop is remembered across configurations.
    ServiceLocatorUtilities.addClasses(locator, ProvidesString.class);

    assertEquals(1, locator.getAllServiceHandles(StartsDirectGenericMethodLoop.class).size());
    assertEquals(1, locator.getAllServiceHandles(DirectGenericMethodLoop.class).size());
  }

  /**
   * Like {@link #testIndirectMethodLoop()}, except that each service in the
   * chain has a distinct generic type.
   */
  @Test
  public void testIndirectGenericMethodLoop() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        StartsIndirectGenericMethodLoop.class);

    assertEquals(1, locator.getAllServiceHandles(StartsIndirectGenericMethodLoop.class).size());
    assertEquals(1, locator.getAllServiceHandles(GenericMethodLoop1.class).size());
    assertEquals(1, locator.getAllServiceHandles(GenericMethodLoop2.class).size());
    assertEquals(1, locator.getAllServiceHandles(GenericMethodLoop3.class).size());

    // Assert that the loop is remembered across configurations.
    ServiceLocatorUtilities.addClasses(locator, ProvidesString.class);

    assertEquals(1, locator.getAllServiceHandles(StartsIndirectGenericMethodLoop.class).size());
    assertEquals(1, locator.getAllServiceHandles(GenericMethodLoop1.class).size());
    assertEquals(1, locator.getAllServiceHandles(GenericMethodLoop2.class).size());
    assertEquals(1, locator.getAllServiceHandles(GenericMethodLoop3.class).size());
  }

  /**
   * Like {@link #testDirectMethodLoop()} but for fields instead of methods.
   */
  @Test
  public void testDirectFieldLoop() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        DirectFieldLoop.class);

    assertEquals(1, locator.getAllServiceHandles(DirectFieldLoop.class).size());

    // Assert that the loop is remembered across configurations.
    ServiceLocatorUtilities.addClasses(locator, ProvidesString.class);

    assertEquals(1, locator.getAllServiceHandles(DirectFieldLoop.class).size());
  }

  /**
   * Like {@link #testIndirectMethodLoop()} but for fields instead of methods.
   */
  @Test
  public void testIndirectFieldLoop() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        StartsIndirectFieldLoop.class);

    assertEquals(1, locator.getAllServiceHandles(StartsIndirectFieldLoop.class).size());
    assertEquals(1, locator.getAllServiceHandles(FieldLoop1.class).size());
    assertEquals(1, locator.getAllServiceHandles(FieldLoop2.class).size());
    assertEquals(1, locator.getAllServiceHandles(FieldLoop3.class).size());

    // Assert that the loop is remembered across configurations.
    ServiceLocatorUtilities.addClasses(locator, ProvidesString.class);

    assertEquals(1, locator.getAllServiceHandles(StartsIndirectFieldLoop.class).size());
    assertEquals(1, locator.getAllServiceHandles(FieldLoop1.class).size());
    assertEquals(1, locator.getAllServiceHandles(FieldLoop2.class).size());
    assertEquals(1, locator.getAllServiceHandles(FieldLoop3.class).size());
  }

  /**
   * Like {@link #testDirectGenericMethodLoop()} but for fields instead of
   * methods.
   */
  @Test
  public void testDirectGenericFieldLoop() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        StartsDirectGenericFieldLoop.class);

    assertEquals(1, locator.getAllServiceHandles(StartsDirectGenericFieldLoop.class).size());
    assertEquals(1, locator.getAllServiceHandles(DirectGenericFieldLoop.class).size());

    // Assert that the loop is remembered across configurations.
    ServiceLocatorUtilities.addClasses(locator, ProvidesString.class);

    assertEquals(1, locator.getAllServiceHandles(StartsDirectGenericFieldLoop.class).size());
    assertEquals(1, locator.getAllServiceHandles(DirectGenericFieldLoop.class).size());
  }

  /**
   * Like {@link #testIndirectGenericMethodLoop()} but for fields instead of
   * methods.
   */
  @Test
  public void testIndirectGenericFieldLoop() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        StartsIndirectGenericFieldLoop.class);

    assertEquals(1, locator.getAllServiceHandles(StartsIndirectGenericFieldLoop.class).size());
    assertEquals(1, locator.getAllServiceHandles(GenericFieldLoop1.class).size());
    assertEquals(1, locator.getAllServiceHandles(GenericFieldLoop2.class).size());
    assertEquals(1, locator.getAllServiceHandles(GenericFieldLoop3.class).size());

    // Assert that the loop is remembered across configurations.
    ServiceLocatorUtilities.addClasses(locator, ProvidesString.class);

    assertEquals(1, locator.getAllServiceHandles(StartsIndirectGenericFieldLoop.class).size());
    assertEquals(1, locator.getAllServiceHandles(GenericFieldLoop1.class).size());
    assertEquals(1, locator.getAllServiceHandles(GenericFieldLoop2.class).size());
    assertEquals(1, locator.getAllServiceHandles(GenericFieldLoop3.class).size());
  }

  /**
   * Verifies that there is no infinite feedback loop between two instances of
   * {@link ProvidesListener}.
   *
   * <p>When a service defining a loop through its {@link Provides} annotations
   * is registered, and an attempt is made to register a second {@link
   * ProvidesListener}, that could theoretically defeat the loop-prevention
   * mechanism of the first {@link ProvidesListener}.
   */
  @Test
  public void testFeedbackLoopFromTwoProvidesListeners() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        StartsIndirectMethodLoop.class);

    assertEquals(1, locator.getAllServiceHandles(MethodLoop1.class).size());

    ServiceLocatorUtilities.addClasses(locator, ProvidesListener.class);

    assertEquals(1, locator.getAllServiceHandles(MethodLoop1.class).size());
  }

  /**
   * Verifies that a {@link MultiException} is thrown when fetching a service
   * provided by a {@link Provides} instance method when the provider is null.
   */
  @Test
  public void testProvidesMethodFromNull() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesMethodFromNull.class);

    try {
      locator.getService(String.class);
      fail("Should have thrown MultiException");
    } catch (MultiException expected) {}
  }

  /**
   * Verifies that a {@link MultiException} is thrown when fetching a service
   * provided by a {@link Provides} instance field when the provider is null.
   */
  @Test
  public void testProvidesFieldFromNull() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesFieldFromNull.class);

    try {
      locator.getService(String.class);
      fail("Should have thrown MultiException");
    } catch (MultiException expected) {}
  }

  /**
   * Verifies that a {@link MultiException} is thrown when disposing of a
   * service provided by a {@link Provides} source whose {@link
   * Provides#disposalHandledBy()} is {@link
   * Provides.DisposalHandledBy#PROVIDER} and the provider is null at the time
   * of disposal.
   */
  @Test
  public void testDisposalHandledByNull() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        DisposalHandledByNull.class);

    ServiceHandle<String> handle = locator.getServiceHandle(String.class);
    assertEquals("hello", handle.getService());

    try {
      handle.close();
      fail("Should have thrown MultiException");
    } catch (MultiException expected) {}
  }

  /**
   * Verifies that a dispose method may refer to its own {@link
   * Method#getTypeParameters()} in its {@link
   * Method#getGenericParameterTypes()} as long as all of the type variables can
   * be resolved to actual type arguments knowing the actual type of the
   * service undergoing disposal.
   */
  @Test
  @Disabled("This feature has not been implemented")
  public void testProvidesTypeArgumentsToDisposeMethod() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    ServiceLocatorUtilities.addClasses(
        locator,
        ProvidesListener.class,
        ProvidesTypeArgumentsToDisposeMethod.class);

    ServiceHandle<ArrayList<String>> handle =
        locator.getServiceHandle(
            new TypeLiteral<ArrayList<String>>() {}.getType());

    assertNotNull(handle);
    ArrayList<String> list = handle.getService();
    assertEquals(listOf("one", "two", "three"), list);
    handle.close();
    assertEquals(listOf("three", "two", "one"), list);
  }

  public static final class ProvidesString {
    @Provides
    public String value() {
      return "hello";
    }
  }

  public static final class ParamForProvides1 {}
  public static final class ParamForProvides2 {}

  public static class ServiceWithLifecycle implements PostConstruct, PreDestroy {
    /*@GuardedBy("this")*/
    private boolean wasStarted = false;

    /*@GuardedBy("this")*/
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

  public static final class ProvidedByStaticField {}
  public static final class ProvidedByInstanceField {}
  public static final class ProvidedByStaticMethod {}
  public static final class ProvidedByInstanceMethod {}

  public static final class ProvidedByStaticMethodWithParams {
    public final ParamForProvides1 param1;
    public final ParamForProvides2 param2;

    ProvidedByStaticMethodWithParams(ParamForProvides1 param1,
                                     ParamForProvides2 param2) {
      this.param1 = Objects.requireNonNull(param1);
      this.param2 = Objects.requireNonNull(param2);
    }
  }

  public static final class ProvidedByInstanceMethodWithParams {
    public final ParamForProvides1 param1;
    public final ParamForProvides2 param2;

    ProvidedByInstanceMethodWithParams(ParamForProvides1 param1,
                                       ParamForProvides2 param2) {
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

  public static final class ProvidesStaticField {
    @Provides
    public static final ProvidedByStaticField staticField =
        new ProvidedByStaticField();

    // Avoid "utility class with non-private constructor" warnings.
    public final int x = 2;
  }

  public static final class ProvidesInstanceField {
    @Provides
    public final ProvidedByInstanceField instanceField =
        new ProvidedByInstanceField();
  }

  public static final class ProvidesStaticMethod {
    @Provides
    public static ProvidedByStaticMethod staticMethod() {
      return new ProvidedByStaticMethod();
    }

    // Avoid "utility class with non-private constructor" warnings.
    public final int x = 2;
  }

  public static final class ProvidesInstanceMethod {
    @Provides
    public ProvidedByInstanceMethod instanceMethod() {
      return new ProvidedByInstanceMethod();
    }
  }

  public static final class ProvidesStaticMethodWithParams {
    @Provides
    public static ProvidedByStaticMethodWithParams staticMethodWithParams(
        ParamForProvides1 param1,
        ParamForProvides2 param2) {
      return new ProvidedByStaticMethodWithParams(param1, param2);
    }

    // Avoid "utility class with non-private constructor" warnings.
    public final int x = 2;
  }

  public static final class ProvidesInstanceMethodWithParams {
    @Provides
    public ProvidedByInstanceMethodWithParams instanceMethodWithParams(
        ParamForProvides1 param1,
        ParamForProvides2 param2) {
      return new ProvidedByInstanceMethodWithParams(param1, param2);
    }
  }

  public static final class ProvidesQualifiedGenericField {
    @Provides
    public final GenericFromProvidesField<String> generic =
        new GenericFromProvidesField<>();
  }

  public static final class ProvidesQualifiedGenericMethod {
    @Provides
    public GenericFromProvidesMethod<String> generic() {
      return new GenericFromProvidesMethod<>();
    }
  }

  public static final class ProvidesChain {
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
  }

  public static final class ProvidesLifecycle {
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
    public static ProvidedWithCustomDisposeFromStaticMethodToFactoryStaticMethod staticMethodToFactoryStaticMethod() {
      return new ProvidedWithCustomDisposeFromStaticMethodToFactoryStaticMethod();
    }

    @Provides(
        disposeMethod = "instanceDestroyMethod",
        disposalHandledBy = Provides.DisposalHandledBy.PROVIDER)
    @Singleton
    public static ProvidedWithCustomDisposeFromStaticMethodToFactoryInstanceMethod staticMethodToFactoryInstanceMethod() {
      return new ProvidedWithCustomDisposeFromStaticMethodToFactoryInstanceMethod();
    }

    @Provides(
        disposeMethod = "customDisposeMethod",
        disposalHandledBy = Provides.DisposalHandledBy.PROVIDED_INSTANCE)
    @Singleton
    public ProvidedWithCustomDisposeFromInstanceMethod instanceMethod() {
      return new ProvidedWithCustomDisposeFromInstanceMethod();
    }

    @Provides(
        disposeMethod = "staticDestroyMethod",
        disposalHandledBy = Provides.DisposalHandledBy.PROVIDER)
    @Singleton
    public ProvidedWithCustomDisposeFromInstanceMethodToFactoryStaticMethod instanceMethodToFactoryStaticMethod() {
      return new ProvidedWithCustomDisposeFromInstanceMethodToFactoryStaticMethod();
    }

    @Provides(
        disposeMethod = "instanceDestroyMethod",
        disposalHandledBy = Provides.DisposalHandledBy.PROVIDER)
    @Singleton
    public ProvidedWithCustomDisposeFromInstanceMethodToFactoryInstanceMethod instanceMethodToFactoryInstanceMethod() {
      return new ProvidedWithCustomDisposeFromInstanceMethodToFactoryInstanceMethod();
    }

    public static void staticDestroyMethod(HasCustomDisposeMethod instance) {
      instance.customDisposeMethod();
    }

    public void instanceDestroyMethod(HasCustomDisposeMethod instance) {
      instance.customDisposeMethod();
    }
  }

  public static class HasCustomDisposeMethod {
    /*@GuardedBy("this")*/
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
  public static final class ProvidedWithCustomDisposeFromStaticMethodToFactoryStaticMethod extends HasCustomDisposeMethod {}
  public static final class ProvidedWithCustomDisposeFromStaticMethodToFactoryInstanceMethod extends HasCustomDisposeMethod {}
  public static final class ProvidedWithCustomDisposeFromInstanceMethodToFactoryStaticMethod extends HasCustomDisposeMethod {}
  public static final class ProvidedWithCustomDisposeFromInstanceMethodToFactoryInstanceMethod extends HasCustomDisposeMethod {}

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
    public static final /*@Nullable*/ NullFromStaticField staticField = null;

    @Provides
    public final /*@Nullable*/ NullFromInstanceField instanceField = null;

    @Provides
    public static /*@Nullable*/ NullFromStaticMethod staticMethod() {
      return null;
    }

    @Provides
    public /*@Nullable*/ NullFromInstanceMethod instanceMethod() {
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

    @SuppressWarnings("ConstructorLeaksThis") // We don't care.
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

  public static final class FilteredProvider {
    @Provides
    public FromFilteredProvider service() {
      return new FromFilteredProvider();
    }
  }

  public static final class UnfilteredProvider {
    @Provides
    public FromUnfilteredProvider service() {
      return new FromUnfilteredProvider();
    }
  }

  public static final class FromFilteredProvider {}
  public static final class FromUnfilteredProvider {}

  @Singleton
  public static final class FilteredProvidesListener extends ProvidesListener {
    @Inject
    public FilteredProvidesListener(ServiceLocator locator) {
      super(locator);
    }

    @Override
    protected Filter getFilter() {
      return d -> !d.getImplementation().equals(FilteredProvider.class.getName());
    }
  }

  public static class ProviderOfConsumesGenericParameter {
    @Provides
    public ConsumesGenericParameter<String> provide() {
      return new ConsumesGenericParameter<>("secret");
    }
  }

  public static class ConsumesGenericParameter<T> {
    private final T value;

    public ConsumesGenericParameter(T value) {
      this.value = Objects.requireNonNull(value);
    }

    @Provides
    public T getValue() {
      return value;
    }

    @Provides
    public FromConsumesGenericParameter<T> consume(T value) {
      return new FromConsumesGenericParameter<>(value);
    }
  }

  public static final class FromConsumesGenericParameter<T> {
    public final T value;

    FromConsumesGenericParameter(T value) {
      this.value = Objects.requireNonNull(value);
    }
  }

  @Qualifier
  @Retention(RUNTIME)
  public @interface BetterNotFindMe {}

  @Qualifier
  @Retention(RUNTIME)
  public @interface BetterFindMe {}

  public static final class UnresolvableTypeVariableInFieldType<T> {
    @Provides
    @BetterNotFindMe
    public final /*@Nullable*/ T bad = null;

    @Provides
    @BetterFindMe
    public final String good = "good";
  }

  public static final class UnresolvableTypeVariableDeepInFieldType<T> {
    @Provides
    @BetterNotFindMe
    public final List<List<Map<? super Number, T[]>>> bad = listOf();

    @Provides
    @BetterFindMe
    public final String good = "good";
  }

  public static final class UnresolvableTypeVariableInStaticMethodReturnType {
    @Provides
    @BetterNotFindMe
    @SuppressWarnings("TypeParameterUnusedInFormals")
    public static <T> /*@Nullable*/ T bad() {
      return null;
    }

    @Provides
    @BetterFindMe
    public static String good() {
      return "good";
    }

    // Avoid "utility class with non-private constructor" warnings.
    public final int x = 2;
  }

  public static final class UnresolvableTypeVariableDeepInStaticMethodReturnType {
    @Provides
    @BetterNotFindMe
    @SuppressWarnings("TypeParameterUnusedInFormals")
    public static <T> List<List<Map<? super Number, T[]>>> bad() {
      return listOf();
    }

    @Provides
    @BetterFindMe
    public static String good() {
      return "good";
    }

    // Avoid "utility class with non-private constructor" warnings.
    public final int x = 2;
  }

  public static final class UnresolvableTypeVariableInInstanceMethodReturnType<T> {
    @Provides
    @BetterNotFindMe
    public /*@Nullable*/ T bad() {
      return null;
    }

    @Provides
    @BetterFindMe
    public String good() {
      return "good";
    }
  }

  public static final class UnresolvableTypeVariableDeepInInstanceMethodReturnType<T> {
    @Provides
    @BetterNotFindMe
    public List<List<Map<? super Number, T[]>>> bad() {
      return listOf();
    }

    @Provides
    @BetterFindMe
    public String good() {
      return "good";
    }
  }

  public static final class UnresolvableTypeVariableInStaticMethodParameterType {
    @Provides
    @BetterNotFindMe
    public static <T> String bad(T value) {
      return "bad";
    }

    @Provides
    @BetterFindMe
    public static String good() {
      return "good";
    }

    // Avoid "utility class with non-private constructor" warnings.
    public final int x = 2;
  }

  public static final class UnresolvableTypeVariableDeepInStaticMethodParameterType {
    @Provides
    @BetterNotFindMe
    public static <T> String bad(List<List<Map<? super Number, T[]>>> value) {
      return "bad";
    }

    @Provides
    @BetterFindMe
    public static String good() {
      return "good";
    }

    // Avoid "utility class with non-private constructor" warnings.
    public final int x = 2;
  }

  public static final class UnresolvableTypeVariableInInstanceMethodParameterType<T> {
    @Provides
    @BetterNotFindMe
    public String bad(T value) {
      return "bad";
    }

    @Provides
    @BetterFindMe
    public String good() {
      return "good";
    }
  }

  public static final class UnresolvableTypeVariableDeepInInstanceMethodParameterType<T> {
    @Provides
    @BetterNotFindMe
    public String bad(List<List<Map<? super Number, T[]>>> value) {
      return "bad";
    }

    @Provides
    @BetterFindMe
    public String good() {
      return "good";
    }
  }

  public static final class ProvidesNullWithLifecycle {
    @Provides
    public static final /*@Nullable*/ ServiceWithLifecycle staticFieldDefaultDispose = null;

    @Provides(
        disposeMethod = "stop",
        disposalHandledBy = Provides.DisposalHandledBy.PROVIDED_INSTANCE)
    public static final /*@Nullable*/ ServiceWithLifecycle staticFieldProvidedInstanceDisposeMethod = null;

    @Provides(
        disposeMethod = "staticDisposeMethod",
        disposalHandledBy = Provides.DisposalHandledBy.PROVIDER)
    public static final /*@Nullable*/ ServiceWithLifecycle staticFieldProviderDisposeMethod = null;


    @Provides
    public final /*@Nullable*/ ServiceWithLifecycle instanceFieldDefaultDispose = null;

    @Provides(
        disposeMethod = "stop",
        disposalHandledBy = Provides.DisposalHandledBy.PROVIDED_INSTANCE)
    public final /*@Nullable*/ ServiceWithLifecycle instanceFieldProvidedInstanceDisposeMethod = null;

    @Provides(
        disposeMethod = "instanceDisposeMethod",
        disposalHandledBy = Provides.DisposalHandledBy.PROVIDER)
    public final /*@Nullable*/ ServiceWithLifecycle instanceFieldProviderDisposeMethod = null;

    @Provides
    public static /*@Nullable*/ ServiceWithLifecycle staticMethodDefaultDispose() {
      return null;
    }

    @Provides(
        disposeMethod = "stop",
        disposalHandledBy = Provides.DisposalHandledBy.PROVIDED_INSTANCE)
    public static /*@Nullable*/ ServiceWithLifecycle staticMethodProvidedInstanceDisposeMethod() {
      return null;
    }

    @Provides(
        disposeMethod = "staticDisposeMethod",
        disposalHandledBy = Provides.DisposalHandledBy.PROVIDER)
    public static /*@Nullable*/ ServiceWithLifecycle staticMethodProviderDisposeMethod() {
      return null;
    }

    @Provides
    public /*@Nullable*/ ServiceWithLifecycle instanceMethodDefaultDispose() {
      return null;
    }

    @Provides(
        disposeMethod = "stop",
        disposalHandledBy = Provides.DisposalHandledBy.PROVIDED_INSTANCE)
    public /*@Nullable*/ ServiceWithLifecycle instanceMethodProvidedInstanceDisposeMethod() {
      return null;
    }

    @Provides(
        disposeMethod = "instanceDisposeMethod",
        disposalHandledBy = Provides.DisposalHandledBy.PROVIDER)
    public /*@Nullable*/ ServiceWithLifecycle instanceMethodProviderDisposeMethod() {
      return null;
    }

    public static void staticDisposeMethod(ServiceWithLifecycle instance) {
      instance.stop();
    }

    public void instanceDisposeMethod(ServiceWithLifecycle instance) {
      instance.stop();
    }
  }

  public static final class ProvidesStringArray {
    @Provides
    public ProvidesGenericArray<String[]> array() {
      return new ProvidesGenericArray<String[]>() {
        @Override
        public String[][] value() {
          return new String[][] { new String[] { "hey" } };
        }
      };
    }
  }

  public abstract static class ProvidesGenericArray<T> {
    @Provides
    public abstract T[] value();
  }

  public static final class ProvidesWrongDisposeMethodParameterType {
    @Provides
    public WrongDisposeMethodParameterType<Optional<?>> service() {
      return new WrongDisposeMethodParameterType<>();
    }
  }

  public static final class WrongDisposeMethodParameterType<T> {
    @Provides(
        disposeMethod = "disposeBad",
        disposalHandledBy = Provides.DisposalHandledBy.PROVIDER)
    @BetterNotFindMe
    public Optional<String> bad() {
      return Optional.of("bad");
    }

    @Provides(
        disposeMethod = "disposeGood",
        disposalHandledBy = Provides.DisposalHandledBy.PROVIDER)
    @BetterFindMe
    public Optional<String> good() {
      return Optional.of("good");
    }

    // In the context of the provider class, the type of this parameter should
    // evaluate to something like "Optional<capture#1 of ? extends Object>".
    // That is not a supertype of the type from our @Provides method, which is
    // "Optional<String>", so this dispose method won't match.
    //
    // If the type of this parameter was wrongly evaluated to "Optional<?>",
    // which is a supertype of "Optional<String>", then this dispose method
    // would wrongly match.
    public void disposeBad(T instance) {}

    // Demonstrate that Optional<?> is a supertype of Optional<String>.
    public void disposeGood(Optional<?> instance) {}
  }

  @Singleton
  public static final class FactoryStats {
    public final AtomicInteger valueCounter = new AtomicInteger();
    public final List<String> valuesProvided = new CopyOnWriteArrayList<>();
    public final List<String> valuesDisposed = new CopyOnWriteArrayList<>();
    public final AtomicInteger factoryDisposeCounter = new AtomicInteger();
    public final AtomicInteger perLookupDependencyInitCounter = new AtomicInteger();
    public final AtomicInteger perLookupDependencyDisposeCounter = new AtomicInteger();
    public final AtomicInteger singletonDependencyInitCounter = new AtomicInteger();
    public final AtomicInteger singletonDependencyDisposeCounter = new AtomicInteger();
  }

  public static final class PerLookupFactoryDependency
      implements PostConstruct, PreDestroy {

    private final FactoryStats stats;

    @Inject
    public PerLookupFactoryDependency(FactoryStats stats) {
      this.stats = Objects.requireNonNull(stats);
    }

    @Override
    public void postConstruct() {
      stats.perLookupDependencyInitCounter.incrementAndGet();
    }

    @Override
    public void preDestroy() {
      stats.perLookupDependencyDisposeCounter.incrementAndGet();
    }
  }

  @Singleton
  public static final class SingletonFactoryDependency
      implements PostConstruct, PreDestroy {

    private final FactoryStats stats;

    @Inject
    public SingletonFactoryDependency(FactoryStats stats) {
      this.stats = Objects.requireNonNull(stats);
    }

    @Override
    public void postConstruct() {
      stats.singletonDependencyInitCounter.incrementAndGet();
    }

    @Override
    public void preDestroy() {
      stats.singletonDependencyDisposeCounter.incrementAndGet();
    }
  }

  public static final class ProvidesDisposeMethodWithMultipleParameters
      implements PreDestroy {

    private final FactoryStats stats;

    @Inject
    public ProvidesDisposeMethodWithMultipleParameters(FactoryStats stats) {
      this.stats = Objects.requireNonNull(stats);
    }

    @Provides(
        disposeMethod = "staticDisposeMethod",
        disposalHandledBy = Provides.DisposalHandledBy.PROVIDER)
    @Named("forStaticDisposeMethod")
    public static String provideForStaticDisposeMethod(FactoryStats stats) {
      String value = String.valueOf(stats.valueCounter.incrementAndGet());
      stats.valuesProvided.add(value);
      return value;
    }

    @Provides(
        disposeMethod = "instanceDisposeMethod",
        disposalHandledBy = Provides.DisposalHandledBy.PROVIDER)
    @Named("forInstanceDisposeMethod")
    public static String provideForInstanceDisposeMethod(FactoryStats stats) {
      String value = String.valueOf(stats.valueCounter.incrementAndGet());
      stats.valuesProvided.add(value);
      return value;
    }

    public void instanceDisposeMethod(String value,
                                      FactoryStats stats,
                                      PerLookupFactoryDependency dependency1,
                                      SingletonFactoryDependency dependency2) {
      Objects.requireNonNull(value);
      Objects.requireNonNull(stats);
      Objects.requireNonNull(dependency1);
      Objects.requireNonNull(dependency2);
      stats.valuesDisposed.add(value);
    }

    public static void staticDisposeMethod(String value,
                                           FactoryStats stats,
                                           PerLookupFactoryDependency dependency1,
                                           SingletonFactoryDependency dependency2) {
      Objects.requireNonNull(value);
      Objects.requireNonNull(stats);
      Objects.requireNonNull(dependency1);
      Objects.requireNonNull(dependency2);
      stats.valuesDisposed.add(value);
    }

    @Override
    public void preDestroy() {
      stats.factoryDisposeCounter.incrementAndGet();
    }
  }

  @Singleton
  public static final class SeenByUsesSelf {
    public final List<ActiveDescriptor<?>> list = new CopyOnWriteArrayList<>();
  }

  public static final class UsesSelf {
    @Inject
    public UsesSelf(@Self ActiveDescriptor<?> self, SeenByUsesSelf seen) {
      seen.list.add(self);
    }

    @Provides(
        disposeMethod = "dispose",
        disposalHandledBy = Provides.DisposalHandledBy.PROVIDER)
    public String provide(@Self ActiveDescriptor<?> self, SeenByUsesSelf seen) {
      seen.list.add(self);
      return "hello";
    }

    public void dispose(String instance, @Self ActiveDescriptor<?> self, SeenByUsesSelf seen) {
      seen.list.add(self);
    }
  }

  public static final class DirectMethodLoop {
    @Provides
    public DirectMethodLoop next() {
      return new DirectMethodLoop();
    }
  }

  public static final class StartsIndirectMethodLoop {
    @Provides
    public MethodLoop1 start() {
      return new MethodLoop1();
    }
  }

  public static final class MethodLoop1 {
    @Provides
    public MethodLoop2 next() {
      return new MethodLoop2();
    }
  }

  public static final class MethodLoop2 {
    @Provides
    public MethodLoop3 next() {
      return new MethodLoop3();
    }
  }

  public static final class MethodLoop3 {
    @Provides
    public MethodLoop1 next() {
      return new MethodLoop1();
    }
  }

  public static final class StartsDirectGenericMethodLoop {
    @Provides
    public DirectGenericMethodLoop<String> start() {
      return new DirectGenericMethodLoop<>();
    }
  }

  public static final class DirectGenericMethodLoop<T> {
    @Provides
    public DirectGenericMethodLoop<List<T>> next() {
      return new DirectGenericMethodLoop<>();
    }
  }

  public static final class StartsIndirectGenericMethodLoop {
    @Provides
    public GenericMethodLoop1<String> start() {
      return new GenericMethodLoop1<>();
    }
  }

  public static final class GenericMethodLoop1<T> {
    @Provides
    public GenericMethodLoop2<List<T>> next() {
      return new GenericMethodLoop2<>();
    }
  }

  public static final class GenericMethodLoop2<T> {
    @Provides
    public GenericMethodLoop3<List<T>> next() {
      return new GenericMethodLoop3<>();
    }
  }

  public static final class GenericMethodLoop3<T> {
    @Provides
    public GenericMethodLoop1<List<T>> next() {
      return new GenericMethodLoop1<>();
    }
  }

  public static final class DirectFieldLoop {
    @Provides
    /*@Nullable*/
    public DirectFieldLoop next;
  }

  public static final class StartsIndirectFieldLoop {
    @Provides
    /*@Nullable*/
    public FieldLoop1 start;
  }

  public static final class FieldLoop1 {
    @Provides
    /*@Nullable*/
    public FieldLoop2 next;
  }

  public static final class FieldLoop2 {
    @Provides
    /*@Nullable*/
    public FieldLoop3 next;
  }

  public static final class FieldLoop3 {
    @Provides
    /*@Nullable*/
    public FieldLoop1 next;
  }

  public static final class StartsDirectGenericFieldLoop {
    @Provides
    /*@Nullable*/
    public DirectGenericFieldLoop<String> start;
  }

  public static final class DirectGenericFieldLoop<T> {
    @Provides
    /*@Nullable*/
    public DirectGenericFieldLoop<List<T>> next;
  }

  public static final class StartsIndirectGenericFieldLoop {
    @Provides
    /*@Nullable*/
    public GenericFieldLoop1<String> start;
  }

  public static final class GenericFieldLoop1<T> {
    @Provides
    /*@Nullable*/
    public GenericFieldLoop2<List<T>> next;
  }

  public static final class GenericFieldLoop2<T> {
    @Provides
    /*@Nullable*/
    public GenericFieldLoop3<List<T>> next;
  }

  public static final class GenericFieldLoop3<T> {
    @Provides
    /*@Nullable*/
    public GenericFieldLoop1<List<T>> next;
  }

  public static final class ProvidesMethodFromNull {
    @Provides
    public /*@Nullable*/ ProvidesString provider() {
      return null;
    }
  }

  public static final class ProvidesFieldFromNull {
    @Provides
    public final /*@Nullable*/ ProvidesString provider = null;
  }

  public static final class DisposalHandledByNull {
    @Provides
    public /*@Nullable*/ StaticProvideInstanceDispose provider() {
      return null;
    }

    public static final class StaticProvideInstanceDispose {
      @Provides(
          disposeMethod = "dispose",
          disposalHandledBy = Provides.DisposalHandledBy.PROVIDER)
      public static String provide() {
        return "hello";
      }

      public void dispose(String value) {}
    }
  }

  public static final class ProvidesTypeArgumentsToDisposeMethod {
    @Provides(
        disposeMethod = "dispose",
        disposalHandledBy = Provides.DisposalHandledBy.PROVIDER)
    public ArrayList<String> provide() {
      return new ArrayList<>(listOf("one", "two", "three"));
    }

    public static <Q> void dispose(List<Q> instance) {
      Collections.reverse(instance);
    }
  }
}
