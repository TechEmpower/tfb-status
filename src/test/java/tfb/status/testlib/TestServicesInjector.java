package tfb.status.testlib;

import java.lang.reflect.Parameter;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glassfish.hk2.api.ActiveDescriptor;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import tfb.status.hk2.extensions.Services;

/**
 * Allows instances of service classes to be injected into test methods as
 * parameters.
 *
 * <p>Example usage:
 *
 * <pre>
 *   &#64;ExtendWith(TestServicesInjector.class)
 *   public final class SomeServiceTest {
 *     &#64;Test
 *     public void testSomeBehavior(SomeService service) {
 *       // SomeService and its dependencies are initialized.
 *       // Make assertions about its behavior.
 *       ...
 *     }
 *   }
 * </pre>
 */
public final class TestServicesInjector implements ParameterResolver {
  // TODO: Move this extension into the hk2.extensions package so it can access
  //       package-private things, but somehow make it not refer to any
  //       tfb-status-specific classes such as testlib.TestServicesBinder.
  //       Instead, it should somehow allow a Services instance to be provided.
  private static Services initializeServices() {
    return new Services().register(TestServicesBinder.class);
  }

  @Override
  public boolean supportsParameter(ParameterContext parameterContext,
                                   ExtensionContext extensionContext) {

    Parameter parameter = parameterContext.getParameter();
    Services services = getServices(extensionContext);
    return services.supportsParameter(parameter);
  }

  @Override
  public @Nullable Object resolveParameter(ParameterContext parameterContext,
                                           ExtensionContext extensionContext) {

    Parameter parameter = parameterContext.getParameter();
    Services services = getServices(extensionContext);
    ServiceHandle<?> root = getRootServiceHandle(extensionContext);
    return services.resolveParameter(parameter, root);
  }

  private Services getServices(ExtensionContext extensionContext) {
    // Since we use the root context's store, a single set of services is shared
    // between all tests.
    ExtensionContext.Store store =
        extensionContext.getRoot().getStore(NAMESPACE);

    StoredServices stored =
        store.getOrComputeIfAbsent(
            /* key= */
            StoredServices.class,

            /* defaultCreator= */
            key -> {
              Services services = initializeServices();
              return new StoredServices(services);
            },

            /* requiredType= */
            StoredServices.class);

    return stored.services;
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
              Services services = getServices(extensionContext);

              ServiceLocator serviceLocator =
                  services.getService(ServiceLocator.class);

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
      ExtensionContext.Namespace.create(TestServicesInjector.class);

  /**
   * Wraps {@link Services} in {@link ExtensionContext.Store.CloseableResource},
   * ensuring that the services are shut down when the store is closed.
   */
  private static final class StoredServices
      implements ExtensionContext.Store.CloseableResource {

    final Services services;

    StoredServices(Services services) {
      this.services = Objects.requireNonNull(services);
    }

    @Override
    public void close() {
      services.shutdown();
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
