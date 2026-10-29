# Demo Readiness — QA Cycle Master (Keycloak Mode)

**Date**: 2026-10-19 (originally 2026-04-12; legal-only since Phase 86 — ADR-330/332, Epic 604 #1576)
**Stack**: Keycloak dev stack (frontend 3000 / backend 8080 / gateway 8443 / keycloak 8180 / mailpit 8025 / portal 3002)
**Driver**: `/qa-cycle-kc` skill — runs each lifecycle script autonomously, dispatching dev/product/QA subagents to fix gaps as they surface.

## Purpose

Drive the one remaining vertical, `legal-za`, to **demo-ready state** — a prospect can walk in cold, the founder/SE opens the Keycloak dev stack, and a 90-day law-firm lifecycle story plays out without bugs, terminology leaks, or missing vertical pack artifacts. (Kazi has been legal-only since Phase 86; the accounting and agency scripts were deleted with their profiles in Epic 604 #1576 — archived at git tag `horizontal-platform-final`.)

The legal lifecycle scripts live under `qa/testplan/demos/`. Each script serves two purposes:
1. **Bug-hunting** — every surface, every modal, every vertical pack artifact is exercised with a checkbox
2. **Demo rails** — once the script runs clean, the SE can cherry-pick 5–6 "wow moment" steps for a live walkthrough

## Scripts covered by this master

| Script | POV | Story / Firm | Role |
|---|---|---|---|
| `demos/legal-za-full-lifecycle-keycloak.md` | Firm + portal interleaved | Mathebula & Partners — one RAF matter for Sipho Dlamini, Moroka Family Trust as isolation target | **QA-cycle driver** (`/qa-cycle-kc`); verified lineage 2026-07-12 (LZKC) |
| `demos/legal-za-90day-keycloak.md` | Firm-led, three portal touchpoints (4.3b, 14.12, 68.1) | Mathebula & Partners — four clients (litigation, deceased estate, conveyancing on Day 14, RAF) | **Phase 87 capstone walk** (Epic 617C, ADR-344 §5); breadth: budget, profitability, disbursements, write-off, closure override, audit sweep |
| `demos/portal-client-90day-keycloak.md` | Portal only | Sipho / Moroka on the client portal | Portal gating; assumes firm-side state from the plans above |
| `demos/admin-audit-30day-keycloak.md` | Admin | Audit-log surfaces over 30 days | Observability layer |
| `demos/ai-core-verification-keycloak.md` | Firm | Verifain Attorneys switching AI on | **DEFERRED** until a human enters a funded Anthropic key (V1) |

Amendment rules: CLAUDE.md §6 (scenario edits are product decisions), ADR-344 (Phase 87 URL/label/nav amendments in the same PR as the IA change), `tasks/qa-scenario-amendments-post86.md` (the 2026-10-19 behaviour-coherence pass). `qa/testplan/MIGRATION-NOTES.md` lists what was removed and what is known-stale.

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
- **Vertical profile loader race on startup**: first test that boots a fresh context fails because the `legal-za` profile hasn't finished loading
- **Orphan Testcontainers**: `~/.testcontainers.properties` caching stale docker host — clean up before re-running

### Relationship to cycle exit criteria

The test suite gate is enforced **per fix PR**, not only at cycle end. But the full cycle exit requires:

- [ ] Last fix PR in the cycle has all four suites green
- [ ] Running the full suites against `main` (after all cycle fixes merged) is still green
- [ ] If the cycle changed any shared code (vertical profile loader, `NavZone`, promoted slug list, billing page), run the smoke sections of the plans not being cycled (the other lifecycle plan's Day 0 — full-lifecycle or 90-day, whichever is not the current cycle — plus portal Day 0 and admin-audit Day 0) and both legal Playwright configs on the :3001 mock stack before declaring demo-ready

## Order of operations (sequential, not parallel)

Run the legal plans **in order**, merging fixes between runs so cascading improvements land in the next pass:

1. **Full lifecycle first** — the QA-cycle driver: firm + portal interleaved, isolation gate, PayFast, closure → Statement of Account. Cycle until clean.
2. **90-day second** — firm-led breadth the full lifecycle lacks (three clients, estate and conveyancing templates, budget, profitability, disbursement trust-link, write-off, closure override, 90-day audit sweep). This is also the Epic 617C capstone walk.
3. **Portal, then admin-audit** — both read firm-side state, so run them on the tenant the preceding plan built, before that tenant is reset.
4. **AI-core** — only when a human has entered a funded Anthropic key; never inferred PASS.

Between plans: **merge fixes to main**, redeploy the Keycloak stack from a clean baseline, then start the next plan.

## Shared prep & reset (run before every vertical cycle)

Each lifecycle script starts with "Prep & Reset — Session 0". This master doc centralises the shared version so scripts can reference it instead of duplicating.

### Session 0 — Stack startup & teardown (≈ 10 min)

**Actor**: Tester / Infra agent (no login required)

- [ ] **M.1** From repo root: `bash compose/scripts/dev-up.sh` — wait for "infra up" confirmation
- [ ] **M.2** If first run on this machine: `bash compose/scripts/keycloak-bootstrap.sh` — confirm output mentions `padmin@docteams.local` created
- [ ] **M.3** In four separate terminals (or via `bash compose/scripts/svc.sh start all`, which starts all four):
  - Backend: `SPRING_PROFILES_ACTIVE=local,keycloak ./mvnw spring-boot:run` (port 8080)
  - Gateway: `./mvnw spring-boot:run` (port 8443)
  - Frontend: `NEXT_PUBLIC_AUTH_MODE=keycloak pnpm dev` (port 3000)
  - Portal: `pnpm dev` in `portal/` (port 3002) — needed by every plan with a portal step (full-lifecycle, portal-client, 90-day 4.3b/14.12/68.1)
- [ ] **M.4** Health checks (run all five):
  - `curl -sf http://localhost:8080/actuator/health` → `{"status":"UP"}`
  - `curl -sf http://localhost:8443/actuator/health` → `{"status":"UP"}`
  - `curl -sf http://localhost:3000/` → 200 OK
  - `curl -sf -o /dev/null -w '%{http_code}' http://localhost:3002/login` → 200
  - `curl -sf http://localhost:8180/realms/docteams` → realm JSON
- [ ] **M.5** Open Mailpit (`http://localhost:8025`) — inbox should be empty
- [ ] **M.6** Drop any leftover tenant schemas from a previous run (Phase 88, register §5a row 1 — wording amendment, founder-authorised 2026-10-29: the Keycloak dev stack's database is `docteams` (`app` is the E2E stack's); tenant schemas are named `tenant_<12 hex>`, `SchemaNameGenerator.java:19`):
  ```bash
  docker exec b2b-postgres psql -U postgres -d docteams -c "SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE 'tenant_%';"
  ```
  For each tenant schema belonging to the vertical being tested (the org's `schema_name` in `public.org_schema_mapping`, a `tenant_<12 hex>` name — never one named after the firm), drop it:
  ```bash
  docker exec b2b-postgres psql -U postgres -d docteams -c "DROP SCHEMA tenant_<12 hex> CASCADE;"
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
- **ID** (e.g. `LZKC-036` — continue the LZKC sequence from `qa_cycle/status.md`)
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
/qa-cycle-kc qa/testplan/demos/legal-za-full-lifecycle-keycloak.md
/qa-cycle-kc qa/testplan/demos/legal-za-90day-keycloak.md
/qa-cycle-kc qa/testplan/demos/portal-client-90day-keycloak.md
/qa-cycle-kc qa/testplan/demos/admin-audit-30day-keycloak.md
```

The skill:
1. Starts the dev stack if not running
2. Walks the script step-by-step
3. On failure, dispatches a Product subagent to interpret intent, then a Dev subagent to fix root cause, then re-runs the failing step
4. On success of all steps, runs exit-criteria checkpoints and writes the gap report
5. Stops when the script runs clean on a single pass

Between the two lifecycle plans (full-lifecycle → 90-day), merge the accumulated fixes to `main`, redeploy, and reset the tenant per the plan's Session 0 before starting the next. Do **not** reset before the portal and admin-audit plans — they read the state the preceding lifecycle plan built (see "Order of operations").

## Recent-change checkpoint reference

Every lifecycle script enforces these three checkpoints explicitly. Quick reference for what "pass" looks like on each:

### Tier removal
- **Where**: `Settings > Billing` (all verticals), any nav item or modal
- **Pass**: Page shows the flat subscription status badge (`TRIALING`, `ACTIVE`, `PAST_DUE`, `PENDING_CANCELLATION`, `GRACE_PERIOD`, `LOCKED`, `SUSPENDED`, `EXPIRED`) and the "Managed Account" card. **No Starter/Pro tier, no upgrade button, no plan-gated CTA, and — since Epic 602 (#1574) — no member count, monthly amount or PayFast self-service UI.**
- **Fail**: Any "Upgrade to Pro", "Starter plan", plan picker, or member-limit-enforced gate anywhere in the product.

### Field promotion
- **Where**: Customer/Client create/edit dialog, Customer detail page (Details tab), Project/Matter/Engagement create/edit dialog, Project detail page, Task create dialog, Invoice/Fee Note create dialog
- **Pass**: Each promoted slug (see `frontend/lib/constants/promoted-field-slugs.ts` for the list) renders as a **first-class native form input** on the create/edit dialog and as an inline field on the **Details tab** (`tab-group-details` → `tab-item-details`) of the detail page. **Promoted slugs do NOT appear again inside the `CustomFieldSection` on the Fields tab** (`tab-group-details` → `tab-item-fields`). Non-promoted custom fields remain on the Fields tab (under the Details tab group).
- **Fail**: Duplicate rendering (promoted slug appears both inline AND in CustomFieldSection), or promoted slug buried inside "Other Fields" instead of inline, or promoted slug missing entirely from the dialog.

### Progressive disclosure
- **Where**: Sidebar nav, breadcrumbs, settings pages, direct-URL navigation
- **Pass (legal-za)**: Sidebar shows Trust Accounting, Court Calendar, Conflict Check, Tariffs nav items (Phase 87 re-shells these into the six zones — ADR-339). The four legal modules are fixed by the profile; `Settings > Features` lists only the horizontal toggles (Bulk billing, Information requests). `GET /api/modules` returns the legal set.
- **Fail**: Any cross-vertical nav item visible, or direct URL to a disabled module page loading successfully instead of 404/redirect.

## Follow-ups surfaced by this cycle (non-blocking)

These are infrastructure/tooling gaps that don't block the cycle but should be captured for future work:

- `compose/scripts/kc-reseed.sh` — automate Session 0 reset (drop schema, delete Keycloak users, clear Mailpit) per vertical tag
- `qa/gap-reports/` directory + template file — currently created ad-hoc; could be scaffolded
- Screenshot capture harness for Keycloak-mode plans — today's screenshot tooling targets the mock-auth stack on port 3001

---

**Next steps after writing**:
1. `/qa-cycle-kc qa/testplan/demos/legal-za-full-lifecycle-keycloak.md`
2. Merge fixes → re-baseline
3. `/qa-cycle-kc qa/testplan/demos/legal-za-90day-keycloak.md` (also the Epic 617C capstone walk once Phase 87 lands)
4. Merge fixes → re-baseline
5. Portal and admin-audit plans against the same tenant
6. AI-core walk when a funded key is available
