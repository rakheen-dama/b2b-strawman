# Phase 88 Ideation — Demo-Ready: Root-Cause Closure of the Capstone Gaps (2026-10-22)

Builds on `phase86-87-ideation-2026-10-15.md` and `qa-demo-plans-post86-ideation-2026-10-19.md`.
Trigger: `tasks/phase87-capstone-findings.md` — 90-day walk on real Keycloak, 228/53/24/2, 59 gaps (5 HIGH),
verdict not demo-ready. Founder asked for a phase that closes gaps and fixes bugs "with strong agents", grouped by
relation, vetted with product thinking.

## Lighthouse domain
Unchanged: SA small-to-medium law firms (legal-za). Mathebula & Partners / Sipho Dlamini story.

## Method (the part worth repeating)
- Ten read-only research agents, one per suspected root-cause cluster, each told to mark CONFIRMED vs HYPOTHESIS
  and to read the code the gap register did *not* point at. Dossiers preserved in `tasks/phase88-research/R1–R10`.
- Output is a root-cause map, not a symptom list: `tasks/phase88-gap-register.md` — 59 symptoms → 24 fix units,
  40 corrections to the capstone register, 8 newly surfaced latent defects (N-1…N-8).
- Biggest re-rootings: G-D7 is a capability-helper bug (`hasCapability` ignores `OWNER_ONLY`); G-B7 is a prod bug
  in both topologies; G-D11 "Outstanding" is "due_date is never computed, and no payment-terms setting exists";
  G-D12 is a missing `status=PENDING` query param; G-D15 is the shell's missing `min-w-0`; the carried six
  Playwright failures share one strict-mode `isVisible().catch(() => false)` pattern and none is a product regression.

## Decision rationale
- **Root-cause units over symptom PRs**: 8 FAILs + 2 DEFERREDs behind G-B3 close with one casing fix + one
  Team tab; 3 "aggregates" gaps close with due-date + FICA-source fixes. Quality Gate 7 "one fix per PR" is
  honoured at the unit level; same-class clusters authorised in the requirements.
- **Feature-grade units kept in** (letterhead/banking settings, portal email→firm directory, payment method):
  each is demo-blocking (wow moment 6, HIGH G-D1, Day 55) and one bounded migration.
- **Founder overrode the "amend, don't build" recommendation** for Decline (G-B19), part payments (G-C11) and
  comment attachments (G-B15): build all three (U23, U15 full, U24). Rationale: client-facing and mandate-relevant;
  pushes Phase 84 out but the pilot pitch needs a finished fee-note/payment loop.
- **One phase, ordered**, mid-phase re-walk after U15 and a full 90-day re-walk at the end (exit: 0 HIGH, 0 FAIL
  outside authorised amendments). Split into 88/89 rejected: the HIGH chain and the terminology sweep rebase badly
  across phases.
- D1–D13 authorised as recommended; §5 scenario amendments authorised (CLAUDE.md §6 record is the register).

## Key design preferences (founder)
- Fix at the root even when the symptom is on a Phase 87 surface; "pre-existing" is not a reason to defer.
- Real-data tests: projections must be tested with rows created through the UI's real service path
  (template instantiation, checklist completion, approve→send) — the capstone's `MatterListRowIntegrationTest` lesson.
- Pack content changes must reconcile existing tenants; "fresh tenants only" is unacceptable for demo surfaces.
- Audit literals stay pinned (ADR-342); legal words live in the label layer.
- Client-facing emails only on explicit intent; generated documents INTERNAL until shared.
- Terminology is a census with a guard test, not a sample.

## Phase roadmap
88 (this) → 84 take-on (re-based on the fixed shell) → design-partner QA cycle on the full-lifecycle plan.
85 (`/legal` page) opportunistic. Part of 87 C.12 (c) (calendar package deletion, profitability cost leg,
redirect-table removal) stays after 84.

## Domain notes
- SA VAT: individuals usually have no VAT number; fee-note send warns for INDIVIDUAL, blocks COMPANY/TRUST
  (`InvoiceValidationService:125-134`) — behaviour right, copy wrong. "Invoice" stays the statutory term on the wire.
- A deeds-office trust payment on a client's behalf is a trust PAYMENT; there is no DISBURSEMENT_PAYMENT type.
- Court dates attribute to the matter lead in the Diary; a fee earner who is not lead will not see them in My work.
- Retention: ADR-249 intended `close()` to start the 5-year clock; today `complete()` does. Phase 88 realigns.
- One client email can exist in several firms; the portal must resolve to N branded links, never guess.
- Outputs: `requirements/claude-code-prompt-phase88.md`, `tasks/phase88-gap-register.md`, `tasks/phase88-research/`.
