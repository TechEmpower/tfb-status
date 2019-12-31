package tfb.status.hk2.extensions;

import com.google.common.collect.ImmutableSet;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
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
import org.glassfish.hk2.utilities.reflection.ReflectionHelper;

/**
 * An {@link ActiveDescriptor} that describes a method annotated with {@link
 * Provides}.
 */
final class MethodProvidesDescriptor extends ProvidesDescriptor {
  private final Method method;
  private final ActiveDescriptor<?> serviceDescriptor;
  private final ServiceLocator serviceLocator;

  MethodProvidesDescriptor(Method method,
                           ActiveDescriptor<?> serviceDescriptor,
                           ServiceLocator serviceLocator) {

    this.method = Objects.requireNonNull(method);
    this.serviceDescriptor = Objects.requireNonNull(serviceDescriptor);
    this.serviceLocator = Objects.requireNonNull(serviceLocator);
  }

  @Override
  public Object create(ServiceHandle<?> root) {
    List<ServiceHandle<?>> perLookupHandles = new ArrayList<>();

    try {
      Parameter[] parameters = method.getParameters();
      Object[] arguments = new Object[parameters.length];

      for (int i = 0; i < parameters.length; i++) {
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

      if (Modifier.isStatic(method.getModifiers())) {
        if (!method.canAccess(null)) {
          method.setAccessible(true);
        }
        Object provided;
        try {
          provided = method.invoke(null, arguments);
        } catch (IllegalAccessException | InvocationTargetException e) {
          throw new MultiException(e);
        }
        serviceLocator.postConstruct(provided);
        return provided;
      }

      ServiceHandle<?> serviceHandle =
          serviceLocator.getServiceHandle(serviceDescriptor);

      if (serviceDescriptor.getScopeAnnotation() == PerLookup.class)
        perLookupHandles.add(serviceHandle);

      Object service = serviceHandle.getService();
      if (!method.canAccess(service)) {
        method.setAccessible(true);
      }
      Object provided;
      try {
        provided = method.invoke(service, arguments);
      } catch (IllegalAccessException | InvocationTargetException e) {
        throw new MultiException(e);
      }
      serviceLocator.postConstruct(provided);
      return provided;

    } finally {
      for (ServiceHandle<?> serviceHandle : perLookupHandles)
        serviceHandle.close();
    }
  }

  @Override
  public void dispose(Object instance) {
    serviceLocator.preDestroy(instance);
  }

  @Override
  public Annotation getScopeAsAnnotation() {
    if (method.getAnnotatedReturnType().isAnnotationPresent(Nullable.class))
      return ServiceLocatorUtilities.getPerLookupAnnotation();

    for (Annotation annotation : method.getAnnotations())
      if (annotation.annotationType().isAnnotationPresent(Scope.class))
        return annotation;

    for (Type contract : getContractTypes()) {
      Class<?> rawClass = ReflectionHelper.getRawClass(contract);
      if (rawClass != null)
        for (Annotation annotation : rawClass.getAnnotations())
          if (annotation.annotationType().isAnnotationPresent(Scope.class))
            return annotation;
    }

    Annotation serviceScope = serviceDescriptor.getScopeAsAnnotation();
    if (serviceScope != null)
      return serviceScope;

    return ServiceLocatorUtilities.getPerLookupAnnotation();
  }

  @Override
  public Class<?> getImplementationClass() {
    return ReflectionHelper.getRawClass(method.getGenericReturnType());
  }

  @Override
  public Type getImplementationType() {
    return method.getGenericReturnType();
  }

  @Override
  public Set<Type> getContractTypes() {
    return ImmutableSet.of(method.getGenericReturnType());
  }

  @Override
  AnnotatedElement annotatedElement() {
    return method;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[" + method + "]";
  }
}
