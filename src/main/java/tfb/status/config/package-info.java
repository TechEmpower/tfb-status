/**
 * Immutable value objects that represent the configuration of this application.
 * These configuration objects are deserialized from a YAML source file on
 * startup, and they are effectively singletons.  {@link
 * tfb.status.config.ApplicationConfig} is the root class of the configuration
 * hierarchy.
 */
@CheckReturnValue
@NonnullByDefault
package tfb.status.config;

import com.google.errorprone.annotations.CheckReturnValue;
import tfb.status.util.NonnullByDefault;
