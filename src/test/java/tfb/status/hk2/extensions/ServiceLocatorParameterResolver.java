package tfb.status.hk2.extensions;

import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Objects;
import org.glassfish.hk2.api.ActiveDescriptor;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * A JUnit 5 extension that resolves parameters of test methods at runtime using
 * a {@link ServiceLocator}.  Subclasses must implement {@link
 * #createServiceLocator()}.
 *
 * <p>Example usage:
 * <pre>
 *   class MyExtension extends ServiceLocatorParameterResolver {
 *     &#64;Override
 *     public ServiceLocator createServiceLocator() {
 *       ServiceLocator locator =
 *           ServiceLocatorUtilities.createAndPopulateServiceLocator();
 *       ServiceLocatorUtilities.addClasses(locator, MyService.class);
 *       return locator;
 *     }
 *   }
 *
 *   &#64;ExtendWith(MyExtension.class)
 *   class MyTest {
 *     &#64;Test
 *     public void testMyService(MyService instance) {
 *       assertTrue(instance.wasStarted());
 *     }
 *   }
 * </pre>
 *
 * @see ParameterResolver
 */
public interface ServiceLocatorParameterResolver extends ParameterResolver {
  /**
   * Creates a new {@link ServiceLocator} instance, and registers all services
   * that may be injected into test method parameters with this {@link
   * ServiceLocator} instance.
   *
   * <p>This {@link ServiceLocator} instance will be cached in the {@linkplain
   * ExtensionContext#getStore(Namespace) store} of the {@linkplain
   * ExtensionContext#getRoot() root extension context}, meaning that this
   * method will only be invoked once, and the same {@link ServiceLocator}
   * instance will be shared between all tests.
   */
  // TODO: Give users the ability to override the default caching behavior.
  ServiceLocator createServiceLocator();

  @Override
  default boolean supportsParameter(ParameterContext parameterContext,
                                    ExtensionContext extensionContext) {

    Parameter parameter = parameterContext.getParameter();
    Type testType = getTestType(parameterContext, extensionContext);
    ServiceLocator locator = getServiceLocator(extensionContext);
    return InjectUtils.supportsParameter(parameter, testType, locator);
  }

  @Override
  default /*@Nullable*/ Object resolveParameter(ParameterContext parameterContext,
                                                  ExtensionContext extensionContext) {

    Parameter parameter = parameterContext.getParameter();
    Type testType = getTestType(parameterContext, extensionContext);
    ServiceLocator locator = getServiceLocator(extensionContext);
    ServiceHandle<?> root = getRootServiceHandle(extensionContext);
    return InjectUtils.serviceFromParameter(parameter, testType, root, locator);
  }

  private Type getTestType(ParameterContext parameterContext,
                           ExtensionContext extensionContext) {
    //
    // This parameter might be declared in an generic base class.
    //
    //   abstract class AbstractTest<T extends ThingIKnowHowToTest> {
    //     @Test
    //     public void testBasicStuff(T service) { ... }
    //   }
    //
    //   class FooTest extends AbstractTest<Foo> { ... }
    //
    // If we're running FooTest, and we can obtain a reference to FooTest.class,
    // then we can resolve the type variable T of testBasicStuff(...) to Foo.
    // Here, FooTest.class comes from `extensionContext.getTestClass()`.
    //
    // If we can't get the test class, the next best thing is the declaring
    // class of the parameter.  In this example, that is AbstractTest.class, and
    // that class does not contain the information required to resolve the type
    // variable T.
    //
    return extensionContext.getTestClass().orElseGet(
        () -> parameterContext.getDeclaringExecutable().getDeclaringClass());
  }

  private ServiceLocator getServiceLocator(ExtensionContext extensionContext) {
    // Since we use the root context's store, a single set of services is shared
    // between all tests.
    Store store = extensionContext.getRoot().getStore(NAMESPACE);

    StoredServiceLocator stored =
        store.getOrComputeIfAbsent(
            /* key= */
            StoredServiceLocator.class,

            /* defaultCreator= */
            key -> {
              ServiceLocator locator = createServiceLocator();
              return new StoredServiceLocator(locator);
            },

            /* requiredType= */
            StoredServiceLocator.class);

    return stored.locator;
  }

  private ServiceHandle<?> getRootServiceHandle(ExtensionContext extensionContext) {
    // Here we use a test-specific store because we want this root ServiceHandle
    // to be shared among parameters in the same test, and we want this
    // ServiceHandle to be closed when the test completes.  This allows the
    // per-lookup services that were created for the sake of the test to be
    // closed.
    Store store = extensionContext.getStore(NAMESPACE);

    StoredServiceHandle stored =
        store.getOrComputeIfAbsent(
            /* key= */
            StoredServiceHandle.class,

            /* defaultCreator= */
            key -> {
              ServiceLocator locator = getServiceLocator(extensionContext);

              ActiveDescriptor<?> activeDescriptor =
                  ServiceLocatorUtilities.addOneConstant(
                      locator,
                      new Object() {});

              ServiceHandle<?> serviceHandle =
                  locator.getServiceHandle(activeDescriptor);

              return new StoredServiceHandle(serviceHandle);
            },

            /* requiredType= */
            StoredServiceHandle.class);

    return stored.serviceHandle;
  }

  // TODO: Consider moving these to a package-private utility class.

  Namespace NAMESPACE = Namespace.create(ServiceLocatorParameterResolver.class);

  final class StoredServiceLocator implements CloseableResource {
    final ServiceLocator locator;

    StoredServiceLocator(ServiceLocator locator) {
      this.locator = Objects.requireNonNull(locator);
    }

    @Override
    public void close() {
      locator.shutdown();
    }
  }

  final class StoredServiceHandle implements CloseableResource {
    final ServiceHandle<?> serviceHandle;

    StoredServiceHandle(ServiceHandle<?> serviceHandle) {
      this.serviceHandle = Objects.requireNonNull(serviceHandle);
    }

    @Override
    public void close() {
      serviceHandle.close();
    }
  }
}
