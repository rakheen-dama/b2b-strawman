package io.b2mash.b2b.b2bstrawman.multitenancy;

/**
 * Marker interface for entities that participate in shared-schema row-level isolation. Entities
 * implementing this interface have a {@code tenant_id} column that is populated by {@link
 * TenantAwareEntityListener} on persist when operating within the {@code tenant_shared} schema.
 */
public interface TenantAware {

  String getTenantId();

  void setTenantId(String tenantId);
}
