# Phase 67 Ideation — Legal Depth II: Daily Operational Loop
**Date**: 2026-04-17

## Lighthouse domain
SA small-to-medium law firms. Legal is the first commercial fork. Phase 67 makes `legal-za` demo-ready for a paying customer, not just a walkthrough.

## Decision
Phase 67 ships the daily operational loop that Phase 55 (court/conflict/tariff) and Phase 60 (trust) did not cover:
1. **Disbursements module** — sibling entity to Expense, module-gated, with category/payment-source/approval workflow + invoice pipeline integration.
2. **Matter closure workflow** — 9 compliance gates (trust-zero, all-disbursements-settled, final-bill-issued, no-open-court-dates, no-open-prescriptions, etc.), owner-only override with justification.
3. **Statement of Account** — SA legal billing artifact (fees + disbursements + trust activity + balance); template + context builder, not a new entity.
4. **Conveyancing pack** — pack-only content (field pack, matter template, 10 clauses, 4 doc templates with 2 pre-wired for Phase 28 acceptance, intake request pack).
5. QA lifecycle retarget + screenshots + gap report.

## Rationale for order
Agreed roadmap from Phase 66 ideation was (1) agency pack ✓, (2) Legal depth-II ← **this phase**, (3) real integrations, (4) AI depth. Founder confirmed staying the course: "A" (legal depth-II).

## Key design decisions
1. **Disbursements = sibling entity, not Expense extension.** Introduce `UnbilledBillableItem` shared contract so invoice generation stays polymorphic. Protects non-legal tenants from schema bloat. Candidate ADR.
2. **Matter closure: enforce, overrideable by owner only.** New capability `matter.close_override` bound to `owner` role. Justification string (≥20 chars) + full gate report in audit log. Compliance, not UX.
3. **New `CLOSED` project status distinct from `COMPLETED`/`ARCHIVED`.** Horizontal archival is visibility; legal closure starts the retention clock via Phase 50 `RetentionPolicy`.
4. **Statement of Account = template + context builder, not a new entity.** Rides Phase 12/31 doc pipeline; `GeneratedDocument` already captures the artifact.
5. **Conveyancing is pack-only.** No new entities. Primitives (project + tasks + custom fields + clauses + templates) cover it. Offer-to-purchase + power-of-attorney pre-wired for Phase 28 acceptance via a new `acceptanceEligible` manifest flag (generalisable beyond conveyancing).
6. **Phase 55 FE verified done** — founder's earlier concern ("not sure it covers everything") confirmed as complete spec; gaps belong in Phase 67 scope, not in Phase 55 remediation.

## Explicitly parked
- **Unified deadline calendar + auto-seeded derived deadlines from matter events** — 15-slice phase on its own. Defer until real usage signal.
- **Advocate brief management, fee notes, deed-office API, bulk CSV import, office-account reconciliation, multi-currency disbursements.**

## Scope snapshot
- 8 epics / ~14 slices
- 2 new entities (`LegalDisbursement`, `MatterClosureLog`), 2 new modules, 1 new capability, 2 migrations, 1 shared interface retrofit
- 1 new Tiptap template (statement of account) + 1 system-owned closure letter template
- 1 field pack + 10 clauses + 4 document templates + 1 request pack (all pack content)
- ~20 backend tests + ~15 frontend tests

## Phase roadmap after 67
- **Phase 68 candidates**: unified deadline calendar (if legal customer pull), or pivot to integrations layer (Xero/QuickBooks/calendar/Slack — commercial unlock for accounting-za and consulting-za).
- Founder's agreed sequence favours integrations after legal-depth-II unless customer signal redirects.

## Domain notes (SA legal)
- Disbursements VAT rules: sheriff + deeds office + court fees are zero-rated pass-throughs; counsel/search/travel are standard 15%. Entity has no markup field (illegal in SA).
- Closure gates reflect Legal Practice Act Section 86 (trust zero) + POPIA (retention ≥ 5 years) + standard professional obligations (final bill, no open matters dangling).
- Statement of Account is distinct from invoice — it is an *informational* document of account activity, not a demand for payment. Common at milestone billing and closure.
- Conveyancing stages follow Deeds Registries Act flow (instruction → draft → lodge → register → collect). 10 deeds offices nationally.

## Next step
`/architecture requirements/claude-code-prompt-phase67.md`
