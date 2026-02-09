package io.b2mash.b2b.b2bstrawman.multitenancy;

import jakarta.persistence.PrePersist;

/**
 * JPA entity listener that sets {@code tenant_id} on new entities when operating in the shared
 * schema ({@code tenant_shared}). For dedicated schemas, this is a no-op — {@code tenant_id} stays
 * null.
 *
 * <p>Reads {@link RequestScopes#TENANT_ID} to determine if the current request targets the shared
 * schema, and {@link RequestScopes#ORG_ID} for the Clerk org ID to use as the row discriminator.
 *
 * @see TenantAware
 */
public class TenantAwareEntityListener {

  private static final String SHARED_SCHEMA = "tenant_shared";

  @PrePersist
  public void setTenantId(Object entity) {
    if (entity instanceof TenantAware tenantAware
        && RequestScopes.TENANT_ID.isBound()
        && SHARED_SCHEMA.equals(RequestScopes.TENANT_ID.get())) {
      tenantAware.setTenantId(RequestScopes.ORG_ID.get());
    }
  }
}
