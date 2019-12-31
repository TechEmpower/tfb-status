package tfb.status.hk2.extensions;

import static java.util.stream.Collectors.toList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.glassfish.hk2.api.ActiveDescriptor;
import org.glassfish.hk2.api.DynamicConfiguration;
import org.glassfish.hk2.api.DynamicConfigurationListener;
import org.glassfish.hk2.api.DynamicConfigurationService;
import org.glassfish.hk2.api.ServiceLocator;
import org.jvnet.hk2.internal.Utilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enables the {@link Provides} annotation.
 */
@Singleton
final class ProvidesAnnotationEnabler implements DynamicConfigurationListener {
  private final ServiceLocator serviceLocator;
  private final Set<Class<?>> classesAnalyzed = ConcurrentHashMap.newKeySet();
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Inject
  public ProvidesAnnotationEnabler(ServiceLocator serviceLocator) {
    this.serviceLocator = Objects.requireNonNull(serviceLocator);
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

    Stream.Builder<ActiveDescriptor<?>> builder = Stream.builder();

    for (Method method : serviceClass.getMethods()) {
      if (method.isAnnotationPresent(Provides.class)) {
        Parameter unsupportedParameter =
            Arrays.stream(method.getParameters())
                  .filter(parameter -> !InjectUtils.supportsParameter(parameter, serviceLocator))
                  .findAny()
                  .orElse(null);

        if (unsupportedParameter != null) {
          logger.warn(
              "Unsupported parameter {} on @{} method {}",
              unsupportedParameter,
              Provides.class.getSimpleName(),
              method);
          continue;
        }

        builder.add(
            new MethodProvidesDescriptor(
                method,
                serviceDescriptor,
                serviceLocator));
      }
    }

    for (Field field : serviceClass.getFields()) {
      if (field.isAnnotationPresent(Provides.class)) {
        builder.add(
            new FieldProvidesDescriptor(
                field,
                serviceDescriptor,
                serviceLocator));
      }
    }

    return builder.build();
  }
}
