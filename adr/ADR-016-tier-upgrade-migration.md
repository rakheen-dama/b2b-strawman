# ADR-016: Tier Upgrade Data Migration (Starter to Pro)

**Status**: Accepted

**Context**: When a Starter organization upgrades to the Pro plan, its data must be migrated from the shared `tenant_shared` schema to a new dedicated `tenant_<hash>` schema. This migration must be reliable, minimize downtime for the upgrading org, and not affect other organizations in the shared schema.

**Options Considered**:

1. **Synchronous migration in webhook handler** — Perform the entire migration (schema creation, data copy, cutover) within the `plan-sync` webhook handler.
   - Pros: Simple; no background job infrastructure needed.
   - Cons: Webhook handlers should return quickly (Clerk/Svix has a 30-second timeout); migration time depends on data volume; timeout risk for orgs with significant data.

2. **Asynchronous migration with status tracking** — Webhook handler enqueues the migration; a background service processes it with status tracking (`UPGRADING` status on the Organization entity).
   - Pros: Webhook returns immediately; migration can take as long as needed; status is observable.
   - Cons: Requires handling the intermediate `UPGRADING` state (what happens to requests during migration?); more complex state machine.

3. **Synchronous for MVP, async migration later** — Given that Starter orgs have limited data (max 2 members, likely small project/document counts), perform migration synchronously. Add async support if migration time exceeds acceptable thresholds.
   - Pros: Simpler implementation; Starter org data volume is bounded (2 members, limited projects); migration typically completes in < 1 second for small datasets.
   - Cons: Not suitable for large datasets (but Starter tier constraints limit data size).

**Decision**: Synchronous migration with idempotent steps (Option 3). Revisit for async if data volumes grow.

**Rationale**: Starter organizations are constrained to 2 members and are on the free tier, so their data footprint is inherently small. A typical Starter org might have 5-20 projects, 10-50 documents, 2 members, and a handful of project_members — all easily migrated in under 1 second via direct SQL `INSERT INTO ... SELECT`. The synchronous approach avoids the complexity of background job infrastructure and intermediate states.

The migration is implemented as a multi-step, idempotent process in `TenantUpgradeService`:

**Migration Steps**:
```
1. Set Organization.provisioningStatus = IN_PROGRESS
2. Generate dedicated schema name: tenant_<12hex>
3. CREATE SCHEMA IF NOT EXISTS tenant_<hash>
4. Run Flyway tenant migrations V1-V7 against tenant_<hash>
   (V7 adds nullable tenant_id columns — required for Hibernate entity mapping consistency;
    the columns remain NULL in dedicated schemas and are never used)
5. Copy data from tenant_shared to tenant_<hash>:
   - INSERT INTO tenant_<hash>.members (id, clerk_user_id, email, ...) SELECT id, clerk_user_id, email, ... FROM tenant_shared.members WHERE tenant_id = ?
   - INSERT INTO tenant_<hash>.projects (id, name, ...) SELECT id, name, ... FROM tenant_shared.projects WHERE tenant_id = ?
   - INSERT INTO tenant_<hash>.documents (id, project_id, ...) SELECT id, project_id, ... FROM tenant_shared.documents WHERE tenant_id = ?
   - INSERT INTO tenant_<hash>.project_members (id, project_id, ...) SELECT id, project_id, ... FROM tenant_shared.project_members WHERE tenant_id = ?
   (tenant_id column is excluded from the INSERT — defaults to NULL in dedicated schemas)
6. In a single transaction:
   a. UPDATE org_schema_mapping SET schema_name = 'tenant_<hash>' WHERE clerk_org_id = ?
   b. DELETE FROM tenant_shared.project_members WHERE tenant_id = ?
   c. DELETE FROM tenant_shared.documents WHERE tenant_id = ?
   d. DELETE FROM tenant_shared.projects WHERE tenant_id = ?
   e. DELETE FROM tenant_shared.members WHERE tenant_id = ?
7. Invalidate TenantFilter schema cache for this clerkOrgId
8. Set Organization.tier = PRO, provisioningStatus = COMPLETED
```

**Idempotency**: Each step is safe to retry:
- `CREATE SCHEMA IF NOT EXISTS` — no-op if exists.
- Flyway `baselineOnMigrate` — skips already-applied migrations.
- Data copy uses `INSERT ... ON CONFLICT DO NOTHING` or is gated by a "data already copied" check.
- Mapping update and data deletion are in a single transaction — either both happen or neither.
- Cache invalidation is safe to repeat.

**Availability During Migration**: Between step 6 (mapping update) and step 7 (cache invalidation), there is a brief window (< 1 second) where the TenantFilter cache may serve the old mapping (`tenant_shared`). Requests during this window would hit `tenant_shared` but find no data (already deleted in step 6b-e). The response would be an empty result set — not a data leak. The next cache miss loads the new mapping and requests work correctly. For Starter orgs with minimal traffic, this is an acceptable trade-off.

**Rollback**: If migration fails at any point before step 6:
- Data in `tenant_shared` is untouched (copy is additive, not destructive).
- The new schema may partially exist — idempotent steps allow retry.
- Organization remains on `tier = STARTER` and continues to work normally.

If migration fails during step 6 (transaction failure):
- Transaction rolls back: mapping unchanged, shared data intact.
- Organization remains on Starter, fully functional.
- Retry from the beginning is safe due to idempotency.

**Consequences**:
- New `TenantUpgradeService` in the `provisioning` package.
- Uses `migrationDataSource` (direct connection) for DDL and data migration.
- Data migration uses raw SQL via `JdbcTemplate` for performance (no ORM overhead).
- Brief data-less window (< 1s) for the upgrading org during cutover — acceptable for Starter-tier traffic volumes.
- `TenantFilter` cache must support explicit eviction by key (add `evictSchema(String clerkOrgId)` method).
- Downgrade (Pro → Starter) is not implemented in Phase 2. If needed, it would involve the reverse migration (dedicated → shared) with additional complexity around `tenant_id` assignment. This is deferred to Phase 3.
- Future: if data volumes grow beyond what synchronous migration can handle (> 5 seconds), implement async migration with `UPGRADING` status and a polling endpoint for the frontend to show progress.
