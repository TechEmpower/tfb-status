package tfb.status.hk2.extensions;

import com.google.common.collect.ImmutableSet;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.Set;
import org.glassfish.hk2.api.ActiveDescriptor;
import org.glassfish.hk2.api.DynamicConfiguration;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;

/**
 * An {@link ActiveDescriptor} that describes a non-instantiable class, such as
 * a utility class, an abstract class, or an interface.
 *
 * <p>A non-instantiable class is not directly usable as a service in that
 * attempting to retrieve an instance of the class would result in an exception.
 * However, the class may provide other services by way of static methods or
 * static fields annotated with {@link Provides}.  Therefore, it is useful to
 * allow such a class to be registered through {@link
 * DynamicConfiguration#addActiveDescriptor(Class)} for its {@link Provides}
 * methods and fields even when the class does not provide itself as a service.
 * The documented contract of {@link
 * DynamicConfiguration#addActiveDescriptor(Class)} gives no indication that
 * returning {@code null} is acceptable, so this {@link ActiveDescriptor}
 * implementation can be returned instead.
 */
final class NonInstantiableClassDescriptor<T> extends ProvidesDescriptor<T> {
  private final Class<T> clazz;

  NonInstantiableClassDescriptor(Class<T> clazz) {
    this.clazz = Objects.requireNonNull(clazz);
  }

  @Override
  AnnotatedElement annotatedElement() {
    return clazz;
  }

  @Override
  public T create(ServiceHandle<?> root) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void dispose(Object instance) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Class<?> getImplementationClass() {
    return clazz;
  }

  @Override
  public Type getImplementationType() {
    return clazz;
  }

  @Override
  public Set<Type> getContractTypes() {
    // Provides no contracts, not even itself.
    return ImmutableSet.of();
  }

  @Override
  public Annotation getScopeAsAnnotation() {
    return ServiceLocatorUtilities.getSingletonAnnotation();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[" + clazz + "]";
  }
}
