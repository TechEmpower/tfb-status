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
  private final ProviderCache seen = new ProviderCache();
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

    for (ActiveDescriptor<?> provider : serviceLocator.getDescriptors(any -> true)) {
      provider = serviceLocator.reifyDescriptor(provider);
      added += addDescriptors(provider, configuration);
    }

    if (added > 0)
      configuration.commit();
  }

  /**
   * Adds descriptors for each of the methods and fields annotated with {@link
   * Provides} in the specified service.
   *
   * @param providerDescriptor the descriptor of the service advertising {@code
   *        providerType} as a contract
   * @param configuration the configuration to be modified with new descriptors
   * @return the number of descriptors added as a result of the call
   */
  private int addDescriptors(
      ActiveDescriptor<?> providerDescriptor,
      DynamicConfiguration configuration) {

    Objects.requireNonNull(providerDescriptor);
    Objects.requireNonNull(configuration);

    if (!seen.add(providerDescriptor))
      return 0;

    TypeToken<?> providerType =
        TypeToken.of(getImplementationType(providerDescriptor));

    int added = 0;

    for (Method method : providerType.getRawType().getMethods()) {
      Provides provides = method.getAnnotation(Provides.class);
      if (provides == null)
        continue;

      if (!seen.add(providerDescriptor, method))
        continue;

      TypeToken<?> providesType =
          providerType.resolveType(method.getGenericReturnType());

      ImmutableSet<Type> providesContracts =
          getContracts(
              provides,
              providesType);

      Annotation scope =
          getScopeAnnotation(
              providerDescriptor,
              method.getAnnotatedReturnType(),
              method,
              providesContracts);

      Function<ServiceHandle<?>, Object> createFunction =
          Modifier.isStatic(method.getModifiers())
              ? getCreateFunctionFromStaticMethod(method, serviceLocator)
              : getCreateFunctionFromInstanceMethod(providerDescriptor, method, serviceLocator);

      Consumer<Object> disposeFunction =
          getDisposeFunction(
              providerDescriptor,
              provides,
              method,
              providesType,
              providerType,
              serviceLocator);

      configuration.addActiveDescriptor(
          new ProvidesDescriptor<>(
              method,
              providesType.getType(),
              providesContracts,
              scope,
              createFunction,
              disposeFunction));

      added++;
    }

    for (Field field : providerType.getRawType().getFields()) {
      Provides provides = field.getAnnotation(Provides.class);
      if (provides == null)
        continue;

      if (!seen.add(providerDescriptor, field))
        continue;

      TypeToken<?> providesType =
          providerType.resolveType(field.getGenericType());

      ImmutableSet<Type> providesContracts =
          getContracts(
              provides,
              providesType);

      Annotation scope =
          getScopeAnnotation(
              providerDescriptor,
              field.getAnnotatedType(),
              field,
              providesContracts);

      Function<ServiceHandle<?>, Object> createFunction =
          Modifier.isStatic(field.getModifiers())
              ? getCreateFunctionFromStaticField(field, serviceLocator)
              : getCreateFunctionFromInstanceField(providerDescriptor, field, serviceLocator);

      Consumer<Object> disposeFunction =
          getDisposeFunction(
              providerDescriptor,
              provides,
              field,
              providesType,
              providerType,
              serviceLocator);

      configuration.addActiveDescriptor(
          new ProvidesDescriptor<>(
              field,
              providesType.getType(),
              providesContracts,
              scope,
              createFunction,
              disposeFunction));

      added++;
    }

    return added;
  }

  private static Type getImplementationType(ActiveDescriptor<?> descriptor) {
    Objects.requireNonNull(descriptor);
    switch (descriptor.getDescriptorType()) {
      case CLASS:
        return descriptor.getImplementationType();
      case PROVIDE_METHOD:
        return Utilities.getFactoryProductionType(
            descriptor.getImplementationClass());
    }
    throw new AssertionError(
        "Unknown descriptor type: " + descriptor.getDescriptorType());
  }

  /**
   * Returns the set of contracts defined by a method or field that is annotated
   * with {@link Provides}.
   *
   * @param provides the {@link Provides} annotation on the method or field
   * @param providesType the {@link Method#getGenericReturnType()} of the
   *        annotated method or the {@link Field#getGenericType()} of the
   *        annotated field
   */
  private static ImmutableSet<Type> getContracts(
      Provides provides,
      TypeToken<?> providesType) {

    Objects.requireNonNull(provides);
    Objects.requireNonNull(providesType);

    if (provides.contracts().length > 0)
      return ImmutableSet.copyOf(provides.contracts());

    // This block of code reproduces the behavior of
    // org.jvnet.hk2.internal.Utilities#getAutoAdvertisedTypes(Type)

    Class<?> rawClass = ReflectionHelper.getRawClass(providesType.getType());
    if (rawClass == null)
      return ImmutableSet.of(providesType.getType());

    ContractsProvided explicit = rawClass.getAnnotation(ContractsProvided.class);
    if (explicit != null)
      return ImmutableSet.copyOf(explicit.value());

    return Stream.concat(Stream.of(providesType.getType()),
                         ReflectionHelper.getAllTypes(providesType.getType())
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
   * @param providerDescriptor the descriptor of the service that defines the
   *        method or field, in case the scope of that service is relevant
   * @param providesAnnotatedType the {@link Method#getAnnotatedReturnType()} or
   *        the {@link Field#getAnnotatedType()} of the method or field that is
   *        annotated with {@link Provides}
   * @param providesMethodOrField the method or field that is annotated with
   *        {@link Provides}
   * @param providesContracts the contracts provided by the method or field
   */
  private static <T extends AccessibleObject & Member> Annotation
  getScopeAnnotation(
      ActiveDescriptor<?> providerDescriptor,
      AnnotatedType providesAnnotatedType,
      T providesMethodOrField,
      Set<Type> providesContracts) {

    Objects.requireNonNull(providerDescriptor);
    Objects.requireNonNull(providesAnnotatedType);
    Objects.requireNonNull(providesMethodOrField);
    Objects.requireNonNull(providesContracts);

    if (providesAnnotatedType.isAnnotationPresent(Nullable.class))
      return ServiceLocatorUtilities.getPerLookupAnnotation();

    for (Annotation annotation : providesMethodOrField.getAnnotations())
      if (annotation.annotationType().isAnnotationPresent(Scope.class))
        return annotation;

    for (Type contract : providesContracts) {
      Class<?> rawType = TypeToken.of(contract).getRawType();
      for (Annotation annotation : rawType.getAnnotations())
        if (annotation.annotationType().isAnnotationPresent(Scope.class))
          return annotation;
    }

    if (!Modifier.isStatic(providesMethodOrField.getModifiers())) {
      Annotation providerScopeAnnotation =
          providerDescriptor.getScopeAsAnnotation();

      if (providerScopeAnnotation != null)
        return providerScopeAnnotation;
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

      return provided;
    };
  }

  /**
   * Returns a function that creates instances of services by invoking an
   * instance method that is annotated with {@link Provides}.
   *
   * @param providerDescriptor the descriptor of the service that defines the
   *        method
   * @param method the instance method that is annotated with {@link Provides}
   * @param serviceLocator the service locator
   */
  private static Function<ServiceHandle<?>, Object>
  getCreateFunctionFromInstanceMethod(
      ActiveDescriptor<?> providerDescriptor,
      Method method,
      ServiceLocator serviceLocator) {

    Objects.requireNonNull(providerDescriptor);
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

      Object provider =
          serviceLocator.getService(providerDescriptor, root, null);

      if (!method.canAccess(provider))
        method.setAccessible(true);

      Object provided;
      try {
        provided = method.invoke(provider, arguments);
      } catch (IllegalAccessException | InvocationTargetException e) {
        throw new MultiException(e);
      }

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

      return provided;
    };
  }

  /**
   * Returns a function that creates instances of services by reading an
   * instance field that is annotated with {@link Provides}.
   *
   * @param providerDescriptor the descriptor of the service that defines the
   *        field
   * @param field the instance field that is annotated with {@link Provides}
   * @param serviceLocator the service locator
   */
  private static Function<ServiceHandle<?>, Object>
  getCreateFunctionFromInstanceField(
      ActiveDescriptor<?> providerDescriptor,
      Field field,
      ServiceLocator serviceLocator) {

    Objects.requireNonNull(providerDescriptor);
    Objects.requireNonNull(field);
    Objects.requireNonNull(serviceLocator);

    return (ServiceHandle<?> root) -> {
      Object provider =
          serviceLocator.getService(providerDescriptor, root, null);

      if (!field.canAccess(provider))
        field.setAccessible(true);

      Object provided;
      try {
        provided = field.get(provider);
      } catch (IllegalAccessException e) {
        throw new MultiException(e);
      }

      return provided;
    };
  }

  /**
   * Returns a function that destroys instances of services that were retrieved
   * from a method or field annotated with {@link Provides}.
   *
   * @param providerDescriptor the descriptor of the service that defines the
   *        method or field
   * @param provides the {@link Provides} annotation on the method or field
   * @param providesMethodOrField the method or field that is annotated with
   *        {@link Provides}
   * @param providesType the {@link Method#getGenericReturnType()} or {@link
   *        Field#getGenericType()}
   * @param providerType the type of the service that defines the method or
   *        field
   * @param serviceLocator the service locator
   * @throws MultiException if the {@link Provides} annotation has a non-empty
   *         {@link Provides#destroyMethod()} and the method it specifies is not
   *         found
   */
  private static <T extends AccessibleObject & Member> Consumer<Object>
  getDisposeFunction(
      ActiveDescriptor<?> providerDescriptor,
      Provides provides,
      T providesMethodOrField,
      TypeToken<?> providesType,
      TypeToken<?> providerType,
      ServiceLocator serviceLocator) {

    Objects.requireNonNull(providerDescriptor);
    Objects.requireNonNull(provides);
    Objects.requireNonNull(providesMethodOrField);
    Objects.requireNonNull(providesType);
    Objects.requireNonNull(providerType);
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
            Arrays.stream(providerType.getRawType().getMethods())
                  .filter(method -> method.getName().equals(provides.destroyMethod()))
                  .filter(method -> isStatic == Modifier.isStatic(method.getModifiers()))
                  .filter(method -> method.getParameterCount() == 1)
                  .filter(method -> providerType.resolveType(method.getGenericParameterTypes()[0])
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
              providerDescriptor.getScopeAnnotation() == PerLookup.class;

          ServiceHandle<?> providerHandle =
              serviceLocator.getServiceHandle(providerDescriptor);

          try {
            Object provider = providerHandle.getService();
            if (!destroyMethod.canAccess(provider))
              destroyMethod.setAccessible(true);

            destroyMethod.invoke(provider, instance);
          } catch (IllegalAccessException | InvocationTargetException e) {
            throw new MultiException(e);
          } finally {
            if (isPerLookup)
              providerHandle.close();
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

  private static final class ProviderCache {
    private final Set<ProviderCacheKey> cache = ConcurrentHashMap.newKeySet();

    boolean add(ActiveDescriptor<?> provider) {
      Objects.requireNonNull(provider);

      ProviderCacheKey key =
          new ProviderCacheKey(provider, null);

      return cache.add(key);
    }

    boolean add(ActiveDescriptor<?> provider, Member member) {
      Objects.requireNonNull(provider);
      Objects.requireNonNull(member);

      ProviderCacheKey key =
          Modifier.isStatic(member.getModifiers())
              ? new ProviderCacheKey(null, member)
              : new ProviderCacheKey(provider, member);

      return cache.add(key);
    }
  }

  private static final class ProviderCacheKey {
    private final @Nullable ActiveDescriptor<?> provider;
    private final @Nullable Member member;

    ProviderCacheKey(@Nullable ActiveDescriptor<?> provider, @Nullable Member member) {
      this.provider = provider;
      this.member = member;
    }

    @Override
    public boolean equals(Object object) {
      if (object == this) {
        return true;
      } else if (!(object instanceof ProviderCacheKey)) {
        return false;
      } else {
        ProviderCacheKey that = (ProviderCacheKey) object;
        return Objects.equals(this.provider, that.provider)
            && Objects.equals(this.member, that.member);
      }
    }

    @Override
    public int hashCode() {
      int hash = 1;
      hash = 31 * hash + Objects.hashCode(provider);
      hash = 31 * hash + Objects.hashCode(member);
      return hash;
    }
  }
}
