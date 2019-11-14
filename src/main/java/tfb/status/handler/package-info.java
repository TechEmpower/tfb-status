/**
 * The HTTP handlers that handle all incoming requests from users.
 */
@CheckReturnValue
@DefaultQualifier(
    value = NonNull.class,
    locations = {
        TypeUseLocation.FIELD,
        TypeUseLocation.PARAMETER,
        TypeUseLocation.RETURN
    })
package tfb.status.handler;

import com.google.errorprone.annotations.CheckReturnValue;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;
import org.checkerframework.framework.qual.TypeUseLocation;
