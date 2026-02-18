# ADR-015: Org Provisioning Flow Per Tier

**Status**: Accepted

**Note**: Partially superseded by [ADR-064](ADR-064-dedicated-schema-only.md) — single provisioning path for all tiers.

**Context**: The existing provisioning flow (ADR-007) creates a dedicated schema for every new organization. With tiered tenancy (ADR-011), provisioning must branch: Starter orgs are mapped to the pre-existing `tenant_shared` schema (no schema creation), while Pro orgs follow the existing full provisioning path. All new organizations start on the Starter tier by default; Pro provisioning occurs when an organization upgrades (see ADR-016).

**Options Considered**:

1. **Determine tier at creation time from Clerk metadata** — Read the org's plan from Clerk's API during the `organization.created` webhook and provision accordingly.
   - Pros: Single provisioning call handles everything.
   - Cons: At `organization.created` time, the subscription may not yet exist (the user creates the org, then subscribes). Race condition between org creation and subscription. Clerk API call adds latency and fragility to the provisioning path.

2. **Always provision as Starter; upgrade to Pro via subscription webhook** — All orgs start as Starter (mapped to `tenant_shared`). When a `subscription.created`/`subscription.updated` webhook indicates a Pro plan, trigger the upgrade flow (dedicated schema creation + data migration).
   - Pros: No race conditions (org creation and subscription are decoupled); Starter provisioning is instant (single DB insert); Pro provisioning happens only when payment is confirmed; clean separation of concerns.
   - Cons: Requires an upgrade flow (ADR-016) that moves data from shared to dedicated schema; brief period where a Pro subscriber operates on the shared schema until the upgrade webhook is processed.

3. **Provision tier from webhook payload** — Pass a `tier` field in the provisioning request, determined by the webhook handler based on available information.
   - Pros: Explicit tier in the provisioning request; handler can check Clerk API for plan.
   - Cons: Same race condition as Option 1 — plan may not exist at org creation time.

**Decision**: Always provision as Starter; upgrade to Pro via subscription webhook (Option 2).

**Rationale**: The `organization.created` webhook fires when the org is created in Clerk, which happens before any subscription is attached. Attempting to determine the tier at this point introduces a race condition. By defaulting all orgs to Starter, the provisioning flow is simplified to a single INSERT (mapping the org to `tenant_shared`). This completes in < 100ms compared to 2-5s for full schema creation + Flyway.

When the user subscribes to the Pro plan (via `<PricingTable>` or Clerk checkout), Clerk sends a `subscription.created` webhook. The handler detects the plan upgrade and triggers the dedicated schema provisioning + data migration flow. This approach is naturally idempotent: if the webhook fires before any data is created in `tenant_shared`, the data migration step is a no-op (zero rows to copy).

**Starter Provisioning Flow** (< 100ms):
```
1. webhook handler calls POST /internal/orgs/provision
2. Create Organization record (tier = STARTER, status = PENDING)
3. Create OrgSchemaMapping (clerkOrgId → "tenant_shared")
4. Mark Organization as COMPLETED
5. Return 201 Created
```

**Pro Provisioning Flow** (triggered by subscription webhook):
```
1. webhook handler calls POST /internal/orgs/plan-sync
2. Read current Organization: tier = STARTER
3. Trigger TenantUpgradeService (see ADR-016)
4. Create dedicated schema, run Flyway, migrate data, update mapping
5. Update Organization: tier = PRO, status = COMPLETED
```

**Shared Schema Bootstrap**:
The `tenant_shared` schema is created once during application startup by `TenantMigrationRunner`:
1. Check if `tenant_shared` schema exists.
2. If not: `CREATE SCHEMA IF NOT EXISTS tenant_shared`.
3. Run all tenant migrations (V1-V7) against `tenant_shared`.
4. V7 adds `tenant_id` columns, indexes, and RLS policies.

This ensures the shared schema is always ready before any Starter org is provisioned.

**Consequences**:
- `ProvisioningController` request DTO does not need a `tier` field (all orgs start as Starter).
- `TenantProvisioningService.provisionTenant()` simplified: always creates mapping to `"tenant_shared"`.
- New `PlanSyncController` with `POST /internal/orgs/plan-sync` endpoint handles plan changes.
- `TenantMigrationRunner` enhanced to bootstrap `tenant_shared` at startup.
- The `validateSchemaName()` method in `TenantProvisioningService` relaxed to also accept `"tenant_shared"`.
- Brief period (< 5s) between Pro subscription and schema provisioning where the org operates on shared schema. This is acceptable — the Hibernate filter ensures data isolation during this window.
- If a user creates an org and immediately subscribes to Pro, the Starter provisioning and Pro upgrade may overlap. The upgrade flow's idempotency (CREATE SCHEMA IF NOT EXISTS, Flyway baseline) handles this gracefully.
