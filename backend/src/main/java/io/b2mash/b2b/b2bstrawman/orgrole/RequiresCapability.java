package io.b2mash.b2b.b2bstrawman.orgrole;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Method-level annotation that enforces capability-based authorization. The annotated method is
 * only accessible if the current member's resolved capabilities include the specified capability
 * (or the special "ALL" capability granted to owner/admin system roles).
 *
 * <p>Processed by {@link CapabilityAuthorizationManager} via a Spring Security method interceptor.
 * Replaces {@code @PreAuthorize} for role-based access control.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresCapability {

  /** The capability name required to access the annotated method (e.g., "INVOICING"). */
  String value();
}
