# ADR-078: Portal Read-Model Extension for Invoices and Tasks

**Status**: Accepted
**Date**: 2026-02-20

**Context**:

The customer portal needs to display invoice data (list, detail, line items, PDF download) and task data (names, statuses, assignees) for client-facing projects. The portal backend (Phase 7) established a dedicated `portal` schema with denormalized read-model tables (`portal_projects`, `portal_documents`, `portal_comments`, `portal_project_summaries`) synced via domain events through `PortalEventHandler`. This read-model serves as a security boundary -- portal queries never touch tenant schemas directly.

The question is whether to extend this read-model pattern for invoices and tasks, or to take a different approach for these new data types.

Invoice data has an additional constraint: **visibility is status-gated**. Only invoices with status SENT or later should be visible to clients. Draft and approved invoices are internal. This filtering must be enforced consistently.

Task data requires a **minimal projection**: clients should see task names, statuses, and assignee names, but not descriptions, time estimates, billable flags, or other internal details.

**Options Considered**:

1. **Extend portal read-model with event-driven sync (chosen)** -- Add `portal_invoices`, `portal_invoice_lines`, and `portal_tasks` tables to the existing `portal` schema. Sync data from tenant schemas via new portal domain events and `PortalEventHandler` additions. Follow the exact same pattern as the existing project/document/comment sync.
   - Pros: Consistent with the Phase 7 architecture -- same patterns, same security boundary, same event-driven approach; visibility filtering happens at write-time (only SENT+ invoices are synced), not at query-time; portal queries remain simple SELECTs against the portal schema; no cross-schema access from portal endpoints; minimal task projection is enforced at sync time (only name, status, assignee synced); read-model can be independently optimized with portal-specific indexes.
   - Cons: Eventual consistency -- there is a brief delay between a status change in the tenant schema and the read-model update; sync logic must handle edge cases (invoice voided after being sent, task deleted while sync is in flight); more event handler code to maintain; data duplication between tenant and portal schemas.

2. **Direct tenant schema queries via portal endpoints** -- Portal invoice and task endpoints query the tenant schema directly (using the tenant scope from the portal JWT), applying visibility filters at query time.
   - Pros: No data duplication; real-time data (no sync delay); simpler initial implementation (no event handlers, no read-model tables); fewer moving parts.
   - Cons: Breaks the portal security boundary established in Phase 7 (portal endpoints would now access tenant schemas, mixing concerns); visibility filtering must be applied in every query (risk of forgetting the status filter on a new endpoint); a portal bug could potentially leak tenant data beyond the customer's scope; portal query performance coupled to tenant schema load; inconsistent with the existing portal architecture (projects and documents use the read-model, but invoices would bypass it).

3. **GraphQL federation layer** -- Introduce a GraphQL gateway that federates data from multiple sources (tenant schema for invoices/tasks, portal schema for projects/documents), providing a unified query interface for the portal frontend.
   - Pros: Flexible query composition; clients can fetch exactly the data they need in one request; decouples portal from backend schema changes; industry-standard for multi-source data aggregation.
   - Cons: Massive infrastructure overhead for the portal's simple needs (list/detail pages); introduces a new technology (GraphQL) to the stack; requires schema stitching between tenant and portal schemas; does not solve the security boundary problem (the gateway still needs tenant schema access); performance overhead from query parsing and resolution; debugging complexity; overkill for 6 endpoints.

**Decision**: Option 1 -- extend the portal read-model with event-driven sync.

**Rationale**:

The portal read-model is a security boundary, not just a performance optimization. When [ADR-031](ADR-031-separate-portal-read-model-schema.md) established the portal schema in Phase 7, the core principle was that portal queries never touch tenant schemas. This prevents a portal bug from leaking internal data -- the read-model contains only the data clients should see, and nothing else. Extending this boundary to invoices and tasks maintains that guarantee.

The status-gating requirement for invoices makes the read-model approach particularly valuable. By syncing invoices to the read-model only when they reach SENT status, the visibility filter is enforced at the data level, not the query level. There is no risk of a developer writing a new portal endpoint that forgets the `WHERE status IN ('SENT', 'PAID')` filter -- draft invoices simply do not exist in the portal schema. The same principle applies to tasks: only the minimal projection (name, status, assignee name) is synced, so internal details like descriptions, billable flags, and time estimates cannot leak even if a portal endpoint has a bug.

The eventual consistency trade-off is acceptable. Portal users are not monitoring real-time status changes -- they receive an email notification when an invoice is sent or a task is updated, then visit the portal to view it. A sub-second sync delay is invisible to this usage pattern.

The event handler additions follow the established `PortalEventHandler` pattern exactly. The existing codebase has 8 event handlers for projects, documents, comments, and summaries. Adding 2 more for invoices and tasks is incremental work, not a new pattern.

**Consequences**:

- Positive:
  - Portal security boundary maintained -- portal queries never access tenant schemas
  - Invoice visibility enforced at sync time -- draft invoices never enter the read-model
  - Task data minimized at sync time -- internal details never enter the read-model
  - Consistent with Phase 7 architecture -- same patterns, easier to maintain
  - Portal-specific indexes can be optimized independently of tenant schemas

- Negative:
  - Eventual consistency between tenant schema and portal read-model (mitigated by sub-second `AFTER_COMMIT` event handling and portal usage patterns)
  - Data duplication for invoices and tasks (mitigated by the read-model containing only the minimal projection needed for display)
  - Additional event handler code to maintain (mitigated by following the established `PortalEventHandler` pattern, which keeps each handler small and focused)
  - V8 global migration adds 3 tables to the portal schema (mitigated by `IF NOT EXISTS` idempotency)
