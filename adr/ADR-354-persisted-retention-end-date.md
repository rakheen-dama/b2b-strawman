# ADR-354: Per-Matter Persisted `retention_ends_on` Stamped at Close; Re-Close Resets It; Completion No Longer Starts the Clock; the Firm Default Is Real and Editable; the Firm-Wide `retention_policies` Row Is No Longer Seeded at Closure

**Status**: Accepted

**Context**:

Closing the estate matter on Day 75 showed "Retention isn't configured yet" (G-D3). R7 §Q5 found four drifts from [ADR-249](ADR-249-retention-clock-starts-on-closure.md). (1) `org_settings.legal_matter_retention_years` has `DEFAULT 5` (`V1__baseline.sql:452`) but rows are created by `new OrgSettings(...)` at several call sites, none of which sets the field, so Hibernate writes NULL and bypasses the default (`OrgSettingsService.java:120-141` is the no-row fallback response builder, not an insert); `setLegalMatterRetentionYears` has zero callers, `DataProtectionSettingsRequest` and `SettingsResponse` do not carry it, and no settings UI exists. (2) The read side (`ProjectService.computeRetentionEndsOn:153-170`) deliberately returns null unless the raw setting is present, while closure uses the *effective* default of 5 (`DataProtectionSettings.getEffectiveLegalMatterRetentionYears:127-131`) — the same tenant disagrees with itself. (3) `retentionEndsAt` is computed into `CloseMatterResponse` (`MatterClosureService.java:319-326`) and never persisted. (4) `Project.complete` stamps `retentionClockStartedAt` on first completion (`Project.java:253-265`, pinned by `RetentionClockIntegrationTest:67,86`) although ADR-249 rejected completion as the anchor; `closeMatter` only sets it if null, so the walk's anchor was the earlier Complete. Closure also inserts a **firm-wide** `retention_policies` row (`MATTER`, `years*365`, `MATTER_CLOSED`) because the Phase 50 table is `UNIQUE (record_type, trigger_event)` with no `entity_id` — a policy, not a per-matter date. Founder decision D10: persist at close, re-close resets, completion does not start the clock, 5-year default editable under Firm › Compliance.

**Options Considered**:

1. **Compute-on-read: `retentionEndsOn = retentionClockStartedAt + effectiveYears` using the same effective default as closure; no schema change.**
   - Pros:
     - Smallest PR.
     - Fixes the contradiction once the setting is exposed.
   - Cons:
     - The end date floats when the firm changes the policy later — a matter closed under a 5-year rule silently becomes 7.
     - Not auditable.
     - Contradicts ADR-249's "fresh row with a new anchor" model.
     - Leaves completion as the anchor unless separately changed.
2. **Persist `projects.retention_ends_on` at close from `closed_at + years`; reopen clears it; re-close re-stamps; completion no longer stamps the clock (CHOSEN).**
   - Pros:
     - A frozen per-matter obligation that survives policy changes, is auditable, and is what ADR-249 literally specified.
     - The Overview card reads one column.
     - One tenant V8 column.
     - The read endpoint stops needing the setting at all for closed matters.
   - Cons:
     - `RetentionClockIntegrationTest` rewrite.
     - A decision on the firm-wide `retention_policies` row.
3. **Persist at close and run a reconcile job that re-derives from the current policy.**
   - Pros:
     - Both frozen and "current".
   - Cons:
     - Two sources of truth with a job to arbitrate.
     - A scheduled writer for a date that should change only on a legal act (close/reopen).
     - No firm has asked for policy changes to be retroactive.

**Decision**: Option 2 — tenant V8 (`V8__phase88_retention_comment_attachments.sql`) adds `projects.retention_ends_on date NULL`; `Project.closeMatter` sets `retentionClockStartedAt = closedAt` (always, not only when null) and `retentionEndsOn = closedAt + effectiveYears`; `reopenMatter` clears `retentionEndsOn` (the started-at stays for audit); `Project.complete` no longer touches the clock; `legal_matter_retention_years` is seeded `5` by the `DataProtectionSettings.legalMatterRetentionYears` field initialiser on every new org-settings row (all tenants — the product is legal-only since [ADR-330](ADR-330-archive-tag-not-fork.md)) and V8 backfills existing NULL rows to 5; the setting is exposed in `DataProtectionSettingsRequest` / `SettingsResponse` and a "Matter retention period (years)" field on Firm › Compliance; `MatterResponse.retentionEndsOn` reads the column only — tenant V8 backfills `retention_ends_on` for already-CLOSED matters from `closed_at + legal_matter_retention_years` (default 5 when the setting is NULL); the read side has no fallback for CLOSED matters (one deliberate exception: the reopen retention-elapsed guard falls back to a computed date only for a legacy CLOSED row whose `retention_ends_on` is NULL); COMPLETED-not-closed matters show no date; `MatterClosureService` stops inserting the firm-wide `retention_policies` `MATTER` row.

**Rationale**:

1. **ADR-249 was right and was never implemented as written.** Closure is the legal act; the per-matter anchor and a fresh anchor on re-close were its consequences. The Phase 50 table could not hold a per-matter row, so 489C/GAP-L-96 parked the anchor on `projects.retention_clock_started_at` and seeded a firm-wide policy instead — the half-measure this ADR completes by giving the matter its own end date.
2. **A persisted date is the only auditable answer** to "when may this file be destroyed?"; a design partner's compliance officer expects a date on the file, not a formula that depends on today's settings.
3. **The firm-wide row is superseded, not deleted** (a decision beyond D10): existing `retention_policies` rows stay and the Data-protection table keeps listing them as firm policy; nothing new is seeded at closure because the matter now carries the obligation. The Phase 50 sweep is unchanged in this phase; `retention_ends_on` is the input for any future matter sweep.
4. **Seeding the default in the embeddable's field initialiser** fixes the NULL-bypass at its cause (every `new OrgSettings(...)` site) so closure and the read endpoint can never disagree again; exposing the setting makes the card's "your firm's setting overrides" copy true.

**Consequences**:

- Positive:
  - Closing on Day 75 shows "Scheduled deletion 2031-10-22" on the matter (G-D3).
  - `day-75-85-closure.spec.ts:297` is un-fixme'd.
  - A matter completed on Day 60 and closed on Day 75 anchors on Day 75.
- Negative:
  - `RetentionClockIntegrationTest:67,86` (`setsOnFirstCompletion`) is rewritten to "sets on close, resets on re-close, untouched by complete".
  - `MatterClosureServiceIntegrationTest:258` (`retentionEndsAt`) and `:709/:740` (MATTER policy seed) change — the seed assertions are removed.
  - `ProjectStatusTest` re-run.
  - `OrgSettingsSchemaSnapshotTest` is unaffected (the column already exists) but `OrgSettingsEmbeddableNullReloadTest:112` (NULL reload) gains the seeded-5 case.
  - `DataProtectionSettingsRequest` DTO and the `settings-sections.test.tsx` / `retention-card.test.tsx` vitests change.
  - V8 is shared with the comment-attachments section (627H) while it is unapplied.
  - Matters closed before V8 are backfilled by the migration (`closed_at + years`, default 5); there is no read-time fallback, and a COMPLETED-but-not-closed matter shows no date.
  - The re-walk closes fresh matters.
- Related:
  - [ADR-249](ADR-249-retention-clock-starts-on-closure.md) (realigned), [ADR-248](ADR-248-matter-closure-distinct-state-with-gates.md) (CLOSED state and gates), `tasks/phase88-research/R7-matter-detail-closure.md` §Q5, `tasks/phase88-gap-register.md` D10.
