# ADR-025: Audit Storage Location

**Status**: Accepted

**Context**: Phase 6 introduces an audit trail for domain and security events. The platform uses two tenancy modes — dedicated schemas (`tenant_<hash>`) for Pro-tier orgs and a shared schema (`tenant_shared`) for Starter-tier orgs. The key question is where audit event data should be stored: in a global/shared table accessible across tenants, in each tenant's own schema following the existing entity pattern, or in some hybrid arrangement.

The answer affects data isolation guarantees, query patterns (tenant-scoped vs cross-tenant), operational complexity, and alignment with the existing multi-tenant architecture. Platform operators need cross-tenant visibility for security monitoring; org admins need scoped access to their own audit trail.

**Options Considered**:

1. **Global `audit_events` table in `public` schema** — A single table with an `org_id` column. All tenants' events co-exist in one table. Cross-tenant queries are trivial (`SELECT ... WHERE org_id = ?`).
   - Pros: Simple cross-tenant queries for platform operators. Single table to manage, index, and back up. No per-tenant migration needed for the audit table.
   - Cons: Breaks the tenant isolation model — introduces a shared table that bypasses `TenantFilter`, `TenantFilterTransactionManager`, and Hibernate `@Filter`. Requires a new access pattern (global repository, manual org_id filtering) that doesn't exist elsewhere. Pro-tier tenants expect schema-level isolation; a global table leaks their data into a shared space. Would need custom RLS rules separate from the existing `app.current_tenant` pattern.

2. **Per-tenant `audit_events` table (same as all other entities)** — The `audit_events` table is created by a tenant migration and lives in each tenant's schema (`tenant_shared`, `tenant_abc123`, etc.). Uses the standard `@FilterDef`/`@Filter`/`TenantAware` pattern.
   - Pros: Zero new access patterns — reuses the entire existing multi-tenant infrastructure. Data isolation is automatic (dedicated schemas are physically separate; shared schema uses `@Filter` + RLS). Consistent with every other entity in the system. Backup/restore of a tenant schema includes their audit trail. Pro-tier tenants get schema-level isolation for audit data.
   - Cons: Cross-tenant queries require iterating over schemas (but this pattern already exists in `TenantMigrationRunner`). Starter-tier shared schema may have hot-spot contention on `audit_events` under heavy write load (mitigated by BRIN or partitioned indexes in the future).

3. **Hybrid: domain events in-tenant, security events in global** — Domain events (`task.created`, etc.) go in the tenant's schema. Security events (`security.access_denied`, etc.) go in a global `public.security_events` table.
   - Pros: Security events are easily queried across tenants for platform monitoring. Domain events maintain tenant isolation.
   - Cons: Two storage locations with different access patterns, different schemas, and different query services. Increases cognitive and operational complexity. The boundary between "domain" and "security" events is blurry (is `document.accessed` a domain event or a security event?). Doubles the migration and testing surface area.

4. **Global table with tenant views** — A single global table, but each tenant schema has a view (`CREATE VIEW audit_events AS SELECT * FROM public.audit_events WHERE org_id = current_setting(...)`) that makes it look local.
   - Pros: Single physical table (easy backup, easy cross-tenant queries). Views provide a local-feeling interface.
   - Cons: Views don't participate in Hibernate `@Filter` — would require a custom Hibernate dialect or bypassing the standard entity pattern. `INSERT` through views requires `INSTEAD OF` triggers. Significantly more complex than either Option 1 or Option 2 for marginal benefit. Not a pattern used anywhere in the existing codebase.

**Decision**: Per-tenant `audit_events` table (Option 2).

**Rationale**: The platform's multi-tenant architecture is built on a consistent pattern: every tenant-scoped entity uses `@FilterDef`/`@Filter`, `TenantAware`, `TenantAwareEntityListener`, and RLS. The `TenantFilterTransactionManager` enables Hibernate filters automatically. The `TenantFilter` resolves the schema from the JWT. All tenant data lives in tenant schemas.

Introducing a global table (Options 1, 3, or 4) would create the first exception to this pattern. It would require new infrastructure: a global `DataSource` or connection routing, a non-tenant-scoped repository, manual `org_id` filtering that bypasses Hibernate filters, and separate RLS policies. This exception would need to be explained to every engineer who touches the codebase.

The trade-off is cross-tenant queries. But this trade-off already exists for every other entity — we don't have a global `projects` table for cross-tenant project queries either. Platform operators who need cross-tenant security monitoring will use log forwarding (CloudWatch, OpenSearch) in a future phase, which is the industry-standard approach for multi-tenant security monitoring. The internal endpoint for cross-tenant queries uses the existing `ScopedValue.where()` pattern to iterate over tenant schemas, which is the same pattern `TenantMigrationRunner` uses.

**Consequences**:
- `audit_events` table is created by tenant migration `V14__create_audit_events.sql`.
- `AuditEvent` entity follows the standard `@FilterDef`/`@Filter`/`TenantAware` pattern.
- Cross-tenant audit queries go through internal endpoints that iterate over schemas (same pattern as `TenantMigrationRunner`).
- Future log forwarding (CloudWatch, OpenSearch, ELK) will provide a centralized view for platform-wide security monitoring.
- Pro-tier tenants get the same physical data isolation for audit data as they do for business data.
- No new access patterns, connection providers, or repository types are introduced.
