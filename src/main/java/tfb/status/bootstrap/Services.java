package tfb.status.bootstrap;

import com.google.common.reflect.TypeToken;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.NoSuchElementException;
import java.util.Objects;
import javax.inject.Provider;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glassfish.hk2.api.IterableProvider;
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
public final class Services {
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
  public Services(Binder... binders) {
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
    // This unchecked cast is safe because getService(Type) is guaranteed to
    // return an instance of the correct type.
    @SuppressWarnings("unchecked")
    T service = (T) getService((Type) type);
    return service;
  }

  /**
   * Returns the service of the specified type.
   *
   * <p>This method may be useful in cases where the service has a generic type.
   * If the type of the service is a non-generic {@link Class}, use {@link
   * #getService(Class)} instead of this method.
   *
   * @throws NoSuchElementException if there is no service of that type
   */
  public <T> T getService(TypeToken<T> type) {
    // This unchecked cast is safe because getService(Type) is guaranteed to
    // return an instance of the correct type.
    @SuppressWarnings("unchecked")
    T service = (T) getService(type.getType());
    return service;
  }

  /**
   * Returns the service of the specified type.
   *
   * <p>If the type of the service is a non-generic {@link Class}, use {@link
   * #getService(Class)} instead of this method.  If the type of the service is
   * generic, use {@link #getService(TypeToken)} instead of this method.
   *
   * @throws NoSuchElementException if there is no service of that type
   */
  public Object getService(Type type) {
    Objects.requireNonNull(type);
    Object service = serviceLocator.getService(type);
    if (service == null)
      throw new NoSuchElementException("There is no service of type " + type);

    return service;
  }

  /**
   * Returns {@code true} if a service of the specified type exists.  In other
   * words, this method returns {@code true} when {@link #getService(Type)}
   * would succeed and {@code false} when that method would throw {@link
   * NoSuchElementException}.
   *
   * <p>Unlike {@link #getService(Type)}, calling this method will never result
   * in the initialization of an instance of the service.
   */
  public boolean hasService(Type type) {
    if (type instanceof ParameterizedType) {
      Type rawType = ((ParameterizedType) type).getRawType();
      if (rawType == Provider.class || rawType == IterableProvider.class) {
        // Injecting Provider<T> always works even when the service locator
        // can't provide an instance of T.  In that case, provider.get() returns
        // null.
        return true;
      }
    }
    return serviceLocator.getServiceHandle(type) != null;
  }
}
