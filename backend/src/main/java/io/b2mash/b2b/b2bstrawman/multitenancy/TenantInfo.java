package io.b2mash.b2b.b2bstrawman.multitenancy;

import io.b2mash.b2b.b2bstrawman.provisioning.Tier;

/**
 * Cached tenant resolution result. Replaces the plain String (schema name) in TenantFilter's
 * Caffeine cache, adding tier awareness for Phase 2.
 *
 * @param schemaName the Postgres schema name (e.g., "tenant_a1b2c3d4e5f6" or "tenant_shared")
 * @param tier the organization's current tier (STARTER or PRO)
 */
public record TenantInfo(String schemaName, Tier tier) {}
