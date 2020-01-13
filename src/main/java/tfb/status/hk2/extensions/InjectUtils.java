package tfb.status.hk2.extensions;

import static tfb.status.hk2.extensions.CompatibleWithJava8.setCopyOf;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.inject.Qualifier;
import javax.inject.Scope;
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

  static Injectee injecteeFromParameter(Parameter parameter, Type parentType) {
    Objects.requireNonNull(parameter);
    Objects.requireNonNull(parentType);

    Executable parent = parameter.getDeclaringExecutable();
    int index = Arrays.asList(parent.getParameters()).indexOf(parameter);
    if (index == -1)
      throw new AssertionError(
          "parameter " + parameter + " not found in parent " + parent);

    Type parameterType =
        TypeUtils.resolveType(
            parentType,
            parameter.getParameterizedType());

    InjecteeImpl injectee = new InjecteeImpl(parameterType);
    injectee.setParent(parent);
    injectee.setPosition(index);

    // This block of code reproduces the behavior of
    // org.jvnet.hk2.internal.Utilities#getParamInformation(Annotation[])
    Set<Annotation> qualifiers = new LinkedHashSet<>();
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
    injectee.setRequiredQualifiers(setCopyOf(qualifiers));

    return injectee;
  }

  static boolean supportsParameter(Parameter parameter,
                                   Type parentType,
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

  static /*@Nullable*/ ServiceHandle<?> serviceHandleFromParameter(
      Parameter parameter,
      Type parentType,
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

  static /*@Nullable*/ Object serviceFromParameter(
      Parameter parameter,
      Type parentType,
      /*@Nullable*/ ServiceHandle<?> root,
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
