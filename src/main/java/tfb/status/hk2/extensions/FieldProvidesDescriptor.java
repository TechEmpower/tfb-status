package tfb.status.hk2.extensions;

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.Set;
import javax.inject.Scope;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glassfish.hk2.api.ActiveDescriptor;
import org.glassfish.hk2.api.MultiException;
import org.glassfish.hk2.api.PerLookup;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;

/**
 * An {@link ActiveDescriptor} that describes a field annotated with {@link
 * Provides}.
 */
final class FieldProvidesDescriptor extends ProvidesDescriptor {
  private final Field field;
  private final ServiceLocator serviceLocator;
  private final @Nullable ActiveDescriptor<?> serviceDescriptor;

  FieldProvidesDescriptor(Field field,
                          ServiceLocator serviceLocator) {

    this.field = Objects.requireNonNull(field);
    this.serviceLocator = Objects.requireNonNull(serviceLocator);
    this.serviceDescriptor = null;

    if (!Modifier.isStatic(field.getModifiers()))
      throw new IllegalArgumentException(
          "Service descriptor required for non-static fields");
  }

  FieldProvidesDescriptor(Field field,
                          ServiceLocator serviceLocator,
                          ActiveDescriptor<?> serviceDescriptor) {

    this.field = Objects.requireNonNull(field);
    this.serviceLocator = Objects.requireNonNull(serviceLocator);
    this.serviceDescriptor = Objects.requireNonNull(serviceDescriptor);

    if (Modifier.isStatic(field.getModifiers()))
      throw new IllegalArgumentException(
          "Service descriptor forbidden for static fields");
  }

  @Override
  public Object create(ServiceHandle<?> root) {
    if (serviceDescriptor == null) {
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

  @Override
  public void dispose(Object instance) {
    serviceLocator.preDestroy(instance);
  }

  @Override
  public Annotation getScopeAsAnnotation() {
    if (field.getAnnotatedType().isAnnotationPresent(Nullable.class))
      return ServiceLocatorUtilities.getPerLookupAnnotation();

    for (Annotation annotation : field.getAnnotations())
      if (annotation.annotationType().isAnnotationPresent(Scope.class))
        return annotation;

    for (Type contract : getContractTypes()) {
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

  @Override
  public Class<?> getImplementationClass() {
    return TypeToken.of(field.getGenericType()).getRawType();
  }

  @Override
  public Type getImplementationType() {
    return field.getGenericType();
  }

  @Override
  public Set<Type> getContractTypes() {
    return ImmutableSet.of(field.getGenericType());
  }

  @Override
  AnnotatedElement annotatedElement() {
    return field;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[" + field + "]";
  }
}
