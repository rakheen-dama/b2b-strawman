# QA Cycle Status — Legal ZA Full Lifecycle (Keycloak) — 2026-04-26

## Current State

**Purpose**: Run the legal ZA full-lifecycle scenario (`qa/testplan/demos/legal-za-full-lifecycle-keycloak.md`) end-to-end on the Keycloak dev stack. Fix every gap encountered at the root cause — no workarounds, no SQL shortcuts. Prior verify-cycle status archived to `qa_cycle/_archive_2026-04-26_post-verify/status.md`.

- **Branch**: `bugfix_cycle_2026-04-26`
- **Scenario**: `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md`
- **ALL_DAYS_COMPLETE**: false
- **QA Position**: Day 0, Checkpoint 1 (scenario start)
- **Dev Stack**: Running — backend:8080, gateway:8443, frontend:3000, portal:3002, keycloak:8180 all healthy as of 2026-04-26
- **NEEDS_REBUILD**: false
- **Cycle Count**: 0 (about to start)

## Tracker

| GAP_ID | Severity | Status | Owner | Summary | Evidence |
|--------|----------|--------|-------|---------|----------|

(Tracker is empty — QA agent will populate as it walks the scenario.)

## Standing Rules (apply to every agent)

- **Root-cause every bug**. No workarounds. No SQL INSERT/UPDATE/DELETE to skip steps. All operations through REST API or browser UI.
- **No mocks for QA**. Drive the browser via Playwright MCP. Mailpit API is the only legitimate REST surface for QA.
- **One fix per PR**. Dev agents commit to `fix/{GAP_ID}` branches off this cycle branch, PR-and-merge with squash.
- **Restart after Java changes**. Backend/gateway: `bash compose/scripts/svc.sh restart {service}`. Frontend/portal: HMR auto-reloads.
- **Read CLAUDE.md** in the relevant subdirectory before changing service code.
- **Commit between turns**. Each agent pushes its state changes (status.md, fix-spec, checkpoint result) before returning.

## Known Out-of-Scope Constraints (do NOT open gaps for these)

- **Payment integration is a stub**. There is no real PSP integration (Stripe, Yoco, Peach, etc.). Invoice "Mark Paid" / portal payment flows are stub endpoints that just flip status. Do NOT open gaps for missing payment-gateway redirects, webhook signing, real card processing, or PSP-side reconciliation. If the scenario asks the user to "pay an invoice", treat the stub Mark-Paid action as the equivalent and continue.

## Log

- 2026-04-26 — Orchestrator: cycle initialized on branch `bugfix_cycle_2026-04-26`. Prior verify-cycle status (ALL_DAYS_COMPLETE 2026-04-25) archived. Dev stack confirmed healthy. About to dispatch QA agent for Day 0.
