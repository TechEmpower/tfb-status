package tfb.status.bootstrap;

import java.util.NoSuchElementException;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.utilities.Binder;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;

/**
 * Manages the instances of HTTP handlers and service classes within this
 * application.
 *
 * <p>Use {@link #getService(Class)} to retrieve instances of service classes.
 * For example, <code>getService(HttpServer.class)</code> returns an instance
 * of {@link HttpServer}.
 *
 * @see #getService(Class)
 * @see #shutdown()
 */
public class Services {
  private final ServiceLocator serviceLocator =
      ServiceLocatorUtilities.createAndPopulateServiceLocator();

  /**
   * Constructs the interface for managing this application's services.
   *
   * @param configFilePath the path to this application's YAML configuration
   *        file, or {@code null} if a default configuration should be used
   */
  public Services(@Nullable String configFilePath) {
    this(new ServicesBinder(configFilePath));
  }

  /**
   * Use the specified binders to register service classes.
   *
   * @param binders the binders that register all of this application's service
   *        classes
   */
  protected Services(Binder... binders) {
    Objects.requireNonNull(binders);
    ServiceLocatorUtilities.bind(serviceLocator, binders);
  }

  /**
   * Shuts down all services.
   */
  public void shutdown() {
    serviceLocator.shutdown();
  }

  /**
   * Returns the service of the specified type.
   *
   * @throws NoSuchElementException if there is no service of that type
   */
  public <T> T getService(Class<T> type) {
    T service = serviceLocator.getService(type);
    if (service == null)
      throw new NoSuchElementException("There is no service of type " + type);

    return service;
  }

  /**
   * Returns {@code true} if a service of the specified type exists.  In other
   * words, this method returns {@code true} when {@link #getService(Class)}
   * would succeed and {@code false} when that method would throw {@link
   * NoSuchElementException}.
   */
  public boolean hasService(Class<?> type) {
    return serviceLocator.getServiceHandle(type) != null;
  }
}
