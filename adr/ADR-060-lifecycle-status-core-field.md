# ADR-060: Lifecycle Status as Core Entity Field

**Status**: Accepted

**Context**: Phase 13 introduces a compliance lifecycle for customers (`PROSPECT` → `ONBOARDING` → `ACTIVE` → `DORMANT` → `OFFBOARDED`). This lifecycle status gates platform actions (e.g., blocking invoice creation during onboarding, making offboarded customers read-only) and drives dashboard queries (e.g., filtering by onboarding status). The question is how to model this lifecycle status on the Customer entity: as a core entity column, as a custom field (Phase 11's FieldDefinition/JSONB system), or as a separate lifecycle tracking table.

The answer affects query performance (WHERE clauses on indexed columns vs. JSONB path queries), code semantics (is lifecycle status structural or metadata?), guard implementation (type-safe enums vs. string value extraction from JSONB), and the relationship to the existing `status` column (ACTIVE/ARCHIVED for soft-delete).

**Options Considered**:

1. **Core entity column on Customer table** — Add `lifecycle_status VARCHAR(20) NOT NULL DEFAULT 'PROSPECT'` as a direct column on the Customer entity, alongside `lifecycle_status_changed_at`, `lifecycle_status_changed_by`, and `offboarded_at` timestamp columns.
   - Pros:
     - Lifecycle status drives platform behaviour. It is used in guards (`CustomerLifecycleGuard.requireActionPermitted()`) to gate mutations (blocking invoice creation during onboarding, preventing new records for offboarded customers), and in queries to filter dashboard views (all onboarding customers, all dormant customers). Core behaviour = core column.
     - Query performance: `WHERE lifecycle_status = 'ONBOARDING'` with a standard B-tree index is fast and simple. JSONB path queries (`WHERE custom_fields->'lifecycle_status'->>'value' = 'ONBOARDING'`) require GIN indexes and are slower, especially with OR conditions and tenant filtering.
     - Type safety: Java enum (`LifecycleStatus.ONBOARDING`) provides compile-time validation and IDE autocomplete. String extraction from JSONB does not.
     - Clear semantics: lifecycle status is not metadata that admins customize — it is a structural field that the platform defines and enforces. The state machine (which transitions are valid, which guards fire) is code logic, not user configuration.
     - Audit trail is simpler: `lifecycle_status_changed_at` and `lifecycle_status_changed_by` are first-class columns, queryable without JSONB extraction. This supports compliance auditing (when was this customer activated? who offboarded them?).
     - The existing `status` column (ACTIVE/ARCHIVED) serves a different purpose (soft-delete visibility). Two columns coexist cleanly: `status` controls whether the record is visible, `lifecycle_status` controls which actions are permitted.
   - Cons:
     - Adds four columns to the Customer table. Schema change (migration V29) required.
     - Lifecycle status is not configurable via FieldDefinition — the platform owns the semantics. This is intentional, but it means admins cannot add custom lifecycle stages (e.g., "PENDING_APPROVAL"). If this becomes a requirement, a separate "custom lifecycle" field can be added as a custom field — the core lifecycle status remains structural.

2. **Custom field via Phase 11's FieldDefinition/JSONB system** — Define lifecycle status as a FieldDefinition with type `SINGLE_SELECT` and options `[PROSPECT, ONBOARDING, ACTIVE, DORMANT, OFFBOARDED]`. Store the value in the Customer `custom_fields` JSONB column.
   - Pros:
     - No schema change required — reuses existing `custom_fields` column.
     - Lifecycle status appears in the same UI as other custom fields, which is conceptually consistent (both are customer properties).
     - If an org wants to customize the lifecycle stages (different names, additional stages), they can edit the FieldDefinition options. This is more flexible than a hardcoded enum.
   - Cons:
     - Lifecycle status is not metadata — it drives platform behaviour. The state machine (valid transitions, guards that fire) is code logic, not user configuration. Storing it in `custom_fields` suggests it is informational, when it is actually behavioural.
     - Query performance: filtering customers by lifecycle status requires JSONB path query (`WHERE custom_fields->'lifecycle_status'->>'value' = 'ONBOARDING'`). This is slower than a typed column (even with a GIN index) and harder to optimize.
     - Guard logic must extract the value from JSONB: `customer.getCustomFields().get("lifecycle_status").get("value").asText()`. This is fragile (null checks, casting) and non-type-safe. No compile-time validation that the field exists or that the value is a valid enum.
     - Audit trail is harder: tracking when the lifecycle status changed requires parsing `AuditEvent` details (JSONB before/after diffs) or maintaining a separate `lifecycle_status_changed_at` field, which duplicates state and is complex to keep in sync.
     - Semantically wrong: custom fields are org-specific metadata that the platform does not interpret. Lifecycle status is platform-defined behaviour that the platform enforces. The two are fundamentally different.
     - Flexibility is a bug, not a feature: allowing orgs to rename lifecycle stages ("ONBOARDING" → "Verification") or add custom stages breaks the guard logic. The platform expects specific enum values to know which actions to block. Custom fields enable configuration that the platform cannot support.

3. **Separate lifecycle tracking table (one-to-one with Customer)** — Create a `customer_lifecycle` table with `customer_id FK`, `status`, `changed_at`, `changed_by`. Join to Customer for queries.
   - Pros:
     - Lifecycle concerns are isolated in a separate table. If Phase 13 is later removed or replaced, the lifecycle table can be dropped without touching the Customer table.
     - Historical tracking is easier: the lifecycle table could store all past statuses (one-to-many), not just the current one. This provides a built-in audit log of lifecycle transitions.
   - Cons:
     - Adds a join to every query that filters by lifecycle status (`JOIN customer_lifecycle ON c.id = cl.customer_id WHERE cl.status = 'ONBOARDING'`). This is slower and more complex than a direct column.
     - The lifecycle status is conceptually part of the Customer identity (a customer **is** onboarding, a customer **is** offboarded). A one-to-one join to express a core property is over-engineered.
     - For the one-to-one case (current lifecycle status only), the separate table provides no benefit over a column. If historical tracking is needed, audit events (Phase 6 infrastructure) already record `CUSTOMER_STATUS_CHANGED` events with before/after state. No need for a separate lifecycle audit table.
     - Guard logic must perform the join to check status: `repository.findByCustomerIdWithLifecycle()` instead of `repository.findById()`. This adds boilerplate and forgettability (easy to forget the join, get a LazyInitializationException).

**Decision**: Core entity column on Customer table (Option 1).

**Rationale**: Lifecycle status drives platform behaviour — it is used in guards, queries, and dashboard filters. This makes it a structural field, not metadata. Core behaviour belongs in core columns.

Query performance is significantly better with a typed column and B-tree index than with JSONB path queries. The guard implementation is simpler and type-safe with a Java enum (`LifecycleStatus`) than with string extraction from JSONB. Audit trail columns (`lifecycle_status_changed_at`, `lifecycle_status_changed_by`) are first-class fields that support compliance queries without JSONB parsing.

The coexistence of `status` (ACTIVE/ARCHIVED) and `lifecycle_status` (PROSPECT/ONBOARDING/ACTIVE/DORMANT/OFFBOARDED) is intentional. They serve different purposes: `status` is the soft-delete mechanism (is the record visible?), while `lifecycle_status` is the compliance lifecycle (which actions are permitted?). Two orthogonal concerns, two columns.

Custom fields (Option 2) were rejected because lifecycle status is not org-specific metadata — it is platform-defined behaviour. The state machine is code logic, not user configuration. Storing it in `custom_fields` is semantically wrong and enables dangerous configuration (admins renaming or adding stages that break guard logic).

The separate table (Option 3) was rejected because lifecycle status is part of the Customer identity, not a separate concern. A one-to-one join adds complexity without benefit. Historical tracking is already provided by audit events (Phase 6 infrastructure) — no need for a dedicated lifecycle audit table.

**Consequences**:
- `Customer.lifecycleStatus` is a `VARCHAR(20) NOT NULL DEFAULT 'PROSPECT'` column with a Java enum (`LifecycleStatus`).
- Three additional columns: `lifecycle_status_changed_at TIMESTAMPTZ`, `lifecycle_status_changed_by UUID`, `offboarded_at TIMESTAMPTZ` (used as the retention period trigger date).
- Migration V29 adds these columns. Existing customers are backfilled to `lifecycle_status = 'ACTIVE'` (they are already in use, presumably past onboarding). New customers created after the migration default to `PROSPECT`.
- `CustomerLifecycleGuard` service provides `requireActionPermitted(Customer, LifecycleAction)` and `requireTransitionValid(Customer, targetStatus, notes)` methods. Guards are enforced at the service layer before mutations.
- Dashboard queries filter by lifecycle status using `WHERE lifecycle_status = ?` with a B-tree index (`idx_customers_lifecycle_status`). Shared-schema queries use a composite index (`idx_customers_tenant_lifecycle` on `tenant_id, lifecycle_status`).
- The state machine (valid transitions, guard rules) is documented in section 13.3 of the Phase 13 architecture doc. It is code logic, not database constraints — the database allows any string value in the column, but the service layer enforces enum values and valid transitions.
- Lifecycle status is NOT a custom field. It does not appear in the custom fields UI. It is a core platform field with its own dedicated UI for lifecycle transitions (lifecycle status dropdown, transition reason modal).
