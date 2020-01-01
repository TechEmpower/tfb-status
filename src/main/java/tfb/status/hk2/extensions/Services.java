package tfb.status.hk2.extensions;

import com.google.common.reflect.TypeToken;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glassfish.hk2.api.ActiveDescriptor;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.api.Injectee;
import org.glassfish.hk2.api.MultiException;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.api.UnsatisfiedDependencyException;
import org.glassfish.hk2.utilities.Binder;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;

/**
 * Manages instance of service classes within this application.
 *
 * @see #getService(Class)
 * @see #shutdown()
 */
public final class Services {
  private final ServiceLocator serviceLocator =
      ServiceLocatorUtilities.createAndPopulateServiceLocator();

  /**
   * Use the specified binders to register service classes.
   *
   * @param binders the binders that register all of this application's service
   *        classes
   */
  public Services(Binder... binders) {
    Objects.requireNonNull(binders);

    ServiceLocatorUtilities.addClasses(
        serviceLocator,
        TopicDistributionServiceImpl.class);

    ServiceLocatorUtilities.addClasses(
        serviceLocator,
        ProvidesAnnotationEnabler.class);

    ServiceLocatorUtilities.bind(serviceLocator, binders);
  }

  /**
   * Shuts down all services.
   */
  public void shutdown() {
    serviceLocator.shutdown();
  }

  /**
   * Returns an instance of the service of the specified type.
   *
   * @throws MultiException if a registered services matches the specified type
   *         but an exception was thrown while retrieving that instance -- if
   *         the service has unsatisfied dependencies or its constructor throws
   *         an exception, for example
   * @throws NoSuchElementException if no registered service matches the
   *         specified type, or if a registered service does match the specified
   *         type but the provider of that service provided {@code null}
   */
  public <T> T getService(Class<T> type) {
    return type.cast(getService((Type) type));
  }

  /**
   * Returns an instance of the service of the specified type.
   *
   * <p>This method may be useful in cases where the service has a generic type.
   * If the type of the service is a non-generic {@link Class}, use {@link
   * #getService(Class)} instead of this method.
   *
   * @throws MultiException if a registered services matches the specified type
   *         but an exception was thrown while retrieving that instance -- if
   *         the service has unsatisfied dependencies or its constructor throws
   *         an exception, for example
   * @throws NoSuchElementException if no registered service matches the
   *         specified type, or if a registered service does match the specified
   *         type but the provider of that service provided {@code null}
   */
  public <T> T getService(TypeToken<T> type) {
    // This unchecked cast is safe because getService(Type) is guaranteed to
    // return an instance of the correct type.
    @SuppressWarnings("unchecked")
    T service = (T) getService(type.getType());
    return service;
  }

  private Object getService(Type type) {
    Injectee injectee = InjectUtils.injecteeFromType(type);

    ActiveDescriptor<?> activeDescriptor =
        serviceLocator.getInjecteeDescriptor(injectee);

    if (activeDescriptor == null)
      throw new NoSuchElementException(
          "There is no service of type " + type);

    Object service =
        serviceLocator.getService(
            activeDescriptor,
            /* root= */ null,
            injectee);

    if (service == null)
      throw new NoSuchElementException(
          "There is no service of type " + type);

    return service;
  }

  /**
   * Returns {@code true} if a call to {@link #resolveParameter(Parameter)} will
   * complete without throwing {@link UnsatisfiedDependencyException}.
   */
  public boolean supportsParameter(Parameter parameter) {
    return InjectUtils.supportsParameter(parameter, serviceLocator);
  }

  /**
   * Returns an instance of the service matching the specified method parameter
   * or constructor parameter.
   *
   * <p>This method returns {@code null} in two scenarios:
   *
   * <ul>
   * <li>There is no registered service matching the parameter, and the
   *     parameter is annotated with {@link org.jvnet.hk2.annotations.Optional}.
   * <li>There is a registered service matching the parameter, and the provider
   *     of that service (such as a {@link Factory#provide()} method) provides
   *     {@code null}.
   * </ul>
   *
   * @throws UnsatisfiedDependencyException if there is no registered service
   *         matching the parameter and the parameter is not annotated with
   *         {@link org.jvnet.hk2.annotations.Optional}
   */
  public @Nullable Object resolveParameter(Parameter parameter) {
    ServiceHandle<?> serviceHandle =
        InjectUtils.serviceHandleFromParameter(parameter, serviceLocator);

    return (serviceHandle == null) ? null : serviceHandle.getService();
  }
}
