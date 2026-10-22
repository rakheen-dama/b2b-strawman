# 617C.2 — E2E runs on :3001 (mock-auth stack) — 2026-10-22

- **Stack SHA:** `dac048f12c2441475c5494c1dc0ceac72fbd80a8` (worktree `epic-617C/capstone-closure` HEAD = main). The only uncommitted changes are under `qa_cycle/evidence/phase87-capstone/` and `marketing/decks/`. No product code differs from main.
- **Built from:** every `e2e-up.sh` logged `Building from source at: /Users/rakheendama/Projects/2026/worktree-epic-617C`.
- **Stacks:** 4 fresh stacks. Each was started with `e2e-down.sh` (volumes `docteams-e2e_e2e_postgres_data` / `_localstack_data` removed) and then `e2e-up.sh`. All containers were healthy and `e2e-seed` exited 0 each time.
  - Stack 1: legal run 1 plus the default-config suites.
  - Stack 2: legal run 2, then projects and customers.
  - Stack 3: portal 90-day.
  - Stack 4: portal responsive.
- **Preconditions:**
  - Ports 3000/3001/3002/8080/8081/8443 were free (svc stack stopped).
  - `docker builder prune -f` reclaimed 2.1 GB and `docker image prune -f` (dangling only, no volumes touched) reclaimed 0.9 GB. The VM had 21.7 GB free.
  - The caffeinate hold (PID 41495) was alive.
  - `pnpm install --frozen-lockfile` ran in the worktree.
- **Statuses** are observed runs. Counts come from Playwright's own summary lines.

## Commands

```bash
WT=/Users/rakheendama/Projects/2026/worktree-epic-617C; P=/opt/homebrew/bin/pnpm
# stack 1
cd $WT && bash compose/scripts/e2e-down.sh && SHELL=/bin/bash bash compose/scripts/e2e-up.sh
cd $WT/frontend
E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 MAILPIT_URL=http://localhost:8026 NODE_OPTIONS="" $P test:e2e:legal-lifecycle   # run 1
E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 MAILPIT_URL=http://localhost:8026 NODE_OPTIONS="" $P test:e2e:legal-depth-ii    # run 1
PLAYWRIGHT_BASE_URL=http://localhost:3001 NODE_OPTIONS="" $P test:e2e -- navigation
PLAYWRIGHT_BASE_URL=http://localhost:3001 NODE_OPTIONS="" $P test:e2e -- settings
PLAYWRIGHT_BASE_URL=http://localhost:3001 NODE_OPTIONS="" $P test:e2e -- projects     # brief's filter: matched EVERY file, see "Process finding P-1"
PLAYWRIGHT_BASE_URL=http://localhost:3001 NODE_OPTIONS="" $P test:e2e -- customers
KGOSI_EMAIL=contact@acme.example.com PLAYWRIGHT_BASE_URL=http://localhost:3001 NODE_OPTIONS="" $P test:e2e -- portal
# stack 2
cd $WT && bash compose/scripts/e2e-down.sh && SHELL=/bin/bash bash compose/scripts/e2e-up.sh
cd $WT/frontend   # same env as run 1
$P test:e2e:legal-lifecycle ; $P test:e2e:legal-depth-ii                                  # run 2
PLAYWRIGHT_BASE_URL=http://localhost:3001 NODE_OPTIONS="" $P test:e2e -- e2e/tests/projects/    # corrected filter
PLAYWRIGHT_BASE_URL=http://localhost:3001 NODE_OPTIONS="" $P test:e2e -- e2e/tests/customers/   # re-run on clean order
# stack 3 / stack 4 (portal, each on its own fresh stack), from $WT/portal
export NODE_OPTIONS="" NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081 BACKEND_URL=http://localhost:8081 \
       PORTAL_ORG_SLUG=e2e-test-org PORTAL_CONTACT_EMAIL=contact@acme.example.com PLAYWRIGHT_BASE_URL=http://localhost:3002
$P test:e2e:portal-client-90day --reporter=list,json --update-snapshots                                         # stack 3
$P exec playwright test --config=playwright.portal.config.ts --project=chromium --reporter=list,json --update-snapshots  # stack 4
```

## Results — legal configs (run 1 = stack 1, run 2 = stack 2)

| Suite | Run 1 (fresh checkout, writes baselines) | Run 2 (meaningful) | 616F baseline run 1 / run 2 | Failing-title diff vs 616F |
|---|---|---|---|---|
| legal-lifecycle | 65 passed / 15 failed / 6 skipped / 6 did not run | **63 passed / 4 failed / 6 skipped / 19 did not run** | 65/15/6/6 · 63/4/6/19 | run 1 IDENTICAL · run 2 IDENTICAL |
| legal-depth-ii | 13 passed / 15 failed / 15 skipped | **28 passed / 0 failed / 15 skipped** | 13/15/15 · 28/0/15 | run 1 IDENTICAL · run 2 IDENTICAL |

**The failing set did NOT grow vs 616F.** Failing titles are identical, with line numbers stripped, in all four comparisons. Evidence: `logs/e2e-baseline-diffs.txt`.

### Run 1 classification (legal-lifecycle, 15 failures)

| Class | Count | Tests |
|---|---|---|
| carried | 6 | day-00 "Trust account settings page loads" (lpff `locator.fill` hits a non-input); day-30 "Create fee note for Sipho Ndlovu…" and "…Apex Holdings…" (`toBeTruthy` @141/151); day-45 "Start new bank reconciliation" (`toBeTruthy`); day-90 "Fee notes list shows multiple fee notes" (`toBeGreaterThanOrEqual` @73); day-90 "Profitability dashboard — revenue…" (`toBeTruthy`) |
| missing-baseline | 9 | day-01-03 "Conflict check for Sipho Ndlovu — CLEAR" and "Create client Apex Holdings"; day-07-14 "Create court date for Sipho", "My Work page shows active items" and "Trust dashboard overview"; day-60 "Check profitability report"; day-90 "Dashboard shows KPIs", "Trust reports — Investment Register" and "Has full owner access to all areas". All 9 error with `A snapshot doesn't exist at …, writing actual.` |
| new | 0 | – |

### Run 1 classification (legal-depth-ii, 15 failures)

All 15 are **missing-baseline**: `A snapshot doesn't exist … writing actual`. They are in day-05 (3), day-14 (5), day-30-45 (2) and day-75-85 (5). All of them pass or skip in run 2. There are no carried or new failures in this suite.

### Run 2 failing titles (legal-lifecycle)

```
day-00-firm-setup.spec.ts › Day 0 — Firm Setup › Alice: Trust account settings page loads
day-30-billing.spec.ts › Day 30 — First Billing Cycle › Alice: Create fee note for Sipho Ndlovu (hourly + tariff + disbursement)
day-45-recon-prescription.spec.ts › Day 45 — Reconciliation & Prescription › Alice: Start new bank reconciliation
day-90-review.spec.ts › Day 90 — Quarter Review › Alice: Fee notes list shows multiple fee notes
```

legal-depth-ii run 2 had no failures.

## Carried locations (checklist says seven; reality is six)

The current lines were re-derived by grepping each title at HEAD (see `logs/e2e-carried-six.txt`).

| Checklist location (606C) | Current location | Test title | Run 1 | Run 2 | Status |
|---|---|---|---|---|---|
| `legal-lifecycle/day-00-firm-setup.spec.ts:326` | `:282` | Alice: Trust account settings page loads | FAILED (`locator.fill` @306: not an input) | FAILED (same) | **still failing — carried** |
| `legal-lifecycle/day-30-billing.spec.ts:32` | `:32` | Alice: Create fee note for Sipho Ndlovu (hourly + tariff + disbursement) | FAILED (`toBeTruthy` @141) | FAILED (same) | **still failing — carried** |
| `legal-lifecycle/day-30-billing.spec.ts:159` | `:159` | Alice: Create fee note for Apex Holdings (fixed fee R35,000 + VAT) | FAILED (`toBeTruthy` @151) | skipped (serial after `:32`) | **still failing — carried** (fails when reached) |
| `legal-lifecycle/day-45-recon-prescription.spec.ts:41` | `:41` | Alice: Start new bank reconciliation | FAILED (`toBeTruthy`) | FAILED (same) | **still failing — carried** |
| `legal-lifecycle/day-90-review.spec.ts:61` | `:63` | Alice: Fee notes list shows multiple fee notes | FAILED (`expect.soft(count) >= 1` @73) | FAILED (same) | **still failing — carried** |
| `legal-lifecycle/day-90-review.spec.ts:177` | `:179` | Alice: Profitability dashboard — revenue, costs, margins for 4 clients | FAILED (`toBeTruthy`) | did not run (serial) | **still failing — carried** (fails when reached) |
| `legal-depth-ii/day-05-disbursements.spec.ts:51` | `:156` (`:51` is now a const) | Bob: Create sheriff-fee OFFICE_ACCOUNT disbursement + submit | failed: missing baseline only | **PASSED** | **resolved by #1582 (pre-Phase 87 depth-ii rewrite) — confirmed by run 2** |

Phase 87 resolved none of the six. Phase 87 also introduced no new failure in either legal config.

## Results — default-config suites

| Suite | Stack | Result | Baseline | Delta |
|---|---|---|---|---|
| navigation | 1 | **16 passed** | 612C 16/16 (no saved log) | none observed |
| settings | 1 | **13 passed / 1 failed / 15 skipped / 1 did not run** | 616F 13/1/15/1 (run 1 and run 2) | failing title IDENTICAL: `settings/packs.spec.ts` "PACK-01 … Seeded legal-za pack is installed and its templates are present" (pre-existing, `toBeVisible` failed) |
| projects (`e2e/tests/projects/`) | 2 | **10 passed / 2 failed / 1 skipped / 12 did not run** | no recorded fresh-stack baseline | no delta claim; see findings F-1 |
| customers | 1 | **4 passed / 2 failed / 2 skipped / 12 did not run** | 615D: 2/1/7/4 (different file set: crud + data-protection + `settings/custom-fields`; no `customer-lifecycle`) | CUST-01 matches 615D; CUST-02 is not in any baseline, see F-2 |
| customers (`e2e/tests/customers/`) | 2 | **4 passed / 2 failed / 2 skipped / 12 did not run** | same as above | same two titles as stack 1, so the result is stable |
| frontend portal (`portal-data-isolation.spec.ts`, `KGOSI_EMAIL`) | 1 | **2 passed** | 617B 2 passed | passing titles IDENTICAL |

The projects failing titles were PROJ-02 "1. Create task on project via UI" and PROJ-03 "1. Log time on task via UI". Both timed out at `getByRole('tab', { name: 'Tasks' })`. PROJ-01 "3. Edit project name" was skipped. PROJ-01 "4b. Legacy expenses URL redirects to the Disbursements tab" **passed**. This is the redirect assertion that 615E added when it retired `project-expenses.spec.ts`.

The customers failing titles were CUST-01 "Create customer with required fields" (strict mode: `getByLabel('Name')` resolved to 2 elements, pre-existing per 615D) and CUST-02 "PROSPECT -> ONBOARDING" (timeout at `getByRole('button', { name: 'Change Status' })`).

## Results — portal suites (#19: 4 + 1 + 1 files)

| Suite | Files | Stack | Result | 617B baseline | Delta |
|---|---|---|---|---|---|
| portal-client-90day (`PORTAL_CONTACT_EMAIL=contact@acme.example.com`) | 4 (`day-00-07`, `day-14-30`, `day-45-75`, `day-85-90`) | 3 | **5 passed / 3 failed / 1 skipped / 3 did not run** | 5/3/1/3 | failing titles IDENTICAL |
| portal responsive (`--project=chromium`) | 1 (`responsive/portal-pages.spec.ts`) | 4 | **21 passed** | 21 passed | IDENTICAL |
| frontend portal-data-isolation | 1 (`frontend/e2e/tests/portal/portal-data-isolation.spec.ts`) | 1 | **2 passed** | 2 passed | IDENTICAL |

The portal 90-day run had three failures. All three are data-prerequisite failures, as in 617B. The seed does not create the data these days need:
- "Day 3: Client uploads document and submits info request": `toBeTruthy` (@ day-00-07:119).
- "Day 60 [legal-za]: Client downloads SoA or financial statement": `toBeTruthy` (@ day-45-75:133).
- "Day 85: Client changes digest cadence to biweekly": `toBeTruthy` (@ day-85-90:91).

**Non-vacuity check:** the 90-day project did not soft-skip. `globalSetup` authenticated and 5 tests passed. I opened two written PNGs with Read:
- `portal/e2e/screenshots/portal-v2/portal-client-90day/day-00-home-landing.png` is the authenticated portal **Home** page. It shows header "E2E Test Organization" and "Acme Corp", the rail "Home · Matters · Trust statement · Diary dates · Fee notes · Documents & requests · Activity", the cards "Pending info requests 0", "Upcoming deadlines 0", "Recent fee notes" and "Last trust movement", and Profile/Logout in the rail.
- `day-21-trust-balance.png` is the authenticated "Trust statement" page.

From the responsive run, `portal-v2/chromium/md-matters-list.png` is the authenticated "Your matters" page with the seeded "Website Redesign" matter. **None of the three is a login page.** Copies are in `/tmp/617c-e2e-artefacts/` (ephemeral).

## `project-expenses.spec.ts` decision (#18)

615E (#1631) retired `project-expenses.spec.ts` (the expenses page was retired per ADR-340). It replaced the spec with the redirect assertion PROJ-01 "4b. Legacy expenses URL redirects to the Disbursements tab", which **passed** on stack 2 in this run. Per the brief (D.4), the decision status is **decided (#1631); awaiting user ratification**.

## Findings (not fixed; 617C fixes nothing)

None of the findings below is a Phase 87 regression in the legal configs, whose failing set is unchanged vs 616F.

### F-1: projects PROJ-02 and PROJ-03 fail on `getByRole('tab', { name: 'Tasks' })`
- **Symptom:** "1. Create task on project via UI" (`projects/project-tasks.spec.ts:119`) and "1. Log time on task via UI" (`projects/project-time.spec.ts:131`) hit `Test timeout of 30000ms exceeded`, waiting for `getByRole('tab', { name: 'Tasks' })`. The remaining 12 tests in those two serial blocks did not run.
- **Hypothesis: spec staleness that predates Phase 87.** "Tasks" is a sub-tab of the **Work** group in `frontend/lib/constants/matter-tab-groups.ts`. `components/shared/grouped-tab-bar.tsx` renders multi-tab groups as a `role="tab"` dropdown trigger labelled by the group ("Work"), and renders sub-tabs as `DropdownMenuItem`s, so no `role="tab"` element is named "Tasks".
- **Why it predates Phase 87:** the same grouping already existed at the pre-Phase-87 before-baseline `41cb211fb`. At that commit, `frontend/lib/constants/project-tab-groups.ts` has "work" → tasks/documents/correspondence/generated, and `grouped-tab-bar.tsx` lines 110/141/172 are the same.
- **Evidence level:** this is static evidence. I did not run an A/B at `41cb211fb`. There is no recorded fresh-stack baseline for this suite.

### F-2: customers CUST-02 "PROSPECT -> ONBOARDING" fails on `getByRole('button', { name: 'Change Status' })`
- **Symptom:** `customers/customer-lifecycle.spec.ts:99` times out after 30 s on both stack 1 and stack 2. The 8 lifecycle tests after it in the serial block did not run.
- **Not in any saved baseline:** the 615D customers log did not include `customer-lifecycle.spec.ts`.
- **Hypothesis: spec staleness that predates Phase 87.** The "Change Status" trigger exists only in `frontend/components/compliance/LifecycleTransitionDropdown.tsx:140`, and nothing in `frontend/app` or `frontend/components` imports that component any more. `git log -S LifecycleTransitionDropdown -- frontend/app frontend/components` shows the last change to the import was Epic 560A (#1395, Customer Detail Redesign), long before Phase 87.
- **Evidence level:** this is static evidence. There was no run at a pre-Phase-87 SHA.

### P-1 (process): the brief's `test:e2e -- projects` filter runs the whole default-config suite
Playwright file filters are case-insensitive regexes matched against the absolute path. Every spec lives under `/Users/rakheendama/**Projects**/2026/…`, so `projects` matched all of them.

The first "projects" invocation (stack 1) therefore ran **all 314 default-config tests**: 169 passed / 39 failed / 68 skipped / 38 did not run. That includes the legal-lifecycle and depth-ii specs a second time against an already-populated tenant. Its failures are state-dependent and are **not** used for any comparison. The log is kept as `/tmp/617c-e2e-default-fullsuite-accidental.log`, with an excerpt in `logs/e2e-default-fullsuite-accidental.txt`.

The projects numbers above come from the corrected filter `e2e/tests/projects/`, on stack 2. The customers suite was also re-run on stack 2 with `e2e/tests/customers/`. Its result was identical to stack 1, so the full-suite pollution did not change it.

Recommendation for future briefs: use path-anchored filters (`e2e/tests/<dir>/`).

## Logs

- **Ephemeral** (`/tmp`):
  - legal: `617c-e2e-lifecycle-run{1,2}.log`, `617c-e2e-depth-run{1,2}.log`
  - default-config: `617c-e2e-navigation.log`, `617c-e2e-settings.log`, `617c-e2e-projects.log`, `617c-e2e-customers.log`, `617c-e2e-customers-stack2.log`, `617c-e2e-fe-portal.log`, `617c-e2e-default-fullsuite-accidental.log`
  - portal: `617c-e2e-portal-90day.{log,json}`, `617c-e2e-portal-responsive.{log,json}`
  - stack: `617c-e2e-up-{1..4}.log`, `617c-e2e-down-{1..4}.log`
  - artefacts: `/tmp/617c-e2e-artefacts/` (test-results, written screenshots and snapshot dirs, copied before the restore)
- **Tracked excerpts** (`logs/`): `e2e-lifecycle-run{1,2}.txt`, `e2e-depth-run{1,2}.txt`, `e2e-navigation.txt`, `e2e-settings.txt`, `e2e-projects.txt`, `e2e-customers-stack{1,2}.txt`, `e2e-fe-portal.txt`, `e2e-portal-90day.txt`, `e2e-portal-responsive.txt`, `e2e-default-fullsuite-accidental.txt`, `e2e-carried-six.txt`, `e2e-baseline-diffs.txt`.

## Restore + stack state

- Tracked files rewritten by the Playwright runs were restored with `git checkout --`. Untracked Playwright output was removed with path-scoped `git clean`. The final `git status --short` is recorded in the 617C.2 report.
- The e2e stack (stack 4) is **left UP** for the closure agent's `curl -I` redirect samples. It serves :3001 frontend and :8081 backend.
