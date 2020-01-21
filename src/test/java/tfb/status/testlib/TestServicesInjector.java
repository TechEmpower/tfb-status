package tfb.status.testlib;

import static org.glassfish.hk2.utilities.ServiceLocatorUtilities.createAndPopulateServiceLocator;

import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import tfb.status.hk2.extensions.NoInstancesFilter;
import tfb.status.hk2.extensions.ServiceLocatorParameterResolver;

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
public final class TestServicesInjector implements ServiceLocatorParameterResolver {
  @Override
  public ServiceLocator createServiceLocator() {
    ServiceLocator locator = createAndPopulateServiceLocator();
    NoInstancesFilter.enableNoInstancesFilter(locator);
    ServiceLocatorUtilities.bind(locator, new TestServicesBinder());
    return locator;
  }
}
