package tfb.status.hk2.extensions;

import static tfb.status.hk2.extensions.CompatibleWithJava8.setCopyOf;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import javax.inject.Qualifier;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glassfish.hk2.api.ActiveDescriptor;
import org.glassfish.hk2.api.Unqualified;
import org.glassfish.hk2.api.messaging.MessageReceiver;
import org.glassfish.hk2.api.messaging.Topic;
import org.glassfish.hk2.utilities.reflection.ReflectionHelper;
import org.glassfish.hk2.utilities.reflection.TypeChecker;

/**
 * A method that receives messages from a {@link Topic}.
 */
final class Subscriber {
  /**
   * The method that accepts messages from the topic.
   */
  final Method method;

  /**
   * The index of the method parameter that corresponds to the message.
   */
  final int parameterIndex;

  /**
   * The type of the method parameter that holds the message.  If a given
   * topic's {@link Topic#getTopicType()} is not a subtype of this type, then
   * that topic's messages won't be delivered to this subscriber.  See also
   * {@link #permittedTypes} for further restrictions on topic types.
   */
  final Type parameterType;

  /**
   * The {@link Qualifier} annotations on the method.
   */
  final Set<Annotation> qualifiers;

  /**
   * The {@link Unqualified} annotation on the method, or {@code null} if there
   * is no such annotation.
   */
  final @Nullable Unqualified unqualified;

  /**
   * The set of types defined in the {@link MessageReceiver} annotation on the
   * class containing the method.  If non-empty, and if a given topic's {@link
   * Topic#getTopicType()} is not a subtype of one of these types, then that
   * topic's messages won't be delivered to this subscriber event when the topic
   * type is otherwise compatible (with the message {@link #parameterType} of
   * this subscriber).
   */
  final Set<Type> permittedTypes;

  /**
   * The descriptor for the service class containing the method.
   */
  final ActiveDescriptor<?> serviceDescriptor;

  Subscriber(Method method,
             int parameterIndex,
             Type parameterType,
             Set<Annotation> qualifiers,
             @Nullable Unqualified unqualified,
             Set<Type> permittedTypes,
             ActiveDescriptor<?> serviceDescriptor) {

    this.method = Objects.requireNonNull(method);
    this.parameterIndex = parameterIndex;
    this.parameterType = Objects.requireNonNull(parameterType);
    this.qualifiers = setCopyOf(Objects.requireNonNull(qualifiers));
    this.unqualified = unqualified;
    this.permittedTypes = setCopyOf(Objects.requireNonNull(permittedTypes));
    this.serviceDescriptor = Objects.requireNonNull(serviceDescriptor);
  }

  /**
   * Returns {@code true} if all messages from the specified topic should be
   * delivered to this subscriber.
   */
  boolean isSubscribedTo(Topic<?> topic) {
    Objects.requireNonNull(topic);

    Type eventType = topic.getTopicType();
    if (!TypeChecker.isRawTypeSafe(parameterType, eventType))
      return false;

    if (!permittedTypes.isEmpty()
        && permittedTypes.stream()
                         .noneMatch(
                             type ->
                                 TypeChecker.isRawTypeSafe(
                                     type,
                                     eventType)))
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
    return getClass().getSimpleName() + "[" + method + "]";
  }
}
