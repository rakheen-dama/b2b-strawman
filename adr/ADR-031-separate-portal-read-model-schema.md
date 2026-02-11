# ADR-031: Separate Portal Read-Model Schema

**Status**: Accepted

**Context**: The customer portal needs to serve denormalized views of projects, documents, comments, and time summaries to external customers. Currently (Phase 4), the portal queries core tenant-schema tables directly via `PortalQueryService`. This works but tightly couples the portal's read path to the core domain's write-optimized schema, making it impossible to optimize read patterns independently or to later extract the portal into a separate service.

The core domain uses schema-per-tenant multitenancy (Pro tier) and shared-schema with `@Filter` (Starter tier). Portal queries must cross tenant boundaries (e.g., looking up a customer's org by magic link token) and aggregate data that spans multiple core tables.

**Options Considered**:

1. **Continue querying core tenant tables directly (current approach)**
   - Pros:
     - Already implemented and working
     - No additional schema or data sync infrastructure
     - Always consistent (reads authoritative data)
   - Cons:
     - Tight coupling — portal read patterns constrained by core write schema
     - Cannot optimize portal queries independently (e.g., denormalized counts, pre-joined views)
     - Extracting the portal to a separate service later requires significant rework
     - Portal queries compete with staff operations for database connections in the tenant schema
     - Cross-schema queries (for shared-schema tenants) add complexity

2. **Dedicated `portal` schema with event-driven projections**
   - Pros:
     - Clean CQRS boundary — portal reads from its own optimized schema
     - Denormalized entities eliminate N+1 queries (e.g., `document_count` on project cards)
     - Portal schema can be extracted to a separate database with minimal code changes
     - Portal queries don't compete with staff operations
     - Events decouple the core domain from portal concerns
   - Cons:
     - Eventual consistency — read models may lag slightly behind writes
     - Additional infrastructure: event handlers, a second DataSource, sync logic
     - More code to maintain (event classes, projection handlers, read-model entities)
     - Requires a resync mechanism for disaster recovery

3. **Materialized views in PostgreSQL**
   - Pros:
     - No application-level sync code — PostgreSQL handles it
     - SQL-level definition of the read model
     - `REFRESH MATERIALIZED VIEW CONCURRENTLY` for updates
   - Cons:
     - Cannot cross schema boundaries (tenant schemas are dynamic)
     - Refresh is all-or-nothing per view — no incremental updates
     - No event-driven refresh — must be triggered by cron or application code
     - Difficult to denormalize across multiple entities (joins in view definition become rigid)
     - Not portable to a separate database in the future

**Decision**: Use a dedicated `portal` schema with event-driven projections (Option 2).

**Rationale**: The portal schema establishes a clean CQRS boundary that aligns with the project's future direction (separate portal deployment). Event-driven projections allow the portal read model to be optimized independently of the core domain schema — denormalized counts, pre-joined fields, and customer-specific projections are straightforward.

The eventual consistency trade-off is acceptable for a customer-facing portal. Customers access the portal infrequently (to check status, download documents), and a sub-second lag between a staff action and its visibility in the portal is imperceptible. The `synced_at` timestamp on read-model entities provides a built-in staleness indicator.

Materialized views (Option 3) cannot cross dynamic tenant schemas and offer no incremental update path, making them unsuitable for this multi-tenant architecture.

The portal schema is intentionally flat (no tenant partitioning, just `org_id` column) — this simplifies the read model and makes future extraction to a separate database trivial: move the schema, point the DataSource, done.

**Consequences**:
- New `portal` schema created in global migration V7
- Second DataSource (`portalDataSource`) and `JdbcClient` configured for portal schema
- Read-model entities (`PortalProject`, `PortalDocument`, `PortalComment`, `PortalProjectSummary`) are JDBC-managed, not JPA
- Portal read models are eventually consistent — staff changes are visible in the portal after event processing (typically < 100ms in-process)
- A resync endpoint (`POST /internal/portal/resync/{orgId}`) is required for recovery from projection failures
- Future service extraction requires moving the `portal` schema to a separate database and swapping the DataSource — no business logic changes needed
