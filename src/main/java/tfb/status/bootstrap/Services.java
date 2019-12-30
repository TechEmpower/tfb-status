package tfb.status.bootstrap;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.stream.Collectors.toList;
import static org.glassfish.hk2.api.messaging.TopicDistributionService.HK2_DEFAULT_TOPIC_DISTRIBUTOR;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Qualifier;
import javax.inject.Scope;
import javax.inject.Singleton;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glassfish.hk2.api.ActiveDescriptor;
import org.glassfish.hk2.api.DynamicConfigurationListener;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.api.Injectee;
import org.glassfish.hk2.api.PerLookup;
import org.glassfish.hk2.api.Self;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.api.Unqualified;
import org.glassfish.hk2.api.UnsatisfiedDependencyException;
import org.glassfish.hk2.api.messaging.MessageReceiver;
import org.glassfish.hk2.api.messaging.SubscribeTo;
import org.glassfish.hk2.api.messaging.Topic;
import org.glassfish.hk2.api.messaging.TopicDistributionService;
import org.glassfish.hk2.utilities.Binder;
import org.glassfish.hk2.utilities.InjecteeImpl;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.glassfish.hk2.utilities.reflection.ReflectionHelper;
import org.jvnet.hk2.annotations.ContractsProvided;
import org.jvnet.hk2.internal.Utilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
   */
  public Services(Binder... binders) {
    Objects.requireNonNull(binders);

    ServiceLocatorUtilities.addClasses(
        serviceLocator,
        TopicDistributionServiceImpl.class);

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
    Injectee injectee = injecteeFromType(type);

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
    return supportsParameter(parameter, serviceLocator);
  }

  private static boolean supportsParameter(Parameter parameter,
                                           ServiceLocator serviceLocator) {

    Objects.requireNonNull(parameter);
    Objects.requireNonNull(serviceLocator);

    // Dodge an exception that would be thrown by
    // org.jvnet.hk2.internal.Utilities#checkLookupType(Class)
    if (parameter.getType().isAnnotation()
        && !parameter.getType().isAnnotationPresent(Scope.class)
        && !parameter.getType().isAnnotationPresent(Qualifier.class))
      return false;

    // Dodge an exception that would be thrown by
    // org.jvnet.hk2.internal.ServiceLocatorImpl#internalGetInjecteeDescriptor(Injectee, boolean)
    // for parameters that are generic type variables such as "T".
    if (ReflectionHelper.getRawClass(parameter.getParameterizedType()) == null)
      return false;

    Injectee injectee = injecteeFromParameter(parameter);
    if (injectee.isOptional())
      return true;

    ActiveDescriptor<?> activeDescriptor =
        serviceLocator.getInjecteeDescriptor(injectee);

    return activeDescriptor != null;
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
        serviceHandleFromParameter(parameter, serviceLocator);

    return (serviceHandle == null) ? null : serviceHandle.getService();
  }

  private static @Nullable ServiceHandle<?> serviceHandleFromParameter(
      Parameter parameter,
      ServiceLocator serviceLocator) {

    Objects.requireNonNull(serviceLocator);
    Objects.requireNonNull(parameter);

    Injectee injectee = injecteeFromParameter(parameter);

    ActiveDescriptor<?> activeDescriptor =
        serviceLocator.getInjecteeDescriptor(injectee);

    if (activeDescriptor == null) {
      if (!injectee.isOptional())
        throw new UnsatisfiedDependencyException(injectee);

      return null;
    }

    return serviceLocator.getServiceHandle(activeDescriptor, injectee);
  }

  private static Injectee injecteeFromType(Type type) {
    Objects.requireNonNull(type);
    var injectee = new InjecteeImpl(type);
    injectee.setParent(FakeInjecteeParent.field);
    return injectee;
  }

  private static Injectee injecteeFromParameter(Parameter parameter) {
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
    @Nullable Object value;
    static final Field field;
    static {
      try {
        field = FakeInjecteeParent.class.getDeclaredField("value");
      } catch (NoSuchFieldException impossible) {
        throw new AssertionError(impossible);
      }
    }
  }

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
  private static final class TopicDistributionServiceImpl
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
                serviceHandleFromParameter(parameters[i], serviceLocator);

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
            serviceLocator.getServiceHandle(subscriber.activeDescriptor);

        if (subscriber.activeDescriptor.getScopeAnnotation() == PerLookup.class)
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
            && !supportsParameter(parameters[i], serviceLocator)) {
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

    private static final class Subscriber {
      final Method method;
      final int parameterIndex;
      final TypeToken<?> parameterType;
      final ImmutableSet<Annotation> qualifiers;
      final @Nullable Unqualified unqualified;
      final ImmutableSet<TypeToken<?>> permittedTypes;
      final ActiveDescriptor<?> activeDescriptor;

      Subscriber(Method method,
                 int parameterIndex,
                 TypeToken<?> parameterType,
                 ImmutableSet<Annotation> qualifiers,
                 @Nullable Unqualified unqualified,
                 ImmutableSet<TypeToken<?>> permittedTypes,
                 ActiveDescriptor<?> activeDescriptor) {

        this.method = Objects.requireNonNull(method);
        this.parameterIndex = parameterIndex;
        this.parameterType = Objects.requireNonNull(parameterType);
        this.qualifiers = Objects.requireNonNull(qualifiers);
        this.unqualified = unqualified;
        this.permittedTypes = Objects.requireNonNull(permittedTypes);
        this.activeDescriptor = Objects.requireNonNull(activeDescriptor);
      }

      boolean isSubscribedTo(Topic<?> topic) {
        Objects.requireNonNull(topic);

        TypeToken<?> eventType = TypeToken.of(topic.getTopicType());
        if (!parameterType.isSupertypeOf(eventType))
          return false;

        if (!permittedTypes.isEmpty()
            && permittedTypes.stream()
                             .noneMatch(type -> type.isSupertypeOf(eventType)))
          return false;

        if (!ReflectionHelper.annotationContainsAll(topic.getTopicQualifiers(),
                                                    qualifiers))
          return false;

        if (unqualified == null || topic.getTopicQualifiers().isEmpty())
          return true;

        if (unqualified.value().length == 0)
          return false;

        Set<Class<? extends Annotation>> topicQualifierTypes = new HashSet<>();
        for (Annotation qualifier : topic.getTopicQualifiers())
          topicQualifierTypes.add(qualifier.annotationType());

        return Collections.disjoint(topicQualifierTypes,
                                    Arrays.asList(unqualified.value()));
      }

      @Override
      public String toString() {
        return "Subscriber[" + method + "]";
      }
    }
  }
}
