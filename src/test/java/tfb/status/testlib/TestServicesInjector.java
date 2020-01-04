package tfb.status.testlib;

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
public final class TestServicesInjector extends ServiceLocatorParameterResolver {
  @Override
  protected void configure() {
    addActiveDescriptor(TestServicesBinder.class);
  }
}
