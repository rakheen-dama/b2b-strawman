# Demo Readiness — QA Cycle Master (Keycloak Mode)

**Date**: 2026-04-12
**Stack**: Keycloak dev stack (frontend 3000 / backend 8080 / gateway 8443 / keycloak 8180 / mailpit 8025)
**Driver**: `/qa-cycle-kc` skill — runs each lifecycle script autonomously, dispatching dev/product/QA subagents to fix gaps as they surface.

## Purpose

Drive all three vertical profiles to **demo-ready state** — a prospect can walk in cold, the founder/SE opens the Keycloak dev stack, and a 90-day lifecycle story plays out without bugs, terminology leaks, or missing vertical pack artifacts.

Each vertical has **one lifecycle script** under `qa/testplan/demos/`. The script serves two purposes:
1. **Bug-hunting** — every surface, every modal, every vertical pack artifact is exercised with a checkbox
2. **Demo rails** — once the script runs clean, the SE can cherry-pick 5–6 "wow moment" steps for a live walkthrough

## Scripts covered by this master

| Vertical | Profile | Story / Firm | Script |
|---|---|---|---|
| Legal | `legal-za` | Mathebula & Partners (Johannesburg litigation firm) | `demos/legal-za-90day-keycloak.md` |
| Accounting | `accounting-za` | Thornton & Associates (Johannesburg accounting firm) | `demos/accounting-za-90day-keycloak-v2.md` |
| Agency | `consulting-generic` | Zolani Creative (digital/marketing agency) | `demos/consulting-agency-90day-keycloak.md` |

The old mock-auth legal plan (`qa-legal-lifecycle-test-plan.md`, port 3001) and the first accounting Keycloak plan (`48-lifecycle-script.md`) are retained for historical reference but are **not** the source of truth for demo readiness going forward.

## Exit criteria ("demo ready")

A vertical is demo-ready when **all** of these hold:

1. `/qa-cycle-kc qa/testplan/demos/{vertical}.md` runs to completion on **one clean pass** — no mid-loop dispatch to fix blocker bugs
2. Every checkpoint marked `[ ]` in the script is checked, with no skipped steps
3. All "📸 wow moment" screenshots capture without visual regressions
4. The vertical's gap report (see below) has **zero HIGH/BLOCKER items**; MEDIUM items are acknowledged and triaged
5. No terminology leaks from other verticals anywhere in sidebar, breadcrumbs, tables, settings pages, or email subjects
6. The three recent-change checkpoints pass on every vertical:
   - **Tier removal** — no Starter/Pro/upgrade UI anywhere; billing page shows flat subscription states only
   - **Field promotion** — promoted slugs render as inline native form inputs, never duplicated in `CustomFieldSection`
   - **Progressive disclosure** — no nav items visible for disabled modules; direct URLs for disabled modules return a clean 404/redirect
7. **Test suite gate** (mandatory — see below) — both backend and frontend unit/integration test suites pass cleanly before any fix PR is merged

## Test suite gate (mandatory — applies to every fix cycle)

**Rule**: Recent QA cycles have been breaking builds — especially tests. **No fix PR merges to `main` until both the backend and frontend test suites pass locally from a clean checkout.** This applies to every dev-agent-authored fix inside a `/qa-cycle-kc` loop.

This is a **hard merge gate**. If a fix PR lands on `main` with failing or skipped tests, treat it as a regression and revert before continuing the cycle.

### Commands (run from repo root before merging any fix PR)

```bash
# Backend — unit + integration tests
cd backend && ./mvnw -B verify

# Frontend — vitest unit + component tests
cd frontend && pnpm test

# Frontend — typecheck (catches promoted-field / org-profile signature drift)
cd frontend && pnpm typecheck

# Frontend — lint (catches ESLint regressions that would fail in CI)
cd frontend && pnpm lint
```

### Pass criteria (all four must be green)

- [ ] `./mvnw -B verify` → `BUILD SUCCESS`, zero test failures, zero skipped tests that weren't already skipped on `main`
- [ ] `pnpm test` → all vitest suites pass, zero failed assertions
- [ ] `pnpm typecheck` → zero TypeScript errors
- [ ] `pnpm lint` → zero lint errors

### QA cycle loop rules

The `/qa-cycle-kc` loop MUST include a "verify tests" sub-step after every dev-agent fix and before the fix PR is merged:

1. Dev subagent writes fix → runs affected tests locally
2. Dev subagent runs the **full** backend + frontend suites (not just affected tests) — "passes in isolation" is order-sensitivity, not clean
3. If any suite fails: dev subagent either fixes the failure as part of the same PR, or reverts the fix and re-approaches. Do **not** push a fix PR with a failing suite.
4. Once suites are green: PR is raised, merged, next vertical cycle starts from the updated baseline.

### Common failure modes to watch for

These have bitten recent QA cycles — agents should check for them proactively:

- **Stale test fixtures**: new promoted slugs added without updating the corresponding customer/project/invoice factory fixtures
- **Module gate changes breaking E2E selectors**: sidebar filtering changes break Playwright tests that hard-code nav item locators
- **Spring context cache poisoning**: integration tests pass in isolation but fail when run in suite order; check `@DirtiesContext` usage and `TestChecklistHelper` cleanup
- **Vertical profile loader race on startup**: first test that boots a fresh context fails because `consulting-generic` profile hasn't finished loading
- **Orphan Testcontainers**: `~/.testcontainers.properties` caching stale docker host — clean up before re-running

### Relationship to cycle exit criteria

The test suite gate is enforced **per fix PR**, not only at cycle end. But the full cycle exit requires:

- [ ] Last fix PR in the cycle has all four suites green
- [ ] Running the full suites against `main` (after all cycle fixes merged) is still green
- [ ] If the cycle changed any shared code (vertical profile loader, `NavZone`, promoted slug list, billing page), run the other two vertical plans' smoke-test sections to catch cross-vertical regressions before declaring the primary vertical demo-ready

## Order of operations (sequential, not parallel)

Run the three verticals **in order**, merging fixes between runs so cascading improvements land in the next pass:

1. **Legal first** — largest existing footprint, most complex vertical packs (4 modules, full FICA flow, trust accounting, court calendar). Highest bug-surface density. Cycle until clean.
2. **Accounting second** — similar onboarding/compliance shape to legal, so fixes for shared surfaces (request flow, Keycloak invites, field promotion, custom field rendering) cascade. Refresh from `48-lifecycle-script.md` with no-tier + progressive disclosure additions.
3. **Agency last** — uses `consulting-generic`, the thinnest profile. Bugs surfaced here are most likely to be genuinely agency-specific (missing primitives, not cross-cutting). Any gaps that cannot be fixed in-cycle get logged to the **agency gap list** in `.claude/ideas/demo-readiness-qa-cycle-2026-04-12.md` for future phase planning.

Between each vertical: **merge fixes to main**, redeploy the Keycloak stack from a clean baseline, then start the next vertical.

## Shared prep & reset (run before every vertical cycle)

Each lifecycle script starts with "Prep & Reset — Session 0". This master doc centralises the shared version so scripts can reference it instead of duplicating.

### Session 0 — Stack startup & teardown (≈ 10 min)

**Actor**: Tester / Infra agent (no login required)

- [ ] **M.1** From repo root: `bash compose/scripts/dev-up.sh` — wait for "infra up" confirmation
- [ ] **M.2** If first run on this machine: `bash compose/scripts/keycloak-bootstrap.sh` — confirm output mentions `padmin@docteams.local` created
- [ ] **M.3** In three separate terminals (or via `bash compose/scripts/svc.sh start all`):
  - Backend: `SPRING_PROFILES_ACTIVE=local,keycloak ./mvnw spring-boot:run` (port 8080)
  - Gateway: `./mvnw spring-boot:run` (port 8443)
  - Frontend: `NEXT_PUBLIC_AUTH_MODE=keycloak pnpm dev` (port 3000)
- [ ] **M.4** Health checks (run all four):
  - `curl -sf http://localhost:8080/actuator/health` → `{"status":"UP"}`
  - `curl -sf http://localhost:8443/actuator/health` → `{"status":"UP"}`
  - `curl -sf http://localhost:3000/` → 200 OK
  - `curl -sf http://localhost:8180/realms/docteams` → realm JSON
- [ ] **M.5** Open Mailpit (`http://localhost:8025`) — inbox should be empty
- [ ] **M.6** Drop any leftover tenant schemas from a previous run:
  ```bash
  docker exec b2b-postgres psql -U postgres -d app -c "SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE 'tenant_%';"
  ```
  For each tenant schema belonging to the vertical being tested (e.g. `tenant_mathebula_partners`), drop it:
  ```bash
  docker exec b2b-postgres psql -U postgres -d app -c "DROP SCHEMA tenant_mathebula_partners CASCADE;"
  ```
- [ ] **M.7** Confirm Keycloak admin console (`http://localhost:8180/admin`) shows **only** `padmin@docteams.local` under Users in the `docteams` realm. Delete any lingering test users (`thandi@*-test.local`, `bob@*-test.local`, `carol@*-test.local`) from previous runs.
- [ ] **M.8** Clear Mailpit inbox (if not already empty).
- [ ] **M.9** Confirm browser: incognito/private window, no stored cookies for `localhost:3000`.

**Session 0 checkpoints**
- [ ] All four services healthy
- [ ] `padmin@docteams.local` is the only Keycloak user in the `docteams` realm
- [ ] No leftover tenant schemas for the vertical being tested
- [ ] Mailpit inbox empty
- [ ] Clean browser session

> **Gap flag**: There is no `kc-reseed.sh` script today. Shared reset is manual (steps M.6–M.8). If this cycle is run repeatedly, capture the manual steps into `compose/scripts/kc-reseed.sh <vertical-tag>` as a follow-up infra task.

## Gap report format

For each cycle pass, the qa-cycle-kc loop writes findings to:

```
qa/gap-reports/demo-readiness-{YYYY-MM-DD}/{vertical}.md
```

Each gap report entry has:
- **ID** (e.g. `LEGAL-01`, `ACCT-12`, `AGENCY-03`)
- **Severity** — `BLOCKER`, `HIGH`, `MEDIUM`, `LOW`
- **Script step** that surfaced it (e.g. "Step 1.14 — FICA checklist missing 'Proof of Address' item")
- **Observed** vs **Expected** behaviour
- **Root cause** (filled in by dev agent after investigation)
- **Fix PR** (linked once merged)
- **Retest step** (which script step to re-run after the fix)

Severity rules:
- **BLOCKER** — stops the cycle (e.g. Keycloak invite email never arrives, vertical profile fails to activate)
- **HIGH** — the step cannot be demo'd as-is (e.g. terminology leak, missing template, field promotion not rendering)
- **MEDIUM** — cosmetic or minor UX issue that a prospect would notice but not walk away from
- **LOW** — copy, spacing, or edge-case issue; triaged separately

## Running the cycle

Drive each vertical via the `/qa-cycle-kc` skill:

```
/qa-cycle-kc qa/testplan/demos/legal-za-90day-keycloak.md
/qa-cycle-kc qa/testplan/demos/accounting-za-90day-keycloak-v2.md
/qa-cycle-kc qa/testplan/demos/consulting-agency-90day-keycloak.md
```

The skill:
1. Starts the dev stack if not running
2. Walks the script step-by-step
3. On failure, dispatches a Product subagent to interpret intent, then a Dev subagent to fix root cause, then re-runs the failing step
4. On success of all steps, runs exit-criteria checkpoints and writes the gap report
5. Stops when the script runs clean on a single pass

Between verticals, merge the accumulated fixes to `main`, redeploy, and drop the previous vertical's tenant schema before starting the next.

## Recent-change checkpoint reference

Every lifecycle script enforces these three checkpoints explicitly. Quick reference for what "pass" looks like on each:

### Tier removal
- **Where**: `Settings > Billing` (all verticals), any nav item or modal
- **Pass**: Page shows flat subscription states (`TRIALING`, `ACTIVE`, `PAST_DUE`, `PENDING_CANCELLATION`, `GRACE_PERIOD`, `LOCKED`, `SUSPENDED`, `EXPIRED`), member count + amount, PayFast self-service flows (for non-admin-managed tenants), payment history. **No Starter/Pro tier, no upgrade button, no plan-gated CTA.**
- **Fail**: Any "Upgrade to Pro", "Starter plan", plan picker, or member-limit-enforced gate anywhere in the product.

### Field promotion
- **Where**: Customer/Client create/edit dialog, Customer detail page, Project/Matter/Engagement create/edit dialog, Project detail page, Task create dialog, Invoice/Fee Note create dialog
- **Pass**: Each promoted slug (see `frontend/lib/constants/promoted-field-slugs.ts` for the list) renders as a **first-class native form input** on the create/edit dialog and as an inline column or field on the detail page. **Promoted slugs do NOT appear again inside the `CustomFieldSection` sidebar.** Non-promoted custom fields remain in the sidebar.
- **Fail**: Duplicate rendering (promoted slug appears both inline AND in CustomFieldSection), or promoted slug buried inside "Other Fields" instead of inline, or promoted slug missing entirely from the dialog.

### Progressive disclosure
- **Where**: Sidebar nav, breadcrumbs, settings pages, direct-URL navigation
- **Pass (legal-za)**: Sidebar shows Trust Accounting, Court Calendar, Conflict Check, Tariffs nav items. All four modules enabled in `Settings > Modules`.
- **Pass (accounting-za)**: Sidebar does **not** show Trust Accounting, Court Calendar, Conflict Check, or Tariffs. `Settings > Modules` either hides them entirely or shows them as greyed-out (not toggleable for this vertical).
- **Pass (consulting-generic)**: Same as accounting — none of the legal-specific modules visible. Also no accounting-za automation rules showing up in `Settings > Automations`.
- **Fail**: Any cross-vertical nav item visible, or direct URL to a disabled module page loading successfully instead of 404/redirect.

## Follow-ups surfaced by this cycle (non-blocking)

These are infrastructure/tooling gaps that don't block the cycle but should be captured for future work:

- `compose/scripts/kc-reseed.sh` — automate Session 0 reset (drop schema, delete Keycloak users, clear Mailpit) per vertical tag
- `qa/gap-reports/` directory + template file — currently created ad-hoc; could be scaffolded
- Screenshot capture harness for Keycloak-mode plans — today's screenshot tooling targets the mock-auth stack on port 3001

---

**Next steps after writing**:
1. `/qa-cycle-kc qa/testplan/demos/legal-za-90day-keycloak.md`
2. Merge fixes → re-baseline
3. `/qa-cycle-kc qa/testplan/demos/accounting-za-90day-keycloak-v2.md`
4. Merge fixes → re-baseline
5. `/qa-cycle-kc qa/testplan/demos/consulting-agency-90day-keycloak.md`
6. Consolidate the agency gap list into a future vertical-phase ideation pass
