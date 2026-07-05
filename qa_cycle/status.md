# QA Cycle Status — Legal ZA Full Lifecycle (Keycloak) — Cycle 2026-07-06

- **Branch**: `bugfix_cycle_2026-07-06`
- **Scenario**: `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md`
- **Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, KC :8180, Mailpit :8025)
- **Started**: 2026-07-06
- **Mode**: Fresh cycle. Prior cycle (AI core live verification, 2026-06-14/15) archived to `_archive_2026-06-15_ai-core-live-verification/`. Orgs/users for this scenario are created through the real onboarding flow per the scenario's Day 0.
- **Note**: Prior-cycle data (e.g. `verifain-attorneys` org) may still exist in the DB — the scenario creates its own org; QA must not reuse stale orgs unless the scenario says to.

## Mandate

- Only acceptable open gaps: **KYC** and **Payments** integrations not yet wired (per CLAUDE.md §6 WONT_FIX exemptions). Everything else is own/fix.
- No SQL shortcuts — all operations via REST API or browser UI. Only legitimate REST use is the Mailpit email API.
- QA drives the browser via Playwright MCP (not claude-in-chrome).

## QA Position

- **Day/Checkpoint**: Day 0 COMPLETE (all Session 0 + 0.1–0.32 + exit checkpoints PASS). Next: Day 1 (1.1 — Settings > Organization branding).

## Dev Stack

- **Status**: Running (2026-07-06) — Docker infra healthy (Postgres :5432, Keycloak :8180, Mailpit :8025, LocalStack :4566); local services all RUNNING+HEALTHY (backend :8080, gateway :8443, frontend :3000, portal :3002); Keycloak bootstrap applied (padmin@docteams.local); backend log clean (0 ERROR), 4 tenants reconciled; Mailpit API OK (0 messages).

## Tracker

| Gap ID | Day/Checkpoint | Summary | Severity | Owner | Status |
|--------|----------------|---------|----------|-------|--------|

## Log

- **2026-07-06 (QA, Day 0)** — Session 0 reset executed (stale prior-cycle Mathebula org/schema/KC users removed per scenario 0.C/0.D/0.E; backend restarted clean). Day 0 executed end-to-end via Playwright on the Keycloak stack: access request + OTP (Mailpit), padmin approval (tenant `tenant_5039f2d497cf` provisioned, `vertical_profile=legal-za`), Thandi KC registration → org dashboard with full legal nav, Bob (Admin) + Carol (Member) invited + registered. ALL Day 0 checkpoints PASS, zero gaps. Noted QA-harness quirk: after OAuth redirect chains, trusted pointer events stop reaching the page — workaround is explicit re-`goto` after every auth redirect (documented in day-00.md; not a product bug).
- **2026-07-06 (Infra, stack start)** — Docker daemon was down; started Docker Desktop, then `dev-up.sh` (all 4 containers healthy, Keycloak SSL fix reapplied + KC restart). Polled `realms/docteams` → 200 first attempt. Ran `keycloak-bootstrap.sh` (idempotent; mappers, padmin, DCR trusted hosts, lifetimes). `svc.sh start all`: backend ready 36s, gateway 6s, frontend 3s, portal 3s — all RUNNING+HEALTHY per `svc.sh status`. Mailpit API sane (`total:0`). Backend startup log has 0 ERROR lines; pack reconciliation 4/4 tenants OK. Prior-cycle data left untouched. No issues.
- **2026-07-06 (Orchestrator, cycle init)** — Branch `bugfix_cycle_2026-07-06` created. Previous cycle state archived to `_archive_2026-06-15_ai-core-live-verification/` (status.md + checkpoint-results + fix-specs). Fresh tracker seeded. Dev stack down → next action: Infra Agent (start stack).
