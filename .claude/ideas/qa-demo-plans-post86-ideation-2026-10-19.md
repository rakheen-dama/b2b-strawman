# QA demo plans after Phase 86 / before Phase 87 — Ideation (2026-10-19)

Builds on `phase86-87-ideation-2026-10-15.md`. Not a product phase: a maintenance decision about the two
legal-za QA lifecycle scripts, triggered by a read-only audit (`qa/testplan/legal-testplan-audit-2026-10-19.md`)
that found the 90-day plan unrunnable past Day 2 and the full-lifecycle plan missing Sipho's activation.

## Lighthouse domain
Unchanged: SA small-to-medium law firms (legal-za). Mathebula & Partners / Sipho Dlamini stay the story.

## Decision rationale
- **Amend, don't rewrite, don't version.** ADR-344 already rejected rewrite (option 4); 653 verified steps +
  LZKC lineage are the regression value. No `-v2` files; git history + inline annotations are the versions.
- **Split drift by kind.** What a step *does* (preconditions, deleted features, non-existent names,
  wrong amounts) is fixed now in one docs PR before Epic 607. What things are *called* (URLs, labels,
  nav, selectors, counts) is left to the Phase 87 IA PRs, which rewrite those lines anyway under ADR-344.
  Boundary rule: "driver cannot complete the step" → now; "completes but reads stale" → 87.
- **Why now, not in 617:** 617C.1 walks the 90-day plan on the Keycloak stack; without a coherent Day 2
  the capstone dies on arrival and every IA PR's "failure set did not grow" check measures against an
  incoherent baseline.
- **Highest-value single hunk:** full-lifecycle 15.10–15.18 isolation probes hit non-existent portal
  routes/endpoints and pass vacuously under a BLOCKER gate. Retargeted to real routes.
- **Engagement letter (ADR-336) added to both plans** — generated doc → Send for Acceptance → `/accept/<token>`
  "I Accept" on portal 3002. Replaces the deleted proposal Day 7/8 in full-lifecycle and the deleted
  Pipeline wow #2 in the 90-day (keeps both wow lists at 7).
- **Drivers:** 617C capstone = 90-day (as ADR-344 §5 says); next `/qa-cycle-kc` = full-lifecycle. Keep both.
- **C items untouched** — steps blocked by `FIXME(depth-ii:…)` stay red until their own one-fix PRs.

## Key design preferences (founder)
- Scenario amendments are product decisions: every hunk classified (A / A-trim / B-86 / B-pre / new / C / D)
  and annotated in the LZKC-029 style; the spec file is the §6 authorisation record.
- Explicit over implicit: activation is scripted as **Activate Customer**, not assumed automatic
  (no auto-activation exists in `compliance/`; the depth-ii fixme that claims it is unverified).
- Realism bounded by step order: Day 2 completes the whole checklist with one uploaded "FICA pack" PDF
  rather than reordering to "letter first, activate later"; the picker-can't-link-GeneratedDocument gap is
  recorded as a product observation, not worked around in the script.

## Roadmap position
Pre-87 docs PR (`tasks/qa-scenario-amendments-post86.md`) → Phase 87 (607–617, ADR-344 amendments in-PR,
617B final sweep uses the "known-stale" list) → Phase 84 take-on → design-partner QA cycle on the
full-lifecycle plan.

## Domain notes
- `legal-za-individual-onboarding` = 9 items / 8 required, five require a linked client document;
  trust pack = 12 / 11. Checklist completion gates activation but does not perform it.
- Portal routes today: `/projects/[id]`, `/requests/[id]`, `/trust/[matterId]`, `/invoices/[id]`,
  `/deadlines`, public `/accept/[token]`, `/auth/exchange`. Portal API: `/portal/projects/{id}/summary`,
  `/portal/requests/{id}`, `/portal/trust/matters/{id}/transactions|statement-documents`, `/portal/trust/summary`.
  Phase 87 (609F/617A) renames `projects` → `matters` on both.
- Acceptance email subject: `<org> -- Please review engagement letter for acceptance: <file>`;
  confirmation: `Confirmed: You have accepted <file>`.
- Billing runs select `lifecycle_status = 'ACTIVE'` only (`BillingRunSelectionService.java:533-535`).
