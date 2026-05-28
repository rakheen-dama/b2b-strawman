package io.b2mash.b2b.b2bstrawman.multitenancy;

/**
 * Cached tenant mapping that combines schema name, external org ID, and shard ID. Used as the cache
 * value type in {@link TenantFilter} to ensure shard information survives cache hits.
 *
 * <p>Without caching the shard ID alongside the schema name, cache-hit requests would leave {@link
 * RequestScopes#SHARD_ID} unbound, silently routing to the primary shard.
 *
 * @param schemaName the tenant schema name (e.g. "tenant_a1b2c3d4e5f6")
 * @param orgId the external organization ID (e.g. "org_abc123")
 * @param shardId the shard identifier (e.g. "primary", "kazi_legal_1")
 */
public record TenantMapping(String schemaName, String orgId, String shardId) {}
