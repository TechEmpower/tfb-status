package tfb.status.hk2.extensions;

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;
import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Qualifier;
import javax.inject.Scope;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glassfish.hk2.api.ActiveDescriptor;
import org.glassfish.hk2.api.DuplicateServiceException;
import org.glassfish.hk2.api.Injectee;
import org.glassfish.hk2.api.MultiException;
import org.glassfish.hk2.api.Self;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.api.Unqualified;
import org.glassfish.hk2.api.UnsatisfiedDependencyException;
import org.glassfish.hk2.utilities.InjecteeImpl;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.glassfish.hk2.utilities.reflection.ReflectionHelper;

/**
 * Utility methods for working with {@link ServiceLocator}, {@link
 * ServiceHandle}, {@link Injectee}, and so on.
 */
final class InjectUtils {
  private InjectUtils() {
    throw new AssertionError("This class cannot be instantiated");
  }

  /**
   * Returns an instance of the service of the specified type.
   *
   * <p>This method may be useful in cases where the service has a generic type.
   * If the type of the service is a non-generic {@link Class}, use {@link
   * ServiceLocator#getService(Class, Annotation...)} instead of this method.
   *
   * @throws MultiException if a registered services matches the specified type
   *         but an exception was thrown while retrieving that instance -- if
   *         the service has unsatisfied dependencies or its constructor throws
   *         an exception, for example
   * @throws NoSuchElementException if no registered service matches the
   *         specified type, or if a registered service does match the specified
   *         type but the provider of that service provided {@code null}
   */
  static <T> T getService(ServiceLocator locator, TypeToken<T> type) {

    Objects.requireNonNull(locator);
    Objects.requireNonNull(type);

    Injectee injectee = injecteeFromType(type.getType());

    ActiveDescriptor<?> activeDescriptor =
        locator.getInjecteeDescriptor(injectee);

    if (activeDescriptor == null)
      throw new NoSuchElementException(
          "There is no service of type " + type);

    Object service =
        locator.getService(
            activeDescriptor,
            /* root= */ null,
            injectee);

    if (service == null)
      throw new NoSuchElementException(
          "There is no service of type " + type);

    // This unchecked cast is safe because the injectee was defined with the
    // caller-specified type.
    @SuppressWarnings("unchecked")
    T serviceAsT = (T) service;
    return serviceAsT;
  }

  static Injectee injecteeFromParameter(Parameter parameter,
                                        TypeToken<?> parentType) {

    Objects.requireNonNull(parameter);
    Objects.requireNonNull(parentType);

    Executable parent = parameter.getDeclaringExecutable();
    int index = Arrays.asList(parent.getParameters()).indexOf(parameter);
    if (index == -1)
      throw new AssertionError(
          "parameter " + parameter + " not found in parent " + parent);

    TypeToken<?> parameterType =
        parentType.resolveType(parameter.getParameterizedType());

    var injectee = new InjecteeImpl(parameterType.getType());
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

  static boolean supportsParameter(Parameter parameter,
                                   TypeToken<?> parentType,
                                   ServiceLocator locator) {

    Objects.requireNonNull(parameter);
    Objects.requireNonNull(parentType);
    Objects.requireNonNull(locator);

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

    Injectee injectee = injecteeFromParameter(parameter, parentType);
    if (injectee.isOptional())
      return true;

    ActiveDescriptor<?> activeDescriptor =
        locator.getInjecteeDescriptor(injectee);

    return activeDescriptor != null;
  }

  static @Nullable ServiceHandle<?> serviceHandleFromParameter(
      Parameter parameter,
      TypeToken<?> parentType,
      ServiceLocator locator) {

    Objects.requireNonNull(parameter);
    Objects.requireNonNull(parentType);
    Objects.requireNonNull(locator);

    Injectee injectee = injecteeFromParameter(parameter, parentType);

    ActiveDescriptor<?> activeDescriptor =
        locator.getInjecteeDescriptor(injectee);

    if (activeDescriptor == null) {
      if (!injectee.isOptional())
        throw new UnsatisfiedDependencyException(injectee);

      return null;
    }

    return locator.getServiceHandle(activeDescriptor, injectee);
  }

  static @Nullable Object serviceFromParameter(
      Parameter parameter,
      TypeToken<?> parentType,
      @Nullable ServiceHandle<?> root,
      ServiceLocator locator) {

    Objects.requireNonNull(parameter);
    Objects.requireNonNull(parentType);
    Objects.requireNonNull(locator);

    Injectee injectee = injecteeFromParameter(parameter, parentType);

    ActiveDescriptor<?> activeDescriptor =
        locator.getInjecteeDescriptor(injectee);

    if (activeDescriptor == null) {
      if (!injectee.isOptional())
        throw new UnsatisfiedDependencyException(injectee);

      return null;
    }

    return locator.getService(activeDescriptor, root, injectee);
  }

  static Injectee injecteeFromType(Type type) {
    Objects.requireNonNull(type);
    var injectee = new InjecteeImpl(type);
    injectee.setParent(FakeInjecteeParent.field);
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

  static void addClassesIdempotent(ServiceLocator locator,
                                   Class<?>... classes) {

    Objects.requireNonNull(locator);
    Objects.requireNonNull(classes);
    for (Class<?> c : classes)
      Objects.requireNonNull(c);

    try {
      ServiceLocatorUtilities.addClasses(locator, true, classes);
    } catch (MultiException e) {
      if (!isDuplicateServiceException(e))
        throw e;
    }
  }

  private static boolean isDuplicateServiceException(MultiException exception) {
    Objects.requireNonNull(exception);
    List<Throwable> errors = exception.getErrors();
    return !errors.isEmpty()
        && errors.stream().allMatch(e -> e instanceof DuplicateServiceException);
  }
}
