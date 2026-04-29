# QA Cycle Status — Legal ZA Full Lifecycle (Keycloak)

- **Branch**: `bugfix_cycle_2026-04-30`
- **Scenario**: `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md`
- **Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, portal :3002, KC :8180, Mailpit :8025)
- **Started**: 2026-04-30
- **Mode**: Clean slate. All Docker volumes wiped. Keycloak bootstrapped (padmin only). Orgs/users will be created through real onboarding flow.

## Mandate (from user)
- Only acceptable open gaps: **KYC** and **Payments** integrations not yet wired in.
- No workarounds besides:
  - **Mailpit API** for OTP / invite-link extraction.
  - Dev-only **Keycloak issues** (theme, admin URL, etc.) — must be documented.
- All other bugs must be **fixed** at the checkpoint they appear.
- Reviewed PRs to **main**, merged, retested before continuing.
- Frontend must run **clean** — no JavaScript/Next.js errors in logs.
- No SQL shortcuts. APIs and browser UI only.
- AI provider 5xx → wait a minute and retry, do not stop.

## QA Position
- **Day**: 0 (not started)
- **Next checkpoint**: First in scenario (org request → admin approval → owner registration)
- **Auth**: Use `frontend/e2e/fixtures/keycloak-auth.ts` patterns; for OTP/invite use `frontend/e2e/helpers/mailpit.ts`

## Stack State
- Dev Stack: **Running** (verified 2026-04-30: backend 18349, gateway 18539, frontend 18686, portal 18737 all healthy)
- NEEDS_REBUILD: false

## Tracker

| Gap ID | Summary | Severity | Owner | Status | Day | Notes |
|--------|---------|----------|-------|--------|-----|-------|
| _none yet_ | | | | | | |

**Status legend**: OPEN, SPEC_READY, FIXED, VERIFIED, REOPENED, STUCK, WONT_FIX
**Severity**: blocker (QA cannot proceed), bug (workaround exists), nit

## Retry Counter
| Gap ID | Attempt | Outcome |
|--------|---------|---------|
| _none yet_ | | |

## Log
- 2026-04-30 — Orchestrator: clean slate. dev-down --clean, dev-up, keycloak-bootstrap, svc start all. All services healthy. Branch `bugfix_cycle_2026-04-30` created from main. qa_cycle archived to `_archive_2026-04-30_legal-clean-slate`. status.md seeded fresh. Dispatching QA agent for Day 0.
