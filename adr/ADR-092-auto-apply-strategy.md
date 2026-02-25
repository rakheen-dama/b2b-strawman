# ADR-092: Auto-Apply Field Group Strategy

**Status**: Accepted

**Context**:

Phase 23 introduces `autoApply` on `FieldGroup` — a boolean flag that, when true, causes the group to be automatically applied to every new entity of its `entityType` at creation time. A gap arises when an admin toggles `autoApply` from `false` to `true` on an existing field group: entities that were created before the toggle do not have the group in their `applied_field_groups` JSONB array. The question is how to retroactively apply the group to those existing entities.

The `applied_field_groups` column is a JSONB array of group UUIDs stored on each entity row. Currently, customers, projects, and tasks have this column (added in V25). Phase 23 adds the same column to invoices via the V38 migration. PostgreSQL provides the `@>` containment operator for testing JSONB array membership and the `||` operator for appending to a JSONB array. The schema-per-tenant architecture means that each tenant schema holds at most a few thousand entities of any single type — there is no shared table containing rows from multiple tenants. Note: the retroactive apply for `INVOICE` entities is only relevant after the V38 migration adds the `applied_field_groups` column to the `invoices` table.

**Options Considered**:

1. **Synchronous JSONB array append UPDATE query (chosen)** -- When `autoApply` is toggled to `true`, immediately execute a single `UPDATE` statement that appends the group ID to `applied_field_groups` on all entities of that type that do not already have it:
   ```sql
   UPDATE customers
   SET applied_field_groups = applied_field_groups || '["<group-id>"]'::jsonb
   WHERE NOT applied_field_groups @> '["<group-id>"]'::jsonb
   ```
   The same pattern is applied per entity type (customers, projects, tasks, invoices) in a single service call from `FieldGroupService.toggleAutoApply()`.
   - Pros:
     - Immediate consistency: by the time the toggle API response returns, all existing entities carry the group
     - Atomic: the `UPDATE` runs inside the same transaction as the `FieldGroup.autoApply = true` write
     - Simple implementation: reuses existing JSONB operators already used elsewhere in the field definition layer
     - No new infrastructure: no scheduler, no job queue, no background thread pool, no retry table
     - Data volume is bounded: schema-per-tenant ensures the affected rows are scoped to a single tenant's data, which is at most a few thousand rows of any entity type
   - Cons:
     - Slight latency on the toggle request for large tenants (thousands of entities): the UPDATE may take a few seconds rather than milliseconds
     - The toggle endpoint becomes a write-heavy operation (multiple entity-type UPDATEs in one request); however, `autoApply` toggling is an infrequent admin action — not on any hot path

2. **Explicit admin "Apply to All Existing" button** -- Do not retroactively apply the group automatically. Instead, surface a secondary action in the settings UI that lets admins explicitly trigger the backfill when they are ready.
   - Pros:
     - Full admin control over when the backfill runs
     - No surprise latency on the toggle request itself
     - No risk of unexpected side effects (e.g., triggering downstream validation rules on entities the admin didn't intend to touch)
   - Cons:
     - Violates the principle of least surprise: if a group is marked "auto-apply," users expect it to be universally applied — including to records that predate the toggle
     - Easy to forget: admins who toggle the flag may not notice or remember to press the separate backfill button, leaving the system in an inconsistent state indefinitely
     - Adds UI complexity (a second action with its own loading state, confirmation dialog, and error handling) for a use case that is fundamentally one operation
     - The mental model of "auto-apply means applied everywhere" is broken until the button is pressed

3. **Background job / async processing** -- Queue an asynchronous job when `autoApply` is toggled. A background worker (e.g., Spring Batch step, or a simple `@Async` task) processes entities in batches, appending the group ID to each entity's `applied_field_groups` array.
   - Pros:
     - No request latency: the toggle API returns immediately and the backfill happens in the background
     - Handles arbitrarily large datasets without blocking the HTTP thread
     - Batch processing allows rate-limiting the database writes to avoid lock contention on other operations
   - Cons:
     - Eventual consistency: there is a window (seconds to minutes) during which `autoApply = true` but many entities do not yet have the group — any query for "entities with this group" returns incomplete results
     - Requires new infrastructure: Spring Batch (or a custom job runner), a job-status table, retry/dead-letter handling, and monitoring for stuck jobs
     - Significantly more complex error handling: what happens if the job fails halfway through? How are partial failures surfaced to the admin?
     - Overkill for the data volumes imposed by schema-per-tenant: the problem that async batch processing solves (millions of rows) does not exist in this architecture

**Decision**: Synchronous JSONB array append UPDATE query (Option 1).

**Rationale**: The schema-per-tenant architecture is the decisive factor. Because each tenant schema holds only that tenant's data, the number of rows affected by the retroactive UPDATE is bounded by the size of a single tenant's entity set — realistically a few hundred to a few thousand rows. A single JSONB containment-filtered UPDATE across that row count completes in milliseconds to low single-digit seconds on PostgreSQL. This is well within acceptable latency for an infrequent admin toggle action.

The synchronous approach provides immediate consistency with zero additional infrastructure. The `applied_field_groups` JSONB array and its `@>` / `||` operators are already established patterns in the field definition layer, so the implementation requires no new abstractions. The atomicity guarantee — both the `FieldGroup.autoApply = true` write and the entity backfill commit together or not at all — eliminates an entire class of partial-state bugs that the async approach would need to handle explicitly.

Option 2 (explicit button) is rejected because it breaks the semantic contract of "auto-apply": an admin who toggles the flag expects the system to handle the consequences, not to require a follow-up action. Option 3 (background job) is rejected as over-engineered for the problem: it adds significant complexity (job runner, retry logic, eventual consistency windows) to solve a data-volume problem that does not exist in this architecture.

**Consequences**:

- Positive:
  - Immediate consistency: the moment `autoApply` is toggled to `true`, all existing entities of the relevant type carry the group — no transitional inconsistency window
  - Zero new infrastructure: no scheduler, job queue, or retry table is required; the implementation is confined to `FieldGroupService.toggleAutoApply()`
  - Reuses existing JSONB patterns (`applied_field_groups`, `@>`, `||`) already present in the field definition layer
  - Atomic: the group flag change and the entity backfill are committed in the same transaction

- Negative:
  - The toggle API request takes longer for tenants with large entity counts (thousands of rows); this is acceptable given that `autoApply` toggling is an infrequent administrative action, not a user-facing or latency-sensitive path
  - The toggle endpoint issues one UPDATE per entity type (up to four: customers, projects, tasks, invoices), making it a write-heavy operation on that request; row-level locking during the UPDATE is brief and isolated to the tenant's schema

- Neutral:
  - New entity creation (customer, project, task, invoice) is a separate code path: creation services call `FieldGroupService.getAutoApplyGroups(entityType)` and apply them before returning; this ADR covers only the retroactive backfill on toggle, not the at-creation path
  - If tenant data volumes grow significantly (e.g., a tenant with hundreds of thousands of entities), the approach can be revisited by adding a dedicated migration step or an optional async path; no pre-emptive complexity is warranted now
