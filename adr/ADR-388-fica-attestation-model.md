# ADR-388: FICA attestation: a stored record of CDD done in the prior system, projected as ATTESTED or ATTESTED_REVIEW

**Status**: Accepted

**Context**:

Requirements decision 3 (RD-3): imported clients land ACTIVE with a FICA attestation and do not re-run onboarding checklists; clients with gaps go to a review queue. The legal basis (high confidence on the principle, medium on the field list): relying on customer due diligence already performed by the *same* accountable institution is compliant — a software change is not a new business relationship, and s21 is triggered by a prospective client; ongoing due diligence continues under s21C at a risk-based frequency set in the firm's RMCP (s42, Guidance Note 7) and under s21D when doubts arise; s22 requires the information and copies of, or references to, the verification documents; s23 requires five years after the relationship ends; s24 permits third-party storage with free and easy access. **The attestation is a pointer to CDD records that must still exist.**

At HEAD there is no stored per-client FICA record. Status is computed by `FicaStatusProjector` (ADR-348: checklist instances first, information requests as fallback, no persisted status column); a client with neither projects `NOT_STARTED`, so every imported client would show as "FICA outstanding" on the client list filter, the saved view and the dashboard widget (which filters `!= DONE`, `DashboardService.java:481`). Risk rating exists only as an unused `risk_rating` custom field; beneficial ownership and sanctions only as checklist item names; there is no PEP field. `MANAGE_COMPLIANCE` gates only POPIA/retention endpoints. A1 (who attests, and the wording) is for an attorney or FICA compliance officer; the default is a `MANAGE_COMPLIANCE` holder.

**Options Considered**:

1. **Instantiate and auto-complete the onboarding checklist for each imported client.**
   - Pros: No new concept; the projector says `DONE`.
   - Cons: Records Kazi verification that did not happen — the requirements require attested to be distinct from verified in Kazi; checklist items would claim documents that are not in Kazi.
2. **Store the attestation fields in each client's custom fields.**
   - Pros: No new table; visible on client detail.
   - Cons: Free-form, unvalidated, editable by anyone with client edit rights; no link to who attested and when; the review queue would query jsonb; the projector would read custom fields.
3. **A persisted FICA status column on `customers` set to "attested".**
   - Pros: Simple filter.
   - Cons: Reverses ADR-348 (status is derived from evidence); a second source that drifts from checklists once the firm runs Kazi onboarding; nowhere to put the evidence fields.
4. **A stored per-client attestation record, given per batch by a `MANAGE_COMPLIANCE` holder, read by the projector as evidence (chosen).**
   - Pros: Attested is distinct from verified; fields are typed and queryable; who attested and on what wording is recorded; ADR-348 is extended, not reversed — status stays computed; Kazi evidence still wins when it exists.
   - Cons: A new table and two new projected statuses every consumer must handle; review intervals become firm settings.

**Decision**: Each committed client row creates a `client_fica_attestations` row (CDD completion date, last review date, risk rating or `UNRATED`, FICA client type, beneficial-ownership status, PEP/sanctions screening status and date, source-of-funds status, CDD records location, source-system reference, derived next review date) under one batch-level attestation by a `MANAGE_COMPLIANCE` holder who also holds `MANAGE_TAKE_ON` (FD-37: the attester reviews the rows in the take-on wizard) bound to the validated batch hash and a versioned statement; `FicaStatusProjector` consults it after checklists and information requests and returns `ATTESTED` ("Verified in prior system (attested)") or, when a review reason applies, `ATTESTED_REVIEW` ("Review due (attested)"), and the review queue is the client list filtered to `ficaStatus=REVIEW`.

**Rationale**:

The record is evidence, not status, which keeps ADR-348's rule that status is computed: once the firm runs a Kazi onboarding checklist for an imported client, that checklist wins. The batch-level attestation (a `take_on_sign_offs` row of kind `FICA_ATTESTATION`, with the statement text `FICA-ATTEST-v1` and the batch hash) satisfies "given per batch … applied to each row" and makes the attester someone other than "whoever uploaded" unless that person holds `MANAGE_COMPLIANCE` (A1 default; owner and admin do). The statement says explicitly that the records remain at the recorded location for as long as FICA requires (s22, s23) and that the clients are not verified in Kazi. `cdd_records_location` is mandatory because the attestation is only as good as the records it points to.

Review reasons are `UNRATED` and `BENEFICIAL_OWNERSHIP_OUTSTANDING` (the requirements), plus `SOURCE_OF_FUNDS_OUTSTANDING`, `NOT_SCREENED` and `REVIEW_DATE_PASSED`, which are the same kind of gap (FD-30). Kazi evidence outranks an attestation: once a Kazi onboarding checklist completes for the client, the projection follows it (FD-29). The next review date is `coalesce(last review, CDD date)` plus the firm's interval for the rating; intervals are firm settings (defaults 36/24/12 months for low/medium/high) because s21C leaves the frequency to the RMCP — Kazi provides the mechanism, not the policy (FD-7). The three consumers switch from `!= DONE` to named predicates (`isOutstanding` = `NOT_STARTED | IN_PROGRESS | ATTESTED_REVIEW`; `isClear` = `DONE | ATTESTED`), so a cleanly attested client is not "outstanding" and a client with a gap is. The queue is a client-list filter and a system saved view "FICA review due" because the client list already filters, pages, saves and links, and it is where the firm already looks for "FICA outstanding" (FD-6); the dashboard widget includes review-due clients through the same predicate. Attestations are loaded in bulk (`activeFor(ids)`) so the client list costs one more query, not one per client.

**Consequences**:

- Positive:
  - Imported clients are ACTIVE and honestly labelled; gaps are visible in one queue.
  - The attestation is attributable, versioned and auditable (`customer.fica_attested`, counts and ids only).
  - ADR-348's computed-status model is preserved and extended.
- Negative:
  - Two more projected statuses; every FICA surface (badges, filters, widget, MCP gap DTO) must handle them.
  - No per-client "update attestation" form this phase; leaving the queue means running a Kazi checklist or a later feature.
  - The attestation's legal wording and the attester rule await A1 sign-off (FD-5); a change is a statement-version change.
  - Review intervals are defaults the firm may never change; the label says they are the firm's choice.

**Related**: [ADR-348](ADR-348-fica-status-source-of-truth.md) (extended), [ADR-385](ADR-385-take-on-architecture.md) (sign-off rows), [ADR-389](ADR-389-imported-client-lifecycle-entry-path.md) (no checklists on the take-on path); [architecture §17.2.5, §17.3.4–17.3.5, §17.10 X3](../architecture/phase93-client-trust-take-on.md); founder decisions FD-5, FD-6, FD-7, FD-23, FD-29, FD-30, FD-37; slice 655A.
