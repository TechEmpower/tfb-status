package tfb.status.hk2.extensions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.inject.Scope;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glassfish.hk2.api.PerLookup;

/**
 * An annotation indicating that a method or field within a service class is the
 * provider of a service.  The contract of the provided service is equal to the
 * return type of that method or the type of that field.
 *
 * <p>The scope of the provided service is:
 * <ul>
 * <li>{@link PerLookup} if the method or field is {@link Nullable}.
 * <li>Otherwise, the {@link Scope} annotation on the method or field, if
 *     present.
 * <li>Otherwise, the {@link Scope} annotation on the method return type or the
 *     field type, if present.
 * <li>Otherwise, if the method or field is non-static, the scope of the
 *     containing service.
 * <li>Otherwise, {@link PerLookup}.
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.FIELD })
public @interface Provides {}

//
// TODO: Re-examine how contracts of @Provides services are determined.
//
//       Are additional contracts picked up from the interfaces that the method
//       or field type implements?  If not, should they be?
//
//       Should there be a mechanism for declaring additional contracts, similar
//       to @ContractsProvided?
//
