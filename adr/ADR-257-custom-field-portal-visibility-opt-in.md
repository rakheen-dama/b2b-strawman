# ADR-257: Custom-Field-Date Portal Visibility — Opt-In

**Status**: Accepted

**Context**:

Phase 48 introduced `FieldDateApproachingEvent` — a scheduler-fired event for custom date fields approaching a configured threshold (e.g. "notify me when a custom 'Contract renewal date' field is within 30 days"). Tenants use this mechanism for an enormous variety of dates: insurance renewals, lease expirations, license renewals, employee probation-end dates, internal follow-up reminders, director rotation dates, and much more. The Phase 48 design was explicitly firm-side — the event drives a firm-side notification; no portal visibility was contemplated.

Phase 68 ships portal deadline visibility and wants to include custom-field-based deadlines. The question is: which custom date fields should show up on the portal? Three extreme positions:

- **All of them** — firm defines a date field, client sees it as a deadline.
- **None of them** — custom-field dates stay firm-side; only first-class deadline sources (filings, court dates) reach the portal.
- **Opt-in** — each `FieldDefinition` gets a boolean flag; only flagged fields promote to portal.

The realistic distribution matters. From surveying current production tenants, most custom date fields are internal operational reminders (probation ends, internal follow-ups, license renewals for staff). A minority are client-facing deadlines that clients legitimately want to see (client-side insurance renewal, client's lease expiry the firm is tracking on their behalf, client contract review date). The ratio is roughly 80/20 internal-to-client.

**Options Considered**:

1. **Opt-in flag on `FieldDefinition`** — New boolean column `FieldDefinition.portalVisibleDeadline`, default `false`. Firm admin toggles it on fields they want clients to see.
   - Pros:
     - Matches the realistic 80/20 distribution — most fields stay internal by default.
     - Firm retains full control; no accidental leakage of internal dates.
     - UI affordance is small: one checkbox on the field-definition settings page.
     - Sync service filters on this flag → signal-over-noise on portal.
   - Cons:
     - Firms must remember to toggle it for fields they DO want clients to see.
     - Schema change (one column); small cost.

2. **All date fields visible by default** — Every custom date field promotes to portal automatically.
   - Pros:
     - Zero config.
     - Matches "trust-the-firm" philosophy.
   - Cons:
     - Floods the portal with internal reminders (probation ends, internal check-ins).
     - Clients confused by fields they don't understand; erodes trust in the deadline surface.
     - Reverse-opt-in (firms individually hiding fields) is more friction than opt-in.

3. **All date fields hidden; no mechanism to surface them** — Custom-field dates stay firm-side; the portal only sees first-class deadline sources.
   - Pros:
     - Simplest: no schema change, no UI toggle.
   - Cons:
     - Loses a legitimate use case (client-facing custom dates like contract reviews).
     - Firms would work around it with custom project-scope hacks.
     - Poorer client experience where applicable.

**Decision**: Option 1 — opt-in flag on `FieldDefinition`, default `false`.

**Rationale**:

**Signal over noise.** The portal deadline surface is valuable only if every row is meaningful to the client. Flooding it with internal firm reminders makes it useless; flagging a subset preserves the signal.

**Default false matches the observed distribution.** 80% of custom date fields are internal-only; defaulting to `false` means 80% of tenants don't need to touch the setting for 80% of their fields. The 20% that want client visibility tick a box once.

**Low-cost UI affordance.** The field-definition settings page already has a modest form (label, type, required, default, help text). Adding one checkbox — "Show as a deadline on the client portal" — is cheap and contextually well-placed.

**Firm remains the privacy arbiter.** The firm decides which dates their clients see. No global heuristic, no default-visibility inversion.

**Consequences**:

- New column `FieldDefinition.portalVisibleDeadline` (tenant migration V110), `BOOLEAN NOT NULL DEFAULT false`.
- Partial index on `(id) WHERE portal_visible_deadline = true` to support the Phase 48 scanner's pre-filter.
- `DeadlinePortalSyncService` gates `FieldDateApproachingEvent` handling on this flag — events for non-flagged fields are ignored for portal purposes (firm-side notifications continue unaffected).
- Firm-side settings UI for custom field definitions gains a new toggle (frontend change under `frontend/app/(app)/org/[slug]/settings/custom-fields/[fieldId]/page.tsx`).
- If a firm toggles a field to visible AFTER dates have been attached, a one-shot backfill is needed. The existing `POST /internal/portal-resync/deadline` endpoint (introduced by Phase 68) handles this.
- Future phase may add per-value portal visibility overrides (field is portal-visible, but this specific row isn't). Not in scope here; the flag lives on the definition, not the value.

**Related**:

- [ADR-256](ADR-256-polymorphic-portal-deadline-view.md) — Custom-field deadlines enter the polymorphic portal deadline table with `source_entity='CUSTOM_FIELD_DATE'` — only when this flag is true.
- [ADR-253](ADR-253-portal-surfaces-as-read-model-extensions.md) — Gating at sync time, not read time.
