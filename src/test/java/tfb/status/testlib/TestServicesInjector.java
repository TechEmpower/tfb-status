package tfb.status.testlib;

import java.util.Optional;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Allows {@link TestServices} and all the services it provides by way of
 * {@link TestServices#getService(Class)} to be injected as parameters of test
 * methods.
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
 *
 * Individual tests should never need to construct their own instances of
 * {@link TestServices}.  Instead, they should rely on this class to do that for
 * them.
 */
public final class TestServicesInjector implements ParameterResolver {
  @Override
  public boolean supportsParameter(ParameterContext parameterContext,
                                   ExtensionContext extensionContext)
      throws ParameterResolutionException {

    Class<?> type = parameterContext.getParameter().getType();
    if (type == TestServices.class)
      return true;

    TestServices services = getTestServices(extensionContext);
    return services.hasService(type);
  }

  @Override
  public Object resolveParameter(ParameterContext parameterContext,
                                 ExtensionContext extensionContext)
      throws ParameterResolutionException {

    Class<?> type = parameterContext.getParameter().getType();
    TestServices services = getTestServices(extensionContext);
    return (type == TestServices.class)
        ? services
        : services.getService(type);
  }

  private TestServices getTestServices(ExtensionContext extensionContext) {
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

    TestServicesWrapper wrapper =
        store.getOrComputeIfAbsent(TestServicesWrapper.class);

    return wrapper.services;
  }

  private static final class TestServicesWrapper
      implements ExtensionContext.Store.CloseableResource {

    private final TestServices services = new TestServices();

    @Override
    public void close() {
      services.shutdown();
    }
  }
}
