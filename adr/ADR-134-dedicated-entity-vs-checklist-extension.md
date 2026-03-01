# ADR-134: Dedicated Entity vs. Checklist Extension — Dedicated InformationRequest Entities

**Status**: Accepted

**Context**:

Phase 34 introduces a Client Information Requests system that allows firms to send structured document collection requests to clients via the customer portal. The platform already has a checklist system (Phase 14) with `ChecklistTemplate`, `ChecklistInstance`, and `ChecklistInstanceItem` entities that support document attachment and completion tracking. The question is whether to extend this existing checklist system to support external-facing information requests, or to introduce dedicated entities.

The checklist system was designed for internal compliance tracking — the firm verifies that FICA documents have been collected, that onboarding steps are complete, etc. The audience is firm members. Information requests have a fundamentally different audience (clients via the portal), a different lifecycle (submit/accept/reject review cycle vs. simple check/uncheck), and different visibility requirements (portal read-model sync, presigned upload URLs, rejection reasons). Additionally, information requests need features that have no analogue in checklists: request numbering, reminder scheduling, template-based instantiation with ad-hoc item support, and text response storage.

**Options Considered**:

1. **Extend ChecklistInstance with portal visibility** -- Add a `portalVisible` flag, `portalContactId`, response type fields, and rejection workflow to the existing checklist entities.
   - Pros:
     - Reuses existing entity infrastructure (templates, instances, items)
     - Single set of tables for all "list of items to complete" use cases
     - Fewer new entities to maintain
   - Cons:
     - Checklist entities accumulate nullable fields for two distinct use cases (internal vs. external)
     - Review cycle (submit/accept/reject) has no analogue in checklists — would require status field overloading
     - Portal read-model sync complexity increases (must distinguish portal-visible checklists from internal ones)
     - Reminder scheduler must filter by portal visibility
     - Template system diverges (checklist templates have compliance-specific fields like `customerType`, `autoInstantiate`)
     - Risk of breaking existing checklist behavior with schema changes

2. **New dedicated entities (InformationRequest, RequestItem, RequestTemplate)** -- Purpose-built entities with their own lifecycle, status machine, and portal integration.
   - Pros:
     - Clean separation of concerns — internal checklists vs. external requests
     - Each entity has exactly the fields it needs, no nullable field bloat
     - Independent evolution — changes to information requests do not risk checklist regressions
     - Simpler portal read-model sync (dedicated tables, clear event boundaries)
     - Review cycle (PENDING/SUBMITTED/ACCEPTED/REJECTED) is a first-class status machine
     - Request numbering, reminder scheduling, and template instantiation are self-contained
   - Cons:
     - More entities and tables to maintain
     - Some conceptual overlap ("list of items to complete")
     - Template duplication (request templates look similar to checklist templates structurally)

3. **Polymorphic base with shared Item model** -- Abstract base entity with `InternalChecklist` and `ExternalRequest` subtypes sharing a common item structure.
   - Pros:
     - Shared item structure reduces duplication
     - Type hierarchy makes the relationship explicit
   - Cons:
     - JPA inheritance adds complexity (SINGLE_TABLE or JOINED strategy, discriminator columns)
     - Shared item model still needs type-specific fields (rejection reason, document reference, text response)
     - Hibernate inheritance is a known source of query complexity and N+1 issues
     - Over-engineers the relationship — the items have more differences than similarities
     - Existing checklist entities would need migration to the new hierarchy

**Decision**: Option 2 -- New dedicated entities.

**Rationale**:

The fundamental distinction is audience and lifecycle. Internal checklists are operated by firm members: an item is either done or not done, with optional document attachment. Information requests are operated by clients: an item goes through a submit-review cycle with rejection reasons and re-submission. These are different state machines with different actors, and attempting to unify them creates a "lowest common denominator" model that serves neither use case well.

The portal integration requirement further strengthens the case for separation. Information requests need portal read-model sync, presigned upload URLs from the portal API, and email notifications to portal contacts. None of these exist in the checklist system. Adding them as conditional behavior (when `portalVisible = true`) would create a fragile dual-personality entity that is hard to reason about and test.

The maintenance cost of dedicated entities is low in this codebase. Post-Phase 13, new entities are plain `@Entity` + `JpaRepository` with no multitenancy boilerplate. The conceptual overlap with checklists is structural only (both are "list of items"), not behavioral. The same argument would apply to tasks (also "list of items") — shared structure does not imply shared implementation.

Related: [ADR-061](ADR-061-checklist-first-class-entities.md) (checklist as first-class entities), [ADR-064](ADR-064-dedicated-schema-only.md) (simplified entity model post-Phase 13).

**Consequences**:

- Five new tables in the tenant schema: `request_templates`, `request_template_items`, `information_requests`, `request_items`, `request_counters`
- Two new tables in the portal schema: `portal_requests`, `portal_request_items`
- New `informationrequest` package with its own entity/repository/service/controller stack
- Checklist system is unaffected — no migration or behavioral changes
- Future phases that need "checklist-like" features (e.g., onboarding steps for portal contacts) can evaluate whether to extend information requests or create another dedicated entity set based on the same principle: separate entities for separate audiences and lifecycles
