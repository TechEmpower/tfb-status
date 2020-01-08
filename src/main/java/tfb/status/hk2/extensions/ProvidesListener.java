package tfb.status.hk2.extensions;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;
import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enables the {@link Provides} annotation.
 */
@Singleton
final class ProvidesListener implements DynamicConfigurationListener {
  private final ServiceLocator locator;
  private final ProvidersSeen seen = new ProvidersSeen();
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Inject
  public ProvidesListener(ServiceLocator locator) {
    this.locator = Objects.requireNonNull(locator);
  }

  @Override
  public void configurationChanged() {
    try {
      findProvidesAnnotations();
    } catch (RuntimeException e) {
      logger.error("Uncaught exception from configurationChanged()", e);
      throw e;
    }
  }

  /**
   * Scans all registered service classes for {@link Provides} annotations and
   * registers the additional services they provide.
   */
  private void findProvidesAnnotations() {
    DynamicConfigurationService configurationService =
        locator.getService(DynamicConfigurationService.class);

    DynamicConfiguration configuration =
        configurationService.createDynamicConfiguration();

    int added = 0;

    for (ActiveDescriptor<?> provider : locator.getDescriptors(any -> true)) {
      provider = locator.reifyDescriptor(provider);
      added += addDescriptors(provider, configuration);
    }

    if (added > 0)
      configuration.commit();
  }

  /**
   * Adds descriptors for each of the methods and fields annotated with {@link
   * Provides} in the specified service.  This method is idempotent.
   *
   * @param providerDescriptor the descriptor of the service which may contain
   *        {@link Provides} annotations
   * @param configuration the configuration to be modified with new descriptors
   * @return the number of descriptors added as a result of the call
   */
  private int addDescriptors(ActiveDescriptor<?> providerDescriptor,
                             DynamicConfiguration configuration) {

    Objects.requireNonNull(providerDescriptor);
    Objects.requireNonNull(configuration);

    if (!seen.add(providerDescriptor))
      return 0;

    TypeToken<?> providerType =
        TypeToken.of(providerDescriptor.getImplementationType());

    int added = 0;

    for (Method method : providerType.getRawType().getMethods()) {
      Provides providesAnnotation = method.getAnnotation(Provides.class);
      if (providesAnnotation == null)
        continue;

      if (!seen.add(providerDescriptor, method))
        continue;

      TypeToken<?> providedType =
          providerType.resolveType(method.getGenericReturnType());

      ImmutableSet<Type> providedContracts =
          getContracts(
              providesAnnotation,
              providedType);

      Annotation scopeAnnotation =
          getScopeAnnotation(
              providerDescriptor,
              method,
              providedContracts);

      Function<ServiceHandle<?>, Object> createFunction =
          Modifier.isStatic(method.getModifiers())
              ? getCreateFunctionFromStaticMethod(method, locator)
              : getCreateFunctionFromInstanceMethod(providerDescriptor, method, locator);

      Consumer<Object> disposeFunction =
          getDisposeFunction(
              providerDescriptor,
              providesAnnotation,
              method,
              providedType,
              providerType,
              locator);

      configuration.addActiveDescriptor(
          new ProvidesDescriptor<>(
              method,
              providedType.getType(),
              providedContracts,
              scopeAnnotation,
              createFunction,
              disposeFunction));

      added++;
    }

    for (Field field : providerType.getRawType().getFields()) {
      Provides providesAnnotation = field.getAnnotation(Provides.class);
      if (providesAnnotation == null)
        continue;

      if (!seen.add(providerDescriptor, field))
        continue;

      TypeToken<?> providedType =
          providerType.resolveType(field.getGenericType());

      ImmutableSet<Type> providedContracts =
          getContracts(
              providesAnnotation,
              providedType);

      Annotation scopeAnnotation =
          getScopeAnnotation(
              providerDescriptor,
              field,
              providedContracts);

      Function<ServiceHandle<?>, Object> createFunction =
          Modifier.isStatic(field.getModifiers())
              ? getCreateFunctionFromStaticField(field, locator)
              : getCreateFunctionFromInstanceField(providerDescriptor, field, locator);

      Consumer<Object> disposeFunction =
          getDisposeFunction(
              providerDescriptor,
              providesAnnotation,
              field,
              providedType,
              providerType,
              locator);

      configuration.addActiveDescriptor(
          new ProvidesDescriptor<>(
              field,
              providedType.getType(),
              providedContracts,
              scopeAnnotation,
              createFunction,
              disposeFunction));

      added++;
    }

    return added;
  }

  /**
   * Returns the set of contracts defined by a method or field that is annotated
   * with {@link Provides}.
   *
   * @param providesAnnotation the {@link Provides} annotation on the method or field
   * @param providedType the {@link Method#getGenericReturnType()} of the
   *        annotated method or the {@link Field#getGenericType()} of the
   *        annotated field
   */
  private static ImmutableSet<Type> getContracts(Provides providesAnnotation,
                                                 TypeToken<?> providedType) {

    Objects.requireNonNull(providesAnnotation);
    Objects.requireNonNull(providedType);

    if (providesAnnotation.contracts().length > 0)
      return ImmutableSet.copyOf(providesAnnotation.contracts());

    // This block of code reproduces the behavior of
    // org.jvnet.hk2.internal.Utilities#getAutoAdvertisedTypes(Type)

    Class<?> rawClass = ReflectionHelper.getRawClass(providedType.getType());
    if (rawClass == null)
      return ImmutableSet.of(providedType.getType());

    ContractsProvided explicit = rawClass.getAnnotation(ContractsProvided.class);
    if (explicit != null)
      return ImmutableSet.copyOf(explicit.value());

    return Stream.concat(Stream.of(providedType.getType()),
                         providedType.getTypes()
                                     .stream()
                                     .map(t -> t.getType())
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
   * @param providerMethodOrField the method or field that is annotated with
   *        {@link Provides}
   * @param providedContracts the contracts provided by the method or field
   */
  private static <T extends AccessibleObject & Member> Annotation
  getScopeAnnotation(ActiveDescriptor<?> providerDescriptor,
                     T providerMethodOrField,
                     Set<Type> providedContracts) {

    Objects.requireNonNull(providerDescriptor);
    Objects.requireNonNull(providerMethodOrField);
    Objects.requireNonNull(providedContracts);

    for (Annotation annotation : providerMethodOrField.getAnnotations())
      if (annotation.annotationType().isAnnotationPresent(Scope.class))
        return annotation;

    for (Type contract : providedContracts) {
      Class<?> rawType = TypeToken.of(contract).getRawType();
      for (Annotation annotation : rawType.getAnnotations())
        if (annotation.annotationType().isAnnotationPresent(Scope.class))
          return annotation;
    }

    if (!Modifier.isStatic(providerMethodOrField.getModifiers())) {
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
   * @param locator the service locator
   */
  private static Function<ServiceHandle<?>, Object>
  getCreateFunctionFromStaticMethod(Method method, ServiceLocator locator) {

    Objects.requireNonNull(method);
    Objects.requireNonNull(locator);

    return (ServiceHandle<?> root) -> {
      Object[] arguments =
          Arrays.stream(method.getParameters())
                .map(
                    parameter ->
                        InjectUtils.serviceFromParameter(
                            parameter,
                            root,
                            locator))
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
   * @param locator the service locator
   */
  private static Function<ServiceHandle<?>, Object>
  getCreateFunctionFromInstanceMethod(ActiveDescriptor<?> providerDescriptor,
                                      Method method,
                                      ServiceLocator locator) {

    Objects.requireNonNull(providerDescriptor);
    Objects.requireNonNull(method);
    Objects.requireNonNull(locator);

    return (ServiceHandle<?> root) -> {
      Object[] arguments =
          Arrays.stream(method.getParameters())
                .map(
                    parameter ->
                        InjectUtils.serviceFromParameter(
                            parameter,
                            root,
                            locator))
                .toArray(length -> new Object[length]);

      Object provider =
          locator.getService(providerDescriptor, root, null);

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
   * @param locator the service locator
   */
  private static Function<ServiceHandle<?>, Object>
  getCreateFunctionFromStaticField(Field field, ServiceLocator locator) {

    Objects.requireNonNull(field);
    Objects.requireNonNull(locator);

    return (ServiceHandle<?> root) -> {
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
   * @param locator the service locator
   */
  private static Function<ServiceHandle<?>, Object>
  getCreateFunctionFromInstanceField(ActiveDescriptor<?> providerDescriptor,
                                     Field field,
                                     ServiceLocator locator) {

    Objects.requireNonNull(providerDescriptor);
    Objects.requireNonNull(field);
    Objects.requireNonNull(locator);

    return (ServiceHandle<?> root) -> {
      Object provider = locator.getService(providerDescriptor, root, null);

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
   * Returns a function that disposes of instances of services that were
   * retrieved from a method or field annotated with {@link Provides}.
   *
   * @param providerDescriptor the descriptor of the service that defines the
   *        method or field
   * @param providesAnnotation the {@link Provides} annotation on the method or
   *        field
   * @param providerMethodOrField the method or field that is annotated with
   *        {@link Provides}
   * @param providedType the {@link Method#getGenericReturnType()} or {@link
   *        Field#getGenericType()}
   * @param providerType the type of the service that defines the method or
   *        field
   * @param locator the service locator
   * @throws MultiException if the {@link Provides} annotation has a non-empty
   *         {@link Provides#disposeMethod()} and the method it specifies is not
   *         found or the annotated member is a static field
   */
  private static <T extends AccessibleObject & Member> Consumer<Object>
  getDisposeFunction(ActiveDescriptor<?> providerDescriptor,
                     Provides providesAnnotation,
                     T providerMethodOrField,
                     TypeToken<?> providedType,
                     TypeToken<?> providerType,
                     ServiceLocator locator) {

    Objects.requireNonNull(providerDescriptor);
    Objects.requireNonNull(providesAnnotation);
    Objects.requireNonNull(providerMethodOrField);
    Objects.requireNonNull(providedType);
    Objects.requireNonNull(providerType);
    Objects.requireNonNull(locator);

    boolean isStatic =
        Modifier.isStatic(providerMethodOrField.getModifiers());

    if (isStatic && providerMethodOrField instanceof Field)
      return instance -> {};

    if (providesAnnotation.disposeMethod().isEmpty())
      return instance -> locator.preDestroy(instance);

    switch (providesAnnotation.disposalHandledBy()) {
      case PROVIDED_INSTANCE: {
        Method disposeMethod =
            Arrays.stream(providedType.getRawType().getMethods())
                  .filter(method -> method.getName().equals(providesAnnotation.disposeMethod()))
                  .filter(method -> !Modifier.isStatic(method.getModifiers()))
                  .filter(method -> method.getParameterCount() == 0)
                  .findAny()
                  .orElseThrow(
                      () ->
                          new MultiException(
                              new NoSuchMethodException(
                                  "Dispose method "
                                      + providesAnnotation
                                      + " on "
                                      + providerMethodOrField
                                      + " not found")));

        return instance -> {
          if (!disposeMethod.canAccess(instance))
            disposeMethod.setAccessible(true);

          try {
            disposeMethod.invoke(instance);
          } catch (IllegalAccessException | InvocationTargetException e) {
            throw new MultiException(e);
          }
        };
      }

      case PROVIDER: {
        Method disposeMethod =
            Arrays.stream(providerType.getRawType().getMethods())
                  .filter(method -> method.getName().equals(providesAnnotation.disposeMethod()))
                  .filter(method -> isStatic == Modifier.isStatic(method.getModifiers()))
                  .filter(method -> method.getParameterCount() == 1)
                  .filter(method -> providerType.resolveType(method.getGenericParameterTypes()[0])
                                                .isSupertypeOf(providedType))
                  .findAny()
                  .orElseThrow(
                      () ->
                          new MultiException(
                              new NoSuchMethodException(
                                  "Dispose method "
                                      + providesAnnotation
                                      + " on "
                                      + providerMethodOrField
                                      + " not found")));

        if (isStatic)
          return instance -> {
            if (!disposeMethod.canAccess(null))
              disposeMethod.setAccessible(true);

            try {
              disposeMethod.invoke(null, instance);
            } catch (IllegalAccessException | InvocationTargetException e) {
              throw new MultiException(e);
            }
          };

        return instance -> {
          boolean isPerLookup =
              providerDescriptor.getScopeAnnotation() == PerLookup.class;

          ServiceHandle<?> providerHandle =
              locator.getServiceHandle(providerDescriptor);

          try {
            Object provider = providerHandle.getService();
            if (!disposeMethod.canAccess(provider))
              disposeMethod.setAccessible(true);

            disposeMethod.invoke(provider, instance);
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
            + Provides.DisposalHandledBy.class.getSimpleName()
            + " value: "
            + providesAnnotation.disposalHandledBy());
  }

  /**
   * Remembers which {@link Provides} sources have already been seen so that
   * duplicate descriptors won't be added for any given source.
   */
  private static final class ProvidersSeen {
    private final Set<CacheKey> cache = ConcurrentHashMap.newKeySet();

    /**
     * Modifies this cache to remember that the specified provider has been
     * seen.  Returns {@code true} if the provider was not seen before.
     */
    boolean add(ActiveDescriptor<?> provider) {
      Objects.requireNonNull(provider);
      CacheKey key = new CacheKey(provider, null);
      return cache.add(key);
    }

    /**
     * Modifies this cache to remember that the specified method or field of the
     * specified provider has been seen.  Returns {@code true} if the method or
     * field was not seen before.
     */
    boolean add(ActiveDescriptor<?> provider, Member methodOrField) {
      Objects.requireNonNull(provider);
      Objects.requireNonNull(methodOrField);

      CacheKey key =
          Modifier.isStatic(methodOrField.getModifiers())
              ? new CacheKey(null, methodOrField)
              : new CacheKey(provider, methodOrField);

      return cache.add(key);
    }

    private static final class CacheKey {
      private final @Nullable ActiveDescriptor<?> provider;
      private final @Nullable Member methodOrField;

      CacheKey(@Nullable ActiveDescriptor<?> provider,
               @Nullable Member methodOrField) {

        this.provider = provider;
        this.methodOrField = methodOrField;
      }

      @Override
      public boolean equals(Object object) {
        if (object == this) {
          return true;
        } else if (!(object instanceof CacheKey)) {
          return false;
        } else {
          CacheKey that = (CacheKey) object;
          return Objects.equals(this.provider, that.provider)
              && Objects.equals(this.methodOrField, that.methodOrField);
        }
      }

      @Override
      public int hashCode() {
        int hash = 1;
        hash = 31 * hash + Objects.hashCode(provider);
        hash = 31 * hash + Objects.hashCode(methodOrField);
        return hash;
      }
    }
  }
}
