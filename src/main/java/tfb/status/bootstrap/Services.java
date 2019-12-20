package tfb.status.bootstrap;

import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;
import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Singleton;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glassfish.hk2.api.ActiveDescriptor;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.api.Injectee;
import org.glassfish.hk2.api.PreDestroy;
import org.glassfish.hk2.api.Self;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.api.Unqualified;
import org.glassfish.hk2.utilities.Binder;
import org.glassfish.hk2.utilities.InjecteeImpl;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.glassfish.hk2.utilities.reflection.ReflectionHelper;

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
   * Use the specified binders to register service classes.
   *
   * @param binders the binders that register all of this application's service
   *        classes
   * @throws InvalidServiceException if services requiring disposal ({@link
   *         PreDestroy} or {@link Factory}) are registered with a non-{@link
   *         Singleton} scope
   */
  public Services(Binder... binders) {
    Objects.requireNonNull(binders);
    ServiceLocatorUtilities.bind(serviceLocator, binders);

    // We could theoretically allow non-singleton services that require
    // disposal, but we'd have to provide an API for calling their disposal
    // methods.
    //
    // Until we have such an API, we should explicitly disallow these services.
    // It is extremely likely that the authors either:
    //
    //   (a) intended to make them singletons but forgot, or
    //   (b) intended to make them non-singletons but thought the dependency
    //       injection container would call their disposal methods, which it
    //       won't.

    List<ActiveDescriptor<?>> wontBeDisposed =
        serviceLocator.getDescriptors(
            descriptor -> {
              ActiveDescriptor<?> activeDescriptor =
                  serviceLocator.reifyDescriptor(descriptor);

              if (activeDescriptor.getScopeAnnotation() == Singleton.class)
                return false;

              Class<?> implementation = activeDescriptor.getImplementationClass();
              if (Factory.class.isAssignableFrom(implementation))
                return true;

              for (Type contract : activeDescriptor.getContractTypes())
                if (TypeToken.of(contract).isSubtypeOf(PreDestroy.class))
                  return true;

              return false;
            });

    if (!wontBeDisposed.isEmpty()) {
      String serviceNames =
          wontBeDisposed.stream()
                        .map(descriptor -> descriptor.getImplementation())
                        .collect(joining(", "));

      throw new InvalidServiceException(
          "The services ["
              + serviceNames
              + "] require disposal (they implement "
              + PreDestroy.class.getSimpleName()
              + " or "
              + Factory.class.getSimpleName()
              + ") but they are are not "
              + Singleton.class.getSimpleName()
              + "-scoped, "
              + "meaning their disposal methods will never be called.  "
              + "Either (a) change the scopes of these services to "
              + Singleton.class.getSimpleName()
              + ", or (b) do not register these types as services, and "
              + "manually manage the instances of these types instead.");
    }
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
    Injectee injectee = getInjectee(type);

    ActiveDescriptor<?> activeDescriptor =
        serviceLocator.getInjecteeDescriptor(injectee);

    if (activeDescriptor == null)
      throw new NoSuchElementException("There is no service of type " + type);

    Object service =
        serviceLocator.getService(
            activeDescriptor,
            /* root= */ null,
            injectee);

    if (service == null)
      throw new NoSuchElementException("There is no service of type " + type);

    return service;
  }

  /**
   * Returns the service matching the specified method parameter.
   *
   * @throws NoSuchElementException if there is no service matching that
   *         parameter
   */
  public Object getService(Parameter parameter) {
    Injectee injectee = getInjectee(parameter);

    ActiveDescriptor<?> activeDescriptor =
        serviceLocator.getInjecteeDescriptor(injectee);

    if (activeDescriptor == null)
      throw new NoSuchElementException(
          "There is no service for parameter " + parameter);

    Object service =
        serviceLocator.getService(
            activeDescriptor,
            /* root= */ null,
            injectee);

    if (service == null)
      throw new NoSuchElementException(
          "There is no service for parameter " + parameter);

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
    Injectee injectee = getInjectee(type);

    ActiveDescriptor<?> activeDescriptor =
        serviceLocator.getInjecteeDescriptor(injectee);

    return activeDescriptor != null;
  }

  /**
   * Returns {@code true} if a service matching the specified parameter exists.
   * In other words, this method returns {@code true} when {@link
   * #getService(Parameter)} would succeed and {@code false} when that method
   * would throw {@link NoSuchElementException}.
   *
   * <p>Unlike {@link #getService(Parameter)}, calling this method will never
   * result in the initialization of an instance of the service.
   */
  public boolean hasService(Parameter parameter) {
    Injectee injectee = getInjectee(parameter);

    ActiveDescriptor<?> activeDescriptor =
        serviceLocator.getInjecteeDescriptor(injectee);

    return activeDescriptor != null;
  }

  /**
   * An exception thrown from {@link Services#Services(Binder...)} when a
   * particular service appears to be invalid.
   */
  public static final class InvalidServiceException
      extends RuntimeException {

    InvalidServiceException(String message) {
      super(Objects.requireNonNull(message));
    }

    private static final long serialVersionUID = 0;
  }

  private Injectee getInjectee(Type type) {
    Objects.requireNonNull(type);
    var injectee = new InjecteeImpl(type);
    injectee.setParent(FakeInjecteeParent.field);
    return injectee;
  }

  private Injectee getInjectee(Parameter parameter) {
    Objects.requireNonNull(parameter);

    Executable parent = parameter.getDeclaringExecutable();

    int index = Arrays.asList(parent.getParameters()).indexOf(parameter);
    if (index == -1)
      throw new AssertionError(
          "parameter " + parameter + " not found in parent " + parent);

    var injectee = new InjecteeImpl(parameter.getParameterizedType());
    injectee.setParent(parent);
    injectee.setPosition(index);

    // This block of code reproduces the behavior of
    // org.jvnet.hk2.internal.Utilities#getParamInformation(Annotation[])
    var qualifiers = new ImmutableSet.Builder<Annotation>();
    for (Annotation annotation : parameter.getAnnotations()) {
      if (ReflectionHelper.isAnnotationAQualifier(annotation)) {
        qualifiers.add(annotation);
      } else if (annotation.annotationType() == org.jvnet.hk2.annotations.Optional.class) {
        injectee.setOptional(true);
      } else if (annotation.annotationType() == Self.class) {
        injectee.setSelf(true);
      } else if (annotation.annotationType() == Unqualified.class) {
        injectee.setUnqualified((Unqualified) annotation);
      }
    }
    injectee.setRequiredQualifiers(qualifiers.build());

    return injectee;
  }

  /**
   * Works around an issue in hk2 where requesting an {@link Optional} service
   * fails if {@link Injectee#getParent()} is {@code null}.
   */
  private static final class FakeInjecteeParent {
    static final @Nullable Object injectee = null;
    static final Field field;
    static {
      try {
        field = FakeInjecteeParent.class.getDeclaredField("injectee");
      } catch (NoSuchFieldException impossible) {
        throw new AssertionError(impossible);
      }
    }
  }
}
