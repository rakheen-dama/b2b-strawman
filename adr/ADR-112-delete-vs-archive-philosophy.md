# ADR-112: Delete vs Archive Philosophy

**Status**: Accepted

**Context**:

Projects can currently be hard-deleted by any OWNER-role member. This was acceptable when projects were simple containers, but Phase 29 introduces lifecycle states and cross-entity relationships that make hard deletion dangerous once a project accumulates operational data. A deleted project leaves orphaned references: time entries that reference a nonexistent `projectId`, audit events that record actions against a vanished entity, invoice line items linked to deleted project tasks, and budget records with no parent. The portal read-model may also hold stale references to deleted projects.

The same concern applies to tasks (which can have time entries) and customers (which can have projects, invoices, and retainers). The system needs a consistent philosophy for when hard deletion is allowed and when archival (or lifecycle transition) should be used instead. This philosophy must balance data integrity and compliance requirements against the user's legitimate desire to clean up mistakes.

**Options Considered**:

1. **Always allow delete (cascade)** -- Delete the entity and cascade-delete all children. Project deletion removes all its tasks, time entries, documents, budget records, and invoice line items. Customer deletion removes all linked projects (and their children).
   - Pros:
     - Simplest implementation: `ON DELETE CASCADE` at the database level handles everything
     - Users can fully undo mistakes: created a project by accident, delete it cleanly
     - No "zombie" data cluttering the system
   - Cons:
     - Destroys financial records: billed time entries, sent invoices, and payment records would vanish, violating basic accounting principles
     - Audit trail becomes incomplete: audit events reference entity IDs that no longer exist, making compliance reporting unreliable
     - Portal read-model references break: portal contacts may have bookmarked or received emails linking to now-deleted projects or documents
     - Irreversible data loss: no undo for cascade deletes in a schema-per-tenant architecture where each tenant's schema is the single source of truth
     - The `AuditEvent` entity stores `entityId` as a UUID -- a cascade delete orphans every audit record that references the deleted entity

2. **Restrict delete, promote archive** -- Allow hard deletion only when the entity has no operational data (no tasks, time entries, invoices, documents for projects; no time entries for tasks). Once operational data exists, require archival or lifecycle transition instead. Return 409 with a descriptive error message guiding the user to the correct action.
   - Pros:
     - Preserves financial and audit integrity: time entries, invoices, and audit events always reference valid entities
     - Users can still delete genuine mistakes (empty projects, tasks with no time logged) -- the restriction is not blanket
     - Archive serves as a soft-delete equivalent: ARCHIVED projects are hidden from default views and read-only, achieving the same outcome as deletion from the user's perspective
     - Portal read-model references remain valid: archived entities still exist and can be displayed as "archived" in the portal
     - Matches professional accounting software patterns: QuickBooks, Xero, and FreshBooks all restrict deletion of entities with financial history
   - Cons:
     - Users cannot permanently remove data they no longer want (even archived data consumes storage and appears in "All" views)
     - The restriction check must query multiple child tables to determine if operational data exists, adding complexity to the delete endpoint
     - Different entities have different deletion criteria (project checks tasks/time/invoices/docs; task checks time entries; customer checks projects/invoices/retainers) -- the rules are entity-specific

3. **Soft delete everything** -- Add a `deletedAt` timestamp column to every entity. "Deleted" entities are excluded from queries via a global Hibernate filter or `@Where` clause. No hard deletes ever.
   - Pros:
     - Maximum data retention: nothing is ever lost, all data can be restored
     - Uniform approach: every entity follows the same pattern, no entity-specific rules
     - Supports "undo delete" functionality without special handling
   - Cons:
     - Adds a `deletedAt` column and filter condition to every entity and every query in the codebase -- significant cross-cutting complexity
     - In a schema-per-tenant architecture (ADR-064), the Hibernate filter must be applied per-schema and interact correctly with the tenant schema resolution. The existing `@Filter`/`@FilterDef` annotations were deliberately removed in Phase 13 to simplify the dedicated-schema model -- reintroducing a global filter contradicts that decision
     - Unique constraints become conditional: a project name must be unique among non-deleted projects, requiring partial indexes
     - "Soft-deleted" entities still participate in relationship checks unless every foreign key query also filters by `deletedAt IS NULL`
     - Over-engineering for the common case: most entities that users want to "delete" are either empty (safe to hard delete) or have operational data (should be archived, not deleted)

**Decision**: Option 2 -- Restrict delete and promote archive for entities with operational data.

**Rationale**:

The core principle is that operational data must be preserved. Time entries represent billable work that may have been invoiced. Invoices represent financial commitments sent to clients. Audit events reference entity IDs as part of the compliance record. Destroying any of these records by cascade-deleting a parent entity violates basic data integrity requirements for a professional services platform.

Hard deletion remains available for genuine mistakes: a project created with the wrong name and no tasks can be deleted immediately. A task added to the wrong project with no time logged can be deleted. This covers the "oops" use case without compromising data integrity. The moment operational data exists, the entity has participated in real business processes and must be preserved.

Soft delete (Option 3) was rejected because Phase 13 (ADR-064) deliberately removed Hibernate `@Filter` annotations to simplify the dedicated-schema architecture. Reintroducing a global `deletedAt` filter contradicts that architectural direction and adds query complexity to every repository method in the codebase. Archive achieves the same user-facing outcome (entity hidden from default views, read-only) without the cross-cutting filter pollution.

The 409 response with a descriptive message ("Archive this project instead. It has 12 tasks and 47 time entries.") guides users toward the correct action. This is a better UX than silently cascade-deleting important data or showing an opaque "delete failed" error.

**Consequences**:

- Project deletion is allowed only when the project has zero tasks, time entries, invoices, and documents. Otherwise, the endpoint returns 409 with a message directing the user to archive
- Task deletion is allowed only when the task has zero time entries. Otherwise, 409 directing the user to cancel the task
- Customer deletion is blocked when linked projects, invoices, or retainers exist. 409 directs the user to the offboarding lifecycle
- COMPLETED and ARCHIVED projects cannot be deleted regardless of children -- they are lifecycle-terminal states where deletion makes no semantic sense
- Empty entities (projects with no children, tasks with no time) can be hard-deleted freely, covering the "created by mistake" use case
- The delete check requires querying child tables (e.g., `taskRepository.countByProjectId()`, `timeEntryRepository.countByProjectId()`), adding a small overhead to delete operations -- acceptable since deletes are infrequent
- Audit events that reference archived entities remain valid and queryable
- No `deletedAt` column or global Hibernate filter is introduced, maintaining the simplification achieved in Phase 13 (ADR-064)
- Related: [ADR-111](ADR-111-project-completion-semantics.md) (ARCHIVED state as the lifecycle destination for projects that cannot be deleted), [ADR-064](ADR-064-dedicated-schema-only.md) (dedicated schema simplification -- no global filters)
