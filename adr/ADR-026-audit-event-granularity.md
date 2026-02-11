# ADR-026: Audit Event Granularity

**Status**: Accepted

**Context**: The audit trail needs to record domain and security events across all tenant-scoped entities. The question is how granular to make the event model: should we log every field change, only key status transitions, or some middle ground? Additionally, we need to decide how event types are represented — as database-level enums, Java enums, or free-form strings with naming conventions.

The answer affects storage volume, query flexibility, the ease of adding new event types as the domain grows, and the usefulness of the audit trail for compliance and debugging.

**Options Considered**:

1. **Full change-data-capture (CDC)** — Store the complete before/after state of every entity on every mutation. The `details` column contains the full entity JSON at both states.
   - Pros: Maximum information — any question about "what was the state at time T?" is answerable. Can reconstruct full entity history.
   - Cons: Enormous storage overhead (every save writes a full entity snapshot). Includes fields that rarely matter for audit purposes (e.g., `updated_at`). Risk of capturing sensitive fields (customer `id_number`, document contents) unless explicit exclusion lists are maintained. Difficult to query meaningfully — "what changed?" requires diffing two JSON blobs.

2. **Key-field-only delta capture** — Store only the fields that changed, and only for fields that are audit-relevant. The `details` JSONB contains `{"field": {"from": "old", "to": "new"}}` for changed fields, plus select context fields (e.g., `project_id` for entity identity).
   - Pros: Compact storage — typically 100-500 bytes per event. Directly answerable: "status changed from OPEN to DONE". No risk of accidentally logging sensitive fields if the whitelist is explicit. Easy to query with JSONB operators. Sufficient for compliance ("who changed the status and when?").
   - Cons: Cannot reconstruct full entity state at a point in time without replaying all events. Requires per-entity knowledge of which fields are audit-relevant.

3. **Event-type-only (no details)** — Log only the event type (`task.created`, `task.updated`) with the entity ID and actor. No `details` column.
   - Pros: Minimal storage. Simplest implementation — no need to compute deltas. Fast writes.
   - Cons: Not useful for compliance — "the task was updated" says nothing about *what* changed. Cannot answer "who changed the status?" without cross-referencing with application logs. Fails the basic audit question: "what happened?"

4. **Structured event subtypes with typed payloads** — Define a Java class hierarchy for event payloads: `TaskCreatedDetails`, `TaskStatusChangedDetails`, `DocumentAccessedDetails`, etc. Each event type has a strongly-typed payload.
   - Pros: Type safety at compile time. IDE support for constructing events. Explicit schema per event type.
   - Cons: Explosion of classes as entities and event types grow (currently ~30 event types → 30 detail classes). Schema changes to event types require class changes and potential migration of stored JSON. Over-engineering for a JSONB column that's primarily queried by humans and simple filters.

**Decision**: Key-field-only delta capture (Option 2) with free-form string event types.

**Rationale**: The primary consumers of audit data are compliance officers and org admins asking questions like "who changed this task's status?" and "when was this document accessed?". Option 2 directly answers these questions with minimal storage overhead.

Event types are stored as strings following the `{entity_type}.{action}` convention (e.g., `task.claimed`, `document.accessed`). This convention is enforced by the `AuditEventBuilder` in application code, not by database constraints. Using strings instead of enums means new event types (e.g., when Phase 7 adds new entities) don't require schema changes or enum migrations.

The `details` JSONB uses a simple convention:
- Scalar context: `{"field": "value"}` — e.g., `{"project_id": "uuid"}` on `task.created`.
- Delta: `{"field": {"from": "old", "to": "new"}}` — e.g., `{"status": {"from": "OPEN", "to": "DONE"}}`.
- The fields captured per event type are documented in the architecture doc (Section 12.3.1) and implemented in each service method. There is no generic reflection-based differ.

**Consequences**:
- Each service method explicitly constructs its `details` map, choosing which fields to include. This is intentional — it prevents accidental logging of sensitive fields.
- New event types are added by calling `AuditService.log()` with a new `eventType` string. No schema migration or enum change needed.
- The `details` column is nullable — events like `customer.archived` may not need any detail beyond the entity ID.
- Future dashboards can filter by event type prefix (`task.%` for all task events) using the `idx_audit_type_time` index.
- Full entity state reconstruction requires querying the entity directly — the audit trail is not a full event sourcing log.
- Storage estimate: ~30 audited operations × average 200 bytes/event × average 100 ops/day/org = ~600 KB/org/day = ~220 MB/org/year. Well within Neon Postgres free-tier limits for Starter orgs.
