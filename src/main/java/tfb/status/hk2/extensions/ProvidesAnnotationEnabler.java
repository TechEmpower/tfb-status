package tfb.status.hk2.extensions;

import static java.util.stream.Collectors.toList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glassfish.hk2.api.ActiveDescriptor;
import org.glassfish.hk2.api.ClassAnalyzer;
import org.glassfish.hk2.api.DynamicConfiguration;
import org.glassfish.hk2.api.DynamicConfigurationListener;
import org.glassfish.hk2.api.DynamicConfigurationService;
import org.glassfish.hk2.api.Populator;
import org.glassfish.hk2.api.Rank;
import org.glassfish.hk2.api.ServiceLocator;
import org.jvnet.hk2.annotations.ContractsProvided;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.internal.Collector;
import org.jvnet.hk2.internal.ServiceLocatorImpl;
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

  private final Set<Class<?>> classesAnalyzed = ConcurrentHashMap.newKeySet();
  private final Set<Method> methodsAnalyzed = ConcurrentHashMap.newKeySet();
  private final Set<Field> fieldsAnalyzed = ConcurrentHashMap.newKeySet();

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
    List<ActiveDescriptor<?>> newDescriptors =
        serviceLocator
            .getDescriptors(any -> true)
            .stream()
            .map(descriptor -> serviceLocator.reifyDescriptor(descriptor))
            .flatMap(descriptor -> providesDescriptors(descriptor))
            .collect(toList());

    if (newDescriptors.isEmpty())
      return;

    for (ActiveDescriptor<?> descriptor : newDescriptors)
      logger.info("Found provides method or field {}", descriptor);

    logger.info(
        "Found {} total provides methods and fields",
        newDescriptors.size());

    // Allow for the possibility that we've been replaced by a different
    // configuration service.
    DynamicConfigurationService configurationService =
        serviceLocator.getService(DynamicConfigurationService.class);

    DynamicConfiguration configuration =
        configurationService.createDynamicConfiguration();

    for (ActiveDescriptor<?> descriptor : newDescriptors)
      configuration.addActiveDescriptor(descriptor);

    configuration.commit();
  }

  /**
   * Returns descriptors for each of the methods and fields annotated with
   * {@link Provides} in the specified service.
   */
  private Stream<ActiveDescriptor<?>> providesDescriptors(
      ActiveDescriptor<?> serviceDescriptor) {

    Objects.requireNonNull(serviceDescriptor);

    Class<?> serviceClass =
        Utilities.getFactoryAwareImplementationClass(serviceDescriptor);

    if (!classesAnalyzed.add(serviceClass))
      return Stream.empty();

    return Stream.concat(
        Arrays.stream(serviceClass.getMethods())
              .map(
                  method ->
                      descriptorFromMethod(
                          method,
                          serviceClass,
                          serviceDescriptor))
              .filter(methodDescriptor -> methodDescriptor != null),

        Arrays.stream(serviceClass.getFields())
              .map(
                  field ->
                      descriptorFromField(
                          field,
                          serviceClass,
                          serviceDescriptor))
              .filter(fieldDescriptor -> fieldDescriptor != null));
  }

  private @Nullable ActiveDescriptor<?> descriptorFromMethod(
      Method method,
      Class<?> serviceClass,
      @Nullable ActiveDescriptor<?> serviceDescriptor) {

    Objects.requireNonNull(method);
    Objects.requireNonNull(serviceClass);

    boolean isStatic = Modifier.isStatic(method.getModifiers());

    if (serviceDescriptor == null && !isStatic)
      throw new IllegalArgumentException(
          "Service descriptor required for non-static methods");

    if (!methodsAnalyzed.add(method))
      return null;

    if (!method.isAnnotationPresent(Provides.class))
      return null;

    // Note: Verifying that all the method parameters are registered service
    // types would be incorrect because those service types may be registered
    // later.

    if (isStatic)
      return new MethodProvidesDescriptor(
          method,
          serviceLocator);

    return new MethodProvidesDescriptor(
        method,
        serviceLocator,
        // requireNonNull for NullAway's sake.
        Objects.requireNonNull(serviceDescriptor));
  }

  private @Nullable ActiveDescriptor<?> descriptorFromField(
      Field field,
      Class<?> serviceClass,
      @Nullable ActiveDescriptor<?> serviceDescriptor) {

    Objects.requireNonNull(field);
    Objects.requireNonNull(serviceClass);

    boolean isStatic = Modifier.isStatic(field.getModifiers());

    if (serviceDescriptor == null && !isStatic)
      throw new IllegalArgumentException(
          "Service descriptor required for non-static fields");

    if (!fieldsAnalyzed.add(field))
      return null;

    if (!field.isAnnotationPresent(Provides.class))
      return null;

    if (isStatic)
      return new FieldProvidesDescriptor(
          field,
          serviceLocator);

    return new FieldProvidesDescriptor(
        field,
        serviceLocator,
        // requireNonNull for NullAway's sake.
        Objects.requireNonNull(serviceDescriptor));
  }

  @Override
  public DynamicConfiguration createDynamicConfiguration() {
    DynamicConfiguration defaultConfiguration =
        defaultConfigurationService.createDynamicConfiguration();

    if (!(serviceLocator instanceof ServiceLocatorImpl)) {
      logger.warn(
          "Unable to replace default configuration service "
              + "because the service locator is of type {}, "
              + "which is not a subclass of {}",
          serviceLocator.getClass().getName(),
          ServiceLocatorImpl.class.getName());

      return defaultConfiguration;
    }

    ServiceLocatorImpl locator = (ServiceLocatorImpl) serviceLocator;

    return new ForwardingDynamicConfiguration() {
      @Override
      public DynamicConfiguration delegate() {
        return defaultConfiguration;
      }

      @Override
      public <T> ActiveDescriptor<T> addActiveDescriptor(Class<T> rawClass)
          throws IllegalArgumentException {

        // Attempt to predict whether the default addActiveDescriptor(rawClass)
        // implementation would throw an exception.  It rejects classes that
        // don't have a usable constructor, which is a constructor annotated
        // with @Inject or a constructor with zero parameters.
        //
        // Normally a class like that would be unusable as a service, but we can
        // potentially make it usable.  If it has a static @Provides factory
        // method, then we know how to instantiate it even if it lacks a usable
        // constructor.

        Service serviceAnnotation = rawClass.getAnnotation(Service.class);

        String analyzerName =
            (serviceAnnotation == null)
                ? null
                : serviceAnnotation.analyzer();

        Collector collector = new Collector();

        ClassAnalyzer analyzer =
            Utilities.getClassAnalyzer(locator, analyzerName, collector);

        Utilities.getConstructor(rawClass, analyzer, collector);

        if (!collector.hasErrors())
          // The constructor exists, so the default implementation will be able
          // to handle this class at least as well as we could.
          return defaultConfiguration.addActiveDescriptor(rawClass);

        ActiveDescriptor<T> factoryMethodDescriptor =
            Arrays.stream(rawClass.getMethods())
                  .filter(method -> Modifier.isStatic(method.getModifiers()))
                  .filter(method -> method.isAnnotationPresent(Provides.class))
                  .filter(method -> method.getReturnType() == rawClass)
                  .map(method -> descriptorFromMethod(method, rawClass, null))
                  .map(
                      methodDescriptor -> {
                        // This cast is safe because we know that the factory
                        // method produces an instance of T.
                        @SuppressWarnings("unchecked")
                        ActiveDescriptor<T> withNarrowedType =
                            (ActiveDescriptor<T>) methodDescriptor;

                        return withNarrowedType;
                      })
                  .findAny()
                  .orElse(null);

        if (factoryMethodDescriptor != null) {
          logger.info(
              "Found static factory method {} for otherwise non-instantiable "
                  + "service class {}",
              factoryMethodDescriptor,
              rawClass);

          return addActiveDescriptor(factoryMethodDescriptor, false);
        }

        // We tried, but we can't help.  The default implementation will
        // probably throw an informative error.
        return defaultConfiguration.addActiveDescriptor(rawClass);
      }
    };
  }

  @Override
  public Populator getPopulator() {
    return defaultConfigurationService.getPopulator();
  }
}
