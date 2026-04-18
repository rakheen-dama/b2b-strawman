# ADR-256: Polymorphic `portal_deadline_view` Over Per-Source Tables

**Status**: Accepted

**Context**:

The Phase 68 portal deadline surface aggregates deadlines from several firm-side sources:

- **Filing statuses** (Phase 51 — `FilingStatus`) for accounting-za tenants doing VAT / PAYE / tax return submissions.
- **Court dates** (Phase 55 — planned; event wiring will ship later) for legal-za tenants.
- **Prescription trackers** (Phase 55 — planned) for legal-za tenants.
- **Custom field dates** (Phase 48 — `FieldDateApproachingEvent`) for any tenant that opts a custom date field into portal visibility via the new `FieldDefinition.portalVisibleDeadline` flag ([ADR-257](ADR-257-custom-field-portal-visibility-opt-in.md)).

The firm-side sources are structurally different — a filing status has a period + form name, a court date has a hearing type + venue, a custom field date has whatever shape the field definition imposes. The portal view, however, is uniform: a single list sorted by `due_date`, grouped by week, with a side-panel detail. No UI affordance today distinguishes between source types beyond an icon or a badge. That means the portal's data access pattern is "all deadlines for this customer, filtered by date range + status" — not "all filings" or "all court dates".

The design question is whether to model this as one polymorphic table or as N per-source tables that the controller unions at query time. The Phase 68 requirements proposed one polymorphic table; this ADR records the decision and the trade-offs.

**Options Considered**:

1. **Polymorphic single table `portal_deadline_view`** — One row per deadline regardless of source. Discriminator column `source_entity` (`FILING_STATUS`, `COURT_DATE`, `PRESCRIPTION_TRACKER`, `CUSTOM_FIELD_DATE`) + `source_id` (UUID of the firm-side row). Uniform columns: `label`, `due_date`, `status`, `description_sanitised`.
   - Pros:
     - Portal read queries are trivial — one `SELECT ... FROM portal_deadline_view WHERE customer_id = ? AND due_date BETWEEN ? AND ?`.
     - One controller endpoint serves every profile + every source.
     - Adding a new source in a future phase is mechanical — add a sync handler, publish to the same table.
     - The "visual shape" of the portal UI perfectly mirrors the data shape — no impedance mismatch.
   - Cons:
     - Source-specific fields (court venue, filing period, etc.) have to be denormalised into `label` + `description_sanitised` at sync time. Lossy if the UI later wants to expose structured fields per source.
     - `UNIQUE (source_entity, source_id)` is needed for idempotent upserts — slightly more index surface.
     - All sync handlers write to the same table — potential for contention at scale, though Postgres handles this fine at current tenant sizes.

2. **One table per source** — `portal_filing_deadline`, `portal_court_date`, `portal_prescription_deadline`, `portal_custom_field_deadline`. Controller unions at query time.
   - Pros:
     - Source-specific columns can be modelled naturally (court venue as a column, not buried in `label`).
     - Each sync handler owns its own table.
   - Cons:
     - Four tables instead of one — four sets of indexes, four sets of migrations.
     - Controller query becomes a `UNION ALL` across four tables for every read — awkward to paginate, awkward to sort by `due_date`.
     - Adding a new source means a new table + migration + controller change.
     - The portal UI doesn't differentiate between sources anyway — the schema complexity buys nothing user-visible.

3. **View/virtual table over source tables** — `CREATE VIEW portal_deadline_view AS SELECT ... UNION ALL ...` reading directly from firm-side `FilingStatus`/`CourtDate` tables.
   - Pros:
     - No storage duplication.
     - Always fresh.
   - Cons:
     - Violates the read-model isolation principle ([ADR-031](ADR-031-separate-portal-read-model-schema.md)) — views on a cross-schema source are exactly what "separate data source" is meant to prevent.
     - Cross-schema views in Postgres tenancy setups (schema-per-tenant) are unwieldy.
     - Availability of the view depends on every tenant schema being up.

**Decision**: Option 1 — polymorphic single table `portal_deadline_view`, with `(source_entity, source_id)` as the idempotency key.

**Rationale**:

**Portal UI is uniform.** The deadline list has no per-source layout. All rows render the same way; the discriminator, if shown at all, is an icon. A uniform table matches a uniform UI.

**One controller, one read path.** Paginating + filtering across the UNION of four tables is painful. Against a single table, it's trivial SQL. The controller stays under 100 LOC.

**New sources plug in cleanly.** When Phase 55 ships court-date events, the implementation is "add a handler that writes to `portal_deadline_view` with `source_entity='COURT_DATE'`". No schema migration. Same for Phase 65+ pack-introduced deadlines.

**Denormalisation cost is acceptable.** The portal UI only needs label + due date + status + description. Source-specific structured fields (court venue, filing period) live firm-side; when the portal wants to render them, it joins via `source_id` back to the source table (but only in the deadline-detail side-panel, which is a secondary path). This can also be done by widening `portal_deadline_view` later — not gated by this ADR.

**Consequences**:

- Migration `V19__portal_vertical_parity.sql` creates one `portal_deadline_view` table with `CHECK (source_entity IN (...))` + `UNIQUE (source_entity, source_id)`.
- `DeadlinePortalSyncService` has one upsert method; callers differentiate by event type in the `@EventListener` handlers and pass the appropriate `deadline_type` + `source_entity` values.
- Adding a new deadline source in a future phase requires exactly: (a) an event handler that invokes the existing upsert with new enum values, (b) updating the `CHECK` constraint if the new `source_entity` value is outside the current list.
- If a future UI needs to render court-venue separately from filing-period, the detail endpoint will join back to the firm-side source using `(source_entity, source_id)`. No schema change required unless the fetched data exceeds the portal's acceptable boundary.
- Phase 68 documents `FILING_STATUS` (not `FILING_SCHEDULE`) as the source_entity value for accounting deadlines, reflecting the actual Phase 51 implementation (`FilingStatus` is the row, not the non-existent `FilingSchedule`).

**Related**:

- [ADR-253](ADR-253-portal-surfaces-as-read-model-extensions.md) — Read-model pattern baseline.
- [ADR-257](ADR-257-custom-field-portal-visibility-opt-in.md) — Custom-field deadlines enter this table only when opted in.
- [ADR-198](ADR-198-phase51-deadlines-model.md) — `FilingStatus` as the Phase 51 primitive.
