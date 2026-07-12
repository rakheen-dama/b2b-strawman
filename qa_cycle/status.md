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

- **Day/Checkpoint**: Day 3 — checkpoint 3.1 (Days 0–2 complete, all PASS; KYC 2.8–2.10 skipped per mandate exemption).

## Dev Stack

- **Status**: VERIFIED RUNNING (2026-07-12, Infra Agent)
- **Docker infra** (all healthy, up 4 days): b2b-postgres :5432, b2b-keycloak :8180, b2b-mailpit :1025/:8025, b2b-localstack :4566. Keycloak realm `docteams` returns 200; `keycloak-bootstrap.sh` re-run (idempotent) — padmin present, mappers/lifetimes/DCR policy applied.
- **Local services** (svc.sh status all RUNNING+HEALTHY):
  - backend :8080 — freshly started 2026-07-12 (was down, stale PID; ports were free, no orphan holders). Runs current `main` code.
  - gateway :8443 — freshly started 2026-07-12 (was down, stale PID). Runs current `main` code.
  - frontend :3000 — running since 2026-07-08 (PID 10342); HMR serves current branch source, no restart needed.
  - portal :3002 — running since 2026-07-08 (PID 10397); HMR, no restart needed.
- **Backend log**: clean — no ERROR/WARN lines after startup; only INFO (prior-cycle Mathebula tenant automation jobs firing on schedule, expected leftover data).
- **Mailpit**: API responsive; 7 prior-cycle messages present (left untouched per cycle note).

## Tracker

| Gap ID | Day/Checkpoint | Summary | Severity | Owner | Status |
|--------|----------------|---------|----------|-------|--------|
| — | — | (new gaps from this cycle start at LZKC-028) | — | — | — |

## Log

- **2026-07-12 (Orchestrator, cycle init)** — Branch `bugfix_cycle_2026-07-12` created. Prior cycle state (status.md, checkpoint-results/, fix-specs/) archived to `_archive_2026-07-12_legal-full-lifecycle-kc-cycle2026-07-06/`. Fresh tracker seeded with carried-forward open items LZKC-023…027. Dev stack status unknown → next action: Infra Agent (verify/start stack).
- **2026-07-12 (QA, Session 0 + Day 0)** — Session 0 reset executed per scenario 0.C/0.D/0.E (stale prior-cycle Mathebula org/schema/KC org+users removed; backend restarted clean; Mailpit purged). Day 0 all 32 checkpoints PASS: access request + OTP → padmin approval (tenant `tenant_5039f2d497cf` re-provisioned, profile legal-za) → Thandi KC registration → team invites → Bob/Carol registered; JIT member sync verified (Owner/Admin/Member). No new gaps. Harness note: trusted Playwright input events dropped session-wide this run (worse than the documented post-OAuth-only quirk); synthetic-event workaround used, outcomes verified via product-visible effects.
- **2026-07-12 (QA, Day 1)** — All checkpoints PASS: logo + #1B3358 brand colour saved and persist across logout/login (verified in Bob's fresh session); LSSA 2024/2025 High Court schedule pre-seeded (19 items, 4(a) R7800/day + 4(c) R780/hr); Section 86 trust account "Mathebula Trust — Main" created, module page shows R 0,00 balance. No new gaps.
- **2026-07-12 (QA, Day 2)** — All checkpoints PASS (KYC 2.8–2.10 SKIPPED per mandate WONT_FIX exemption, unchanged from prior cycle): Sipho Dlamini created (INDIVIDUAL, SA-legal promoted fields incl. ID 8501015800088); conflict check CLEAR; DEAL-0001 created R87 500 and dragged to Conflict check (30%); client Work>Deals row verified. LZKC-001 pipeline hydration fix holds (0 console errors). No new gaps.
- **2026-07-12 (Infra, stack start)** — Docker infra confirmed healthy (postgres/keycloak/mailpit/localstack, up 4 days); realm `docteams` 200; keycloak-bootstrap re-run idempotently. Backend + gateway were DOWN (stale PIDs, ports free) → started fresh via `svc.sh start backend gateway` (ready in 30s/6s), so both run current `main` code post-2026-07-09 merges. Frontend/portal left running (HMR serves current source). Backend log clean (no ERROR/WARN; prior-cycle Mathebula automation jobs firing on schedule is expected). Mailpit API OK (7 old messages, untouched). All 4 services RUNNING+HEALTHY — QA may proceed with Day 0.
