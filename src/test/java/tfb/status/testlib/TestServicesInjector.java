package tfb.status.testlib;

import java.util.Optional;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import tfb.status.bootstrap.Services;
import tfb.status.bootstrap.ServicesBinder;

/**
 * Allows the HTTP handlers and service classes of this application to be
 * injected into tests as parameters.
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
                                   ExtensionContext extensionContext)
      throws ParameterResolutionException {

    Class<?> type = parameterContext.getParameter().getType();
    Services services = getServices(extensionContext);
    return services.hasService(type);
  }

  @Override
  public Object resolveParameter(ParameterContext parameterContext,
                                 ExtensionContext extensionContext)
      throws ParameterResolutionException {

    Class<?> type = parameterContext.getParameter().getType();
    Services services = getServices(extensionContext);
    return services.getService(type);
  }

  private Services getServices(ExtensionContext extensionContext) {
    //
    // We're expecting that extensionContext is one of these:
    //
    //   a) The context for a test class, as in a static @BeforeAll method.
    //   b) The context for a test method, as in a non-static @Test method.
    //
    // In any other unanticipated case we'd like to fail loudly so that we know
    // to investigate further.
    //

    Class<?> testClass = extensionContext.getRequiredTestClass();

    ExtensionContext testClassContext = extensionContext;
    while (testClassContext.getElement().orElse(null) != testClass) {
      Optional<ExtensionContext> parent = testClassContext.getParent();
      testClassContext = parent.orElseThrow(
          () -> new ParameterResolutionException(
              "Unexpected extension context "
                  + extensionContext
                  + " that does not have a test class context as a parent"));
    }

    //
    // Since the store we used is scoped to the test class, each test class will
    // construct its own TestServices instance.
    //
    // TODO: Consider using the root context instead.  That would mean all tests
    //       classes share a single TestServices instance, which would speed up
    //       the execution of the full test suite.
    //
    ExtensionContext.Store store =
        testClassContext.getStore(ExtensionContext.Namespace.GLOBAL);

    return store.getOrComputeIfAbsent(
        Services.class,
        key -> new TestServices(),
        Services.class);
  }

  private static final class TestServices
      extends Services
      implements ExtensionContext.Store.CloseableResource {

    TestServices() {
      super(new ServicesBinder("test_config.yml"),
            new TestServicesBinder());
    }

    @Override
    public void close() {
      shutdown();
    }
  }
}
