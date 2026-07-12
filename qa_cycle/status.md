# QA Cycle Status — Legal ZA Full Lifecycle (Keycloak) — Cycle 2026-07-12

- **Branch**: `bugfix_cycle_2026-07-12`
- **Scenario**: `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md`
- **Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, KC :8180, Mailpit :8025)
- **Started**: 2026-07-12
- **Mode**: Fresh cycle. Prior cycle (2026-07-06, all 21 LZKC gaps fixed/verified/merged to main via PRs #1511–#1533) archived to `_archive_2026-07-12_legal-full-lifecycle-kc-cycle2026-07-06/`. This cycle re-runs the full lifecycle on current `main` to confirm the merged fixes hold end-to-end and to surface new gaps.
- **Note**: Prior-cycle data (Mathebula & Partners org `tenant_5039f2d497cf`, Moroka Family Trust, etc.) exists in the DB and Keycloak. The scenario's Day 0 / Session 0 reset governs whether to wipe and recreate — QA follows the scenario, does not reuse stale orgs unless the scenario says to.
- **Gap numbering**: continue from **LZKC-028** (001–027 belong to the prior cycle).

## Mandate

- Only acceptable open gaps: **KYC** and **Payments** integrations not yet wired (per CLAUDE.md §6 WONT_FIX exemptions). Everything else is own/fix.
- No SQL shortcuts — all operations via REST API or browser UI. Only legitimate REST use is the Mailpit email API.
- QA drives the browser via Playwright MCP (not claude-in-chrome).

## Carried-Forward Known Open Items (do NOT re-file as new gaps)

From the 2026-07-06 cycle — already tracked, awaiting Product/epic disposition:

| Gap ID | Summary | Severity | Status |
|--------|---------|----------|--------|
| LZKC-023 | Latent class: 28 other Dialog-family components share the `<DialogTrigger asChild>` radix useId hydration-mismatch class under the unstable org shell; true fix = shell stabilization (epic) | Low | OPEN (follow-up epic) |
| LZKC-024 | `settings/StageReorder.tsx` DndContext lacks `id` prop — dnd-kit module-counter hydration-drift class (one-line PR) | Low | OPEN (follow-up) |
| LZKC-025 | `ProposalService.declineProposal` emits `proposal.declined` with no actor attribution — portal declines attribute to SYSTEM (LZKC-020 class, decline verb) | Low | OPEN (follow-up) |
| LZKC-026 | Features-page module clobber: enabling Automation Rule Builder silently removed `deadlines` module (stale module list on save) | Medium | OPEN |
| LZKC-027 | Inert org-level CreateProposalDialog client combobox — `form.tsx` FormControl cloneElement discards PopoverTrigger props (pre-existing) | Low | OPEN |

Also known (no IDs; observations/deferred epics from prior cycle — re-observe but don't re-file duplicates):
- Receipt/payment-confirmation artefact after portal payment — DEFERRED epic (LZKC-012 pt 2).
- Banking details / firm contact block on fee-note & SoA PDFs — DEFERRED epic, needs new OrgSettings fields (LZKC-007/017 pt 2).
- LZKC-009 terminology sites 3/4 ("Invoice Cover Letter" template display name; audit-facet entity "Invoice") — deferred as authorized.
- Unsubscribe URL `{appUrl}/api/email/unsubscribe?token=` targets frontend host with no such route → likely 404 (latent find f, PR #1533 log).
- `DemoProvisionService` welcome-email loginUrl targets bare `/org/{slug}` with no root page (latent find g).
- `InformationRequestEmailService` 4 emails send customers to legacy embedded `/portal` on :3000 (latent find h).
- Duplicate base-url property names `docteams.app.base-url` vs `app.base-url` (latent find i).
- Recurring `/api/assistant/invocations` 404 console noise on settings routes (observation c).
- `project.created` renders raw feed copy (observation d).
- `proposal.accepted` payload `actor_name` still "System" though display attribution correct (observation e, LZKC-025 family).
- `reopenedByName` render follow-up (PR #1529 review find).

## QA Position

- **Day/Checkpoint**: Day 0 — not started.

## Dev Stack

- **Status**: Unknown (last confirmed running 2026-07-09). Infra Agent must verify Docker infra + all 4 local services before QA starts.

## Tracker

| Gap ID | Day/Checkpoint | Summary | Severity | Owner | Status |
|--------|----------------|---------|----------|-------|--------|
| — | — | (new gaps from this cycle start at LZKC-028) | — | — | — |

## Log

- **2026-07-12 (Orchestrator, cycle init)** — Branch `bugfix_cycle_2026-07-12` created. Prior cycle state (status.md, checkpoint-results/, fix-specs/) archived to `_archive_2026-07-12_legal-full-lifecycle-kc-cycle2026-07-06/`. Fresh tracker seeded with carried-forward open items LZKC-023…027. Dev stack status unknown → next action: Infra Agent (verify/start stack).
