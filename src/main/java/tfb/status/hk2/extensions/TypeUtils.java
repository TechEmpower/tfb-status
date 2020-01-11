package tfb.status.hk2.extensions;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.glassfish.hk2.utilities.reflection.GenericArrayTypeImpl;
import org.glassfish.hk2.utilities.reflection.ParameterizedTypeImpl;

/**
 * Utility methods for working with {@link Type}.
 */
final class TypeUtils {
  private TypeUtils() {
    throw new AssertionError("This class cannot be instantiated");
  }

  static boolean containsTypeVariable(Type type) {
    Objects.requireNonNull(type);
    return new TypeVariableDetector().matches(type);
  }

  private static final class TypeVariableDetector {
    private final Set<Type> seen = new HashSet<>();

    boolean matches(Type type) {
      if (!seen.add(type))
        return false;

      if (type instanceof TypeVariable)
        return true;

      if (type instanceof Class)
        return false;

      if (type instanceof WildcardType) {
        WildcardType wildcardType = (WildcardType) type;

        for (Type lowerBound : wildcardType.getLowerBounds())
          if (matches(lowerBound))
            return true;

        for (Type upperBound : wildcardType.getUpperBounds())
          if (matches(upperBound))
            return true;

        return false;
      }

      if (type instanceof ParameterizedType) {
        ParameterizedType parameterizedType = (ParameterizedType) type;

        if (matches(parameterizedType.getOwnerType()))
          return true;

        for (Type argument : parameterizedType.getActualTypeArguments())
          if (matches(argument))
            return true;

        return false;
      }

      if (type instanceof GenericArrayType) {
        GenericArrayType genericArrayType = (GenericArrayType) type;
        return matches(genericArrayType.getGenericComponentType());
      }

      return false;
    }
  }

  static Type resolveType(Type contextType, Type dependentType) {
    Objects.requireNonNull(contextType);
    Objects.requireNonNull(dependentType);
    TypeVariableResolver resolver = new TypeVariableResolver();
    resolver.populateFrom(contextType);
    return resolver.resolve(dependentType);
  }

  private static final class TypeVariableResolver {
    private final Set<Type> seen = new HashSet<>();
    private final Map<TypeVariable<?>, Type> mappings = new HashMap<>();

    void populateFrom(Type[] types) {
      Objects.requireNonNull(types);
      for (Type type : types)
        populateFrom(type);
    }

    void populateFrom(Type type) {
      Objects.requireNonNull(type);

      if (!seen.add(type))
        return;

      if (type instanceof TypeVariable) {
        TypeVariable<?> typeVariable = ((TypeVariable<?>) type);
        populateFrom(typeVariable.getBounds());
      }

      else if (type instanceof Class) {
        Class<?> clazz = (Class<?>) type;
        Type superclass = clazz.getGenericSuperclass();
        if (superclass != null)
          populateFrom(superclass);

        populateFrom(clazz.getGenericInterfaces());
      }

      else if (type instanceof WildcardType) {
        WildcardType wildcardType = (WildcardType) type;
        populateFrom(wildcardType.getUpperBounds());
      }

      else if (type instanceof ParameterizedType) {
        ParameterizedType parameterizedType = (ParameterizedType) type;
        Class<?> rawClass = (Class<?>) parameterizedType.getRawType();
        TypeVariable<?>[] typeVariables = rawClass.getTypeParameters();
        Type[] typeArguments = parameterizedType.getActualTypeArguments();

        if (typeVariables.length != typeArguments.length)
          throw new IllegalStateException();

        for (int i = 0; i < typeVariables.length; i++)
          mappings.putIfAbsent(typeVariables[i], typeArguments[i]);

        populateFrom(rawClass);

        Type ownerType = parameterizedType.getOwnerType();
        if (ownerType != null)
          populateFrom(ownerType);
      }
    }

    Type[] resolve(Type[] types) {
      Type[] resolved = new Type[types.length];
      Arrays.setAll(resolved, i -> resolve(types[i]));
      return resolved;
    }

    Type resolve(Type type) {
      Objects.requireNonNull(type);

      if (type instanceof TypeVariable<?>) {
        Type resolved = type;
        while (resolved instanceof TypeVariable<?>
            && mappings.containsKey(resolved)) {
          resolved = mappings.get(resolved);
          if (resolved.equals(type))
            return type;
        }

        return resolved;
      }

      if (type instanceof Class<?>)
        return type;

      if (type instanceof ParameterizedType) {
        ParameterizedType parameterizedType = (ParameterizedType) type;
        Type rawType = resolve(parameterizedType.getRawType());
        Type[] typeArguments =
            resolve(parameterizedType.getActualTypeArguments());
        return new ParameterizedTypeImpl(rawType, typeArguments);
      }

      if (type instanceof GenericArrayType) {
        GenericArrayType genericArrayType = (GenericArrayType) type;
        Type componentType =
            resolve(genericArrayType.getGenericComponentType());
        return new GenericArrayTypeImpl(componentType);
      }

      if (type instanceof WildcardType) {
        WildcardType wildcardType = (WildcardType) type;
        Type[] lowerBounds = resolve(wildcardType.getLowerBounds());
        Type[] upperBounds = resolve(wildcardType.getUpperBounds());
        return new WildcardTypeImpl(lowerBounds, upperBounds);
      }

      return type;
    }
  }

  private static final class WildcardTypeImpl implements WildcardType {
    private final Type[] lowerBounds;
    private final Type[] upperBounds;

    WildcardTypeImpl(Type[] lowerBounds, Type[] upperBounds) {
      this.lowerBounds = Objects.requireNonNull(lowerBounds);
      this.upperBounds = Objects.requireNonNull(upperBounds);
    }

    @Override
    public Type[] getLowerBounds() {
      return lowerBounds.clone();
    }

    @Override
    public Type[] getUpperBounds() {
      return upperBounds.clone();
    }

    @Override
    public boolean equals(Object object) {
      if (object == this) {
        return true;
      } else if (!(object instanceof WildcardType)) {
        return false;
      } else {
        WildcardType that = (WildcardType) object;
        return Arrays.equals(this.lowerBounds, that.getLowerBounds())
            && Arrays.equals(this.upperBounds, that.getUpperBounds());
      }
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(lowerBounds) ^ Arrays.hashCode(upperBounds);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder("?");

      for (Type lowerBound : lowerBounds)
        sb.append(" super ").append(lowerBound);

      for (Type upperBound : upperBounds)
        if (upperBound != Object.class)
          sb.append(" extends ").append(upperBound);

      return sb.toString();
    }
  }
}
