package tfb.status.hk2.extensions;

import java.lang.reflect.Parameter;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glassfish.hk2.api.ActiveDescriptor;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * A JUnit 5 extension that resolves parameters of test methods at runtime using
 * a {@link ServiceLocator}.  Subclasses must implement {@link
 * AbstractBinder#configure()} in order to register their services.
 *
 * <p>Example usage:
 * <pre>
 *   class MyExtension extends ServiceLocatorParameterResolver {
 *     &#64;Override
 *     protected void configure() {
 *       addActiveDescriptor(MyService.class);
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
 * @see AbstractBinder
 * @see ParameterResolver
 */
public abstract class ServiceLocatorParameterResolver
    extends AbstractBinder
    implements ParameterResolver {

  @Override
  public boolean supportsParameter(ParameterContext parameterContext,
                                   ExtensionContext extensionContext) {

    Parameter parameter = parameterContext.getParameter();
    ServiceLocator serviceLocator = getServiceLocator(extensionContext);
    return InjectUtils.supportsParameter(parameter, serviceLocator);
  }

  @Override
  public @Nullable Object resolveParameter(ParameterContext parameterContext,
                                           ExtensionContext extensionContext) {

    Parameter parameter = parameterContext.getParameter();
    ServiceLocator serviceLocator = getServiceLocator(extensionContext);
    ServiceHandle<?> root = getRootServiceHandle(extensionContext);
    return InjectUtils.serviceFromParameter(parameter, root, serviceLocator);
  }

  private ServiceLocator getServiceLocator(ExtensionContext extensionContext) {
    // Since we use the root context's store, a single set of services is shared
    // between all tests.
    ExtensionContext.Store store =
        extensionContext.getRoot().getStore(NAMESPACE);

    StoredServiceLocator stored =
        store.getOrComputeIfAbsent(
            /* key= */
            StoredServiceLocator.class,

            /* defaultCreator= */
            key -> {
              ServiceLocator serviceLocator =
                  ServiceLocatorUtilities.createAndPopulateServiceLocator();

                ServiceLocatorUtilities.addClasses(
                    serviceLocator,
                    TopicDistributionServiceImpl.class,
                    ProvidesAnnotationEnabler.class);

              ServiceLocatorUtilities.bind(serviceLocator, this);

              return new StoredServiceLocator(serviceLocator);
            },

            /* requiredType= */
            StoredServiceLocator.class);

    return stored.serviceLocator;
  }

  private ServiceHandle<?> getRootServiceHandle(ExtensionContext extensionContext) {
    // Here we use a test-specific store because we want this root ServiceHandle
    // to be shared among parameters in the same test, and we want this
    // ServiceHandle to be closed when the test completes.  This allows the
    // per-lookup services that were created for the sake of the test to be
    // closed.
    ExtensionContext.Store store = extensionContext.getStore(NAMESPACE);

    StoredServiceHandle stored =
        store.getOrComputeIfAbsent(
            /* key= */
            StoredServiceHandle.class,

            /* defaultCreator= */
            key -> {
              ServiceLocator serviceLocator =
                  getServiceLocator(extensionContext);

              ActiveDescriptor<?> activeDescriptor =
                  ServiceLocatorUtilities.addOneConstant(
                      serviceLocator,
                      new Object() {});

              ServiceHandle<?> serviceHandle =
                  serviceLocator.getServiceHandle(activeDescriptor);

              return new StoredServiceHandle(serviceHandle);
            },

            /* requiredType= */
            StoredServiceHandle.class);

    return stored.serviceHandle;
  }

  private static final ExtensionContext.Namespace NAMESPACE =
      ExtensionContext.Namespace.create(ServiceLocatorParameterResolver.class);

  /**
   * Wraps {@link ServiceLocator} in {@link
   * ExtensionContext.Store.CloseableResource}, ensuring that the locator is
   * shut down when the store is closed.
   */
  private static final class StoredServiceLocator
      implements ExtensionContext.Store.CloseableResource {

    final ServiceLocator serviceLocator;

    StoredServiceLocator(ServiceLocator serviceLocator) {
      this.serviceLocator = Objects.requireNonNull(serviceLocator);
    }

    @Override
    public void close() {
      serviceLocator.shutdown();
    }
  }

  /**
   * Wraps {@link ServiceHandle} in {@link
   * ExtensionContext.Store.CloseableResource}, ensuring that the handle is
   * closed when the store is closed.
   */
  private static final class StoredServiceHandle
      implements ExtensionContext.Store.CloseableResource {

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
