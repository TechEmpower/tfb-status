package tfb.status.hk2.extensions;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Scope;
import javax.inject.Singleton;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glassfish.hk2.api.ActiveDescriptor;
import org.glassfish.hk2.api.ClassAnalyzer;
import org.glassfish.hk2.api.ContractIndicator;
import org.glassfish.hk2.api.DynamicConfiguration;
import org.glassfish.hk2.api.DynamicConfigurationListener;
import org.glassfish.hk2.api.DynamicConfigurationService;
import org.glassfish.hk2.api.HK2RuntimeException;
import org.glassfish.hk2.api.MultiException;
import org.glassfish.hk2.api.PerLookup;
import org.glassfish.hk2.api.Populator;
import org.glassfish.hk2.api.Rank;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.utilities.NamedImpl;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.glassfish.hk2.utilities.reflection.ReflectionHelper;
import org.jvnet.hk2.annotations.Contract;
import org.jvnet.hk2.annotations.ContractsProvided;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.internal.Collector;
import org.jvnet.hk2.internal.Utilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enables the {@link Provides} annotation.
 */
@Singleton
@ContractsProvided({
    DynamicConfigurationListener.class,
    DynamicConfigurationService.class
})
@Rank(1) // Override the default DynamicConfigurationService.
final class ProvidesAnnotationEnabler
    implements DynamicConfigurationListener, DynamicConfigurationService {

  private final ServiceLocator serviceLocator;
  private final DynamicConfigurationService defaultConfigurationService;

  private final Set<Class<?>> classesFullyAnalyzed = ConcurrentHashMap.newKeySet();

  @GuardedBy("this")
  private final Map<Class<?>, ImmutableList<ActiveDescriptor<?>>>
      staticMethodsByClass = new HashMap<>();

  @GuardedBy("this")
  private final Map<Class<?>, ImmutableList<ActiveDescriptor<?>>>
      staticFieldsByClass = new HashMap<>();

  @GuardedBy("this")
  private final Map<Class<?>, ImmutableList<ActiveDescriptor<?>>>
      instanceMethodsByClass = new HashMap<>();

  @GuardedBy("this")
  private final Map<Class<?>, ImmutableList<ActiveDescriptor<?>>>
      instanceFieldsByClass = new HashMap<>();

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Inject
  public ProvidesAnnotationEnabler(ServiceLocator serviceLocator) {
    this.serviceLocator = Objects.requireNonNull(serviceLocator);

    this.defaultConfigurationService =
        serviceLocator
            .getAllServiceHandles(DynamicConfigurationService.class)
            .stream()
            .filter(
                serviceHandle ->
                    serviceHandle.getActiveDescriptor()
                                 .getImplementationClass()
                        != getClass())
            .map(serviceHandle -> serviceHandle.getService())
            .findAny()
            .orElseThrow();
  }

  @Override
  public void configurationChanged() {
    try {
      findAllProvidesAnnotations();
    } catch (RuntimeException e) {
      logger.error("Uncaught exception from configurationChanged()", e);
      throw e;
    }
  }

  /**
   * Scans all registered service classes for {@link Provides} annotations and
   * registers the additional services they provide.
   */
  private void findAllProvidesAnnotations() {
    // Allow for the possibility that we've been replaced by a different
    // configuration service.
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
   * Provides} in the specified service.  Skips non-static methods and
   * non-static fields if {@code serviceDescriptor} is {@code null}.  (This
   * method may be called again later with a non-null service descriptor for the
   * same service, at which time those methods and fields that were previously
   * skipped will not be skipped.)
   *
   * @param serviceClass the service class to be scanned for {@link Provides}
   *        annotations
   * @param serviceDescriptor the descriptor for the service if available, or
   *        {@code null} if a descriptor for the service is not available yet
   * @param configuration the configuration to be modified with new descriptors
   * @return the number of descriptors added as a result of the call
   */
  @CanIgnoreReturnValue
  private int addProvidesDescriptors(
      Class<?> serviceClass,
      @Nullable ActiveDescriptor<?> serviceDescriptor,
      DynamicConfiguration configuration) {

    Objects.requireNonNull(serviceClass);
    Objects.requireNonNull(configuration);

    int added = 0;

    boolean staticMethodsKnown;
    boolean staticFieldsKnown;
    boolean instanceMethodsKnown;
    boolean instanceFieldsKnown;

    synchronized (this) {
      staticMethodsKnown = staticMethodsByClass.containsKey(serviceClass);
      staticFieldsKnown = staticFieldsByClass.containsKey(serviceClass);
      instanceMethodsKnown = instanceMethodsByClass.containsKey(serviceClass);
      instanceFieldsKnown = instanceFieldsByClass.containsKey(serviceClass);
    }

    ImmutableList<ActiveDescriptor<?>> staticMethods;
    if (staticMethodsKnown) {
      staticMethods = null;
    } else {
      staticMethods =
          Arrays.stream(serviceClass.getMethods())
                .filter(method -> Modifier.isStatic(method.getModifiers()))
                .map(
                    method ->
                        descriptorFromMethod(
                            method,
                            serviceClass,
                            serviceLocator,
                            null))
                .filter(methodDescriptor -> methodDescriptor != null)
                .map(methodDescriptor -> configuration.addActiveDescriptor(methodDescriptor))
                .collect(toImmutableList());
      added += staticMethods.size();
    }

    ImmutableList<ActiveDescriptor<?>> staticFields;
    if (staticFieldsKnown) {
      staticFields = null;
    } else {
      staticFields =
          Arrays.stream(serviceClass.getFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .map(
                    field ->
                        descriptorFromField(
                            field,
                            serviceClass,
                            serviceLocator,
                            null))
                .filter(fieldDescriptor -> fieldDescriptor != null)
                .map(fieldDescriptor -> configuration.addActiveDescriptor(fieldDescriptor))
                .collect(toImmutableList());
      added += staticFields.size();
    }

    ImmutableList<ActiveDescriptor<?>> instanceMethods;
    if (serviceDescriptor == null || instanceMethodsKnown) {
      instanceMethods = null;
    } else {
      instanceMethods =
          Arrays.stream(serviceClass.getMethods())
                .filter(method -> !Modifier.isStatic(method.getModifiers()))
                .map(
                    method ->
                        descriptorFromMethod(
                            method,
                            serviceClass,
                            serviceLocator,
                            serviceDescriptor))
                .filter(methodDescriptor -> methodDescriptor != null)
                .map(methodDescriptor -> configuration.addActiveDescriptor(methodDescriptor))
                .collect(toImmutableList());
      added += instanceMethods.size();
    }

    ImmutableList<ActiveDescriptor<?>> instanceFields;
    if (serviceDescriptor == null || instanceFieldsKnown) {
      instanceFields = null;
    } else {
      instanceFields =
          Arrays.stream(serviceClass.getFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(
                    field ->
                        descriptorFromField(
                            field,
                            serviceClass,
                            serviceLocator,
                            serviceDescriptor))
                .filter(fieldDescriptor -> fieldDescriptor != null)
                .map(fieldDescriptor -> configuration.addActiveDescriptor(fieldDescriptor))
                .collect(toImmutableList());
      added += instanceFields.size();
    }

    if (staticMethods != null
        || staticFields != null
        || instanceMethods != null
        || instanceFields != null) {

      synchronized (this) {
        if (staticMethods   != null)   staticMethodsByClass.put(serviceClass, staticMethods);
        if (staticFields    != null)    staticFieldsByClass.put(serviceClass, staticFields);
        if (instanceMethods != null) instanceMethodsByClass.put(serviceClass, instanceMethods);
        if (instanceFields  != null)  instanceFieldsByClass.put(serviceClass, instanceFields);
      }
    }

    return added;
  }

  private static @Nullable ActiveDescriptor<?> descriptorFromMethod(
      Method method,
      Class<?> serviceClass,
      ServiceLocator serviceLocator,
      @Nullable ActiveDescriptor<?> serviceDescriptor) {

    Objects.requireNonNull(method);
    Objects.requireNonNull(serviceClass);
    Objects.requireNonNull(serviceLocator);

    boolean isStatic = Modifier.isStatic(method.getModifiers());

    if (serviceDescriptor == null && !isStatic)
      throw new IllegalArgumentException(
          "Service descriptor required for non-static methods");

    Provides provides = method.getAnnotation(Provides.class);
    if (provides == null)
      return null;

    ImmutableSet<Type> contracts =
        getContracts(provides, method.getGenericReturnType());

    Annotation scope =
        getScopeAnnotation(method, contracts, serviceDescriptor);

    Supplier<Object> createFunction =
        (serviceDescriptor == null)
            ? () -> createFromStaticMethod(method, serviceLocator)
            : () -> createFromInstanceMethod(method, serviceLocator, serviceDescriptor);

    Consumer<Object> destroyFunction =
        getDestroyFunction(
            provides,
            isStatic,
            method,
            TypeToken.of(method.getGenericReturnType()),
            serviceClass,
            serviceDescriptor,
            serviceLocator);

    // Note: Verifying that all the method parameters are registered service
    // types would be incorrect because those service types may be registered
    // later.

    return new ProvidesDescriptor<>(
        method,
        method.getGenericReturnType(),
        contracts,
        scope,
        createFunction,
        destroyFunction);
  }

  private static @Nullable ActiveDescriptor<?> descriptorFromField(
      Field field,
      Class<?> serviceClass,
      ServiceLocator serviceLocator,
      @Nullable ActiveDescriptor<?> serviceDescriptor) {

    Objects.requireNonNull(field);
    Objects.requireNonNull(serviceClass);
    Objects.requireNonNull(serviceLocator);

    boolean isStatic = Modifier.isStatic(field.getModifiers());

    if (serviceDescriptor == null && !isStatic)
      throw new IllegalArgumentException(
          "Service descriptor required for non-static fields");

    Provides provides = field.getAnnotation(Provides.class);
    if (provides == null)
      return null;

    ImmutableSet<Type> contracts =
        getContracts(provides, field.getGenericType());

    Annotation scope =
        getScopeAnnotation(field, contracts, serviceDescriptor);

    Supplier<Object> createFunction =
        (serviceDescriptor == null)
            ? () -> createFromStaticField(field, serviceLocator)
            : () -> createFromInstanceField(field, serviceLocator, serviceDescriptor);

    Consumer<Object> destroyFunction =
        getDestroyFunction(
            provides,
            isStatic,
            field,
            TypeToken.of(field.getGenericType()),
            serviceClass,
            serviceDescriptor,
            serviceLocator);

    return new ProvidesDescriptor<>(
        field,
        field.getGenericType(),
        contracts,
        scope,
        createFunction,
        destroyFunction);
  }

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

  private static boolean isContract(Type type) {
    Objects.requireNonNull(type);

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

  private static Annotation getScopeAnnotation(
      Method method,
      Set<Type> contracts,
      @Nullable ActiveDescriptor<?> serviceDescriptor) {

    return getScopeAnnotation(
        method.getAnnotatedReturnType(),
        method,
        contracts,
        serviceDescriptor);
  }

  private static Annotation getScopeAnnotation(
      Field field,
      Set<Type> contracts,
      @Nullable ActiveDescriptor<?> serviceDescriptor) {

    return getScopeAnnotation(
        field.getAnnotatedType(),
        field,
        contracts,
        serviceDescriptor);
  }

  private static Annotation getScopeAnnotation(
      AnnotatedType annotatedType,
      AnnotatedElement annotatedElement,
      Set<Type> contracts,
      @Nullable ActiveDescriptor<?> serviceDescriptor) {

    Objects.requireNonNull(annotatedType);
    Objects.requireNonNull(annotatedElement);
    Objects.requireNonNull(contracts);

    if (annotatedType.isAnnotationPresent(Nullable.class))
      return ServiceLocatorUtilities.getPerLookupAnnotation();

    for (Annotation annotation : annotatedElement.getAnnotations())
      if (annotation.annotationType().isAnnotationPresent(Scope.class))
        return annotation;

    for (Type contract : contracts) {
      Class<?> rawType = TypeToken.of(contract).getRawType();
      for (Annotation annotation : rawType.getAnnotations())
        if (annotation.annotationType().isAnnotationPresent(Scope.class))
          return annotation;
    }

    if (serviceDescriptor != null) {
      Annotation serviceScope = serviceDescriptor.getScopeAsAnnotation();
      if (serviceScope != null)
        return serviceScope;
    }

    return ServiceLocatorUtilities.getPerLookupAnnotation();
  }

  private static Object createFromStaticMethod(
      Method method,
      ServiceLocator serviceLocator) {

    return createFromMethod(method, serviceLocator, null);
  }

  private static Object createFromInstanceMethod(
      Method method,
      ServiceLocator serviceLocator,
      ActiveDescriptor<?> serviceDescriptor) {

    return createFromMethod(method, serviceLocator, Objects.requireNonNull(serviceDescriptor));
  }

  private static Object createFromMethod(
      Method method,
      ServiceLocator serviceLocator,
      @Nullable ActiveDescriptor<?> serviceDescriptor) {

    Objects.requireNonNull(method);
    Objects.requireNonNull(serviceLocator);

    List<ServiceHandle<?>> perLookupHandles = new ArrayList<>();

    try {
      Parameter[] parameters = method.getParameters();
      Object[] arguments = new Object[parameters.length];

      for (int i = 0; i < parameters.length; i++) {
        ServiceHandle<?> parameterHandle =
            InjectUtils.serviceHandleFromParameter(parameters[i], serviceLocator);

        if (parameterHandle == null)
          arguments[i] = null;

        else {
          if (parameterHandle.getActiveDescriptor().getScopeAnnotation() == PerLookup.class)
            perLookupHandles.add(parameterHandle);

          arguments[i] = parameterHandle.getService();
        }
      }

      if (serviceDescriptor == null) {
        if (!method.canAccess(null))
          method.setAccessible(true);

        Object provided;
        try {
          provided = method.invoke(null, arguments);
        } catch (IllegalAccessException | InvocationTargetException e) {
          throw new MultiException(e);
        }

        serviceLocator.postConstruct(provided);
        return provided;
      }

      ServiceHandle<?> serviceHandle =
          serviceLocator.getServiceHandle(serviceDescriptor);

      if (serviceDescriptor.getScopeAnnotation() == PerLookup.class)
        perLookupHandles.add(serviceHandle);

      Object service = serviceHandle.getService();
      if (!method.canAccess(service))
        method.setAccessible(true);

      Object provided;
      try {
        provided = method.invoke(service, arguments);
      } catch (IllegalAccessException | InvocationTargetException e) {
        throw new MultiException(e);
      }

      serviceLocator.postConstruct(provided);
      return provided;

    } finally {
      for (ServiceHandle<?> serviceHandle : perLookupHandles)
        serviceHandle.close();
    }
  }

  private static Object createFromStaticField(
      Field field,
      ServiceLocator serviceLocator) {

    Objects.requireNonNull(field);
    Objects.requireNonNull(serviceLocator);

    if (!field.canAccess(null))
      field.setAccessible(true);

    Object provided;
    try {
      provided = field.get(null);
    } catch (IllegalAccessException e) {
      throw new MultiException(e);
    }

    serviceLocator.postConstruct(provided);
    return provided;
  }

  private static Object createFromInstanceField(
      Field field,
      ServiceLocator serviceLocator,
      ActiveDescriptor<?> serviceDescriptor) {

    Objects.requireNonNull(field);
    Objects.requireNonNull(serviceLocator);
    Objects.requireNonNull(serviceDescriptor);

    boolean isPerLookupService =
        serviceDescriptor.getScopeAnnotation() == PerLookup.class;

    ServiceHandle<?> serviceHandle =
        serviceLocator.getServiceHandle(serviceDescriptor);

    try {
      Object service = serviceHandle.getService();
      if (!field.canAccess(service))
        field.setAccessible(true);

      Object provided;
      try {
        provided = field.get(service);
      } catch (IllegalAccessException e) {
        throw new MultiException(e);
      }

      serviceLocator.postConstruct(provided);
      return provided;
    } finally {
      if (isPerLookupService)
        serviceHandle.close();
    }
  }

  private static Consumer<Object> getDestroyFunction(
      Provides provides,
      boolean isStatic,
      AnnotatedElement providesMethodOrField,
      TypeToken<?> providesType,
      Class<?> serviceClass,
      @Nullable ActiveDescriptor<?> serviceDescriptor,
      ServiceLocator serviceLocator) {

    Objects.requireNonNull(provides);
    Objects.requireNonNull(providesMethodOrField);
    Objects.requireNonNull(providesType);
    Objects.requireNonNull(serviceClass);
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
          throw new HK2RuntimeException(
              "Destroy method "
                  + provides
                  + " on "
                  + providesMethodOrField
                  + " not found");

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
          throw new HK2RuntimeException(
              "Destroy method "
                  + provides
                  + " on "
                  + providesMethodOrField
                  + " not found");

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

        // The calling code should not let this be null if we've reached this
        // case.
        Objects.requireNonNull(serviceDescriptor);
        return instance -> {
          boolean isPerLookupService =
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
            if (isPerLookupService)
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

  @Override
  public DynamicConfiguration createDynamicConfiguration() {
    return new ForwardingDynamicConfiguration() {
      private final DynamicConfiguration defaultConfiguration =
          defaultConfigurationService.createDynamicConfiguration();

      @Override
      public DynamicConfiguration delegate() {
        return defaultConfiguration;
      }

      @Override
      public <T> ActiveDescriptor<T> addActiveDescriptor(Class<T> rawClass)
          throws IllegalArgumentException {

        addProvidesDescriptors(rawClass, null, this);

        ImmutableList<ActiveDescriptor<?>> staticMethods;
        ImmutableList<ActiveDescriptor<?>> staticFields;
        synchronized (ProvidesAnnotationEnabler.this) {
          staticMethods =
              staticMethodsByClass.getOrDefault(rawClass, ImmutableList.of());
          staticFields =
              staticFieldsByClass.getOrDefault(rawClass, ImmutableList.of());
        }

        if (staticMethods.isEmpty() && staticFields.isEmpty())
          // If there are no static @Provides methods or fields, there is no
          // chance that we would want to handle anything differently from the
          // default implementation.
          return defaultConfiguration.addActiveDescriptor(rawClass);

        // If there are static @Provides methods or fields, then registering
        // this class has already served a purpose, and so we want to avoid
        // throwing an exception.  If the class itself can't be instantiated
        // through normal means, the default implementation would throw an
        // exception, but we don't want to do that because the caller did
        // nothing wrong.
        //
        // Attempt to predict whether the default addActiveDescriptor(rawClass)
        // implementation would throw an exception.  It rejects classes that
        // don't have a usable constructor, which is a constructor annotated
        // with @Inject or a constructor with zero parameters.

        Service serviceAnnotation = rawClass.getAnnotation(Service.class);

        String analyzerName =
            (serviceAnnotation == null)
                ? serviceLocator.getDefaultClassAnalyzerName()
                : serviceAnnotation.analyzer();

        ClassAnalyzer analyzer =
            serviceLocator.getService(
                ClassAnalyzer.class,
                new NamedImpl(analyzerName));

        Collector collector = new Collector();

        Constructor<T> constructor =
            Utilities.getConstructor(rawClass, analyzer, collector);

        if (!collector.hasErrors()
            && !rawClass.isEnum()
            && !Modifier.isAbstract(rawClass.getModifiers())
            && !isUtilityClassConstructor(constructor))
          // The constructor exists and we don't expect it to throw errors, so
          // the default implementation will be able to handle this class at
          // least as well as we could.
          return defaultConfiguration.addActiveDescriptor(rawClass);

        // We don't think this class can be instantiated through normal means.
        // If we found a static @Provides method or field that produces an
        // instance of this class, return the descriptor for that method or
        // field.
        for (ActiveDescriptor<?> descriptor : Iterables.concat(staticMethods, staticFields)) {
          if (descriptor.getContractTypes().contains(rawClass)) {
            // This cast is safe because the descriptor's contracts guarantee
            // that it will produce an instance of this class.
            @SuppressWarnings("unchecked")
            ActiveDescriptor<T> descriptorOfT = (ActiveDescriptor<T>) descriptor;
            return descriptorOfT;
          }
        }

        // Otherwise, we suspect this class can't be instantiated at all.
        // Return a descriptor modeling this, which advertises no contracts and
        // which is incapable of producing instances.
        ActiveDescriptor<T> descriptor =
            new ProvidesDescriptor<>(
                rawClass,
                rawClass,
                ImmutableSet.of(), // Provides no contracts, not even itself.
                ServiceLocatorUtilities.getSingletonAnnotation(),
                () -> { throw new UnsupportedOperationException(); },
                instance -> { throw new UnsupportedOperationException(); });

        return addActiveDescriptor(descriptor);
      }
    };
  }

  @Override
  public Populator getPopulator() {
    return defaultConfigurationService.getPopulator();
  }

  /**
   * Returns {@code true} if the the specified constructor appears to be the
   * constructor of a utility class.  Traditionally, utility classes have only
   * static members, and they declare a single private zero-argument constructor
   * that either does nothing or throws an exception.
   */
  private static boolean isUtilityClassConstructor(@Nullable Constructor<?> constructor) {
    return constructor != null
        && Modifier.isPrivate(constructor.getModifiers())
        && constructor.getParameterCount() == 0
        && Arrays.asList(constructor.getDeclaringClass().getDeclaredConstructors())
                 .equals(List.of(constructor))
        && Arrays.stream(constructor.getDeclaringClass().getDeclaredFields())
                 .allMatch(field -> Modifier.isStatic(field.getModifiers()))
        && Arrays.stream(constructor.getDeclaringClass().getDeclaredMethods())
                 .allMatch(method -> Modifier.isStatic(method.getModifiers()));
  }
}
