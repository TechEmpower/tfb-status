package tfb.status.hk2.extensions;

import com.google.common.reflect.TypeToken;
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
    return new TypeVariableDetector().containsTypeVariable(type);
  }

  private static final class TypeVariableDetector {
    private final Set<Type> seen = new HashSet<>();

    boolean containsTypeVariable(Type type) {
      if (!seen.add(type))
        return false;

      if (type instanceof TypeVariable)
        return true;

      if (type instanceof Class)
        return false;

      if (type instanceof WildcardType) {
        WildcardType wildcardType = (WildcardType) type;

        for (Type lowerBound : wildcardType.getLowerBounds())
          if (containsTypeVariable(lowerBound))
            return true;

        for (Type upperBound : wildcardType.getUpperBounds())
          if (containsTypeVariable(upperBound))
            return true;

        return false;
      }

      if (type instanceof ParameterizedType) {
        ParameterizedType parameterizedType = (ParameterizedType) type;

        if (containsTypeVariable(parameterizedType.getOwnerType()))
          return true;

        for (Type argument : parameterizedType.getActualTypeArguments())
          if (containsTypeVariable(argument))
            return true;

        return false;
      }

      if (type instanceof GenericArrayType) {
        GenericArrayType genericArrayType = (GenericArrayType) type;
        return containsTypeVariable(genericArrayType.getGenericComponentType());
      }

      return false;
    }
  }

  static Type resolveType(Type contextType, Type dependentType) {
    Objects.requireNonNull(contextType);
    Objects.requireNonNull(dependentType);
    return TypeToken.of(contextType)
                    .resolveType(dependentType)
                    .getType();
  }

  static boolean isSupertype(Type a, Type b) {
    Objects.requireNonNull(a);
    Objects.requireNonNull(b);
    return TypeToken.of(a).isSupertypeOf(b);
  }
}
