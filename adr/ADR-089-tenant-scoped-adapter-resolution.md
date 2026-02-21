# ADR-089: Tenant-Scoped Adapter Resolution

**Status**: Accepted

**Context**:

Phase 21 introduces a BYOAK (Bring Your Own API Key) framework where each tenant can configure which external provider to use for a given integration domain (e.g., Xero for accounting, OpenAI for AI). The `IntegrationRegistry` must resolve the correct adapter bean at request time based on the current tenant's `OrgIntegration` configuration. This resolution happens on every domain service call that uses an integration port (e.g., syncing an invoice to the tenant's configured accounting provider).

The resolution involves reading from the `org_integrations` table in the tenant's dedicated schema, matching the stored `provider_slug` to a registered adapter bean, and optionally injecting credentials from the `SecretStore`. This lookup must be fast (it's on the critical path of domain operations), consistent (a config change should take effect within a bounded time), and tenant-isolated (one tenant's configuration must never leak to another). The existing codebase uses Caffeine caching for `OrgSettings` with short TTLs, establishing a precedent for this pattern.

**Options Considered**:

1. **Per-request DB lookup with Caffeine cache (chosen)** -- On each call to `IntegrationRegistry.resolve(domain, portInterface)`, read the tenant's `OrgIntegration` for that domain. Cache the result in a Caffeine cache keyed by `(tenantSchema, domain)` with a 60-second TTL. Cache misses hit the database. Cache entries are evicted on TTL expiry or explicitly when a config change is made via the management API.
   - Pros:
     - Simple and predictable: one cache, one TTL, one eviction strategy
     - Matches the existing `OrgSettings` caching pattern (Caffeine, short TTL) -- developers already understand this model
     - Bounded staleness: config changes take effect within 60 seconds at worst, immediately when evicted explicitly
     - No background threads or listeners -- resolution is purely on-demand
     - Caffeine is already a project dependency (used by `OrgSettingsService`)
   - Cons:
     - 60-second staleness window: if an admin changes the provider, in-flight requests in other threads may use the old provider for up to 60 seconds
     - Cache key includes tenant schema, so memory grows linearly with active tenants (but each entry is tiny -- one `OrgIntegration` row)
     - First request after cache expiry incurs a DB query on the critical path (typically <1ms for a single-row lookup by domain + unique constraint)

2. **Tenant context listener that pre-loads** -- Register a Spring event listener (or Hibernate interceptor) that fires when the tenant context is established (e.g., in the `TenantFilter`). The listener pre-loads all `OrgIntegration` rows for the tenant and stores them in a request-scoped map. Domain services read from this map with zero latency.
   - Pros:
     - Zero latency at resolution time -- all integration configs are pre-loaded
     - No cache staleness: every request gets fresh data
     - Request-scoped storage means no cross-tenant leakage risk
   - Cons:
     - Pre-loads ALL integration configs even if the request doesn't use any integrations (most requests don't) -- wasted DB queries
     - Requires hooking into the tenant filter chain, adding complexity to the already-delicate `ScopedValue`-based filter pipeline
     - No caching benefit across requests -- the same tenant's config is re-loaded on every request
     - If a request spans multiple integration domains (rare but possible), the pre-load is efficient; but for the common case of zero or one domain, it's wasteful

3. **Lazy resolution on first use per request** -- Similar to Option 2, but the integration config is loaded lazily on first call to `IntegrationRegistry.resolve()` within a request. Uses a request-scoped bean (or `ScopedValue`) to store the resolved config. Subsequent calls within the same request reuse the cached result.
   - Pros:
     - Only loads config when actually needed (no wasted queries for non-integration requests)
     - Fresh data every request -- no staleness window
     - Request-scoped isolation prevents cross-tenant leakage
   - Cons:
     - Requires a request-scoped bean or `ScopedValue` carrier -- adds lifecycle complexity
     - No cross-request caching: if the same tenant makes 100 requests/second, that's 100 DB queries/second for the same config row
     - Request-scoped beans interact poorly with async processing or background jobs that don't have a request context
     - Complicates testing: tests must set up request scope or mock the resolution

**Decision**: Per-request DB lookup with Caffeine cache (Option 1).

**Rationale**: The Caffeine cache approach is the simplest option that provides acceptable performance. The `org_integrations` table has a unique constraint on `domain`, so each tenant has at most 4 rows (one per integration domain). A single-row lookup by `(domain)` within the tenant's schema is sub-millisecond. Caching with a 60-second TTL reduces this to zero cost for the vast majority of requests while keeping staleness bounded.

The 60-second staleness window is acceptable because integration configuration changes are rare administrative actions (an admin switching from Xero to QuickBooks happens once, not continuously). The explicit cache eviction on config change via the management API provides immediate consistency for the common case where the admin changes a setting and immediately tests it.

This approach also works cleanly with background jobs and scheduled tasks that run outside a request context -- they can use the same cache without needing request-scoped infrastructure. The existing `OrgSettings` pattern validates this approach at scale.

**Consequences**:

- Positive:
  - Consistent with existing caching patterns in the codebase (`OrgSettings`, `BillingRate` resolution)
  - Caffeine is already in the dependency tree -- no new libraries
  - Simple to reason about: "integration config is cached for 60 seconds"
  - Explicit eviction on config change provides immediate consistency for the admin flow
  - Works in both request and non-request contexts (background jobs, scheduled tasks)

- Negative:
  - 60-second staleness window exists for edge cases (e.g., admin changes config on node A, request hits node B that hasn't evicted yet). Acceptable for v1; can be addressed with distributed eviction events if multi-node deployment requires it.
  - Cache memory grows with active tenants, though each entry is small (~200 bytes)

- Neutral:
  - Cache key format: `tenantSchema + ":" + domain.name()` (e.g., `"tenant_abc:ACCOUNTING"`)
  - Cache is per-JVM; in a multi-node deployment, each node maintains its own cache. This is fine because the 60-second TTL bounds divergence.
