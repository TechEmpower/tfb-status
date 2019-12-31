package tfb.status.hk2.extensions;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.stream.Collectors.toList;
import static org.glassfish.hk2.api.messaging.TopicDistributionService.HK2_DEFAULT_TOPIC_DISTRIBUTOR;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Named;
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
import org.jvnet.hk2.annotations.ContractsProvided;
import org.jvnet.hk2.internal.Utilities;
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
@Named(HK2_DEFAULT_TOPIC_DISTRIBUTOR)
@ContractsProvided({
    TopicDistributionService.class,
    DynamicConfigurationListener.class
})
final class TopicDistributionServiceImpl
    implements TopicDistributionService, DynamicConfigurationListener {

  private final ServiceLocator serviceLocator;
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @GuardedBy("this")
  private ImmutableList<Subscriber> allSubscribers = ImmutableList.of();

  @Inject
  public TopicDistributionServiceImpl(ServiceLocator serviceLocator) {
    this.serviceLocator = Objects.requireNonNull(serviceLocator);
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
              InjectUtils.serviceHandleFromParameter(parameters[i], serviceLocator);

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
          serviceLocator.getServiceHandle(subscriber.serviceDescriptor);

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
      reloadAllSubscribers();
    } catch (RuntimeException e) {
      // The contract of DynamicConfigurationListener states that exceptions
      // thrown from this method are ignored.  If we don't log it, no one
      // will.
      logger.error("Uncaught exception from configurationChanged()", e);
      throw e;
    }
  }

  private synchronized ImmutableList<Subscriber> getAllSubscribers() {
    return allSubscribers;
  }

  private void reloadAllSubscribers() {
    ImmutableList<Subscriber> subscribers =
        serviceLocator
            .getDescriptors(
                descriptor ->
                    descriptor.getQualifiers().contains(
                        MessageReceiver.class.getName()))
            .stream()
            .map(descriptor -> serviceLocator.reifyDescriptor(descriptor))
            .flatMap(
                descriptor -> {
                  Class<?> serviceClass =
                      Utilities.getFactoryAwareImplementationClass(descriptor);

                  ImmutableSet<TypeToken<?>> permittedMessageTypes =
                      descriptor
                          .getQualifierAnnotations()
                          .stream()
                          .filter(annotation -> annotation.annotationType() == MessageReceiver.class)
                          .map(annotation -> (MessageReceiver) annotation)
                          .map(annotation -> annotation.value())
                          .flatMap(classes -> Arrays.stream(classes))
                          .map(clazz -> TypeToken.of(clazz))
                          .collect(toImmutableSet());

                  return Arrays
                      .stream(serviceClass.getMethods())
                      .map(
                          method ->
                              subscriberFromMethod(
                                  method,
                                  permittedMessageTypes,
                                  serviceClass,
                                  descriptor))
                      .filter(subscriber -> subscriber != null);
                })
            .collect(toImmutableList());

    for (Subscriber subscriber : subscribers)
      logger.info("Found subscriber {}", subscriber);

    logger.info("Found {} total subscribers", subscribers.size());

    synchronized (this) {
      this.allSubscribers = subscribers;
    }
  }

  private @Nullable Subscriber subscriberFromMethod(
      Method method,
      ImmutableSet<TypeToken<?>> permittedTypes,
      Class<?> serviceClass,
      ActiveDescriptor<?> activeDescriptor) {

    if (method.getDeclaringClass() == Object.class)
      return null;

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
              serviceClass);
          return null;
        }
      }
    }

    if (parameterIndex == -1)
      return null;

    for (int i = 0; i < parameters.length; i++) {
      if (i != parameterIndex
          && !InjectUtils.supportsParameter(parameters[i], serviceLocator)) {
        logger.warn(
            "Unsupported parameter {} at index {} in method {} of service {}",
            parameters[i],
            i,
            method,
            serviceClass);
        return null;
      }
    }

    if (method.getReturnType() != void.class)
      // Not show-stopping, but probably a mistake.
      logger.warn(
          "Subscriber method {} of service {} has non-void return type {}, "
              + "but values returned from subscriber methods are ignored",
          method,
          serviceClass,
          method.getAnnotatedReturnType());

    Parameter parameter = parameters[parameterIndex];

    TypeToken<?> parameterType =
        TypeToken.of(parameter.getParameterizedType());

    if (!permittedTypes.isEmpty()
        && permittedTypes.stream()
                         .noneMatch(
                             type -> type.isSupertypeOf(parameterType))) {
      logger.warn(
          "Subscriber method {} of service {} will receive no messages "
              + "because its message parameter type {} is not a subtype of "
              + "any of the permitted types {} that were specified in the "
              + "service's @{} qualifier",
          method,
          serviceClass,
          parameterType,
          permittedTypes,
          MessageReceiver.class.getSimpleName());
    }

    var qualifiers = new ImmutableSet.Builder<Annotation>();
    for (Annotation annotation : parameter.getAnnotations())
      if (ReflectionHelper.isAnnotationAQualifier(annotation))
        qualifiers.add(annotation);

    Unqualified unqualified = parameter.getAnnotation(Unqualified.class);

    return new Subscriber(
        method,
        parameterIndex,
        parameterType,
        qualifiers.build(),
        unqualified,
        permittedTypes,
        activeDescriptor);
  }
}
