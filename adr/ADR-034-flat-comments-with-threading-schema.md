# ADR-034: Flat Comments with Threading-Ready Schema

**Status**: Accepted

**Context**: Phase 6.5 introduces a comments system that allows project members to annotate tasks and documents. The user experience requirements call for a simple, chronological comment list on each entity, but the product roadmap includes threaded/nested replies as a future enhancement (e.g., replying to a specific comment to start a sub-conversation).

Threaded comments add significant complexity to both the backend (recursive query patterns, depth limits, sort ordering within threads) and the frontend (nested rendering, collapse/expand state, reply-to context). For a B2B project management tool, the initial use case is straightforward: team members leave timestamped notes on tasks and documents. Full threading is a "nice to have" that should not delay the initial release.

The key design question is whether to reserve space in the schema for threading now (avoiding a future migration) or keep the schema minimal and add threading columns later.

**Options Considered**:

1. **Flat comments only (no parent_id column)**
   - Pros:
     - Simplest schema and query patterns
     - No unused columns in the table
     - Clear signal to developers that threading is not supported
   - Cons:
     - Adding `parent_id` later requires an `ALTER TABLE` migration across all tenant schemas
     - Migration must be coordinated with application deployment (new code expects the column)
     - For Starter-tier tenants in `tenant_shared`, the migration runs against a shared table used by all orgs

2. **Flat comments with `parent_id` nullable column (reserved for threading)**
   - Pros:
     - Zero-migration path to threading later -- only service logic and UI changes needed
     - Column is nullable, so all existing rows are valid without backfill
     - No overhead -- nullable UUID column with no index costs nothing when unused
     - Schema communicates intent: "threading is planned"
   - Cons:
     - Unused column in the table until threading ships
     - Developers might prematurely build threading logic against the column
     - Slightly larger schema surface to document

3. **Full threaded comments now (parent_id + depth + materialized path)**
   - Pros:
     - Feature-complete from day one
     - No future schema changes needed
   - Cons:
     - Significant additional complexity in service layer (recursive fetching, depth limits, re-threading on delete)
     - Frontend must handle nested rendering, collapse/expand, "replying to" context
     - Over-engineers Phase 6.5 scope -- delays delivery without clear user demand
     - Materialized path (`/root/child1/child2`) or closure table adds maintenance burden

**Decision**: Flat comments with `parent_id` nullable column (Option 2).

**Rationale**: Option 2 delivers the simplest possible user experience (flat, chronological comments) while reserving the schema structure needed for threading. The `parent_id` column is a single nullable UUID that costs nothing when unused -- no index, no constraint, no application logic references it in Phase 6.5. All Phase 6.5 queries ignore `parent_id` entirely.

When threading is needed, the migration path is purely application-level:
1. Add a `parent_id` foreign key index to the existing column.
2. Update `CommentService` to support `parentId` in create requests and to fetch comments as a tree.
3. Update the frontend `CommentSection` to render nested replies.

No `ALTER TABLE`, no backfill, no coordinated migration across tenant schemas. This is the lowest-risk approach for a multi-tenant system where schema migrations must run across potentially hundreds of tenant schemas.

Option 1 was rejected because the `ALTER TABLE ADD COLUMN` migration, while straightforward in PostgreSQL (nullable columns are metadata-only), still requires a coordinated deployment across all tenant schemas. Option 3 was rejected as premature -- threading adds frontend and backend complexity that is not justified by current user needs.

**Consequences**:
- The `comments` table includes `parent_id UUID` (nullable, no FK constraint, no index) from day one
- Phase 6.5 code never reads or writes `parent_id` -- it is always NULL
- The `CommentService.createComment()` method does not accept a `parentId` parameter in Phase 6.5
- Frontend `CommentSection` renders a flat list ordered by `created_at ASC`
- Future threading work (estimated 1-2 slices) requires only service + UI changes, zero migrations
- Documentation explicitly notes `parent_id` as "reserved for future threading" to prevent premature use
