package tfb.status.hk2.extensions;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toUnmodifiableList;
import static java.util.stream.Collectors.toUnmodifiableSet;

import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
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
import org.glassfish.hk2.api.DynamicConfigurationListener;
import org.glassfish.hk2.api.PerLookup;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.api.Unqualified;
import org.glassfish.hk2.api.messaging.MessageReceiver;
import org.glassfish.hk2.api.messaging.SubscribeTo;
import org.glassfish.hk2.api.messaging.Topic;
import org.glassfish.hk2.api.messaging.TopicDistributionService;
import org.glassfish.hk2.utilities.reflection.ReflectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enables {@link Topic} distribution.
 *
 * <p>This is similar to the default topic distribution service provided in
 * {@code hk2-extras}, except this implementation will construct services in
 * order to deliver messages to them.
 */
@Singleton
final class TopicDistributionServiceImpl
    implements TopicDistributionService, DynamicConfigurationListener {

  private final ServiceLocator locator;
  private final Set<ActiveDescriptor<?>> seen = ConcurrentHashMap.newKeySet();
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @GuardedBy("this")
  private List<Subscriber> allSubscribers = List.of();

  @Inject
  public TopicDistributionServiceImpl(ServiceLocator locator) {
    this.locator = Objects.requireNonNull(locator);
  }

  @Override
  public void distributeMessage(Topic<?> topic, Object message) {
    Objects.requireNonNull(topic);
    Objects.requireNonNull(message);

    List<Subscriber> subscribers =
        getAllSubscribers()
            .stream()
            .filter(subscriber -> subscriber.isSubscribedTo(topic))
            .collect(toList());

    if (subscribers.isEmpty()) {
      logger.warn(
          "Ignoring message {} because topic of type {} with qualifiers {} "
              + "has no subscribers",
          message,
          topic.getTopicType(),
          topic.getTopicQualifiers());
      return;
    }

    for (Subscriber subscriber : subscribers)
      distributeMessageToSubscriber(topic, message, subscriber);
  }

  private void distributeMessageToSubscriber(Topic<?> topic,
                                             Object message,
                                             Subscriber subscriber) {
    Objects.requireNonNull(topic);
    Objects.requireNonNull(message);
    Objects.requireNonNull(subscriber);

    List<ServiceHandle<?>> perLookupHandles = new ArrayList<>();

    try {
      Parameter[] parameters = subscriber.method.getParameters();
      Object[] arguments = new Object[parameters.length];

      for (int i = 0; i < parameters.length; i++) {
        if (i == subscriber.parameterIndex)
          arguments[i] = message;

        else {
          ServiceHandle<?> parameterHandle =
              InjectUtils.serviceHandleFromParameter(
                  parameters[i],
                  subscriber.serviceDescriptor.getImplementationType(),
                  locator);

          if (parameterHandle == null)
            arguments[i] = null;

          else {
            if (parameterHandle.getActiveDescriptor().getScopeAnnotation() == PerLookup.class)
              perLookupHandles.add(parameterHandle);

            arguments[i] = parameterHandle.getService();
          }
        }
      }

      if (Modifier.isStatic(subscriber.method.getModifiers())) {
        if (!subscriber.method.canAccess(null))
          subscriber.method.setAccessible(true);

        try {
          subscriber.method.invoke(null, arguments);
        } catch (InvocationTargetException | IllegalAccessException e) {
          logger.error(
              "Error distributing message {} for topic of type {} "
                  + "with qualifiers {} to subscriber {}",
              message,
              topic.getTopicType(),
              topic.getTopicQualifiers(),
              subscriber,
              e);
        }
        return;
      }

      ServiceHandle<?> serviceHandle =
          locator.getServiceHandle(subscriber.serviceDescriptor);

      if (subscriber.serviceDescriptor.getScopeAnnotation() == PerLookup.class)
        perLookupHandles.add(serviceHandle);

      Object service = serviceHandle.getService();
      if (service == null) {
        logger.error(
            "The method for subscriber {} is non-static, but its service "
                + "handle produced null instead of producing an instance "
                + "of the service, so there is no instance on which this "
                + "non-static method can be invoked",
            subscriber);
        return;
      }

      if (!subscriber.method.canAccess(service))
        subscriber.method.setAccessible(true);

      try {
        subscriber.method.invoke(service, arguments);
      } catch (InvocationTargetException | IllegalAccessException e) {
        logger.error(
            "Error distributing message {} for topic of type {} "
                + "with qualifiers {} to subscriber {}",
            message,
            topic.getTopicType(),
            topic.getTopicQualifiers(),
            subscriber,
            e);
      }
    } finally {
      for (ServiceHandle<?> serviceHandle : perLookupHandles)
        serviceHandle.close();
    }
  }

  @Override
  public void configurationChanged() {
    try {
      findNewSubscribers();
    } catch (RuntimeException e) {
      logger.error("Uncaught exception from configurationChanged()", e);
      throw e;
    }
  }

  private synchronized List<Subscriber> getAllSubscribers() {
    return allSubscribers;
  }

  /**
   * Scans all registered service classes for services qualified with {@link
   * MessageReceiver} having methods where a parameter is annotated with {@link
   * SubscribeTo}.
   */
  private void findNewSubscribers() {
    List<Subscriber> newSubscribers =
        locator
            .getDescriptors(
                descriptor ->
                    descriptor.getQualifiers().contains(
                        MessageReceiver.class.getName()))
            .stream()
            .map(descriptor -> locator.reifyDescriptor(descriptor))
            .flatMap(
                descriptor -> {
                  if (!seen.add(descriptor))
                    return Stream.empty();

                  Set<Type> permittedMessageTypes =
                      descriptor
                          .getQualifierAnnotations()
                          .stream()
                          .filter(annotation -> annotation.annotationType() == MessageReceiver.class)
                          .map(annotation -> (MessageReceiver) annotation)
                          .map(annotation -> annotation.value())
                          .flatMap(classes -> Arrays.stream(classes))
                          .collect(toUnmodifiableSet());

                  return Arrays.stream(descriptor.getImplementationClass().getMethods())
                               .map(
                                   method ->
                                       subscriberFromMethod(
                                           method,
                                           permittedMessageTypes,
                                           descriptor))
                               .filter(subscriber -> subscriber != null);
                })
            .collect(toUnmodifiableList());

    if (newSubscribers.isEmpty())
      return;

    for (Subscriber subscriber : newSubscribers)
      logger.info("Found new subscriber {}", subscriber);

    logger.info("Found {} new subscribers", newSubscribers.size());

    synchronized (this) {
      allSubscribers =
          Stream.concat(allSubscribers.stream(),
                        newSubscribers.stream())
                .collect(toUnmodifiableList());
    }
  }

  private @Nullable Subscriber subscriberFromMethod(
      Method method,
      Set<Type> permittedTypes,
      ActiveDescriptor<?> activeDescriptor) {

    if (method.getDeclaringClass() == Object.class)
      return null;

    Type serviceType = activeDescriptor.getImplementationType();

    Parameter[] parameters = method.getParameters();

    int parameterIndex = -1;
    for (int i = 0; i < parameters.length; i++) {
      if (parameters[i].isAnnotationPresent(SubscribeTo.class)) {
        if (parameterIndex == -1) {
          parameterIndex = i;
        } else {
          logger.warn(
              "Two @{} parameters in method {} of service {}",
              SubscribeTo.class.getSimpleName(),
              method,
              serviceType);
          return null;
        }
      }
    }

    if (parameterIndex == -1)
      return null;

    for (int i = 0; i < parameters.length; i++) {
      if (i != parameterIndex
          && !InjectUtils.supportsParameter(parameters[i], serviceType, locator)) {
        // TODO: Should we accept this method anyway?  Is it possible that the
        //       service for this parameter will be registered later?  Even if
        //       not, issuing a warning here may be no better than throwing
        //       exceptions later on when messages are received.
        logger.warn(
            "Unsupported parameter {} at index {} in method {} of service {}",
            parameters[i],
            i,
            method,
            serviceType);
        return null;
      }
    }

    if (method.getReturnType() != void.class)
      // Not show-stopping, but probably a mistake.
      logger.warn(
          "Subscriber method {} of service {} has non-void return type {}, "
              + "but values returned from subscriber methods are ignored",
          method,
          serviceType,
          method.getAnnotatedReturnType());

    Parameter parameter = parameters[parameterIndex];

    Type parameterType =
        InjectUtils.resolveType(
            serviceType,
            parameter.getParameterizedType());

    if (!permittedTypes.isEmpty()
        && permittedTypes.stream()
                         .noneMatch(
                             type -> InjectUtils.isSupertype(type, parameterType))) {
      logger.warn(
          "Subscriber method {} of service {} will receive no messages "
              + "because its message parameter type {} is not a subtype of "
              + "any of the permitted types {} that were specified in the "
              + "service's @{} qualifier",
          method,
          serviceType,
          parameterType,
          permittedTypes,
          MessageReceiver.class.getSimpleName());
    }

    Set<Annotation> qualifiers =
        Set.copyOf(
            ReflectionHelper.getQualifierAnnotations(parameter));

    Unqualified unqualified = parameter.getAnnotation(Unqualified.class);

    return new Subscriber(
        method,
        parameterIndex,
        parameterType,
        qualifiers,
        unqualified,
        permittedTypes,
        activeDescriptor);
  }
}
