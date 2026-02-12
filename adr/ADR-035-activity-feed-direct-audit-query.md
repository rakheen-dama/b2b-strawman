# ADR-035: Activity Feed via Direct Audit Query

**Status**: Accepted

**Context**: Phase 6.5 adds a project activity feed that shows team members a human-readable timeline of everything that has happened in a project: task creation, document uploads, comments, status changes, member additions, time entries, and more. The existing `audit_events` table (Phase 6, V14 migration) already captures all domain mutations with rich metadata -- actor, action, entity type, JSONB details, and timestamps.

The question is whether to query `audit_events` directly for the activity feed, or to maintain a separate materialized data store optimized for activity timeline queries. A secondary question is how to filter audit events by project, since the `audit_events` table does not have a first-class `project_id` column -- project context is stored inside the JSONB `details` column (e.g., `details->>'project_id'`).

**Options Considered**:

1. **Separate `activity_feed` table (materialized from audit events)**
   - Pros:
     - Schema optimized for activity queries (project_id as indexed column, pre-formatted messages)
     - Decoupled from audit schema evolution
     - Can include denormalized display data (actor name, entity title) for zero-join reads
   - Cons:
     - Data duplication -- every audit event is written twice (audit + activity)
     - Must keep in sync -- missed events or bugs create divergence
     - Additional migration, entity, repository, and write path to maintain
     - Audit events are the source of truth; a derived table adds indirection

2. **Direct query on `audit_events` with service-layer message formatting**
   - Pros:
     - Single source of truth -- no data duplication or sync concerns
     - No additional table, entity, or write path
     - Formatting logic is in a testable service class (not in SQL or stored procedures)
     - Leverages existing audit infrastructure (tenant isolation, append-only guarantees)
   - Cons:
     - Query performance depends on audit table size (mitigated by indexing)
     - Formatting happens on every read (no pre-computed messages)
     - Requires a way to filter by project_id on a table that lacks the column

3. **PostgreSQL materialized view refreshed periodically**
   - Pros:
     - Pre-computed query results with project_id extracted from JSONB
     - No application-level sync code
     - Can be refreshed concurrently (no read-lock during refresh)
   - Cons:
     - Stale data between refreshes (activity feed shows outdated information)
     - Materialized view refresh is a full recomputation -- expensive as table grows
     - Cannot be refreshed per-tenant; a single refresh covers the entire table
     - Adds PostgreSQL-specific operational complexity (refresh scheduling, monitoring)

**Sub-Decision: Project ID Filtering**

For Option 2 (chosen), the `audit_events` table needs to support efficient `WHERE project_id = ?` queries. Two sub-options:

**2a. Add `project_id UUID` column to `audit_events` via ALTER TABLE**
   - Pros: Clean indexed column, standard B-tree index, simple WHERE clause
   - Cons: Modifies the append-only audit schema (ALTER TABLE is allowed -- only UPDATE is blocked by trigger); requires backfill of existing rows from JSONB; adds a column that duplicates data already in JSONB `details`

**2b. Expression index on `details->>'project_id'` (btree)**
   - Pros: No schema change to the audit table; index is maintained automatically; query uses `WHERE details->>'project_id' = ?`
   - Cons: Expression index is slightly less intuitive for developers; JSONB text extraction returns VARCHAR (must cast UUID comparison); not all audit events have a `project_id` in details (NULL-safe needed)

**Decision**: Direct query on `audit_events` with service-layer message formatting (Option 2), using an expression index on `details->>'project_id'` (Sub-option 2b).

**Rationale**: The activity feed is a read view of data that already exists in `audit_events`. Creating a separate table (Option 1) or materialized view (Option 3) introduces data duplication, sync complexity, and operational burden -- all for a feature that is fundamentally "show me what happened recently in this project."

Direct querying keeps a single source of truth. The service-layer formatter (`ActivityMessageFormatter`) maps `(eventType, entityType)` pairs to human-readable templates. Actor names are resolved by batch-loading from the `members` table using the `actorId` stored on each `AuditEvent`; entity titles come from the JSONB `details` map. This formatting logic is straightforward, testable, and does not require pre-computation.

For project filtering, the expression index (Sub-option 2b) was chosen over adding a column (2a) because:
1. The `audit_events` table is intentionally append-only with a minimal, stable schema. Adding a `project_id` column sets a precedent for adding more query-specific columns over time.
2. The expression index is automatically maintained by PostgreSQL and requires zero application changes -- audit events already store `project_id` in their JSONB `details`.
3. The performance of `WHERE (details->>'project_id') = :projectId` with a btree expression index is equivalent to a column-based index for equality lookups.
4. Not all audit events are project-scoped (e.g., org-level events like member sync). A nullable column would need special handling; the JSONB extraction naturally returns NULL for events without a `project_id` key.

**Performance considerations**: At moderate scale (10K-100K audit events per tenant), the expression index with pagination (`LIMIT 20 OFFSET ...`) provides sub-millisecond query times. If a tenant exceeds 1M+ audit events and activity feed performance degrades, the migration path is: (a) add a retention policy for old audit events, or (b) introduce a materialized activity table as a cache layer.

**Consequences**:
- V15 migration adds an expression index: `CREATE INDEX idx_audit_project ON audit_events ((details->>'project_id'))` alongside the comments table creation
- `ActivityService` queries `audit_events` directly using `AuditEventRepository` (or a new repository method)
- `ActivityMessageFormatter` maps `(eventType, entityType)` to templates; unknown combinations produce a generic "{actor} performed {action} on {entityType}"
- Activity feed response includes formatted messages, actor names, entity references, and timestamps
- No additional table, entity class, or write-path code for the activity feed
- Services that log audit events must include `project_id` in the `details` map for the event to appear in project activity feeds (some services already include it; others require enrichment -- see "Audit Event Enrichment" prerequisite in the Phase 6.5 architecture document)
- Future migration to a materialized table (if needed for scale) requires only adding a new event listener that writes to the materialized table -- the API and formatter remain unchanged
