package tfb.status.hk2.extensions;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

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

    // FIXME: Avoid this dependency on Guava.
    return com.google.common.reflect.TypeToken
        .of(contextType)
        .resolveType(dependentType)
        .getType();
  }
}
