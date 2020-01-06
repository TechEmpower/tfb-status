package tfb.status.hk2.extensions;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;
import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Scope;
import javax.inject.Singleton;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glassfish.hk2.api.ActiveDescriptor;
import org.glassfish.hk2.api.ContractIndicator;
import org.glassfish.hk2.api.DynamicConfiguration;
import org.glassfish.hk2.api.DynamicConfigurationListener;
import org.glassfish.hk2.api.DynamicConfigurationService;
import org.glassfish.hk2.api.MultiException;
import org.glassfish.hk2.api.PerLookup;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.glassfish.hk2.utilities.reflection.ReflectionHelper;
import org.jvnet.hk2.annotations.Contract;
import org.jvnet.hk2.annotations.ContractsProvided;
import org.jvnet.hk2.internal.Utilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enables the {@link Provides} annotation.
 */
@Singleton
final class ProvidesListener implements DynamicConfigurationListener {
  private final ServiceLocator serviceLocator;
  private final Set<Class<?>> classesFullyAnalyzed = ConcurrentHashMap.newKeySet();
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Inject
  public ProvidesListener(ServiceLocator serviceLocator) {
    this.serviceLocator = Objects.requireNonNull(serviceLocator);
  }

  @Override
  public void configurationChanged() {
    try {
      findAllAnnotations();
    } catch (RuntimeException e) {
      logger.error("Uncaught exception from configurationChanged()", e);
      throw e;
    }
  }

  /**
   * Scans all registered service classes for {@link Provides} annotations and
   * registers the additional services they provide.
   */
  private void findAllAnnotations() {
    DynamicConfigurationService configurationService =
        serviceLocator.getService(DynamicConfigurationService.class);

    DynamicConfiguration configuration =
        configurationService.createDynamicConfiguration();

    int added = 0;

    for (ActiveDescriptor<?> serviceDescriptor : serviceLocator.getDescriptors(any -> true)) {
      serviceDescriptor = serviceLocator.reifyDescriptor(serviceDescriptor);

      Class<?> serviceClass =
          Utilities.getFactoryAwareImplementationClass(serviceDescriptor);

      if (classesFullyAnalyzed.add(serviceClass))
        added +=
            addProvidesDescriptors(
                serviceClass,
                serviceDescriptor,
                configuration);
    }

    if (added > 0)
      configuration.commit();
  }

  /**
   * Adds descriptors for each of the methods and fields annotated with {@link
   * Provides} in the specified service.
   *
   * @param serviceClass the service class to be scanned for {@link Provides}
   *        annotations
   * @param serviceDescriptor the descriptor for the service
   * @param configuration the configuration to be modified with new descriptors
   * @return the number of descriptors added as a result of the call
   */
  private int addProvidesDescriptors(Class<?> serviceClass,
                                     ActiveDescriptor<?> serviceDescriptor,
                                     DynamicConfiguration configuration) {

    Objects.requireNonNull(serviceClass);
    Objects.requireNonNull(serviceDescriptor);
    Objects.requireNonNull(configuration);

    int added = 0;

    for (Method method : serviceClass.getMethods()) {
      Provides provides = method.getAnnotation(Provides.class);
      if (provides == null)
        continue;

      ImmutableSet<Type> contracts =
          getContracts(provides, method.getGenericReturnType());

      Annotation scope =
          getScopeAnnotation(
              method.getAnnotatedReturnType(),
              method,
              contracts,
              serviceDescriptor);

      Function<ServiceHandle<?>, Object> createFunction =
          Modifier.isStatic(method.getModifiers())
              ? getCreateFunctionFromStaticMethod(method, serviceLocator)
              : getCreateFunctionFromInstanceMethod(method, serviceLocator, serviceDescriptor);

      Consumer<Object> disposeFunction =
          getDisposeFunction(
              provides,
              method,
              TypeToken.of(method.getGenericReturnType()),
              serviceClass,
              serviceDescriptor,
              serviceLocator);

      // Note: Verifying that all the method parameters are registered service
      // types would be incorrect because those service types may be registered
      // later.

      ActiveDescriptor<?> methodDescriptor =
          new ProvidesDescriptor<>(
              method,
              method.getGenericReturnType(),
              contracts,
              scope,
              createFunction,
              disposeFunction);

      configuration.addActiveDescriptor(methodDescriptor);
      added++;
    }

    for (Field field : serviceClass.getFields()) {
      Provides provides = field.getAnnotation(Provides.class);
      if (provides == null)
        continue;

      ImmutableSet<Type> contracts =
          getContracts(provides, field.getGenericType());

      Annotation scope =
          getScopeAnnotation(
              field.getAnnotatedType(),
              field,
              contracts,
              serviceDescriptor);

      Function<ServiceHandle<?>, Object> createFunction =
          Modifier.isStatic(field.getModifiers())
              ? getCreateFunctionFromStaticField(field, serviceLocator)
              : getCreateFunctionFromInstanceField(field, serviceLocator, serviceDescriptor);

      Consumer<Object> disposeFunction =
          getDisposeFunction(
              provides,
              field,
              TypeToken.of(field.getGenericType()),
              serviceClass,
              serviceDescriptor,
              serviceLocator);

      ActiveDescriptor<?> fieldDescriptor =
          new ProvidesDescriptor<>(
              field,
              field.getGenericType(),
              contracts,
              scope,
              createFunction,
              disposeFunction);

      configuration.addActiveDescriptor(fieldDescriptor);
      added++;
    }

    return added;
  }

  /**
   * Returns the set of contracts defined by a method or field that is annotated
   * with {@link Provides}.
   *
   * @param provides the {@link Provides} annotation on the method or field
   * @param type the {@link Method#getGenericReturnType()} of the annotated
   *        method or the {@link Field#getGenericType()} of the annotated field
   */
  private static ImmutableSet<Type> getContracts(Provides provides, Type type) {
    Objects.requireNonNull(provides);
    Objects.requireNonNull(type);

    if (provides.contracts().length > 0)
      return ImmutableSet.copyOf(provides.contracts());

    // This block of code reproduces the behavior of
    // org.jvnet.hk2.internal.Utilities#getAutoAdvertisedTypes(Type)

    Class<?> rawClass = ReflectionHelper.getRawClass(type);
    if (rawClass == null)
      return ImmutableSet.of(type);

    ContractsProvided provided = rawClass.getAnnotation(ContractsProvided.class);
    if (provided != null)
      return ImmutableSet.copyOf(provided.value());

    return Stream.concat(Stream.of(type),
                         ReflectionHelper.getAllTypes(type)
                                         .stream()
                                         .filter(t -> isContract(t)))
                 .collect(toImmutableSet());
  }

  /**
   * Returns {@code true} if the specified type is a contract.
   */
  private static boolean isContract(Type type) {
    Objects.requireNonNull(type);

    // This block of code reproduces the behavior of
    // org.jvnet.hk2.internal.Utilities#hasContract(Class)

    Class<?> rawClass = ReflectionHelper.getRawClass(type);
    if (rawClass == null)
      return false;

    if (rawClass.isAnnotationPresent(Contract.class))
      return true;

    for (Annotation annotation : rawClass.getAnnotations())
      if (annotation.annotationType().isAnnotationPresent(ContractIndicator.class))
        return true;

    return false;
  }

  /**
   * Returns the scope annotation for a method or field that is annotated with
   * {@link Provides}.
   *
   * @param annotatedType the {@link Method#getAnnotatedReturnType()} or the
   *        {@link Field#getAnnotatedType()} of the method or field that is
   *        annotated with {@link Provides}
   * @param methodOrField the method or field that is annotated with {@link
   *        Provides}
   * @param contracts the contracts provided by the method or field
   * @param serviceDescriptor the descriptor of the service that defines the
   *        method or field, in case the scope of that service is relevant
   */
  private static <T extends AccessibleObject & Member> Annotation
  getScopeAnnotation(
      AnnotatedType annotatedType,
      T methodOrField,
      Set<Type> contracts,
      ActiveDescriptor<?> serviceDescriptor) {

    Objects.requireNonNull(annotatedType);
    Objects.requireNonNull(methodOrField);
    Objects.requireNonNull(contracts);
    Objects.requireNonNull(serviceDescriptor);

    if (annotatedType.isAnnotationPresent(Nullable.class))
      return ServiceLocatorUtilities.getPerLookupAnnotation();

    for (Annotation annotation : methodOrField.getAnnotations())
      if (annotation.annotationType().isAnnotationPresent(Scope.class))
        return annotation;

    for (Type contract : contracts) {
      Class<?> rawType = TypeToken.of(contract).getRawType();
      for (Annotation annotation : rawType.getAnnotations())
        if (annotation.annotationType().isAnnotationPresent(Scope.class))
          return annotation;
    }

    if (!Modifier.isStatic(methodOrField.getModifiers())) {
      Annotation serviceScope = serviceDescriptor.getScopeAsAnnotation();
      if (serviceScope != null)
        return serviceScope;
    }

    return ServiceLocatorUtilities.getPerLookupAnnotation();
  }

  /**
   * Returns a function that creates instances of services by invoking a static
   * method that is annotated with {@link Provides}.
   *
   * @param method the static method that is annotated with {@link Provides}
   * @param serviceLocator the service locator
   */
  private static Function<ServiceHandle<?>, Object>
  getCreateFunctionFromStaticMethod(
      Method method,
      ServiceLocator serviceLocator) {

    Objects.requireNonNull(method);
    Objects.requireNonNull(serviceLocator);

    return (ServiceHandle<?> root) -> {
      Object[] arguments =
          Arrays.stream(method.getParameters())
                .map(
                    parameter ->
                        InjectUtils.serviceFromParameter(
                            parameter,
                            root,
                            serviceLocator))
                .toArray(length -> new Object[length]);

      if (!method.canAccess(null))
        method.setAccessible(true);

      Object provided;
      try {
        provided = method.invoke(null, arguments);
      } catch (IllegalAccessException | InvocationTargetException e) {
        throw new MultiException(e);
      }

      if (provided != null)
        serviceLocator.postConstruct(provided);

      return provided;
    };
  }

  /**
   * Returns a function that creates instances of services by invoking an
   * instance method that is annotated with {@link Provides}.
   *
   * @param method the instance method that is annotated with {@link Provides}
   * @param serviceLocator the service locator
   * @param serviceDescriptor the descriptor of the service that defines the
   *        method
   */
  private static Function<ServiceHandle<?>, Object>
  getCreateFunctionFromInstanceMethod(
      Method method,
      ServiceLocator serviceLocator,
      ActiveDescriptor<?> serviceDescriptor) {

    Objects.requireNonNull(method);
    Objects.requireNonNull(serviceLocator);
    Objects.requireNonNull(serviceDescriptor);

    return (ServiceHandle<?> root) -> {
      Object[] arguments =
          Arrays.stream(method.getParameters())
                .map(
                    parameter ->
                        InjectUtils.serviceFromParameter(
                            parameter,
                            root,
                            serviceLocator))
                .toArray(length -> new Object[length]);

      Object service = serviceLocator.getService(serviceDescriptor, root, null);
      if (!method.canAccess(service))
        method.setAccessible(true);

      Object provided;
      try {
        provided = method.invoke(service, arguments);
      } catch (IllegalAccessException | InvocationTargetException e) {
        throw new MultiException(e);
      }

      if (provided != null)
        serviceLocator.postConstruct(provided);

      return provided;
    };
  }

  /**
   * Returns a function that creates instances of services by reading a static
   * field that is annotated with {@link Provides}.
   *
   * @param field the static field that is annotated with {@link Provides}
   * @param serviceLocator the service locator
   */
  private static Function<ServiceHandle<?>, Object>
  getCreateFunctionFromStaticField(
      Field field,
      ServiceLocator serviceLocator) {

    Objects.requireNonNull(field);
    Objects.requireNonNull(serviceLocator);

    return (ServiceHandle<?> root) -> {
      // Ignore the root handle because no other ServiceHandle instances can be
      // created as a result of this call.  In other words, there is no other
      // ServiceHandle that must be closed when the handle for this field is
      // closed.

      if (!field.canAccess(null))
        field.setAccessible(true);

      Object provided;
      try {
        provided = field.get(null);
      } catch (IllegalAccessException e) {
        throw new MultiException(e);
      }

      if (provided != null)
        serviceLocator.postConstruct(provided);

      return provided;
    };
  }

  /**
   * Returns a function that creates instances of services by reading an
   * instance field that is annotated with {@link Provides}.
   *
   * @param field the instance field that is annotated with {@link Provides}
   * @param serviceLocator the service locator
   * @param serviceDescriptor the descriptor of the service that defines the
   *        field
   */
  private static Function<ServiceHandle<?>, Object>
  getCreateFunctionFromInstanceField(
      Field field,
      ServiceLocator serviceLocator,
      ActiveDescriptor<?> serviceDescriptor) {

    Objects.requireNonNull(field);
    Objects.requireNonNull(serviceLocator);
    Objects.requireNonNull(serviceDescriptor);

    return (ServiceHandle<?> root) -> {
      Object service = serviceLocator.getService(serviceDescriptor, root, null);
      if (!field.canAccess(service))
        field.setAccessible(true);

      Object provided;
      try {
        provided = field.get(service);
      } catch (IllegalAccessException e) {
        throw new MultiException(e);
      }

      if (provided != null)
        serviceLocator.postConstruct(provided);

      return provided;
    };
  }

  /**
   * Returns a function that destroys instances of services that were retrieved
   * from a method or field annotated with {@link Provides}.
   *
   * @param provides the {@link Provides} annotation on the method or field
   * @param providesMethodOrField the method or field that is annotated with
   *        {@link Provides}
   * @param providesType the {@link Method#getGenericReturnType()} or {@link
   *        Field#getGenericType()}
   * @param serviceClass the class of the service that defines the method or
   *        field
   * @param serviceDescriptor the descriptor of the service that defines the
   *        method or field, may be {@code null} if the method or field is
   *        static
   * @param serviceLocator the service locator
   * @throws MultiException if the {@link Provides} annotation has a non-empty
   *         {@link Provides#destroyMethod()} and the method it specifies is not
   *         found
   */
  private static <T extends AccessibleObject & Member> Consumer<Object>
  getDisposeFunction(
      Provides provides,
      T providesMethodOrField,
      TypeToken<?> providesType,
      Class<?> serviceClass,
      ActiveDescriptor<?> serviceDescriptor,
      ServiceLocator serviceLocator) {

    Objects.requireNonNull(provides);
    Objects.requireNonNull(providesMethodOrField);
    Objects.requireNonNull(providesType);
    Objects.requireNonNull(serviceClass);
    Objects.requireNonNull(serviceDescriptor);
    Objects.requireNonNull(serviceLocator);

    if (provides.destroyMethod().isEmpty())
      return instance -> serviceLocator.preDestroy(instance);

    switch (provides.destroyedBy()) {
      case PROVIDED_INSTANCE: {
        Method destroyMethod =
            Arrays.stream(providesType.getRawType().getMethods())
                  .filter(method -> method.getName().equals(provides.destroyMethod()))
                  .filter(method -> !Modifier.isStatic(method.getModifiers()))
                  .filter(method -> method.getParameterCount() == 0)
                  .findAny()
                  .orElse(null);

        if (destroyMethod == null)
          throw new MultiException(
              new NoSuchMethodException(
                  "Destroy method "
                      + provides
                      + " on "
                      + providesMethodOrField
                      + " not found"));

        return instance -> {
          if (!destroyMethod.canAccess(instance))
            destroyMethod.setAccessible(true);

          try {
            destroyMethod.invoke(instance);
          } catch (IllegalAccessException | InvocationTargetException e) {
            throw new MultiException(e);
          }
        };
      }

      case PROVIDER: {
        boolean isStatic = Modifier.isStatic(providesMethodOrField.getModifiers());

        Method destroyMethod =
            Arrays.stream(serviceClass.getMethods())
                  .filter(method -> method.getName().equals(provides.destroyMethod()))
                  .filter(method -> isStatic == Modifier.isStatic(method.getModifiers()))
                  .filter(method -> method.getParameterCount() == 1)
                  .filter(method -> TypeToken.of(method.getGenericParameterTypes()[0])
                                             .isSupertypeOf(providesType))
                  .findAny()
                  .orElse(null);

        if (destroyMethod == null)
          throw new MultiException(
              new NoSuchMethodException(
                  "Destroy method "
                      + provides
                      + " on "
                      + providesMethodOrField
                      + " not found"));

        if (isStatic)
          return instance -> {
            if (!destroyMethod.canAccess(null))
              destroyMethod.setAccessible(true);

            try {
              destroyMethod.invoke(null, instance);
            } catch (IllegalAccessException | InvocationTargetException e) {
              throw new MultiException(e);
            }
          };

        return instance -> {
          boolean isPerLookup =
              serviceDescriptor.getScopeAnnotation() == PerLookup.class;

          ServiceHandle<?> serviceHandle =
              serviceLocator.getServiceHandle(serviceDescriptor);

          try {
            Object service = serviceHandle.getService();
            if (!destroyMethod.canAccess(service))
              destroyMethod.setAccessible(true);

            destroyMethod.invoke(service, instance);
          } catch (IllegalAccessException | InvocationTargetException e) {
            throw new MultiException(e);
          } finally {
            if (isPerLookup)
              serviceHandle.close();
          }
        };
      }
    }

    throw new AssertionError(
        "Unknown "
            + Provides.Destroyer.class.getSimpleName()
            + " value "
            + provides.destroyedBy());
  }
}
