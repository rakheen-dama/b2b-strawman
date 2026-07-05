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

- **Day/Checkpoint**: Not started — begin at the scenario's first day (Day 0 / first checkpoint).

## Dev Stack

- **Status**: Not running (checked 2026-07-06: backend/gateway/frontend/portal all down, Docker infra down).

## Tracker

| Gap ID | Day/Checkpoint | Summary | Severity | Owner | Status |
|--------|----------------|---------|----------|-------|--------|

## Log

- **2026-07-06 (Orchestrator, cycle init)** — Branch `bugfix_cycle_2026-07-06` created. Previous cycle state archived to `_archive_2026-06-15_ai-core-live-verification/` (status.md + checkpoint-results + fix-specs). Fresh tracker seeded. Dev stack down → next action: Infra Agent (start stack).
