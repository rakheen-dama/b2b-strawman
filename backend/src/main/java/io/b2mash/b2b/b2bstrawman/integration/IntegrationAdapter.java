package io.b2mash.b2b.b2bstrawman.integration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Spring bean as an integration adapter. The IntegrationRegistry discovers beans annotated
 * with this at startup and builds the domain -> slug -> adapter mapping.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface IntegrationAdapter {
  /** The integration domain this adapter serves. */
  IntegrationDomain domain();

  /** Unique slug for this adapter within its domain (e.g., "xero", "noop"). */
  String slug();
}
