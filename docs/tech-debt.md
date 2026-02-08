# Tech Debt

Tracked items that are acceptable for now but should be addressed as the system scales.

## TD-001: Unbounded ConcurrentHashMap caches in filters

**Introduced**: Epic 18A (MemberFilter), Epic 6 (TenantFilter)
**Severity**: Low (current scale), Medium (at scale)
**Affected files**:
- `member/MemberFilter.java` — `memberCache` (keyed by `tenantId:clerkUserId`)
- `multitenancy/TenantFilter.java` — `schemaCache` (keyed by `clerkOrgId`)

**Problem**: Both caches are unbounded `ConcurrentHashMap` instances with no TTL or max size. Entries are never evicted (except explicit `evictFromCache` on member delete). At scale (e.g., 1000 tenants x 50 users = 50k entries in memberCache), this could cause memory pressure.

**Why acceptable now**: Entry count is proportional to active users, each entry is ~100 bytes (String key + UUID value), and TenantFilter uses the same pattern without issues. Member IDs are immutable so cached values never go stale (except on delete, which evicts).

**Fix when needed**: Replace with Caffeine cache (`maximumSize` + `expireAfterAccess`). Consider Spring Cache abstraction if multiple caches need unified management.

**Trigger to fix**: When active user count exceeds 10k or memory profiling shows cache as a significant contributor.
