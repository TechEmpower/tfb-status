package tfb.status.testlib;

import java.lang.reflect.Parameter;
import org.checkerframework.checker.nullness.qual.Nullable;
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
    return services.resolveParameter(parameter);
  }

  private Services getServices(ExtensionContext extensionContext) {
    // Since we use the root context's store, a single set of services is shared
    // between all tests.
    ExtensionContext.Store store =
        extensionContext.getRoot().getStore(NAMESPACE);

    StoredServices stored =
        store.getOrComputeIfAbsent(
            /* key= */ StoredServices.class,
            /* defaultCreator= */ key -> new StoredServices(),
            /* requiredType= */ StoredServices.class);

    return stored.services;
  }

  private static final ExtensionContext.Namespace NAMESPACE =
      ExtensionContext.Namespace.create(TestServicesInjector.class);

  /**
   * Wraps {@link Services} in {@link ExtensionContext.Store.CloseableResource},
   * ensuring that the services are shut down when the store is closed.
   */
  private static final class StoredServices
      implements ExtensionContext.Store.CloseableResource {

    final Services services = new Services();

    StoredServices() {
      services.addClass(TestServicesBinder.class);
    }

    @Override
    public void close() {
      services.shutdown();
    }
  }
}
