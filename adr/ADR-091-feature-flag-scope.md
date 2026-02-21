# ADR-091: Feature Flag Scope

**Status**: Accepted

**Context**:

Phase 21 introduces three new integration domains (Accounting, AI, Document Signing) that must be gated behind per-tenant feature flags. When a domain is disabled for a tenant, the backend should reject API calls with a 403 ProblemDetail response, and the frontend should render disabled/coming-soon states. The gating is coarse-grained: an entire integration domain is either enabled or disabled for a tenant, not individual operations within a domain.

The existing system already has precedent for per-tenant configuration via `OrgSettings` (Phase 8), which stores default currency, branding, pack statuses, and compliance settings. It also has plan-tier enforcement (Starter vs. Pro) via `PlanEnforcementService`, which gates features based on subscription level. The integration feature flags are distinct from plan-tier gating -- they control whether a tenant has opted in to using an integration domain, regardless of their plan tier. (Plan-tier restrictions on integrations, e.g., "AI only on Pro", can be layered on separately via the existing plan enforcement system.)

**Options Considered**:

1. **Boolean columns on OrgSettings (chosen)** -- Add three `NOT NULL DEFAULT FALSE` boolean columns to the existing `org_settings` table: `accounting_enabled`, `ai_enabled`, `document_signing_enabled`. Read them via the existing `OrgSettingsService` (which already has Caffeine caching). Toggle them via the existing `OrgSettings` update API with additional fields in the DTO.
   - Pros:
     - Minimal new code: extends an existing entity, service, and API rather than creating new ones
     - Leverages existing `OrgSettings` Caffeine cache -- no new caching infrastructure
     - Type-safe: each flag is a named boolean field, not a string lookup in a generic table
     - Migration is a simple `ALTER TABLE` -- no new table, no new index
     - Frontend already fetches `OrgSettings` on page load -- the flags come "for free" in the existing response
     - Easy to reason about: "is accounting enabled?" is `orgSettings.isAccountingEnabled()`, not `featureFlagService.isEnabled("accounting")`
   - Cons:
     - Adding a new integration domain requires a schema migration (new column) + entity change + DTO change. This is acceptable when new domains are added once per phase, not dynamically.
     - Not extensible to arbitrary feature flags -- if the platform later needs 50 feature flags, this approach doesn't scale
     - The `OrgSettings` entity grows by 3 fields (currently 12 fields, would become 15). Still manageable but approaching the point where sub-entities might be cleaner.

2. **Generic feature flag table** -- Create a new `feature_flags` table with `(id, flag_key VARCHAR, enabled BOOLEAN, metadata JSONB)` in the tenant schema. An `FeatureFlagService` provides `isEnabled(String flagKey)` lookups with caching. Flags are managed via a dedicated API.
   - Pros:
     - Infinitely extensible: adding a new flag is an INSERT, not a schema migration
     - Supports metadata per flag (description, category, rollout percentage)
     - Clean separation from `OrgSettings` -- flags are a distinct concern
   - Cons:
     - Over-engineered for 3 flags: creates a new entity, repository, service, controller, migration, frontend page, and caching layer for something that could be 3 boolean columns
     - String-based flag keys (`"accounting_enabled"`) are error-prone -- typos compile but fail at runtime
     - Requires a separate cache from `OrgSettings`, doubling the caching infrastructure for tenant config
     - The "generic" design implies it should handle all feature flags, but plan-tier gating is already handled by `PlanEnforcementService` -- having two feature-gating systems creates confusion
     - YAGNI: if/when 50+ feature flags are needed, a third-party service (Option 3) would be more appropriate than a custom table

3. **Third-party feature flag service (LaunchDarkly, Unleash, Flagsmith)** -- Integrate a dedicated feature flag platform that provides SDKs, targeting rules, gradual rollouts, and a management UI.
   - Pros:
     - Purpose-built for feature management with sophisticated targeting (by tenant, by user, by percentage)
     - Management UI for non-technical stakeholders
     - Real-time flag updates without deployment
     - Analytics and audit trail built in
   - Cons:
     - Cost: LaunchDarkly starts at $10/seat/month; Unleash OSS is free but requires hosting
     - Adds an external runtime dependency -- if the flag service is down, the system must have fallback behavior
     - Introduces a new infrastructure component to deploy, monitor, and maintain
     - Massive overkill for 3 boolean flags on a per-tenant basis
     - The tenant-scoping model (dedicated schemas, ScopedValue-based context) may not map cleanly to the flag service's concept of "user" or "context"
     - Vendor lock-in for a non-core concern

**Decision**: Boolean columns on OrgSettings (Option 1).

**Rationale**: The current requirement is exactly three boolean flags that gate integration domains at the tenant level. Boolean columns on the existing `OrgSettings` entity are the simplest implementation that meets this requirement. The flags are type-safe, cached via the existing Caffeine cache, and exposed through the existing settings API and DTO -- no new infrastructure, no new caching layer, no new management UI.

The concern about scalability (what if we need 50 flags?) is addressed by the fact that integration domains are added at most once per phase, and each addition is a deliberate architectural decision that warrants a schema migration. If the platform ever needs a general-purpose feature flag system with gradual rollouts and per-user targeting, that would be a dedicated phase with its own ADR -- and it would likely be a third-party service, not a custom table. The boolean columns approach and the future third-party approach are both clean end-states; the generic table (Option 2) is an awkward middle ground that provides neither the simplicity of columns nor the power of a real feature flag platform.

The `IntegrationGuardService` provides a single entry point for checking flags: `integrationGuard.requireEnabled(IntegrationDomain.ACCOUNTING)` reads from `OrgSettings` (cached), checks the appropriate boolean field, and throws a 403 `IntegrationDisabledException` if the domain is disabled. This keeps the guard logic in one place rather than scattered across controllers.

**Consequences**:

- Positive:
  - Zero new infrastructure: no new tables, services, or caching layers
  - Type-safe flag access: `orgSettings.isAccountingEnabled()` is compile-time checked
  - Existing `OrgSettings` cache serves flag reads with zero additional latency
  - Frontend receives flags in the existing `/api/org-settings` response -- no additional API call
  - Simple mental model: "flags are settings, settings are in OrgSettings"

- Negative:
  - Adding a 4th integration domain flag requires an `ALTER TABLE` migration + entity change + DTO change (estimated 15 minutes of work per flag)
  - The `OrgSettings` entity continues to grow as a catch-all for per-tenant configuration. If this becomes unwieldy, a future refactoring could extract a `TenantFeatures` sub-entity.

- Neutral:
  - Payment and notification domains do not get feature flags -- they are always available as existing core functionality
  - Plan-tier restrictions on integrations (e.g., "AI requires Pro plan") remain a separate concern handled by `PlanEnforcementService`
