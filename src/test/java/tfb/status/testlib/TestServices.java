package tfb.status.testlib;

import tfb.status.bootstrap.HttpServer;
import tfb.status.bootstrap.Services;
import tfb.status.bootstrap.ServicesBinder;

/**
 * Manages the instances of HTTP handlers and service classes within this
 * application during tests.
 *
 * <p>Use {@link #getService(Class)} to retrieve instances of service classes.
 * For example, <code>getService(HttpServer.class)</code> returns an instance
 * of {@link HttpServer}.
 *
 * <p><strong>Important:</strong> Call {@link #shutdown()} once the tests are
 * complete.
 *
 * @see #getService(Class)
 * @see #shutdown()
 */
public final class TestServices extends Services {
  /**
   * Constructs the interface for managing this application's services during
   * tests.
   */
  public TestServices() {
    super(new ServicesBinder("test_config.yml"),
          new TestServicesBinder());
  }
}
