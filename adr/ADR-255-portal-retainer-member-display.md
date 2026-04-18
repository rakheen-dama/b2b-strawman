# ADR-255: Retainer Member Display on Portal

**Status**: Accepted

**Context**:

Phase 68 ships the portal retainer consumption list — a client-facing view of "who logged what time against my retainer". Every consumption entry carries the name of the firm member who did the work. The question is: what should that name look like?

Three practical axes drive this question. **Privacy**: junior firm members may not want their full legal names shown to every client. **Transparency**: clients paying for a retainer legitimately want to know who's working on their matter — the answer cannot be "nobody knows". **Billing norms**: SA consulting firms typically disclose the consultant's full name on invoices; SA law firms more commonly disclose the attorney's initials + role title. One default cannot serve both verticals well.

The fallback implication is important too: if the default is wrong, firms will overcompensate (set it to `ANONYMISED` reflexively) or ignore it (leave `FULL_NAME` on by accident). We want the default to be "the safest thing that still meets transparency expectations".

**Options Considered**:

1. **Default `FIRST_NAME_ROLE`, configurable via `OrgSettings.portalRetainerMemberDisplay`** — Enum with four values: `FULL_NAME`, `FIRST_NAME_ROLE`, `ROLE_ONLY`, `ANONYMISED`. Default is `FIRST_NAME_ROLE` (e.g. `"Alice (Attorney)"`).
   - Pros:
     - Balances privacy (no last names by default) with transparency (first name + role is identifiable enough for a client to say "I worked with Alice on this").
     - Four discrete values cover the realistic spectrum — more granular than most firms need, simpler than a free-text format string.
     - Setting lives on `OrgSettings` — one firm-wide choice, not per-matter.
     - Default can be overridden per firm without code change.
   - Cons:
     - "First name" assumption fails for very common first names in a firm (two Alices).
     - Role mapping depends on `Member.roleLabel()` being well-populated — which may not be true for every tenant.

2. **Default `FULL_NAME`** — Same enum, different default.
   - Pros:
     - Most transparent.
     - Matches consulting-vertical billing norms out of the box.
   - Cons:
     - Privacy-unfriendly default. Junior staff at a law firm would have their full names exposed to every client unless the firm proactively changes the setting — and firms won't.
     - Violates the principle of least surprise on the privacy axis.

3. **Default `ANONYMISED`** — Render every consumption entry as `"Team member"`.
   - Pros:
     - Maximum privacy.
   - Cons:
     - Undermines the core client expectation ("I'm paying for this retainer; I expect to see who's working on it").
     - Firms would have to proactively opt into transparency — exactly the wrong direction for a product trying to differentiate on client relationship.
     - Makes the feature feel broken on first view.

**Decision**: Option 1 — `FIRST_NAME_ROLE` default, configurable on `OrgSettings`.

**Rationale**:

**Privacy-respecting but still identifiable.** First name + role balances "I shouldn't expose my paralegal's full legal name to every client" against "my client should know who did their work". Matches common law-firm practice.

**Per-firm choice, not per-matter.** Retainer display is a firm-wide policy question. Per-matter configuration would be friction without clear value — no firm would use different settings on different matters.

**Defaults matter more than config.** Research on product defaults: ~95% of users never change defaults. The chosen default has to be correct for most firms. `FIRST_NAME_ROLE` is defensible for every profile we ship (legal-za, consulting-za); `FULL_NAME` is too exposing for legal-za; `ANONYMISED` is too opaque for consulting-za; `ROLE_ONLY` is too impersonal across the board.

**Sync-time resolution.** The display name is computed at sync time and stored in `portal_retainer_consumption_entry.member_display_name` — so changing the setting only affects new entries. Existing entries require the admin resync endpoint to refresh. This trade-off keeps the portal read query pure (no joins to firm members) and is acceptable because firms rarely change the setting.

**Consequences**:

- New column `OrgSettings.portalRetainerMemberDisplay` (tenant migration V110), enum-style varchar with CHECK constraint, default `'FIRST_NAME_ROLE'`.
- `RetainerPortalSyncService` resolves the display name at sync time using the configured strategy.
- Firm-side settings UI (under the portal-settings section) exposes the four values via a radio group with descriptions of each option.
- Changing the setting requires a one-time backfill to update existing rows — exposed as `POST /internal/portal-resync/retainer?refreshDisplayNames=true`.
- If a firm later wants a custom format string (`"{lastName}, {firstName}"`), this ADR is the point of revisit; the implementation would extend the enum without breaking existing values.
- `Member.roleLabel()` is assumed to be populated for every tenant. Tenants without role labels fall back to `"Team member"` inside the sync service.

**Related**:

- [ADR-253](ADR-253-portal-surfaces-as-read-model-extensions.md) — Display name is computed in the sync service, not the portal controller.
- [ADR-254](ADR-254-portal-description-sanitisation.md) — Other sync-time content transforms follow the same pattern.
