# ADR-261: Audit Severity Derived at Read Time, Not Persisted

**Status**: Accepted

**Context**:

Phase 69 introduces an `AuditSeverity` classification (`INFO | NOTICE | WARNING | CRITICAL`) and an `AuditEventGroup` classification (`SECURITY | COMPLIANCE | FINANCIAL | DATA | STANDARD`) for every audit event. Severity drives the row-level pill colour, the Sensitive filter preset, and the dashboard widget's "show me CRITICAL+WARNING in the last 7 days" query. Group powers the Compliance / Security / Financial filter presets.

The `audit_events` table predates this classification. Events have been written since [Phase 6](../architecture/phase6-audit-compliance-foundations.md); there are tens of thousands of historical rows in production tenants. The table is `@Immutable` ([ADR-028](ADR-028-audit-integrity-approach.md)), append-only, with a DB trigger (`audit_events_no_update`) that rejects every UPDATE statement at the database level. This immutability is load-bearing — it is what makes the audit log trustworthy as a forensic record.

Two architectural questions follow. Where do `severity` and `group` live? And when are they computed?

The shape of `severity`/`group` is itself stable across most events: `security.login.failure` has been WARNING since the day it was written, and will be WARNING tomorrow. But the classification can evolve. If a future Phase 71 decides that `security.login.failure` should be CRITICAL after three consecutive failures, the change must be applicable retroactively to historical rows — without breaking immutability.

**Options Considered**:

1. **Backfill + add `severity` + `group` columns.** Add `severity` and `group` columns to `audit_events`. Write a global migration (claiming the next two consecutive global slots — e.g. V23 + V24) that backfills every historical row by running each row's `eventType` through the registry once at migration time. Update the writer path to populate the columns at insert. Drop the `@Immutable` annotation on the read path? No — the columns can be set at insert and the trigger keeps blocking UPDATEs.
   - Pros:
     - Severity and group are first-class columns. Indexes on them make the Sensitive filter preset and the dashboard widget queries trivial — `WHERE severity IN ('WARNING', 'CRITICAL') ORDER BY occurred_at DESC LIMIT 5` is a textbook indexed query.
     - The read path doesn't need to walk the registry on every row; the DB has the answer.
     - Historical rows are classified once, at backfill, and the answer is durable.
   - Cons:
     - The backfill itself is a one-shot, locked-in classification. A future change to the registry (Phase 71 deciding `security.login.failure` is CRITICAL after three failures, or that `trust.transaction.rejected` should be CRITICAL rather than WARNING) requires *another* migration to re-backfill the affected rows. Backfills on a large `audit_events` table on a busy tenant are slow and can lock writes.
     - The classification of historical rows would be "what we thought severity meant on the day of the backfill" — not "what severity means today." Over time, the column drifts from the registry.
     - DB triggers reject UPDATEs on `audit_events`, so re-backfilling requires either dropping the trigger temporarily or using `INSERT ... ON CONFLICT` patterns that bypass it — both compromise the integrity invariant the trigger exists to enforce.
     - The classification is policy. Policy lives in code; persisting policy to a column couples the policy lifecycle to the data lifecycle.

2. **Read-time derivation via registry (CHOSEN).** Severity and group are computed by `AuditEventTypeRegistry.resolve(eventType)` at every read. The registry is in-code (a static list of `AuditEventTypeMetadata` records). No column changes; no migration; no backfill.
   - Pros:
     - Zero migrations. The phase ships without touching `audit_events`.
     - Classification is always consistent with the *current* registry — historical rows automatically pick up new classifications when the registry changes. No backfill needed.
     - `@Immutable` is preserved — the entity does not gain new persisted fields.
     - The registry is small (~30 entries covering prefixes; longest-prefix-wins resolver). A read-time lookup is microseconds; the DB never sees the resolution.
     - Policy stays in code, where it belongs. A registry change is a code review, not a data migration.
   - Cons:
     - Severity-filtered queries cannot use a DB index on `severity`. The implementation pattern (§12.3.5 of the architecture doc) is: at query time, walk the registry to compute the set of `eventType` strings (and prefix patterns) whose severity is in the requested set, and add `event_type IN (:exact) OR event_type LIKE ANY(:prefix)` to the existing filter query. This works because the registry is small and `eventType` is already indexed (or amenable to a low-cardinality index).
     - The frontend cannot compute severity from a JSON-only response — it needs the registry to render the pill. Solution: ship a `/api/audit-events/metadata` endpoint that returns the catalogue once per session.
     - Two callers (DB query pre-flight + frontend pill rendering) both walk the registry. The single source of truth (`AuditEventTypeRegistry`) prevents drift.

3. **Materialised view that computes severity.** Keep `audit_events` as-is. Create a `audit_events_with_severity` materialised view (or a regular view with a CASE statement) that joins to a registry table.
   - Pros:
     - Reads via the view get severity for free.
     - Refresh is centralised — the materialised view is recomputed on a cadence.
   - Cons:
     - Materialised views need refreshing. A stale view shows stale severity until refresh; a real-time view (regular VIEW with CASE) re-computes severity on every read against the underlying table — no better than read-time derivation.
     - A registry *table* would require a migration, defeating the "zero migration" goal.
     - The view becomes a parallel data structure that any future audit query needs to be aware of.
     - Materialised views in Postgres don't honour `search_path` cleanly across schema-per-tenant — refresh logic would need per-tenant orchestration, which is significantly more complex than a code-level registry.

**Decision**: Option 2 — read-time derivation via registry.

**Rationale**:

The classification is policy, not data. `audit_events` is the data; "this event type is sensitive enough to warrant a coloured pill" is a policy expression. Persisting policy as a column couples the policy lifecycle to the data lifecycle — and `audit_events` rows are immutable by design ([ADR-028](ADR-028-audit-integrity-approach.md)). Coupling immutable data to evolving policy means policy changes have to fight the immutability invariant, either by bending it (re-backfill) or by living with stale classifications (Option 1's gradual drift).

Read-time derivation keeps the lifecycles separate. Policy lives in `AuditEventTypeRegistry`, expressed in code, version-controlled, code-reviewed, and changeable in a single PR. Data lives in `audit_events`, immutable, append-only, queried fresh through the policy filter every read. The DB does not need to know what severity means today; the application computes it on the fly.

The performance concern (Option 1's "indexes on severity!" appeal) is illusory at the volumes Kazi operates at. The severity-filter pre-flight (§12.3.5 of the architecture doc) walks a 30-entry registry once per request — sub-millisecond — and produces an `event_type IN (...) OR LIKE ANY(...)` predicate that the existing `eventType` index serves efficiently. A 100k-event tenant on a year's range with `severities=WARNING,CRITICAL` returns in tens of milliseconds without a dedicated severity index.

The materialised view (Option 3) introduces a parallel structure that every other audit query has to know about, and per-tenant refresh in a schema-per-tenant model is a non-trivial orchestration. The simplicity of a code-level registry is a meaningful architectural win.

Phase 69 ships zero migrations precisely because of this decision. That has knock-on benefits: faster builds, smaller blast radius for the phase, no Flyway high-water-mark coordination with adjacent phases.

**Consequences**:

- Positive:
  - Zero migrations in this phase. `db/migration/global/` and `db/migration/tenant/` retain their current high-water marks.
  - Severity classifications can evolve in a single PR — change `AuditEventTypeRegistry`, ship. No backfill, no DB downtime, no data drift.
  - `@Immutable` invariant on `AuditEvent` is preserved entirely. The entity does not change at all.
  - Historical rows automatically pick up new classifications as the registry evolves. A row written in Phase 6 reads with Phase 71's classification if Phase 71 reclassifies the event type.
  - The same registry powers the backend severity filter, the frontend pill rendering, the facet enrichment, and the dashboard widget — single source of truth.

- Negative:
  - The severity filter cannot use a dedicated DB index on `severity`. The implementation must walk the registry to compute an `eventType` set, then filter on that set in the DB. This adds a small per-request overhead (registry walk in microseconds; query predicate slightly larger).
  - The frontend must know the registry to render pills correctly for paginated rows. Mitigation: a single `/api/audit-events/metadata` endpoint returns the catalogue once per session and the frontend caches it.
  - There is a subtle prefix-vs-exact resolution bug to handle: `matter.closure.*` is NOTICE, but `matter.closure.override_used` is CRITICAL. The pre-flight resolver re-runs `resolve()` for each candidate to confirm the final severity matches — see §12.3.5 of the architecture doc.

- Neutral:
  - If a future phase decides per-tenant overrides of the classification are needed (a tenant wants `member.role_changed` to be CRITICAL rather than WARNING), the registry can grow a tenant-keyed lookup or migrate to a table — but that is a separate ADR and a separate phase. The current ADR scopes to a global, code-defined registry.
  - The registry is a public API in spirit — the frontend reads it, downstream consumers (export columns) embed it. Changes to the registry are user-visible; classification reviews are part of phase work.

- Related: [ADR-028](ADR-028-audit-integrity-approach.md) (`@Immutable` + DB trigger — the immutability invariant this ADR preserves), [ADR-029](ADR-029-audit-logging-abstraction.md) (`AuditService` abstraction — where the severity filter pre-flight lives), [ADR-259](ADR-259-audit-ui-read-only-no-write-changes.md) (no-write-changes scope — this ADR is the technical mechanism that lets Phase 69 ship without migrations), [ADR-260](ADR-260-audit-generic-diff-over-event-templates-v1.md) (the metadata registry — same registry serves both label and severity).
