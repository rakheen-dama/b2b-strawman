# Implementation Brief: Slice 628F — Default-config fixes + seed rows + per-suite scripts + rule widening

Worktree `/Users/rakheendama/Projects/2026/worktree-epic-628F` · base `origin/main` 43e8fddb2 (628E docs) · scout: read-only, 2026-10-29.
Path shorthands: `$E` = `frontend/e2e/tests`, `APP` = `frontend/app/(app)/org/[slug]`, `CMP` = `frontend/components`, `$B` = `backend/src/main/java/io/b2mash/b2b/b2bstrawman`. All other paths are relative to the worktree root.
Scratch evidence (scout, outside the repo): `/tmp/628F-scout-eslint.json` (ESLint run with the rule widened), `/tmp/628F-scout-sites.txt` (187 site contexts), `/tmp/628F-census-{A,B,C}.md` (per-group census, copied in full below).

## Scope / branch

- **Branch** `phase88/11F-default-config-seed-scripts`. **One PR.** The PR touches `frontend/e2e/**`, `frontend/eslint.config.mjs`, `frontend/__tests__/eslint-rules/no-isvisible-catch.test.ts`, `frontend/package.json` (scripts only), `frontend/e2e/AUTOMATION-NOTES.md`, `frontend/CLAUDE.md` (one bullet), and `compose/seed/**`. That is about 40 files, well under the 300-file PR cap.
- **No product code.** Do not edit `frontend/app|components|lib|hooks`, the backend, or the portal. When a real flow is blocked by a product defect you have reproduced in the run, use `test.fixme(true, "628F-BLOCKER: <short> — <product file:line>")` as the first statement of that test (convention: AUTOMATION-NOTES › Assertion rules › Product blockers) and list it in the PR body. Four candidate blockers were found by reading source (OD-4). Each must be reproduced before it is fixme'd (root `CLAUDE.md` §4).
- **No scenario docs.** Do not edit `qa/testplan/demos/*.md` (that is 628H).
- **Out of scope:**
  - the portal 90-day specs (628G consumes the seed rows; see "Seed design › consumer specs");
  - `portal/` ESLint;
  - `compose/scripts/e2e-up.sh` (see OD-9);
  - `scripts/regression-test.sh`, untouched because no helper signature changes. Only a new helper is added.
- **Authorisation already in hand:** register **D13** (U22) authorises "Playwright locator/flow amendments in-slice (611B lesson B-7) and the seed additions (invoices, trust account, Day 3/60/85 portal rows)". Spec titles and comments may be corrected in-spec. List every rename (old → new) in the PR body.

**Headline findings (read first).**

1. **Site count.** The default-config sweep is **187 sites in 28 files**, not 188 in 29. 628E already converted `e2e/fixtures/keycloak-auth.ts`.
   - Every one of the 187 is `.isVisible(…).catch(…)`.
   - 65 of them also carry the ignored `{ timeout }`, always inside the same expression, never on their own.
   - There are no `.then(f, r)` forms.
   - Measured by running ESLint with the rule forced on `e2e/` (`--rule`). Raw output: `/tmp/628F-scout-eslint.json`.
2. **The suite is mostly vacuous, as the lifecycle suite was.** Verified against product source, **83 of the 187 sites guard a target that does not exist in today's product** (class c).
   - Examples: pages that were removed (`/documents`) or never built (`/information-requests` list); "tabs" that are menu items; a "Change Status" dropdown with no importer since Epic 560A; native `confirm()` mistaken for a dialog; `.flex-1` resolving to the sidebar.
   - A further class is "passes for the wrong reason": the `bodyText.includes("404")` guard probably fires on every page, because the inline RSC payload contains Next's default not-found tree. This is inferred from the baseline skip pattern, not observed.
   - So the slice is effectively a rewrite of roughly 25 default-config spec files onto the strict style. That is why OD-1 asks for two sequential builders.
3. **The legal suites run twice today.** They run under the default config because `testIgnore` lacks them. They fail there on run-state collisions: the fresh baseline at 43e8fddb2 has 13 legal failures out of 21 under the default config, against 101/0/3 and 39/0/4 under their own configs. `testIgnore` alone removes 13 of the 21 failures.
4. **P-1 reproduced.**
   - `playwright test --config e2e/playwright.config.ts --list projects` → **326 tests in 57 files**, i.e. everything (the checkout path contains `/Projects/`).
   - `--list e2e/tests/projects/` → 25 tests in 4 files.
   - `e2e/tests/customers/` → 20/3; `e2e/tests/settings/` → 30/8; `e2e/tests/navigation/` → 16/2.

## Tasks (full text + acceptance)

**Slice row 628F** (`tasks/phase88-demo-ready-root-cause-closure.md:867`, verbatim): "628.14–628.15 | `settings/packs.spec.ts`, `projects/project-{tasks,time}.spec.ts`, `customers/customer-{crud,lifecycle}.spec.ts`, `e2e/playwright.config.ts`, `package.json`, `compose/seed/seed.sh` + `lib/{invoices,trust,information-requests,statements}.sh` | Default-config fixes; seed rows; per-suite scripts (P-1); E2E-DEFAULT 0 failed. **Hand-off from 628E (R-2/R-4):** widen `no-isvisible-catch` (`frontend/eslint.config.mjs`) from the legal suites/helpers/fixtures to `e2e/**` and convert the **188 default-config sites in 29 files** (census in `$EV/628E/epic-brief.md` › Site Census; flip the wiring test's "not yet enforced" pins); convert `legal-depth-ii/day-14-conveyancing.spec.ts:142-143` bare `isVisible()` after a click to an `a.or(b)` wait; `openGroupedTab` is already lifted to `e2e/helpers/grouped-tabs.ts` (consume it for PROJ-02/03); the trust seed must use **"Mathebula & Partners Trust Account", GENERAL, Standard Bank** (primary GENERAL is unique — Day 0 creates it only if absent); A5 is already green from lifecycle data, so the invoice seed is additive."

**628.14 — Default-config fixes + seed rows + scripts** (`:889`, verbatim): "`$E/settings/packs.spec.ts:94` (PACK-01 `exact`/role), `$E/customers/customer-crud.spec.ts:47` (CUST-01 `getByLabel('Name', {exact: true})`), `customer-lifecycle.spec.ts` (CUST-02 header smart action), `$E/projects/project-{tasks,time}.spec.ts` (PROJ-02/03 via `openGroupedTab` lifted to `frontend/e2e/helpers/grouped-tabs.ts` from `legal-depth-ii.ts`); `frontend/e2e/playwright.config.ts` (`testIgnore: ["**/legal-lifecycle/**","**/legal-depth-ii/**"]`); `frontend/package.json` (`test:e2e:projects|customers|settings|navigation` with path-anchored `--grep`/dir filters — P-1); `compose/seed/seed.sh` + `lib/invoices.sh` (two invoices via the API, one approved + sent), NEW `lib/trust.sh` (a trust account), NEW `lib/information-requests.sh` (Day 3: `POST /api/information-requests` + `/send`), NEW `lib/statements.sh` (Day 60: `POST /api/matters/{id}/statements` with `shareWithClient: true` — 624E), `PATCH /api/settings/portal-digest-cadence BIWEEKLY` (Day 85)". Tests: "**E2E-DEFAULT 0 failed** on a fresh `e2e-down && e2e-up`". Pattern refs: "`compose/seed/lib/common.sh` helpers; memory `feedback_no_sql_shortcuts_in_qa` (seed uses APIs only)". Note: "`scripts/regression-test.sh` untouched unless a helper signature changes (REGRESSION re-run if it does)."

**628.15 — E2E-DEFAULT + E2E-LEGAL re-run** (`:890`, verbatim): "**E2E-DEFAULT** 0 failed; **E2E-LEGAL** 0 failed (A5 now seeded)". "Both run logs cited in the PR body." The three `628E-BLOCKER` fixmes (Day 14 conflict-check crash, Day 60 interest approval, Day 90 Section 35 report) remain skipped. They are not failures.

**Architecture acceptance** (`architecture/phase88-demo-ready-root-cause-closure.md:795-801`, U22): "`compose/seed/seed.sh` gains two invoices (one SENT), a trust account, the Day 3 information request (`POST /api/information-requests` + `/send`), the Day 60 statement (`POST /api/matters/{id}/statements` with `shareWithClient: true` after U13) and `PATCH /api/settings/portal-digest-cadence BIWEEKLY`. `package.json` gains `test:e2e:projects|customers|settings|navigation` with path-anchored filters; the default config `testIgnore`s `legal-lifecycle/**` and `legal-depth-ii/**`; `AUTOMATION-NOTES.md` records the rule. **Acceptance (observed).** On a fresh `e2e-down && e2e-up`: legal-lifecycle and legal-depth-ii 0 failed …, default config 0 failed, portal 90-day 0 failed and 0 soft-skipped." The portal part belongs to 628G.

**Process rules carried in:**
- Lesson B-7 (611B) cuts both ways. Removing a guard turns a vacuous **pass** into a real **fail**. Resolve every such fail by driving the real flow, or by a reproduced `628F-BLOCKER` fixme. Never resolve it with a new soft path or a lint-evading swap (`.catch` removed but the soft `if` kept). 628E R-2 forbids that explicitly.
- Title honesty (628E R-12): no test may pass vacuously, and each title must describe what the test does.

## Baseline (failures + root causes)

**Fresh baseline on 43e8fddb2** (orchestrator, `/tmp/628F/base/`, fresh stack, run order lifecycle → depth-ii → default):

| Suite | Result |
|---|---|
| legal-lifecycle | **101 passed / 0 failed / 3 skipped** (the 3 `628E-BLOCKER` fixmes) |
| legal-depth-ii | **39 / 0 / 4** |
| default config (`pnpm test:e2e`) | **156 passed / 21 failed / 52 skipped / 97 did not run** (`/tmp/628F/base/default.log`) |

The 628E baseline on 2b442c0b8 was 197/15/56/52 (`qa_cycle/evidence/phase88-rewalk/628E/baseline-main/default.log`).

The 21 default-config failures:
- **13 legal** (re-run under the default config: conflict checks find the previous run's clients, the depth-ii serial chain lands on retries, and so on). All are fixed by `testIgnore`:
  - depth-ii: `day-05:448`, `day-14:350`, `day-30-45:138`;
  - lifecycle: `day-01-03:100`, `:303`, `:377`; `day-07-14:62`, `:197`; `day-30:90`; `day-45:96`; `day-60:96`; `day-75:91`; `day-90:69`, `:327`.
- **2 Keycloak-only scripts:** `demo-full-90day.spec.ts:151` and `demo-recording.spec.ts:23`. Both fail with `page.waitForURL(/\/realms\//)` timeout at `e2e/fixtures/keycloak-auth.ts:14` → OD-2.
- **6 non-legal failures.** Root causes and exact fixes are in the table below.

| # | Failure | Error (default.log) | Root cause (product evidence) | Exact fix |
|---|---|---|---|---|
| 1 | **PACK-01** `settings/packs.spec.ts:94` (assert at `:120`) | `getByText('Power of Attorney')` strict mode, 4 elements: link "Power of Attorney", its `<p>` description, link "Power of Attorney to Pass Transfer", its description | `APP/settings/documents/_tabs/templates-content.tsx` renders each template as `<a href=…/templates/{id}/edit>{name}</a>` plus a `<p class="text-xs">` description. `getByText` is a substring, case-insensitive match. | `:120` → `await expect(page.getByRole("link", { name: LEGAL_TEMPLATE_NAME, exact: true })).toBeVisible({ timeout: 10_000 });`. Test 2 (`:123`, did-not-run at baseline) will now run. Its tooltip check `/edited\|modified/` is safe: `TemplatePackInstaller` collects reasons with "edited" first (`$B/packs/TemplatePackInstaller.java:389-398`). The seeded SoA adds a "documents generated" reason, which the test already tolerates (`packs.spec.ts:170-174`). |
| 2 | **CUST-01** `customers/customer-crud.spec.ts:47` (fails `:61`) | `getByLabel('Name')` → 2 elements (`name`, `contactName`) | `CMP/customers/create-customer-dialog.tsx:314` "Name" / `:552` "Contact Name". `:350` "Email" vs `:565` "Contact Email". `:368` "Phone (optional)" vs `:578` "Contact Phone". `getByLabel` is substring + case-insensitive. | Scope to the dialog. Use `getByLabel("Name", { exact: true })`, `getByLabel("Email", { exact: true })` and `getByLabel(/^Phone/)`. Replace `createBtn.evaluate(el => el.click())` with a real click. Assert `dialog` hidden plus URL `/clients/<uuid>` plus `getByTestId("client-name")`. Full rewrite in Census › group B (CUST-01). Reference: `createClient()` at `e2e/helpers/legal-depth-ii.ts:194-227`. |
| 3 | **CUST-02** `customers/customer-lifecycle.spec.ts:99` (fails `:106`) | timeout waiting for `getByRole('button', { name: 'Change Status' })` | `CMP/compliance/LifecycleTransitionDropdown.tsx` has no importer since Epic 560A (#1395, 252e1e676). The client header now carries a smart action: `CMP/customers/client-header-card.tsx:28-51` (PROSPECT "Start Onboarding", ONBOARDING "Activate Client", ACTIVE/DORMANT "Edit", OFFBOARDING "Complete Offboarding") rendered as `data-testid="smart-primary-action"` (`:174-184`). It is wired in `client-header-card-with-lifecycle.tsx:70`, with target status from `APP/clients/[id]/page.tsx:433-470` (ONBOARDING/ACTIVE only). Confirm dialog: `CMP/compliance/TransitionConfirmDialog.tsx`, an alertdialog "Start Onboarding" with a "Start Onboarding" button. | Reuse `startClientOnboarding(page)` (`e2e/helpers/legal-depth-ii.ts:229-238`, quoted in Reference patterns). Rewrite the rest of CUST-02 per Census › group B; tests 7 and 8 are blockers → OD-4. |
| 4 | **PROJ-02** `projects/project-tasks.spec.ts:119` (fails `:128`) | timeout waiting for `getByRole('tab', { name: 'Tasks' })` | Only group triggers are `role="tab"` (`data-testid="tab-group-<g>"`). "Tasks" is a menu item `tab-item-tasks` in group `work` (`frontend/lib/constants/matter-tab-groups.ts:39-47`). The tabpanel has **no accessible name**, so `getByRole("tabpanel", { name: "Tasks" })` (`:129`) is a second latent failure. | `import { openGroupedTab } from "../../helpers/grouped-tabs";`. Every `getByRole("tab", { name: "Tasks" }).click()` (`:128, :145, :187, :219, :241, :259, :313`) becomes `await openGroupedTab(page, "work", "tasks");`, and `getByRole("tabpanel", { name: "Tasks" })` becomes `page.getByRole("tabpanel")`. The tests now run, so make their assertions honest: test 3 `/In Progress/i.first()` also matches the filter chip "In Progress" (`CMP/tasks/task-list-panel.tsx:41`); scope it to the row: `panel.getByRole("row").filter({ has: page.getByRole("button", { name: \`Open task detail for ${EDITED_TITLE}\` }) })` → `toContainText("In Progress")` (status badge `CMP/tasks/task-list-table-row.tsx:118`, label `task-badge-config.ts:19`). Test 7 `/Carol/.first()`: scope it to the "Assignable Task" row. Test 1's reload: `openGroupedTab` again after `page.reload()`. Remove `waitForTimeout(1000)` (`:143`); the dialog-hidden assertion already waits. |
| 5 | **PROJ-03** `projects/project-time.spec.ts:131` (fails `:140`) | same as #4 | same as #4. Additionally, "Time recording" (`:211`) is the menu item `tab-item-time` in group `finance`. | Test 1: `openGroupedTab(page, "work", "tasks")`. Then follow `logTimeOnTask` (`e2e/helpers/legal-lifecycle.ts:227-265`, quoted below): select the row by `Open task detail for ${TASK_TITLE}`, use `#time-hours` / `#time-minutes` (the hours and minutes inputs have no accessible name, `CMP/tasks/log-time-dialog.tsx:180-205`), `getByRole("textbox", { name: /^Description/ })`, then click "Log Time" and assert `dialog` hidden. Test 2: `openGroupedTab(page, "finance", "time")`, then `const panel = page.getByRole("tabpanel"); await expect(panel.getByRole("row").filter({ hasText: TASK_TITLE })).toContainText("3h");` (`CMP/projects/time-summary-panel.tsx:121-170` "By Task" table, `formatDuration(180)` = "3h", `packages/shared/src/format.ts:250-257`). Drop the loose `.or(/3h\|3:00\|180/)`. Test 5: `openGroupedTab(page, "work", "tasks")`, then the row's "Log Time" button, then `expect(dialog.getByRole("checkbox", { name: "Billable" })).toBeChecked()`. Test 7 (`/my-work`): replace the `main` / body checks with the page h1. |
| 6 | (baseline-only) CUST-01 `:47` and CUST-02 `:99` stop their serial chains. 4 + 8 tests did not run. | — | Once fixed, the downstream tests run and expose the class-c sites listed in the census. | Census › group B. |

**Skips that remain after this slice** (not sites; their reasons are documented and legitimate):
- `admin-audit-30day/admin-audit-lifecycle.spec.ts` ×8: `SKIP_REASON`, TODO 510B.
- `auth/auth-change-password.spec.ts:38`: KC-only.
- `portal/portal-data-isolation.spec.ts:33`: no `KGOSI_EMAIL`.

Leave them. They are not in the census.

## Site Census

### Method

1. ESLint ran from the main checkout (same code, `node_modules` present) with `--rule '{"kazi-frontend-local/no-isvisible-catch":"error"}' -f json e2e`. The `kazi-frontend-local` plugin is registered for `**/*` in `eslint.config.mjs:20-26`, so `--rule` turns it on everywhere. Output: `/tmp/628F-scout-eslint.json`.
2. The run produced **187 `swallowed` reports, all `isVisible`, in 28 files**, plus **65 `timeoutIgnored`**. Every `timeoutIgnored` sits inside a swallowed site's range: 0 standalone, 0 `.then(f, r)`.
3. No other ESLint errors showed up in `e2e/`. The rest of the tree is already lint-clean.
4. Three read-only sub-scouts then classified every site by reading the spec plus the product component, with file:line evidence for each.

### Classes

- **(a) Target exists.** Mechanical `expect(x).toBeVisible()`, then act unconditionally.
- **(b) Genuinely conditional.** Either the state is known from the seed, so the branch collapses, or use the sanctioned `expect(a.or(b).first()).toBeVisible()` and then branch.
- **(c) Target does not exist in the product.** Delete the branch, re-target it to the real element, or `test.fixme` it if it is a product blocker.
- **(d) Exists only with 628F seed data.**
- **(KC) Keycloak-only.** The demo or debug script cannot run on the mock stack (OD-2).

### Counts per class

| Group / files | sites | a | b | c | d | KC |
|---|---|---|---|---|---|---|
| A: documents ×2, information-requests, invoices ×3, automations ×2, compliance, finance ×2 | 54 | 19 | 6 | 24 | 5 | — |
| B: customers ×3, settings/data-protection-settings | 46 | 11 | 6 | 29 | 0 | — |
| C: settings ×5, notifications ×2, navigation/command-palette, projects ×2 | 64 | 31 | 2 | 30 | 1 | — |
| D: demo-full-90day, demo-recording, debug-kc-invite | 23 | — | — | — | — | 23 |
| **Total (28 files)** | **187** | **61** | **14** | **83** | **6** | **23** |

**Per-file counts:**

| file | sites | a/b/c/d | baseline (default, 628E run) |
|---|---|---|---|
| automations/automation-crud | 1 | 1/0/0/0 | -4 |
| automations/automation-triggers | 1 | 0/0/1/0 | -3 |
| compliance/compliance-overview | 1 | 0/1/0/0 | ✓2 |
| customers/customer-crud | 6 | 1/5/0/0 | ✘1 -4 |
| customers/customer-data-protection | 17 | 5/1/11/0 | ✓3 -2 |
| customers/customer-lifecycle | 3 | 0/0/3/0 | ✓1 ✘1 -8 |
| debug-kc-invite | 2 | KC | ✓1 |
| demo-full-90day | 19 | KC | ✘1 |
| demo-recording | 2 | KC | ✘1 |
| documents/document-generation | 17 | 1/1/12/3 | ✓1 -3 |
| documents/template-management | 17 | 14/3/0/0 | ✓4 |
| finance/profitability | 3 | 0/0/3/0 | ✓4 |
| finance/reports | 3 | 1/0/2/0 | ✓2 -1 |
| information-requests/information-request-crud | 6 | 0/1/3/2 | -5 |
| invoices/billing-runs | 2 | 0/0/2/0 | -2 |
| invoices/invoice-crud | 1 | 1/0/0/0 | ✓5 |
| invoices/invoice-lifecycle | 2 | 1/0/1/0 | ✓8 |
| navigation/command-palette | 7 | 5/0/2/0 | ✓3 |
| notifications/notification-bell | 8 | 5/1/2/0 | ✓5 |
| notifications/notification-preferences | 5 | 0/0/5/0 | ✓2 -1 |
| projects/project-budget | 4 | 0/0/4/0 | ✓3 |
| projects/project-crud | 4 | 2/0/2/0 | ✓7 -1 |
| settings/custom-fields | 11 | 8/0/3/0 | ✓1 -3 |
| settings/data-protection-settings | 20 | 5/0/15/0 | ✓6 -3 |
| settings/general-settings | 4 | 4/0/0/0 | ✓3 |
| settings/roles-capabilities | 7 | 0/0/7/0 | -3 |
| settings/tags | 9 | 4/1/4/0 | -3 |
| settings/tax-settings | 5 | 3/0/1/1 | -3 |

**(d) seed dependencies** (these must stay green with the seed and would fail without it):
- `documents/document-generation.spec.ts:229, :282, :298`: the Statement of Account on Website Redesign.
- `information-requests/information-request-crud.spec.ts:81, :128`: the SENT information request on Website Redesign.
- `settings/tax-settings.spec.ts:104`: an `INV-\d{4}` link, i.e. the APPROVED + SENT fee note.

### Cross-cutting findings (apply to many files)

- **X1. `isVisible({ timeout })` does not wait.** Playwright ignores the option, so every such call is an instant probe. Several baseline "passes" probe before hydration.
- **X2. `body.textContent()` + `includes("404")` / `"Something went wrong"` guards are probably always true.**
  - There is no root `not-found.tsx` (only `APP/matters/[id]/not-found.tsx`), and `textContent` includes inline `<script>` RSC payload text.
  - Evidence: every test whose only skip path is this guard is skipped at baseline (roles 1, tags 1, custom-fields 1–2, notification-preferences 1, automation-crud 1, billing-runs 1). notification-preferences tests 2–3 have the same flow without the "404" clause, and they pass.
  - This is inferred, not observed. Either way, **delete every such guard** and assert the page's h1 or a role landmark.
  - Files: information-request-crud ×5, billing-runs ×2, automation-crud ×4, automation-triggers ×3, compliance-overview, custom-fields ×4, tags ×2, roles ×3, tax, notification-preferences, data-protection-settings ×9, customer-data-protection.
- **X3. `page.locator('main, [role="main"], .flex-1').first()` resolves to the sidebar `<nav class="… flex-1 …">`** (`CMP/desktop-sidebar.tsx:101`), which comes before `<main>`. Every content check built on it is vacuous. Use `page.locator("#main-content")` (the org layout's `<main id="main-content">`) or a heading. `expect(page.locator("body")).toContainText(…)` also reads script text, so scope it.
- **X4. `customers.find(c => c.lifecycleStatus === "ACTIVE")` is nondeterministic.** Default-config files run in parallel workers, and other specs create ACTIVE "TestCust…" clients. Target `c.name === "Acme Corp"` when the test relies on seed data.
- **X5. Grouped tabs.** On matter and client pages only the group triggers are `role="tab"`. Sub-tabs are menu items, and the panel is unnamed.
  - Use `openGroupedTab(page, groupId, tabId)` from `e2e/helpers/grouped-tabs.ts`.
  - Matter groups (`frontend/lib/constants/matter-tab-groups.ts`):
    - `details` → `details` / `fields` / `team`
    - `overview`
    - `work` → `tasks` / `documents` / `correspondence` / `generated`
    - `finance` → `time` / `disbursements` / `budget` / `rates` / `financials` / `statements` / `trust`
    - `client` → `clients` / `requests` / `customer-comments` / `adverse-parties`
    - `schedule` → `court-dates`
    - `activity`
  - The default matter tab is overview (`lib/constants/tab-group-types.ts:40`).
  - Client groups: `lib/constants/client-tab-groups.ts`.
- **X6. Removed or never-built pages and controls:**
  - `/org/[slug]/documents` was removed in #814; only orphan `loading.tsx` and `actions.ts` remain.
  - `/org/[slug]/information-requests` (the list page) never existed. The lists live on the matter and client `?tab=requests`.
  - The "Change Status" dropdown is orphaned.
  - System roles have no edit UI (`APP/settings/firm/_tabs/roles.tsx:64-104`, "cannot be modified").
  - There are no inner tabs on Settings › Data protection.
  - The profitability page has no tabs, only three cards.
- **X7. The default config is not `fullyParallel`.** Files run in parallel across workers, but tests inside a file run in order. Each test needs **run-unique names** (`RUN_ID`) and must not assume list emptiness, because sibling files create clients, matters, tags and fee notes.

### Group D — Keycloak-only scripts (23 sites; disposition in OD-2)

| file:line | guarded locator | note |
|---|---|---|
| `debug-kc-invite.spec.ts:45, :51` | KC registration `emailField`, `firstNameField` | Debug probe: `console.log`s Mailpit at hard-coded `:8025` (the dev stack), no assertions; "passes" on the mock stack because the email lookup is wrapped in `try/catch` |
| `demo-recording.spec.ts:130, :136` | trust `transactionsTab`, `ledgersTab` | KC recording script for org `mathebula-partners` (`:12`); `loginAs(page, "thandi@mathebula-test.local", …)` from `fixtures/keycloak-auth`; 8 more bare `if (await x.isVisible())` at `:48,:60,:78,:86,:93,:100,:107,:148` (not lint-reported, same vacuity) |
| `demo-full-90day.spec.ts:63, :71, :108, :120, :125, :219, :225, :229, :328, :341, :368, :372, :386, :405, :421, :425, :449, :465, :469` | KC registration fields, platform-admin "Approve", "New Client", "New Matter", template options, `searchInput` | KC "zero to hero" recording (access request → admin approval → KC registration → firm setup); prerequisites `compose/scripts/demo-cleanup.sh` + `E2E_AUTH_MODE=keycloak … --headed` (header `:11-17`); every flow step is an `if (visible)` guard |

Git: substantive edits only in #1029 (2026-04-14). Since then there have been mechanical renames only: 610G #1611, 611F #1617. These scripts are not in any gate, and they cannot be verified on the mock stack.

### Group A — per-site table (documents, information-requests, invoices, automations, compliance, finance)

#### documents/document-generation.spec.ts — 17 sites (baseline: ✓1, -3)

| line | test (title, start) | usage | guarded locator | class | evidence | replacement |
|---|---|---|---|---|---|---|
| 65 | Generate document… (:39) | C | client page `button /Generate Document\|Generate/` | c (+BLOCKER) | No such button on the client page. "Generate Document" is a **menuitem** inside the `client-overflow-trigger` menu (`components/customers/client-overflow-menu.tsx:117-126`). Selecting it does `router.push(...?action=generate-document)`, and **nothing reads `action`**: a grep for `get("action")` across app/components/lib finds no consumer. The readiness card's "Generate" is a *link* to `?generateTemplate=` (`components/setup/template-readiness-card.tsx:30-32`). Its only consumer is `GenerateDocumentDropdown.tsx:57-68`, which the client page does not mount. Epic 560A (#1395, 252e1e676) removed the dropdown from the client page. | Re-target test 1 to the matter page (see :80/:93). Add a separate `test.fixme(true, "628F-BLOCKER: client Generate Document menu item routes to ?action=generate-document with no handler — components/customers/client-overflow-menu.tsx:120")` for the client-level path. |
| 80 | same | B | matters list link filtered `/Annual\|Tax\|Audit/` | c | The legal-za seed has only "Website Redesign". No matter matches the filter (the accounting seed was removed in Phase 86). | `await page.goto(\`${BASE}/matters/${websiteRedesignId}\`)` (id via `GET /api/matters` by name), or `page.getByRole("link", { name: "Website Redesign" }).first().click()` |
| 93 | same | B | matter page `button /Generate Document\|Generate/` | a | `components/projects/overflow-actions-menu.tsx:83-92` mounts `GenerateDocumentDropdown` (canManage + PROJECT templates; legal-za has 12 PROJECT templates, `backend/src/main/resources/template-packs/legal-za/pack.json`). Button text is "Generate Document" (`components/templates/GenerateDocumentDropdown.tsx:82-91`). A "Generate Statement of Account" button follows it, so use the exact name. | `const gen = page.getByRole("button", { name: "Generate Document" }); await expect(gen).toBeVisible(); await gen.click();` |
| 111 | same | C | `getByRole("dialog")` right after the trigger click | c | The trigger opens a DropdownMenu (role=menu) of template names, not a dialog (`GenerateDocumentDropdown.tsx:93-131`). The dialog opens only after a menuitem is chosen and the prerequisite check passes (`:97-121`, 150 ms setTimeout). | `await page.getByRole("menuitem", { name: "Notice of Motion" }).click();` (no clause associations; clause packs only link engagement letters/conveyancing). Then `const dialog = page.getByRole("dialog", { name: /Generate: Notice of Motion/ }); await expect(dialog.or(page.getByRole("dialog", { name: /prerequisite/i })).first()).toBeVisible({ timeout: 15_000 });` |
| 119 | same | C | dialog combobox/select (template picker) | c | There is no template select in `GenerateDocumentDialog`; the template is picked in the menu. | Delete the branch. |
| 128 | same | C | dialog button `/Generate\|Create/` | c | The dialog footer has "Download PDF" / "Save to Documents" (or "…anyway" when there are warnings) (`components/templates/GenerateDocumentDialog.tsx:347-366`). There is no Generate/Create button. | `await dialog.getByRole("button", { name: /Save to Documents\|Save anyway/ }).click(); await expect(dialog.getByText(/saved/i).or(dialog.getByRole("button", { name: "Close" })).first()).toBeVisible();` then assert the row in `?tab=generated` |
| 164 | Preview shows resolved variables (:138) | B | client page Generate button | c (+BLOCKER) | Same as :65. | Re-target to the matter page plus the :93 and :111 flow, or fixme with the same BLOCKER. |
| 175 | same | C | dialog after trigger | c | Same as :111. | As :111 |
| 180 | same | C | dialog combobox | c | Same as :119. | Delete |
| 186 | same | C | dialog button `/Generate\|Create\|Preview/` | c | The preview auto-loads in the no-clause flow (`GenerateDocumentDialog.tsx:140-146`) into an **iframe** `title="Document Preview"` (`components/documents/a4-preview-wrapper.tsx:72-76`). | `const frame = dialog.frameLocator('iframe[title="Document Preview"]'); await expect(frame.locator("body")).not.toHaveText(/\{\{.*?\}\}/); await expect(frame.locator("body")).toContainText("Acme Corp");` and replace the :194-201 innerText block |
| 209 | Download PDF (:204) | B | `/documents` h1 `/Documents/` | c | The `/org/[slug]/documents` page was removed in Epic 393B (#814, eada92ff0). Only orphan `documents/loading.tsx` and `actions.ts` remain, so the route 404s and the test always skips. | Re-target: `page.goto(\`${BASE}/matters/${websiteRedesignId}?tab=generated\`)`. Tab id "generated" is in `lib/constants/matter-tab-groups.ts:46`; the panel is `app/(app)/org/[slug]/matters/[id]/page.tsx:597-605`. |
| 229 | same | C/B | Download link/button | d | Needs seed: **Statement of Account generated on Website Redesign**. StatementService persists a PROJECT GeneratedDocument linked to a Document (`backend/.../verticals/legal/statement/StatementService.java:137-172`), so it is a "saved" row. The row has an icon button `title="Download PDF"` (`components/templates/GeneratedDocumentsList.tsx:401-410`). The blob anchor click fires a download (`:184-210`). | `const row = page.getByRole("row", { name: /Statement of Account/ }); await expect(row).toBeVisible(); const dl = page.waitForEvent("download"); await row.getByRole("button", { name: "Download PDF" }).click(); expect((await dl).suggestedFilename()).toMatch(/\.pdf$/);` |
| 266 | Generated documents list (:261) | B | `/documents` h1 | c | Same as :209. | Same re-target as :209 |
| 282 | same | A | `getByRole("table")` | d | Needs the SoA seed. The table renders when there are ≥1 saved docs (`GeneratedDocumentsList.tsx:302-307`). | `await expect(page.getByTestId("generated-documents-list").getByRole("table")).toBeVisible()` |
| 287 | same | A | empty state `/No.*documents/` | b | Two empty states exist: "No documents generated yet" (`GeneratedDocumentsList.tsx:274`) and "No saved documents — N unsaved hidden." (`:303-305`). The state is known from the seed (SoA saved), so collapse to the table. | Delete (the table assertion covers it) |
| 298 | same | C | `getByText("File")` | d | Substring match on the "File Size" columnheader (`GeneratedDocumentsList.tsx:312`), which exists only with a row (SoA seed). | `await expect(page.getByRole("columnheader", { name: "Template Name" })).toBeVisible(); await expect(page.getByRole("columnheader", { name: "File Size" })).toBeVisible();` |
| 303 | same | C | `getByText("Status")` | c | There is no Status column. Headers are Template Name / Generated By / Date / File Size / Format / [Acceptance] / Actions (`GeneratedDocumentsList.tsx:309-316`). | Delete. Optionally assert `getByRole("cell", { name: /Statement of Account/ })`. |

**Other soft patterns:** `waitForTimeout` ×4 (:108, :130, :172, :188). Runtime `test.skip(` ×8 (:52, :83, :98, :151, :167, :214, :239, :271). The :194-201 check is tautological: `if (bodyText.includes("Customer")||…) expect(hasUnresolved)…`, but the preview is inside an iframe, so `body.innerText` never sees template text. `expect(hasTable || hasEmptyState)` at :294 and `expect(hasFileColumn || hasStatusColumn)` at :308. :246 `waitForEvent("download").catch(() => null)` + `if (download)` is soft. :233-241 API fallback `GET /api/generated-documents` without the required `entityType`/`entityId` params (`backend/.../template/GeneratedDocumentController.java:41-44`) returns 400, which leads to a skip. X3 applies at :47/:146.
**Expected outcome after conversion:** Tests 1 and 2 run for real on the matter page. They should pass unless the prerequisite gate blocks; branch with `.or()` on the prereq modal and record what is observed. The client-level generation path needs a `test.fixme` (BLOCKER). Tests 3 and 4 run for real against Website Redesign `?tab=generated` and **depend on the 628F SoA seed**. Without the seed they fail at :229/:282. Currently 0 of 4 tests exercise generation.

---

#### documents/template-management.spec.ts — 17 sites (baseline: ✓4)

`/settings/templates` 308-redirects to `/settings/documents?tab=templates` (`lib/legacy-redirects.ts:184-187`).

| line | test | usage | locator | class | evidence | replacement |
|---|---|---|---|---|---|---|
| 28 | Template list… (:22) | B | `h1` hasText Templates | a | `app/(app)/org/[slug]/settings/documents/_tabs/templates.tsx:26-29` | `await expect(page.locator("h1").filter({ hasText: "Templates" })).toBeVisible({ timeout: 10_000 })` |
| 39 | same | A | first table | a | `_tabs/templates-content.tsx:132`; legal-za pack seeds 17 templates | `await expect(page.getByRole("table").first()).toBeVisible()` |
| 44 | same | A | `/No templates/` | b | The EmptyState (`templates-content.tsx:111-123`) cannot appear with the seeded pack. The state is known, so collapse. | Delete |
| 48 | same | A | names `/Engagement Letter\|…/` | a | Category h2 "Engagement Letter" (`templates-content.tsx:40,127-129`) and names "Engagement Letter — Litigation" etc. | `await expect(page.getByRole("link", { name: "Engagement Letter — General" })).toBeVisible()` |
| 66 | same | A | `[data-testid=format-filter]` | a | `templates-content.tsx:82` | `await expect(page.getByTestId("format-filter")).toBeVisible()` |
| 75 | Create new template (:70) | B | h1 Templates | a | As :28 | As :28 |
| 83 | same | B | link `/New Template/` | a | `templates-content.tsx:101-106` (Link wraps Button; canManage) | `const link = page.getByRole("link", { name: "New Template" }); await expect(link).toBeVisible(); await link.click();` |
| 105 | same | C | `getByLabel(/Name/i)` | a | `components/templates/CreateTemplateForm.tsx:84-89` (`#new-template-name`, placeholder "Template name") | `await page.getByLabel("Name", { exact: true }).fill(name)`. Then, to make this a real create, click "Create Template" (`CreateTemplateForm.tsx:172-173`) and assert the edit URL or a list row. The builder must observe which category/entity defaults the form needs. |
| 120 | Clone template (:115) | B | h1 | a | As :28 | As :28 |
| 129 | same | B | first table | a | As :39 | As :39 |
| 151 | same | B | row `getByRole("button").last()` | a | Actions trigger `templates-content.tsx:197-198` → `components/templates/TemplateActionsMenu.tsx:70-73` (unnamed icon button) | `const row = page.getByRole("row").filter({ has: page.getByRole("link", { name: "Letter of Demand", exact: true }) }); await row.getByRole("button").last().click();` |
| 162 | same | B | menuitem `/Clone/` | a | "Clone & Customize" is shown only for `source === "PLATFORM"` (`TemplateActionsMenu.tsx:98-103`). Today's "first row" is the alphabetically first category (`templates-content.tsx:70` sorts category keys → CLOSURE → "Matter Closure Letter", PLATFORM). Pin the row by exact name (see :151) so a prior clone never takes row 1. | `await page.getByRole("menuitem", { name: "Clone & Customize" }).click(); await expect(page).toHaveURL(/\/settings\/documents\/templates\/[^/]+\/edit/);` Clone router-pushes to the edit page (`TemplateActionsMenu.tsx:34-36`). Then assert `getByRole("button", { name: "Save" })` is visible (custom ⇒ editable). |
| 185 | Edit template content (:180) | B | h1 | a | As :28 | As :28 |
| 198 | same | B | first table link | a | `templates-content.tsx:147-152` | `const link = page.getByRole("link", { name: "Letter of Demand", exact: true }); await expect(link).toBeVisible(); await link.click();` |
| 217 | same | A | button `/Save/` | b | Save renders only when `!readOnly` (`components/templates/TemplateEditorClient.tsx:273-277`). `readOnly = !isAdmin \|\| source === "PLATFORM"` (`settings/documents/templates/[id]/edit/page.tsx:38`). The first link is PLATFORM ⇒ no Save. The state is known (PLATFORM). | Either assert the read-only contract (`expect(page.getByRole("button", { name: "Save" })).toHaveCount(0)`), or open the ORG_CUSTOM clone from test 3, then `getByLabel("Template name").fill(...)` + Save for a real edit |
| 221 | same | A | `.ProseMirror` | a | DocumentEditor is always mounted (`TemplateEditorClient.tsx:411-418`, `editable={!readOnly}`) | `await expect(page.locator(".ProseMirror").first()).toBeVisible()` |
| 226 | same | A | `getByLabel(/Name\|Content/)` | b | Read-only shows the name as an h1 (`TemplateEditorClient.tsx:204-208`). The editable input is `aria-label="Template name"` (`:210-216`). The settings "Name" label (`template-editor-settings.tsx:80`) sits in the collapsed panel. Present only for custom templates. | Custom: `await expect(page.getByLabel("Template name")).toBeVisible()`; Platform: delete |

**Other soft patterns:** `waitForTimeout` ×1 (:170). Runtime `test.skip(` ×10. Tautologies: `expect(hasTable||hasEmptyState||hasTemplateNames)` :56, `expect(hasFilter).toBeTruthy()` :67, `expect(hasSave||hasEditor||hasFormInputs)` :232. `rowCount === 0` guard :142. Test 2 never submits. Test 3 asserts nothing about the clone (re-navigates and checks "no error").
**Expected outcome:** All 4 tests should still pass after the mechanical conversion. Turning tests 2, 3 and 4 into real create/clone/edit flows needs browser observation (Create form defaults; Clone may 409 on a non-fresh stack). The info-request and invoice seeds have no impact.

---

#### information-requests/information-request-crud.spec.ts — 6 sites (baseline: -5)

**The `/org/[slug]/information-requests` list page has never existed.** The directory holds only `[id]/page.tsx` + `[id]/actions.ts` (`git log --all` shows no `information-requests/page.tsx` ever), so every test ends on a 404. The X2 guards also fire. The real list surfaces are the matter/client tab `?tab=requests` ("Information requests", `lib/constants/matter-tab-groups.ts:69`, `client-tab-groups.ts:62`). They are rendered by `app/(app)/org/[slug]/matters/[id]/page.tsx:607-622` (needs a linked customer) and `clients/[id]/page.tsx:700-706`.

| line | test | usage | locator | class | evidence | replacement |
|---|---|---|---|---|---|---|
| 47 | Create information request (:36) | B | button `/new\|create\|add/` | c | Page missing. The real control is "New Request" (`components/information-requests/create-request-dialog.tsx:220-224`) on the requests tab. | `await page.goto(\`${base}/matters/${websiteRedesignId}?tab=requests\`); const btn = page.getByRole("button", { name: "New Request" }); await expect(btn).toBeVisible(); await btn.click();` |
| 59 | same | C | dialog | c | Unreachable today. After the re-target the dialog is "Create Information Request" (`create-request-dialog.tsx:226-228`). | `const dialog = page.getByRole("dialog", { name: "Create Information Request" }); await expect(dialog).toBeVisible();` |
| 61 | same | C | dialog first textbox (title) | c | There is no title field. The fields are Template select (`:243-246`), Portal Contact select (`:264-272`), Reminder (number), Due Date. The ad-hoc item name textbox appears only for an ad-hoc template. | Pick a template plus the contact "contact@acme…" via combobox/option, click "Save as Draft" (`:466-478`), then assert a new `REQ-\d{4}` row with "Draft" |
| 81 | Send information request (:67) | B | link filtered `/request\|info/` | d | Needs seed: **SENT info request to Acme's portal contact on Website Redesign**. The row link text is the request number, `REQ-%04d` (`components/information-requests/request-list.tsx:58-63`; `backend/.../informationrequest/RequestNumberService.java:25`). The filter `/request\|info/i` would NOT match "REQ-0001", but it WOULD match the sidebar "Data requests" link (`lib/nav-items.ts:164`). | On the requests tab: `const link = page.getByRole("link", { name: /^REQ-\d{4}$/ }).first(); await expect(link).toBeVisible(); await link.click(); await expect(page).toHaveURL(/information-requests\//);` |
| 93 | same | C | button `/send/` | b | "Send Request" exists only for DRAFT (`components/information-requests/request-detail-client.tsx:184-192`). The seed is SENT, so this reaches the SENT state, where the overflow menu has a "Resend Notification" menuitem (`:194-206`). The trigger is an **unnamed** icon button (`:196-199`). | Real send: open the DRAFT created in test 2 (or create one via "Save as Draft"), click "Send Request", then `expect(page.getByText("Sent", { exact: true })).toBeVisible()`. For the seeded SENT row: `expect(page.getByRole("button", { name: "Send Request" })).toHaveCount(0)` + open the overflow → `getByRole("menuitem", { name: "Resend Notification" })` visible |
| 128 | Track completion status (:114) | B | same link | d | Same seed row as :81 | As :81, then assert the "Progress" block (`request-detail-client.tsx:263-270`) |

**Other soft patterns:** body `textContent` + `includes("404")` guards at :27, :40, :71, :104, :118 (X2). `waitForTimeout` ×3 (:55, :89, :136). Runtime `test.skip(` ×8. Tests 1 and 4 only assert `main` visible ("Portal view" never touches the portal). Test 2 never submits.
**Seed impact (628F):** Today, none, because the page doesn't exist and the tests skip. After the re-target, the seeded SENT request is the only reason tests 3 and 5 find a row (otherwise "No information requests yet", `request-list.tsx:15-21`). Test 3's "send" becomes a SENT-state check unless the test creates its own DRAFT. Tests that count rows must not assume an empty list: the tab will show ≥1 row.
**Expected outcome:** All 5 tests become real once re-targeted to `?tab=requests`. Expected to pass if the create dialog loads contacts (Acme portal contact auto-provisioned). There is no product blocker; the spec targets a page that was never built.

---

#### invoices/billing-runs.spec.ts — 2 sites (baseline: -2)

| line | test | usage | locator | class | evidence | replacement |
|---|---|---|---|---|---|---|
| 57 | New billing run wizard (:42) | B | button `/new\|create\|start/` | c | Wrong role. "New Billing Run" is a **link** (`app/(app)/org/[slug]/invoices/billing-runs/page.tsx:56-61`; the EmptyState action at `:101-107` is a second link). No button on the page matches. `bulk_billing` is enabled for legal-za (`backend/src/main/resources/vertical-profiles/legal-za.json:5`). | `const link = page.getByRole("link", { name: "New Billing Run" }).first(); await expect(link).toBeVisible(); await link.click(); await expect(page).toHaveURL(/billing-runs\/new/);` |
| 69 | same | C | dialog | c | The wizard is a page, not a dialog (`invoices/billing-runs/new/page.tsx:38-39`) | `await expect(page.getByRole("heading", { level: 1, name: "New fee run" })).toBeVisible(); await expect(page.getByRole("heading", { name: "Configure Billing Run" })).toBeVisible();` (`components/billing-runs/configure-step.tsx:64-65`) |

**Other soft patterns:** X2 guards (:24-34, :44-53). They also retry a non-existent `/billing-runs`. `waitForTimeout` ×1 (:63). Runtime `test.skip(` ×3. Test 1 asserts only `text.length > 0`; replace with h1 "Fee runs" (`billing-runs/page.tsx:55`).
**Seed impact:** None (no billing-run rows are seeded; the invoices are not runs).
**Expected outcome:** Both tests run and should pass. Note the label inconsistency "New Billing Run" / "Configure Billing Run" vs h1 "Fee runs" (terminology remnant, not a blocker).

---

#### invoices/invoice-crud.spec.ts — 1 site (baseline: ✓5)

| line | test | usage | locator | class | evidence | replacement |
|---|---|---|---|---|---|---|
| 217 | Remove line item from draft (:178) | A (negative) | `getByText("Line to remove")` | a | Line descriptions render in `components/invoices/invoice-line-table.tsx:118`. The instant `isVisible` probe (X1) makes today's negative check vacuous before hydration. | `await expect(page.getByText("Updated consulting").first()).toBeVisible({ timeout: 10_000 }); await expect(page.getByText("Line to remove")).toHaveCount(0);` |

**Other soft patterns:** None (the other assertions are web-first). X3: `findActiveCustomer` (:43-55) picks an arbitrary ACTIVE client. This is harmless here because the test creates its own invoice.
**Seed impact (628F Acme DRAFT + APPROVED/SENT):** None. Every assertion targets the invoice id the test created; no list or count assertions.
**Expected outcome:** Passes, and is now a real negative check.

---

#### invoices/invoice-lifecycle.spec.ts — 2 sites (baseline: ✓8)

| line | test | usage | locator | class | evidence | replacement |
|---|---|---|---|---|---|---|
| 148 | APPROVED -> SENT (:128) | B | button `/Send fee note/` | a | `components/invoices/invoice-header-actions.tsx:80-85` (APPROVED). The branch runs only if the API send fails, which it doesn't (baseline ✓). | Drop the fallback: `expect(sendRes.ok).toBeTruthy()`. Or make the UI primary: click "Send fee note", then handle the override path (`components/invoices/use-invoice-detail.ts:179-206`) |
| 281 | VOID a sent invoice (:250) | C | `getByRole("dialog")` after Void | c | **The test is a vacuous pass.** Void confirms with native `window.confirm` (`components/invoices/use-invoice-detail.ts:225-226`), not a role=dialog. Playwright auto-dismisses native dialogs ⇒ `confirm()` returns false ⇒ **the invoice is never voided**. The final `getByText(/Void/i).first()` (:289) matches the still-present **"Void" button** (`invoice-header-actions.tsx:97-101`). | Before the click: `page.once("dialog", (d) => d.accept());` then `await voidBtn.click(); await expect(page.getByRole("button", { name: "Void" })).toHaveCount(0); await expect(page.getByText("Void", { exact: true })).toBeVisible();` and an API check `status === "VOID"` (badge label `components/invoices/status-badge.tsx:12`) |

**Other soft patterns:** `waitForTimeout` ×3 (:122, :156, :287). Runtime `test.skip(` ×4 (:151, :174, :190, :194). Tautology :313-316 `expect(true).toBeTruthy()`, and :308-312 asserts only `status === "VOID"` on a row it selected by VOID. The "VOID releases time entries" test checks nothing: the invoices are created with `timeEntryIds: []`. `/Sent/i` and `/Draft/i` with `.first()` also match the h1 "Draft fee note" / buttons.
**Seed impact:** None on outcomes. :298-305 lists `/api/invoices` and picks the first VOID. The 628F seed adds DRAFT + SENT (no VOID). Today no VOID exists anywhere because of the :281 bug. After the fix it finds the test's own voided invoice.
**Expected outcome:** :148 passes. :281 becomes a real void and should pass if backend void works; if it fails, that is a real defect surfaced. Mark "VOID releases time entries" fixme/rewrite (no time entries).

---

#### automations/automation-crud.spec.ts — 1 site (baseline: -4)

| line | test | usage | locator | class | evidence | replacement |
|---|---|---|---|---|---|---|
| 118 | View execution history (:99) | B | `a[href*="executions"]` etc. | a | `/settings/automations` → `/settings/connections?tab=automations` (`lib/legacy-redirects.ts:280-282`). The "View Execution Log →" link has href `/settings/connections/automations/executions` (`app/(app)/org/[slug]/settings/connections/_tabs/automations.tsx:43-50`, admin only). | `const link = page.getByRole("link", { name: /View Execution Log/ }); await expect(link).toBeVisible(); await link.click(); await expect(page.getByRole("heading", { level: 1, name: "Execution Log" })).toBeVisible();` (`connections/automations/executions/page.tsx:63`) |

**Other soft patterns:** X2 guards in all 4 tests (the likely cause of the 4 skips). `toggles.count() === 0` skip guards (:59, :86): 4 rules are seeded from `backend/src/main/resources/automation-templates/legal-za.json` via `AutomationTemplateSeeder.java:118-136`, and the switches are `aria-label="Toggle <rule name>"` (`components/automations/rule-list.tsx:101-109`). `waitForTimeout` ×3 (:69, :96, :126). Runtime `test.skip(` ×7. The Disable and Enable tests click and assert nothing. Replace with `const sw = page.getByRole("switch", { name: "Toggle Matter Onboarding Reminder" }); const was = await sw.getAttribute("aria-checked"); await sw.click(); await expect(sw).toHaveAttribute("aria-checked", was === "true" ? "false" : "true");` (plus `seedEnabled` may leave some rules off).
**Expected outcome:** All 4 run. Test 1 should pass with an h1 "Automations" assert (`_tabs/automations.tsx:39`). Toggles should pass if the toggle action persists. The executions page should pass.

---

#### automations/automation-triggers.spec.ts — 1 site (baseline: -3)

| line | test | usage | locator | class | evidence | replacement |
|---|---|---|---|---|---|---|
| 56 | Customer status change fires automation rule (:30) | C | `[role=tab]:has-text("History"), a:has-text("History"), [role=tab]:has-text("Executions")` | c | There is no tab or anchor with "History"/"Executions" text on the Automations tab. The only entry is "View Execution Log →" (`_tabs/automations.tsx:43-50`), and the settings nav has no History item. | Re-target as automation-crud :118 (link "View Execution Log" → h1 "Execution Log") |

**Other soft patterns:** X2 guards ×3 tests. `waitForTimeout` ×1 (:58). Runtime `test.skip(` ×4. `toggles.count()` :102. **None of the three tests checks what its title claims** ("fires automation rule" / "does NOT fire"). Each asserts only "no 'Something went wrong'". Recommend `test.fixme` on the titles or a rewrite: trigger the event via API, then poll the execution log.
**Expected outcome:** After the X2 guards are removed, all 3 run and pass vacuously unless rewritten. Flag for the orchestrator: honest titles or fixme.

---

#### compliance/compliance-overview.spec.ts — 1 site (baseline: ✓2)

| line | test | usage | locator | class | evidence | replacement |
|---|---|---|---|---|---|---|
| 41 | Compliance requests list (:30) | C | in-main `[role=tab]:has-text("Requests"), a:has-text("Requests")` | b | Tabs are only "Overview"/"AI Audit" (`app/(app)/org/[slug]/compliance/page.tsx:79-82`). The "View All Requests" link renders only when urgent DSARs exist (`components/compliance/DataRequestsSection.tsx:47,105-110`), and the seed has none; parallel data-protection specs may create some. In the else path, the X2 guard at :48 likely bounces back to `/compliance`, so **the requests page is never actually asserted**. | Collapse: `await page.goto(\`${base}/compliance/requests\`); await expect(page.getByRole("heading", { level: 1, name: "Data Requests" })).toBeVisible();` (`compliance/requests/page.tsx:55`; admin-gated `:22-31`) |

**Other soft patterns:** `waitForTimeout` ×1 (:43). Body textContent "404" guard :47-48. Test 1 asserts `text.length > 0`; replace with h1 "Compliance" (`compliance/page.tsx:76`).
**Expected outcome:** Both pass.

---

#### finance/profitability.spec.ts — 3 sites (baseline: ✓4)

`/profitability` → `/reports/profitability` (`lib/legacy-redirects.ts:44-48`). The page has **no tabs**: three stacked cards. Page: `app/(app)/org/[slug]/reports/profitability/page.tsx:69-85` shows an EmptyState "No profitability data yet" (`lib/messages/en/empty-states.json:62`) when there are no utilization members and no matters. Utilization comes only from `time_entries` (`backend/.../report/ReportRepository.java:244-261`), so the state depends on whether other specs logged time.

| line | test | usage | locator | class | evidence | replacement |
|---|---|---|---|---|---|---|
| 43 | Project profitability view (:33) | C | tab/button/link "Project" | c | Nothing labelled "Project". The card is "Matter Profitability" (`components/profitability/project-profitability-table.tsx:139-142`). | `await expect(page.getByText("Matter Profitability").or(page.getByText("No profitability data yet")).first()).toBeVisible({ timeout: 15_000 }); if (await page.getByText("Matter Profitability").isVisible()) { await expect(page.getByTestId("profitability-export-csv")).toBeVisible(); }` |
| 63 | Customer profitability view (:53) | C | tab/button/link "Customer" | c | The card is "Client Profitability" (`components/profitability/customer-profitability-section.tsx:163`). | Same `.or` pattern with "Client Profitability" |
| 82 | Utilization metrics (:72) | C | tab/button/link "Utilization" | c | "Team Utilization" is a CardTitle div, not a tab/button/link (`components/profitability/utilization-table.tsx:107-110`). | Same `.or` pattern with "Team Utilization" |

**Other soft patterns:** `waitForTimeout` ×3 (:45, :65, :84). `text.length > 0` (:30, :91). Test 1: replace with h1 "Profitability" (`reports/profitability/page.tsx:56`).
**Seed impact:** The 628F invoices don't create time entries, so they don't change empty vs content (the matters list may change if the profitability query counts revenue; not verified).
**Expected outcome:** All 4 pass. The branches are deterministic via `.or`. Seed time entries if the content path must be exercised.

---

#### finance/reports.spec.ts — 3 sites (baseline: ✓2, -1)

| line | test | usage | locator | class | evidence | replacement |
|---|---|---|---|---|---|---|
| 41 | Run a report with parameters (:33) | C | button `/run\|generate\|view/` on `/reports` | c | The hub has only link cards (`app/(app)/org/[slug]/reports/page.tsx:10-33,72-76,98-104`). There is no such button. | Go to a definition: `await page.goto(\`${base}/reports/timesheet\`)` (slugs `timesheet`/`invoice-aging`/`project-profitability`, `backend/.../reporting/StandardReportPackSeeder.java:337-339`). Fill the required date params by label (`components/reports/report-parameter-form.tsx:45-46,73-76`), click `getByRole("button", { name: "Run Report" })` (`:143`), then assert the results table / "no rows" |
| 44 | same | C | `getByRole("link").first()` | a | It exists, but it is the first sidebar link. The follow-up `a[href*="report"]` `.first()` (:46-48) hits the sidebar "Reports" nav link, i.e. the same page. | Replace with `page.getByRole("link", { name: /Profitability/ }).first()` in main or the direct goto above |
| 63 | Export report to CSV (:57) | B | button `/export\|csv\|download/` on hub | c | The hub has no export. The real "Export CSV" lives in the report runner, disabled until a run (`components/reports/report-runner.tsx:106-116`), and the download goes through an anchor click (`:60-68`). | On `/reports/invoice-aging`: fill "As of Date", Run Report, then `const dl = page.waitForEvent("download"); await page.getByRole("button", { name: "Export CSV" }).click(); expect((await dl).suggestedFilename()).toBe("invoice-aging-report.csv");` |

**Other soft patterns:** `waitForTimeout` ×2 (:43, :49). `.count() > 0` on broad `a[href*="report"]` (:47). Runtime `test.skip(` ×1 (:66). `waitForEvent("download").catch(() => null)` + `if (download)` (:71-77). Test 1 asserts `text.length > 0`; replace with h1 "Reports" (`reports/page.tsx:61`).
**Seed impact:** The 628F APPROVED+SENT Acme invoice gives `invoice-aging` a non-empty row (optional (d)-flavour, not required for the export to work).
**Expected outcome:** All 3 run for real. Export should pass. Run-report is untested in a browser (needs observation).


**Group A product blocker (source-proven; reproduce before fixme):** client-level document generation is unreachable — client overflow "Generate Document" pushes `?action=generate-document` (`CMP/customers/client-overflow-menu.tsx:117-126`, push `:120`) and nothing reads `action` (grep of app/components/hooks/lib: no consumer); the readiness-card "Generate" link `?generateTemplate=<id>` (`CMP/setup/template-readiness-card.tsx:31`) is only consumed by `GenerateDocumentDropdown.tsx:57-68`, which the client page no longer mounts (Epic 560A #1395). Affects document-generation `:65`, `:164` (→ OD-4).

### Group B — per-site table (customers ×3, settings/data-protection-settings)

Product facts used throughout:
- Client header: `components/customers/client-header-card.tsx:28-51` smart action (PROSPECT "Start Onboarding", ONBOARDING "Activate Client", ACTIVE/DORMANT "Edit", OFFBOARDING "Complete Offboarding", OFFBOARDED/ANONYMIZED none), button `data-testid="smart-primary-action"` :174-184 with `onClick={onPrimaryAction}`.
- `components/customers/client-header-card-with-lifecycle.tsx:70` passes `onPrimaryAction={targetLifecycleStatus ? handlePrimaryAction : undefined}`; `targetLifecycleStatus` = `lifecycleActionPrompt?.targetStatus` (`app/(app)/org/[slug]/clients/[id]/page.tsx:525`), which is only ONBOARDING (from PROSPECT) or ACTIVE (from ONBOARDING with checklist complete/absent) and only for admins with record status ACTIVE (`page.tsx:434-470`).
- Overflow menu `components/customers/client-overflow-menu.tsx`: trigger `data-testid="client-overflow-trigger"` (sr-only "More actions") :89-98; menuitems "Edit Client" :101-106 (hidden when anonymized, :74), "Export Data" :152-155 (always), "Anonymize" :159-166 (owner && !anonymized, :79), "Archive" :169-176. **No lifecycle-transition items.**
- `components/compliance/LifecycleTransitionDropdown.tsx` ("Change Status") has **zero importers** (removed from the page by 252e1e676, Epic 560A #1395).
- Clients list: name cells are links (`components/clients/clients-table.tsx:165-172`), rows `data-testid="client-row"` (`client-table-row.tsx:35`), search box `role=search name="Search clients"` placeholder "Search clients…" (`components/clients/client-search.tsx:44-45`), footer `data-testid="clients-footer"` (`clients/page.tsx:265`). No pagination (`clients/page.tsx:120`).
- e2e seed: only client is **Acme Corp** (ACTIVE) — `compose/seed/seed.sh:120-213`. No "Kgosi/Naledi/Vukani/Moroka" anywhere in the seed.

---

#### customers/customer-crud.spec.ts — 6 sites (baseline: ✘1 (:47 getByLabel('Name') strict), -4)

| line | test (title, start) | usage | guarded locator | class | evidence | replacement |
|---|---|---|---|---|---|---|
| 76 | Create customer with required fields, :47 | C | `getByRole("dialog").isVisible()` → Escape | b (collapses) | On success the dialog closes and routes to the new client: `create-customer-dialog.tsx:221-229` (`setOpen(false)`; `router.push(/clients/{id})`). On failure it stays open with an error — that must FAIL the test, not be Escaped away. | `await expect(dialog).toBeHidden({ timeout: 15_000 }); await expect(page).toHaveURL(/\/clients\/[0-9a-f-]{36}/); await expect(page.getByTestId("client-name")).toHaveText(CUSTOMER_NAME);` |
| 113 | Create customer with custom fields (step 2), :91 | A (tautology :117) | `getByText(/Additional Information/i)` | a | Step-2 title "Additional Information" + "Step 2 of 2 — …" `create-customer-dialog.tsx:296-300` | `await expect(dialog.getByText("Step 2 of 2")).toBeVisible();` — delete `expect(step2Visible \|\| true)` |
| 125 | same, :91 | C | dialog still open → Escape | b (collapses) | as :76 | as :76 (hidden + URL) |
| 194 | Search customer list, :185 | A (`hasEdited \|\| hasOriginal`) | `getByText(EDITED_NAME)` | b (collapses) | serial describe: test "Edit customer name" precedes; on its failure later tests don't run. Search box exists `client-search.tsx:44-45` (the spec comment "no search input" is stale). | `await page.getByPlaceholder("Search clients…").fill(RUN_ID); await expect(page.getByRole("link", { name: EDITED_NAME, exact: true })).toBeVisible({ timeout: 10_000 }); await expect(page.getByRole("link", { name: CUSTOMER_NAME, exact: true })).toHaveCount(0);` |
| 199 | same, :185 | A | `getByText(CUSTOMER_NAME)` | b (collapses) | as :194 | folded into :194 replacement (negative check) |
| 219 | Customer list pagination, :207 | C (table vs empty) | `locator("table")` | b (collapses) | Seed Acme + 2 clients created in this file ⇒ table always (`clients/page.tsx:250-262`); EmptyState branch unreachable. | `await expect(page.getByTestId("client-row").first()).toBeVisible({ timeout: 10_000 }); await expect(page.getByTestId("clients-footer")).toContainText(/Showing \d+/);` (drop the else; rename title — there is no pagination) |

**Full test rewrites (non-site defects that the strict sweep will expose):**
- **:47 Create** — scope to the dialog and disambiguate labels (substring match hits "Contact Name/Email/Phone", `create-customer-dialog.tsx:314/350/368` vs `:552/565/578`):
  ```ts
  const dialog = page.getByRole("dialog", { name: /^(Create Client|Additional Information)$/ });
  await expect(dialog).toBeVisible();
  await dialog.getByLabel("Name", { exact: true }).fill(CUSTOMER_NAME);
  await dialog.getByLabel("Email", { exact: true }).fill(CUSTOMER_EMAIL);
  await dialog.getByLabel(/^Phone/).fill(CUSTOMER_PHONE);       // "Phone (optional)" ≠ "Contact Phone"
  await dialog.getByRole("button", { name: "Next" }).click();
  await expect(dialog.getByText("Step 2 of 2")).toBeVisible();   // replaces waitForTimeout(2000)
  await dialog.getByRole("button", { name: "Create Client" }).click(); // replaces evaluate(el=>el.click())
  await expect(dialog).toBeHidden({ timeout: 15_000 });
  await expect(page.getByTestId("client-name")).toHaveText(CUSTOMER_NAME);
  ```
  Then list check: `getByRole("link", { name: CUSTOMER_NAME, exact: true })`. Or reuse `createClient()` from `e2e/helpers/legal-depth-ii.ts:194-227` (it also fills the billing address — harmless here). Remove waitForTimeout :67/:73/:82.
- **:91 Step 2** — same label fix (:105/:106 hit the same strict violation once :47 passes). To make "custom fields" real: on step 2 fill `dialog.getByLabel(/ID \/ Passport Number/)` (legal-za intake, used by helper `legal-depth-ii.ts:217-219`) and assert via `GET /api/clients/{id}` that `customFields.id_passport_number` equals it. `evaluate(el=>el.click())` :122 → normal click. Remove :110/:123/:131.
- **:145 Edit** — `getByRole("button", { name: /Edit/i })` :167 has no target: a fresh client is PROSPECT ⇒ header shows "Start Onboarding" (client-header-card.tsx:37-38); editing lives in the overflow menu. Also `dialog.getByRole("textbox", { name: "Name" })` :172 is a strict violation ("Contact Name", `edit-customer-dialog.tsx:200` vs `:420`). Rewrite:
  ```ts
  expect(target, "client created in test 1").toBeDefined();          // replaces test.skip :156
  await page.getByTestId("client-overflow-trigger").click();
  await page.getByRole("menuitem", { name: "Edit Client" }).click();
  const dialog = page.getByRole("dialog", { name: "Edit Client" });   // edit-customer-dialog.tsx:190
  const nameInput = dialog.getByRole("textbox", { name: "Name", exact: true });
  await nameInput.fill(EDITED_NAME);
  await dialog.getByRole("button", { name: "Save Changes" }).click();  // :548
  await expect(dialog).toBeHidden({ timeout: 10_000 });
  await page.reload();
  await expect(page.getByTestId("client-name")).toHaveText(EDITED_NAME);
  ```
- **:207** — remove waitForTimeout :214.

**Other soft patterns:** `expect(step2Visible || true)` tautology :117; `expect(hasEdited || hasOriginal)` :204; `createBtn.evaluate(el => el.click())` :72, :122 (bypasses actionability; masks a disabled/hidden button); waitForTimeout ×8 (:67 :73 :82 :110 :123 :131 :178 :214); runtime `test.skip` :156.

**Expected outcome after conversion:** all 5 tests really run. :47 should pass (strict labels; product creates + navigates). :91 pass. :145 passes only with the overflow-menu rewrite (the current body would fail on the missing "Edit" button and then the strict "Name"). :185/:207 pass. No product blocker.

---

#### customers/customer-lifecycle.spec.ts — 3 sites (baseline: ✓1, ✘1 (:99 "Change Status" not found), -8)

| line | test (title, start) | usage | guarded locator | class | evidence | replacement |
|---|---|---|---|---|---|---|
| 281 | ACTIVE -> DORMANT, :253 | B (`if (!has) test.skip`) | `button "Change Status"` | c → re-target | "Change Status" = `LifecycleTransitionDropdown.tsx` — no importer since #1395. For ACTIVE the header shows a **dead** "Edit" button (client-header-card.tsx:41-43, onClick undefined per client-header-card-with-lifecycle.tsx:70). The only firm-UI ACTIVE→DORMANT path is Compliance › Dormancy Check: `compliance/page.tsx:96` → `DormancyCheckSection.tsx:33-39` "Check for Dormant Clients" → `DormancyCandidateList.tsx:155-161` per-row "Mark as Dormant" → `markCustomerDormant` (`compliance/actions.ts:99-111`). Candidate rule: ACTIVE + status ACTIVE + no activity (NULL counts) or older than threshold (default 90) — `backend/.../compliance/CustomerLifecycleService.java:44-73,211-218`. The LC client has no matters/docs/invoices ⇒ always a candidate. | see rewrite of test 6 below |
| 312 | DORMANT -> OFFBOARDING, :305 | B | `button "Change Status"` | c → **fixme (PRODUCT BLOCKER)** | No firm-UI path to OFFBOARDING exists: overflow menu has no transition items (client-overflow-menu.tsx:101-177); DORMANT header action is the dead "Edit"; the dropdown that had "Offboard Client" is orphaned (LifecycleTransitionDropdown.tsx:23-24,37). Backend paths only: DSAR deletion (`backend/.../datarequest/DataAnonymizationService.java:163-167`, OFFBOARDING→OFFBOARDED atomically) and Archive (`backend/.../customer/CustomerService.java:801-808`, jumps straight to OFFBOARDED). | `test.fixme(true, "628F-BLOCKER: no firm-UI DORMANT→OFFBOARDING transition — LifecycleTransitionDropdown orphaned since #1395; components/customers/client-header-card-with-lifecycle.tsx:70 + app/(app)/org/[slug]/clients/[id]/page.tsx:434-470");` |
| 344 | OFFBOARDING -> OFFBOARDED, :337 | B | `button "Change Status"` | c → **fixme (PRODUCT BLOCKER)** | Header renders "Complete Offboarding" for OFFBOARDING (client-header-card.tsx:44-45) but `targetLifecycleStatus` is null for OFFBOARDING (page.tsx:434-470) ⇒ `onPrimaryAction` undefined (client-header-card-with-lifecycle.tsx:70) and no TransitionConfirmDialog is mounted (:73) ⇒ **dead button**. The dialog config for it already exists (`TransitionConfirmDialog.tsx` OFFBOARDED: "Complete Offboarding"). | `test.fixme(true, "628F-BLOCKER: header 'Complete Offboarding' button has no onClick — components/customers/client-header-card-with-lifecycle.tsx:70, app/(app)/org/[slug]/clients/[id]/page.tsx:434-470");` |

**Per-test rewrites (whole describe):**
1. **:83 PROSPECT default** — create the LC client *with a billing address* so activation can succeed later (LIFECYCLE_ACTIVATION needs address_line1/city/country; tax number exempt for INDIVIDUAL, the default type — `backend/.../prerequisite/StructuralPrerequisiteCheck.java:65-70,105-111`, `Customer.java:149`): body `{ name, email, phone, addressLine1: "1 Long Street", city: "Cape Town", postalCode: "8001", country: "ZA" }` (mirrors seed.sh:126-133). Badge: `await expect(page.getByTestId("lifecycle-badge")).toHaveText("Prospect")` (client-header-card.tsx:136-139) instead of `getByText(/Prospect/)`.
2. **:99 PROSPECT→ONBOARDING** (the baseline failure) — Bob is admin ⇒ prompt target ONBOARDING:
   ```ts
   await expect(page.getByTestId("smart-primary-action")).toHaveText("Start Onboarding");
   await page.getByTestId("smart-primary-action").click();
   const confirm = page.getByRole("alertdialog", { name: "Start Onboarding" });
   await confirm.getByRole("button", { name: "Start Onboarding" }).click();
   await expect(confirm).toBeHidden({ timeout: 15_000 });
   await expect(page.getByTestId("lifecycle-badge")).toHaveText("Onboarding", { timeout: 15_000 });
   ```
   (= `startClientOnboarding()` `e2e/helpers/legal-depth-ii.ts:229-238`; reuse it.) Drop waitForTimeout :122 + reload.
3. **:129 ONBOARDING→ACTIVE** — today vacuous: FICA items with `requiresDocument` reject `/complete` with 400 (`backend/.../checklist/ChecklistInstanceService.java:229-233`), responses are unchecked, the client has no address ⇒ stays ONBOARDING, and `expect(["ACTIVE","ONBOARDING"])` (:179/:190) accepts it. Rewrite: `expect(checklistRes.ok).toBe(true)`; per item `PUT …/complete`, on non-2xx `PUT …/skip {reason}` and `expect(skip.ok).toBe(true)` (same pattern as seed.sh:183-196); then `await expect.poll(() => status(customerId)).toBe("ACTIVE")` (auto-transition `ChecklistInstanceService.java:538-573`). If not auto-activated, `transitionViaApi(…,"ACTIVE")` must be `ok`. No `["ACTIVE","ONBOARDING"]`.
4. **:193 PROSPECT blocked from project** — tautology `[400,409,422].includes(s) || projectRes.ok` (:224). Strict: `expect(projectRes.status).toBe(400); expect(await projectRes.text()).toContain("PROSPECT");` (guard `backend/.../compliance/CustomerLifecycleGuard.java:19-24` via `project/ProjectFieldService.java:127`; `InvalidStateException` = 400, `exception/InvalidStateException.java:10-14`).
5. **:227 PROSPECT blocked from invoice** — STALE PREMISE, needs orchestrator decision: CREATE_INVOICE is *permitted* for PROSPECT (`CustomerLifecycleGuard.java:49-61`, GAP-L-60). It passes only because the no-address prospect fails INVOICE_GENERATION prerequisites ⇒ 422 (`StructuralPrerequisiteCheck.java:48-52`, `InvoiceCreationService.java:805-812`). Options: (i) retitle "Fee note blocked for client without billing address" and assert `status === 422` + violation `address_line1`; (ii) delete. Do not keep the lifecycle claim.
6. **:253 ACTIVE→DORMANT** — replace `test.skip` guards (:266/:270) with `expect(status).toBe("ACTIVE")`; UI via Compliance:
   ```ts
   await page.goto(`${BASE}/compliance`);
   await page.getByRole("button", { name: "Check for Dormant Clients" }).click();
   const row = page.getByRole("row").filter({ has: page.getByRole("link", { name: LIFECYCLE_NAME, exact: true }) });
   await expect(row).toBeVisible({ timeout: 15_000 });
   await row.getByRole("button", { name: "Mark as Dormant" }).click();
   await expect(row.getByRole("button", { name: "Mark as Dormant" })).toBeEnabled({ timeout: 15_000 }); // "Marking..." done
   await page.goto(`${BASE}/clients/${customerId}`);
   await expect(page.getByTestId("lifecycle-badge")).toHaveText("Dormant");
   ```
   (candidate list is client state; it is not re-fetched after marking, so assert on the client page.)
7. **:305 DORMANT→OFFBOARDING** — `test.fixme` (blocker above).
8. **:337 OFFBOARDING→OFFBOARDED** — `test.fixme` (blocker above).
9. **:368 OFFBOARDED blocked from project** — tautology `|| projectRes.ok` (:386); depends on fixme'd 7–8. Make it self-contained: own client with address → ONBOARDING → checklist complete/skip → ACTIVE → API `OFFBOARDING` → API `OFFBOARDED` (backend allows, `customer/LifecycleStatus.java:18-24`), then `expect(projectRes.status).toBe(400)` + body contains "OFFBOARDED".
10. **:389 PROSPECT→ACTIVE rejected** — `expect(res.status).toBe(400)` (`customer/Customer.java:189-194`), not `[400,409].includes`.

**Other soft patterns:** tautologies :179, :190, :224, :386; unchecked fetches in the checklist loop :147; `if (checklistRes.ok)` / `if (Array.isArray…)` / `if (customer.lifecycleStatus === "ONBOARDING")` guards :139/:142/:168/:171; runtime `test.skip` :266 :270 :284 :315 :347; waitForTimeout ×4 (:122 :300 :332 :363).

**Expected outcome after conversion:** tests 1–4, 6, 9, 10 really run and should pass; test 5 per orchestrator ruling; tests 7–8 `test.fixme` (product blockers). Serial mode is fine with fixme (skip ≠ failure). Today's chain was 1 pass + 8 not-run; even with "Change Status" restored, 6–8 would have test.skip'd because activation never succeeded.

---

#### customers/customer-data-protection.spec.ts — 17 sites (baseline: ✓3, -2)

Baseline reading: tests 1–2 skip (no "Kgosi…" client); 3–5 "pass" vacuously (3 never clicks Execute; 4/5 see below).

| line | test (title, start) | usage | guarded locator | class | evidence | replacement |
|---|---|---|---|---|---|---|
| 45 | Download customer data export, :35 | B | link filter `/Kgosi\|Naledi\|Vukani\|Moroka/` | c → re-target | Names not in e2e seed (seed.sh:120-133: Acme Corp only) | `await openClient(page, "Acme Corp")` (`e2e/helpers/legal-depth-ii.ts:155-162`) or `getByRole("link", { name: "Acme Corp", exact: true })` |
| 61 | same | C | button `/download.*data\|export.*data\|data.*export/` | c → delete | No such button on the client page; export is a menuitem | delete branch |
| 63 | same | C | button `/more\|actions\|menu/i`.first() | a (re-target) | trigger `data-testid="client-overflow-trigger"` client-overflow-menu.tsx:89-98 | `await page.getByTestId("client-overflow-trigger").click();` |
| 69 | same | C | menuitem `/download.*data\|export.*data/` | a | "Export Data" client-overflow-menu.tsx:152-155 | `await page.getByRole("menuitem", { name: "Export Data" }).click(); const dlg = page.getByRole("dialog", { name: "Download All Data" }); await dlg.getByRole("button", { name: "Export Now" }).click(); await expect(dlg.getByRole("heading", { name: "Export Ready" })).toBeVisible({ timeout: 30_000 }); await expect(dlg.getByRole("link", { name: "Download Archive" })).toHaveAttribute("href", /.+/);` (`data-export-dialog.tsx:92,102,129,136-140`) |
| 79 | same | B/C | tab `/data.*protection\|privacy/` | c → delete | `customer-grouped-tabs.tsx:149-213` has no such tab | delete branch + its `test.skip` |
| 98 | Anonymize customer preview, :89 | B | seeded-name link | c → re-target | as :45 | `openClient(page, "Acme Corp")` |
| 114 | same | C | button `/anonymi\|delete.*personal\|remove.*data/` | c → delete | no header button; menuitem only | delete |
| 116 | same | C | moreMenu | a (re-target) | as :63 | testid trigger |
| 122 | same | C | menuitem `/anonymi\|delete.*personal/` | a | "Anonymize" client-overflow-menu.tsx:159-166 (owner only — Alice is owner) | `await page.getByRole("menuitem", { name: "Anonymize" }).click();` |
| 136 | same | C | `getByRole("dialog")` | c → re-target (wrong role) | AnonymizeCustomerDialog is an **AlertDialog** (`anonymize-customer-dialog.tsx:108-120`) ⇒ role `alertdialog`; `dialog` never matches, so the assertion never ran | `const dlg = page.getByRole("alertdialog", { name: "Anonymize Client" }); await expect(dlg).toContainText(/Matters: \d+/); await expect(dlg).toContainText(/Fee notes: \d+/); await expect(dlg.getByRole("button", { name: "Continue" })).toBeEnabled(); await dlg.getByRole("button", { name: "Cancel" }).click();` (preview list :147-154) |
| 179 | Execute anonymization, :143 | C | moreMenu | a (re-target) | as :63 | testid trigger |
| 189 | same | B | button/menuitem `/anonymi\|delete.*personal/` | a | menuitem "Anonymize" :159-166 (button half of the union is dead) | `await page.getByRole("menuitem", { name: "Anonymize" }).click();` |
| 201 | same | C | textbox `/confirm\|customer.*name\|type.*name/` | c → re-target | Confirm input only exists on the "confirm" step after **Continue** (the spec never clicks Continue); its `<label>` is not associated, name comes from `placeholder={customerName}` (`anonymize-customer-dialog.tsx:178,236-247`) | `await dlg.getByRole("button", { name: "Continue" }).click(); await dlg.getByPlaceholder(CUSTOMER_NAME).fill(CUSTOMER_NAME);` |
| 207 | same | C | button `/confirm\|anonymi\|execute\|delete/`.last() | c → re-target | On the preview step only "Cancel"/"Continue" exist ⇒ nothing matched ⇒ vacuous pass. Real button "Execute Anonymization" :256-262 | `await dlg.getByRole("button", { name: "Execute Anonymization" }).click(); await expect(dlg.getByRole("heading", { name: "Anonymization Complete" })).toBeVisible({ timeout: 30_000 }); await dlg.getByRole("button", { name: "Close" }).click(); await expect(page.getByTestId("lifecycle-badge")).toHaveText("Anonymized");` (:275, :293; name becomes "Anonymized Client xxxxxx" — `DataAnonymizationService.java:245-246`) |
| 221 | ANONYMIZED customer is read-only, :213 | B | `text=/anonymi/i` in list | b → make self-contained | Depends on test 3 having anonymized something earlier in the run (order not guaranteed across files; within the file it is sequential but not serial). | Create + anonymize via API in the test: `POST /api/clients` then `POST /api/clients/{id}/anonymize {confirmationName: name, reason: "E2E"}` (`AnonymizationController.java:24-39`), `expect(res.ok)`; `page.goto(/clients/{id})`. |
| 230 | same | C | `anonBadge.locator("..").locator("a")` | c → delete | Badge's parent is its own cell; the name link is in another cell (`clients-table.tsx:165-172`) — structural guess | navigate by id (above) |
| 240 | same | C | button `/edit/i`.first() → `toBeDisabled` | c → re-target (negative) | Product **hides** edit, it does not disable: header action null for ANONYMIZED (client-header-card.tsx:46-48), "Edit Client"/"Anonymize" hidden (client-overflow-menu.tsx:74,79) | `await expect(page.getByText("Client data anonymized")).toBeVisible(); /* page.tsx:534 */ await expect(page.getByTestId("smart-primary-action")).toHaveCount(0); await page.getByTestId("client-overflow-trigger").click(); await expect(page.getByRole("menuitem", { name: "Edit Client" })).toHaveCount(0); await expect(page.getByRole("menuitem", { name: "Anonymize" })).toHaveCount(0);` |

**Test 5 (:246, no sites) — vacuous pass:** `?status=ANONYMIZED` is not a list param (backend takes `lifecycleStatus`, `backend/.../customer/CustomerController.java:94-108`) so it returns arbitrary clients (Acme first), and `PUT /api/clients/{id}/status` does not exist (only `POST /{clientId}/transition`, :333) ⇒ 404/405 satisfies `>=400 && <500`. Rewrite: create + anonymize via API (as test 4), then `POST /api/clients/{id}/transition {targetStatus:"ACTIVE"}` ⇒ `expect(res.status).toBe(400)` + body contains "ANONYMIZED" (`customer/LifecycleStatus.java:24` ANONYMIZED → `Set.of()`, `Customer.java:189-194`). Replace `if (!listRes.ok) test.skip` / `if (!anonCustomer) test.skip`.

**Other soft patterns:** runtime `test.skip` ×10 (:48 :83 :101 :130 :161 :173 :190 :224 :257 :266); waitForTimeout ×11 (:53 :65 :72 :81 :106 :118 :125 :181 :195 :209 :236); `body.textContent().includes("404")` guard :170-175 (fragile — any "404" in body text skips); `dialogText?.length > 0` :139 (always true). `getAuthToken()` :20-28 omits `orgId`/`orgRole` unlike the other specs' `getToken` — align it (anonymize needs `MANAGE_COMPLIANCE_DESTRUCTIVE`, AnonymizationController.java:25). Test 3 should `expect(createRes.ok)` instead of skip.

**Expected outcome after conversion:** all 5 tests really run. 1 (export on Acme) — should pass if the LocalStack S3 export works (e2e compose has localstack, `compose/docker-compose.e2e.yml:25-37`; never observed through this spec — needs a run). 2 (preview on Acme, Cancel) — pass. 3 (execute on own client) — first time this flow is ever exercised end-to-end here (it also runs a pre-anonymization S3 export, `DataAnonymizationService.java:242`); expected pass, needs observation. 4, 5 — pass with API setup. Uses no 628F seed rows.

---

#### settings/data-protection-settings.spec.ts — 20 sites (baseline: ✓6, -3)

Product: `/settings/firm?tab=data-protection` renders one page with all sections — h1 "Data Protection", Jurisdiction, Information Officer, DSAR link card, Retention Policies, Processing Register, PAIA Manual (`app/(app)/org/[slug]/settings/firm/_tabs/data-protection.tsx:54-108`). There are **no inner tabs**. Family tabs are role=tab labelled "Data protection", "Data requests", … (`components/settings/settings-family-tabs.tsx:85-89`, `lib/settings-nav.ts:73-88`). DSARs live on `?tab=data-requests` (h1 "DSAR Requests", `_tabs/data-requests.tsx:39-44`). Baseline skips are probably View retention/processing/DSAR or Edit retention/Create DSAR (the `text.match` checks are always true, so those 3 "pass"; Edit retention + Create DSAR skip on dead selectors; one more).

| line | test (title, start) | usage | guarded locator | class | evidence | replacement |
|---|---|---|---|---|---|---|
| 62 | View data protection tab, :47 | C/B | tab `/data.*protection\|privacy\|POPIA/` (fallback) | c → delete | The `"Something went wrong"/"404"` fallback is dead — the route exists (`_tabs/index.ts:19`) | delete the whole `if (bodyText…)` block; assert `await expect(page.getByRole("heading", { level: 1, name: "Data Protection", exact: true })).toBeVisible();` (data-protection.tsx:57) |
| 64 | same | C/B | link `/data.*protection\|privacy/` (fallback) | c → delete | same dead fallback | (same) |
| 88 | Set jurisdiction to South Africa, :77 | B | fallback tab/link "Data protection" | c → delete | dead fallback | delete block (+ :92 skip); keep `setJurisdictionToSouthAfrica` |
| 115 | Set information officer, :104 | B | fallback | c → delete | dead fallback | delete block |
| 152 | View retention policies, :141 | B | fallback | c → delete | dead fallback | delete block |
| 165 | same | C | `[role=tab]/a :has-text("Retention")` | c → delete | no inner tab or link with "Retention" (sidebar labels: settings-sidebar.tsx:70,88; nav labels settings-nav.ts) | delete; assert `getByRole("heading", { name: "Retention Policies" })` (retention-policies-table.tsx:189-191) and `getByLabel("Retention days for Customer")` visible (row from generic compliance packs `backend/src/main/resources/compliance-packs/sa-fica-individual/pack.json:86-99`, CUSTOMER/CUSTOMER_OFFBOARDED; ZA defaults added on jurisdiction set `RetentionService.java:277-293`) |
| 191 | Edit retention period, :180 | B | fallback | c → delete | dead fallback | delete block |
| 204 | same | C | Retention tab | c → delete | as :165 | delete |
| 213 | same | B | `input[type=number][name*=retention i], input[name*=months i]` | c → re-target | Inputs have no `name`; they are `aria-label="Retention days for {Type}"` (retention-policies-table.tsx:226-237) — the selector never matched ⇒ always skipped | `const days = page.getByLabel("Retention days for Customer"); await expect(days).toBeVisible(); const v = String(1825 + (Date.now() % 100)); await days.fill(v);` (keep ≥1800; don't shorten retention) |
| 220 | same | C | button `/save\|update/i`.first() | c → re-target (wrong element) | First match is the Information Officer "Save" (DOM order), not the row save. Row save is `aria-label="Save Customer policy"` (:268-273) | `await page.getByRole("button", { name: "Save Customer policy" }).click(); await expect(page.getByRole("button", { name: "Save Customer policy" })).toBeEnabled(); await page.reload(); await expect(page.getByLabel("Retention days for Customer")).toHaveValue(v);` (no success toast exists; persistence is the oracle) |
| 237 | View processing register, :226 | B | fallback | c → delete | dead fallback | delete block |
| 252 | same | C | `[role=tab]/a :has-text("Processing"/"Register")` | c → delete | no such tab/link | delete; assert `getByRole("heading", { name: "Processing Register" })` + `getByRole("button", { name: "Add Activity" })` visible (processing-register-table.tsx:328-345). Replace `text.match(/processing\|register\|activit/)` (:259, always true) |
| 277 | Generate PAIA manual, :266 | B | fallback | c → delete | dead fallback | delete block (rest of test is already strict) |
| 308 | View DSAR list, :297 | B | fallback | c → delete | dead fallback | delete block |
| 323 | same | C | `[role=tab]:has-text("DSAR"), a:has-text("DSAR"), [role=tab]:has-text("Requests")` | a (re-target) | `[role=tab]:has-text("Requests")` matches the family tab "Data requests" (settings-nav.ts:82-83; `:has-text` is case-insensitive substring); "View DSAR Requests →" link also exists (data-protection.tsx:87-92) | `await page.getByRole("tab", { name: "Data requests" }).click(); await expect(page.getByRole("heading", { level: 1, name: "DSAR Requests" })).toBeVisible();` — or `goto(?tab=data-requests)`. Replace `text.match(/DSAR\|request\|subject/)` (:330, always true) |
| 348 | Create DSAR, :337 | B | fallback | c → delete | dead fallback | delete block |
| 363 | same | C | DSAR tab union | a (re-target) | as :323 | as :323 (or direct goto `?tab=data-requests`) |
| 370 | same | B | button `/new\|create\|add\|log/i`.first() | a (re-target exact) | "Log New Request" (log-dsar-dialog.tsx:105-110); the regex `.first()` can hit any earlier shell button | `await page.getByRole("button", { name: "Log New Request" }).click();` |
| 380 | same | C | `getByRole("dialog")` | a | Dialog "Log DSAR Request" (:114) | `const dlg = page.getByRole("dialog", { name: "Log DSAR Request" }); await expect(dlg).toBeVisible();` |
| 382 | same | C | `dialog.getByRole("textbox").first()` | a | "Subject Name" (:127-131) — test never submits today | fill + submit, see below |

**Create DSAR full rewrite (it never submitted):** Acme id via API (`GET /api/clients`, `find(c => c.name === "Acme Corp")`), then
```ts
await dlg.getByLabel("Subject Name").fill(`E2E DSAR ${RUN}`);
await dlg.getByLabel("Client ID").fill(acmeId);           // log-dsar-dialog.tsx:191-194 (raw UUID field)
await dlg.getByRole("button", { name: "Log Request" }).click();
await expect(dlg).toBeHidden({ timeout: 10_000 });
```
Oracle: count rows containing "Acme Corp" before/after (`dsar-requests-table.tsx:340` shows `subjectName ?? clientName`; backend has no subjectName ⇒ shows "Acme Corp"), `await expect(rows).toHaveCount(before + 1)` (revalidatePath `_tabs/data-requests-actions.ts:40-41`). Uses ACCESS type (default) — benign. Note: adds an open DSAR on Acme; compliance-overview counts may shift (check it asserts no fixed number).

**Other soft patterns:** runtime `test.skip` ×14 (:67 :92 :119 :156 :176 :195 :214 :241 :262 :281 :312 :333 :352 :371); waitForTimeout ×16 (:72 :90 :117 :154 :167 :193 :206 :222 :239 :254 :279 :310 :325 :350 :365 :376); `body.textContent().includes("404")` guard in 9 tests (fragile + dead); `content.textContent().match(...)` always-true checks :173, :259, :330; `.flex-1` generic content locator :73/:171/:257/:328. Strongly suggest one `openDataProtectionTab(page)` helper (goto + h1 assertion) replacing the nine copied fallback blocks.

**Expected outcome after conversion:** all 9 tests really run. View tab / jurisdiction / officer / PAIA — pass (already strict). View retention / processing / DSAR list — pass on real headings. Edit retention — real save + persistence; expected pass (needs observation). Create DSAR — real submit; expected pass (needs observation). No 628F seed dependency; no product blocker.

---

### Group C — per-site table (settings ×5, notifications ×2, navigation, projects ×2)

Group C uses `M/[id]` = `APP/matters/[id]`, `S` = `APP/settings`. Its cross-cutting findings 1–5 are X2, X3, X3(body), settings URLs (notification preferences must use `/settings/connections?tab=notifications`), and X5 above.

---

#### settings/custom-fields.spec.ts — 11 sites (baseline: ✓1, -3)

| line | test (title, start) | usage | guarded locator | class | evidence | replacement |
|---|---|---|---|---|---|---|
| 51 | Create text custom field, 40 | B | button /new\|create\|add/ .first() | a | First match in content is "Add Field" `S/matters/_tabs/custom-fields-content.tsx:99-102` (HelpTips are "Help: Field types/scoping", no match) | `const createBtn = page.getByRole("button", { name: "Add Field" }); await expect(createBtn).toBeVisible(); await createBtn.click();` |
| 60 | 〃 | C | getByRole("dialog") | a | Dialog "Add Field Definition" `components/field-definitions/FieldDefinitionDialog.tsx:72-77` | `const dialog = page.getByRole("dialog", { name: "Add Field Definition" }); await expect(dialog).toBeVisible();` (drop `waitForTimeout(1000)`) |
| 63 | 〃 | C | dialog textbox /name\|label/ | a | `#fd-name` label "Name" `FieldDefinitionDialog.tsx:117-124` | `await dialog.getByLabel("Name", { exact: true }).fill(fieldName)` |
| 69 | 〃 | C | `select[name*="type"]` | c | Selects have **no `name` attr**. Field type is `#fd-field-type` labelled "Field Type" `:145-158`. Also, `selectOption({label: /text/i})` is invalid: label must be a string | `await dialog.getByLabel("Field Type").selectOption("TEXT")` |
| 75 | 〃 | C | `select[name*="entity"]` | c | `#fd-entity-type` "Entity Type" `:89-112`, options Matters/Tasks/Clients (`field-definition-constants.ts:3-7`) | `await dialog.getByLabel("Entity Type").selectOption("CUSTOMER")` |
| 82 | 〃 | C | dialog button /create\|save\|add/ .last() | a | "Create Field" submit `:351-360` | `await dialog.getByRole("button", { name: "Create Field" }).click(); await expect(dialog).toBeHidden();` then `await page.getByRole("tab", { name: "Clients" }).click(); await expect(page.getByTestId("field-row").filter({ hasText: fieldName })).toBeVisible();` (`custom-fields-content.tsx:76-80,128`) |
| 102 | Custom field appears on entity form, 89 | B | button /new.*client/ | a | "New Client" `components/customers/create-customer-dialog.tsx:281-283`, gated by CUSTOMER_MANAGEMENT `clients/page.tsx:174-176` | `const newBtn = page.getByRole("button", { name: "New Client" }).first(); await expect(newBtn).toBeVisible(); await newBtn.click();` |
| 112 | 〃 | C | button /next/ | a | Step 1 always shows "Next" `create-customer-dialog.tsx:690-707`; **email is required** (`lib/schemas/customer.ts:33`), so filling only Name keeps the dialog on step 1 | Unconditional: fill Name and Email, click Next, `await expect(dialog.getByText("Step 2 of 2", { exact: false })).toBeVisible()` (`:298-300`) |
| 115 | 〃 | C | textbox /name/ .first() | a | FormLabel "Name" `:314` (modal ⇒ outside nodes aria-hidden) | `await dialog.getByRole("textbox", { name: "Name", exact: true }).fill(...)` and `dialog.getByRole("textbox", { name: "Email", exact: true }).fill(\`cf-${RUN}@example.com\`)` |
| 124 | 〃 | C | getByRole("dialog") | a | Dialog "Create Client" → "Additional Information" `:296` | `const dialog = page.getByRole("dialog"); await expect(dialog).toBeVisible();` then on step 2 `await expect(dialog.getByText("No additional fields required.").or(dialog.locator("input, select, textarea").first())).toBeVisible()` (`:651-680`) |
| 149 | Custom field value persists, 134 | B | link /Kgosi\|Naledi/ | c | Not seeded: the only seed client is "Acme Corp" (`compose/seed/seed.sh:126`); "Kgosi"/"Naledi" appear in no seed or product file | Re-target `page.getByRole("link", { name: "Acme Corp" }).first()`, assert visible, click, `await openGroupedTab(page, "details", "fields")` (`lib/constants/client-tab-groups.ts` details→fields "Custom fields"; panel `clients/[id]/page.tsx:577-590`) and assert the panel renders |

**Other soft patterns / notes:** 7 runtime `test.skip(` calls, including 4 body-text guards at 28-31, 45-48, 94-97, 140-143 (finding 1). 2 `.flex-1` content reads at 33, 158 (finding 2). 5 `waitForTimeout`. Test 1's `text.length > 0` is vacuous; use `expect(page.getByRole("heading", { level: 1, name: "Custom Fields" })).toBeVisible()` (`S/matters/_tabs/custom-fields.tsx:63-66`). Test 2 never asserts the field was created. Test 3 never asserts a custom field is present, and test 4 never asserts a value persisted: the titles over-claim.

**Expected outcome after conversion:** all 4 run. Test 1 should pass. Test 2 should pass (Alice is owner ⇒ `canManage`). It creates a TEXT field per run, so use a unique name. Test 3 should pass and becomes a real step-1→step-2 check (abandon the dialog without creating). Test 4 should pass on Acme. If the title "value persists" is to be honoured, it needs a set-value-and-reload flow, which is a scope call for the orchestrator.

---

#### settings/tags.spec.ts — 9 sites (baseline: -3)

| line | test | usage | guarded locator | class | evidence | replacement |
|---|---|---|---|---|---|---|
| 49 | Create tag, 38 | B | button /new\|create\|add/ .first() | a | Header "Add Tag" `S/matters/_tabs/tags-content.tsx:30-38`. With zero tags the empty-state CTA also renders (`:41-56`), so `.first()` is needed | `const addBtn = page.getByRole("button", { name: "Add Tag" }).first(); await expect(addBtn).toBeVisible(); await addBtn.click();` |
| 58 | 〃 | C | getByRole("dialog") | a | TagDialog title "Create Tag" `components/tags/TagDialog.tsx:101` | `const dialog = page.getByRole("dialog", { name: "Create Tag" }); await expect(dialog).toBeVisible();` |
| 60 | 〃 | C | textbox /name\|label/ | a | `#tag-name` "Name" `TagDialog.tsx:110-118` | `await dialog.getByLabel("Name", { exact: true }).fill(tagName)` |
| 66 | 〃 | C | `input[type="color"]` | c | No color input: the colour field is a **text** input `#tag-color` labelled "Color (optional)" `TagDialog.tsx:123-130` | `await dialog.getByLabel("Color (optional)").fill("#3B82F6")` |
| 71 | 〃 | C | button /create\|save\|add/ .last() | a | Submit "Create" `TagDialog.tsx:154-161` | `await dialog.getByRole("button", { name: "Create", exact: true }).click(); await expect(dialog).toBeHidden(); await expect(page.getByRole("cell", { name: tagName })).toBeVisible();` (or `getByText(tagName)` in the table) |
| 89 | Apply tag to entity, 78 | B | link filter /project/ | c | Matter rows carry matter names, none containing "project". Seed matter is "Website Redesign" (`compose/seed/seed.sh:224`) | `await page.getByRole("link", { name: "Website Redesign" }).first().click()` after `expect(...).toBeVisible()` |
| 92 | 〃 | B | `main a` fallback | c | Dead fallback: it clicks whatever link comes first | Delete branch |
| 104 | 〃 | B | button /tag\|label/ .first() | c | "Add Tag" lives on the **Details** sub-tab (`components/projects/matter-details-tab.tsx:112-125`, `components/tags/TagInput.tsx:200-209`), but the default tab is overview (`tab-group-types.ts:40`). Also, `/tag/i` matches "stage" | `await openGroupedTab(page, "details", "details"); const addTag = page.getByRole("button", { name: "Add Tag" }); await expect(addTag).toBeVisible(); await addTag.click();` |
| 114 | 〃 | C | getByRole("option").first() | b | Options exist only if org tags exist. No tag seed; test 2 creates one, but only if it passed earlier in the same worker | Collapse deterministically. Type a unique name in `getByPlaceholder("Search tags...")` (`TagInput.tsx:213-217`), click `getByRole("option", { name: \`Create "${name}"\` })` (`:236-242`, Alice owner ⇒ `canInlineCreate`), then `await expect(page.getByRole("button", { name: \`Remove ${name}\` })).toBeVisible()` (`:188-195`) |

**Other soft patterns / notes:** 5 runtime `test.skip(` (body guards at 28-31, 43-46 per finding 1). `.flex-1` at 33. 5 `waitForTimeout`. Test 3 has a body `not.toContainText` check only. Test 1 should assert `heading level 1 "Tags"` (`S/matters/_tabs/tags.tsx:24-27`).

**Expected outcome after conversion:** all 3 run, and all should pass. Test 2 creates a tag per run, so use a unique `RUN_ID` name. Test 3 attaches a new tag to Website Redesign, which leaves a visible side effect on the shared seed matter. Prefer a cleanup click on `Remove <name>`.

---

#### settings/roles-capabilities.spec.ts — 7 sites (baseline: -3)

| line | test | usage | guarded locator | class | evidence | replacement |
|---|---|---|---|---|---|---|
| 56 | View role capabilities, 41 | C | link /admin/ | c | System roles are static cards: `<h3>{role.name}</h3>` plus a "System" badge, no link or button (`S/firm/_tabs/roles.tsx:70-104`). "Built-in roles that cannot be modified" (`:64-67`) | Delete the click chain. `await expect(page.getByRole("heading", { level: 3, name: "Admin", exact: true })).toBeVisible()`, then `await page.getByRole("button", { name: "Capability Reference" }).click(); await expect(page.getByRole("cell", { name: "Financial Visibility" })).toBeVisible();` (`capability-reference.tsx:14-50`; label `lib/capabilities` CAPABILITY_META) |
| 58 | 〃 | C | button /admin/ | c | No such button | Delete |
| 60 | 〃 | C | tr filter /admin/ | c | Only rows are in the collapsed Capability Reference (unmounted when closed) | Delete |
| 90 | Edit role capability, 75 | C | link /admin/ | c | As at 56. System roles are not editable | Re-target to the custom-role flow: `getByRole("button", { name: "New Role" })` (`components/roles/custom-roles-section.tsx:29-32`) → dialog "Create Role" (`components/roles/role-dialog.tsx:108`) → `getByLabel("Name", { exact: true }).fill(\`E2E Role ${RUN}\`)` → `getByRole("checkbox", { name: "Financial Visibility" }).check()` (Radix Checkbox `aria-label={cap.label}` `:143-149`) → "Create" → card visible → `getByRole("button", { name: \`Edit E2E Role ${RUN}\` })` (`role-card.tsx:35`) → uncheck → "Save" → reopen and assert `not.toBeChecked()` → delete role (cleanup) |
| 92 | 〃 | C | button /admin/ | c | none | Delete |
| 94 | 〃 | C | tr /admin/ | c | none | Delete |
| 123 | 〃 | C | page button /save\|update/ | c | No page-level save. Saving is the dialog submit "Create"/"Save" (`role-dialog.tsx:173-180`) | Fold into the custom-role flow above (`dialog.getByRole("button", { name: "Save", exact: true })`) |

**Other soft patterns / notes:** 6 runtime `test.skip(`. Body "404" guards at 28-31, 46-49, 80-83 are the reason all 3 tests skip at baseline (finding 1). `.flex-1` at 33/70. Test 1's `text.match(/owner|admin|member/)` runs on the sidebar and would be false; replace with h3 headings Owner/Admin/Member (`roles.tsx:79`; names from `OrgRoleService.java:394-395`). `toggles = 'button[role="switch"], input[type="checkbox"]'` (104) is wrong for the custom-role checkboxes, which are `button[role="checkbox"]`, and the hidden Radix bubble `input[type=checkbox]` is not clickable. 5 `waitForTimeout`. The `.count()` guard is at 105.

**Expected outcome after conversion:** test 1 should pass. Test 2's original purpose ("click a role to view its capabilities") does not exist for system roles. Re-target it to "system role cards + capability reference" (should pass) or `test.fixme` it. Test 3 is a real CRUD flow on custom roles and should pass, provided cleanup deletes the created role. Whether to re-scope tests 2 and 3 to custom roles is an orchestrator call (finding: the original "edit Admin capability" UI was never built for system roles).

---

#### settings/tax-settings.spec.ts — 5 sites (baseline: -3)

| line | test | usage | guarded locator | class | evidence | replacement |
|---|---|---|---|---|---|---|
| 63 | Create tax rate, 48 | B | button /new\|add\|create/ .first() | a | "Add Tax Rate" `S/fees/_tabs/tax-rate-table.tsx:98-103` (TaxSettingsForm only has "Save Settings") | `const addBtn = page.getByRole("button", { name: "Add Tax Rate" }); await expect(addBtn).toBeVisible(); await addBtn.click();` |
| 75 | 〃 | C | getByRole("dialog") | a | Dialog "Add Tax Rate" `components/settings/add-tax-rate-dialog.tsx:98` | `const dialog = page.getByRole("dialog", { name: "Add Tax Rate" }); await expect(dialog).toBeVisible();` |
| 77 | 〃 | C | textbox /name/ | a | `#tax-rate-name` "Name" `:104-112` | `await dialog.getByLabel("Name", { exact: true }).fill(\`E2E Rate ${RUN}\`)` |
| 81 | 〃 | C | `input[name*="rate"]` | c | No `name` attr. Field is `#tax-rate-percent` labelled "Rate (%)" `:116-128` | `await dialog.getByLabel("Rate (%)").fill("10")`, then `dialog.getByRole("button", { name: "Create", exact: true }).click()` (`:167-168`), `expect(dialog).toBeHidden()`, and the new row is visible in the table |
| 104 | Tax applies to invoices, 87 | B | link /INV-/ | d | Fee-note list link text is `invoiceNumber ?? "Draft"` (`app/(app)/org/[slug]/invoices/page.tsx:307-312`). Numbers are `INV-%04d` (`backend/.../invoice/InvoiceNumberService.java:49`), so a link exists only once an approved fee note exists. **Needs the 628F seed APPROVED+SENT fee note for Acme** | `const inv = page.getByRole("link", { name: /^INV-\d{4}$/ }).first(); await expect(inv).toBeVisible(); await inv.click();` then assert the totals tax label `page.getByText(/^(VAT\|Tax)$/)` (`components/invoices/invoice-totals-section.tsx:34`) rather than `body` |

**Other soft patterns / notes:** 5 runtime `test.skip(`. Test 1 skips because `.flex-1` resolves to the sidebar, which has no "tax"/"vat" (finding 2). Body "404" guard at 53-56. The `hasError` fallback at 28-34 is dead. Test 3's `/fee note/` check passes on sidebar text and `body toContainText(/tax|vat/)` reads script text (finding 3). 2 `waitForTimeout`. Test 1 should assert h1 "Tax Settings" (`S/fees/_tabs/tax.tsx:32`) and the seeded legal-za "VAT" 15% row (`backend/src/main/resources/vertical-profiles/legal-za.json:15`).

**Expected outcome after conversion:** test 1 should pass. Test 2 should pass, but it creates a rate per run, so use a unique name. Test 3 should pass once the 628F seed lands; without it, it fails. Note that invoice-crud/lifecycle specs may also create approved notes.

---

#### settings/general-settings.spec.ts — 4 sites (baseline: ✓3)

| line | test | usage | guarded locator | class | evidence | replacement |
|---|---|---|---|---|---|---|
| 44 | Update brand colour, 37 | B | getByLabel("Brand Color") | a | `#brand-color` text input `components/settings/general-settings-form.tsx:303-317` (the native `type=color` swatch is unlabelled) | `const colour = page.getByLabel("Brand Color", { exact: true }); await expect(colour).toBeVisible(); await colour.fill("#1a8f6e");` |
| 56 | 〃 | C | button "Save Settings" | a | `:341-344` (Letterhead uses "Save letterhead"; TaxSettingsForm is not on this tab) | Click unconditionally, `await page.reload()`, `await expect(page.getByLabel("Brand Color", { exact: true })).toHaveValue("#1a8f6e")` |
| 74 | Update document footer, 67 | B | getByLabel("Document Footer Text") | a | `#footer-text` `:326-334` | `await expect(footer).toBeVisible(); await footer.fill(testFooter);` |
| 85 | 〃 | C | button "Save Settings" | a | `:341-344` | Click, reload, `await expect(footer).toHaveValue(testFooter)` |

**Other soft patterns / notes:** 2 runtime `test.skip(`. 2 `waitForTimeout` (60, 89). The persistence checks only assert "no Something went wrong", so add `toHaveValue`. Brand colour changes persist into the shared tenant (branding shows in portal/PDFs). Consider restoring the original value.

**Expected outcome after conversion:** all 3 run and should pass.

---

#### notifications/notification-bell.spec.ts — 8 sites (baseline: ✓5)

| line | test | usage | guarded locator | class | evidence | replacement |
|---|---|---|---|---|---|---|
| 29 | Bell shows unread count, 18 | B | `button[aria-label*=notification]…` | a | Bell `<Button aria-label="Notifications">` `components/notifications/notification-bell.tsx:53-57` (the sidebar "Notifications" entry is a link) | `const bell = page.getByRole("button", { name: "Notifications", exact: true }); await expect(bell).toBeVisible();` |
| 50 | Click bell opens dropdown, 40 | B | same | a | same | same, then `await bell.click()` |
| 66 | 〃 | A | `[role=dialog]…` .first() | a | Panel `role="dialog" aria-label="Notifications"` `:78-82` | `await expect(page.getByRole("dialog", { name: "Notifications" })).toBeVisible()`. Delete the url fallback / soft assert at 68-72 |
| 85 | Mark notification as read, 75 | B | same bell | a | same | as 29 |
| 99 | 〃 | C | `[data-testid*="notification-item"], .notification-item` | c | No testid or class: items are plain `<button>` (`components/notifications/notification-item.tsx:64-97`), unread marked by `aria-label="Unread"` dot (`:91-93`). Presence is also data-dependent | Re-target with an or-branch: `const panel = page.getByRole("dialog", { name: "Notifications" }); const unread = panel.getByRole("button").filter({ has: page.getByLabel("Unread") }); await expect(unread.first().or(panel.getByText("No notifications"))).toBeVisible();` (`notification-dropdown.tsx:89-93`). If unread exists, click it and assert navigation or the dot gone. Better: create a notification for Bob first (see notes) so the branch collapses |
| 127 | Mark all as read, 120 | B | button /mark all.*read/ | b | Rendered only when `hasUnread` (`components/notifications/notifications-page-client.tsx:114-119`). Bob's unread state is not seeded; baseline ✓ only because earlier specs generated notifications | Make the state known by generating a Bob notification in `beforeAll` via API (e.g. Alice assigns a task to Bob on Website Redesign → TASK_ASSIGNED, `backend/.../notification/NotificationService.java`). Then unconditionally click "Mark all as read" and `expect(page.getByLabel("Unread")).toHaveCount(0)`. Otherwise use `expect(markAll.or(<all-read state>))` |
| 143 | 〃 | C | bell | a | `notification-bell.tsx:53-57` | Unconditional `expect(bell).toBeVisible()` |
| 146 | 〃 | C | `.badge, [data-testid="unread-count"]` | c | Badge is an unnamed `<span aria-hidden="true">` `:65-75`, with no class or testid | After `page.reload()`: `await expect(bell.locator('span[aria-hidden="true"]')).toHaveCount(0)`. A reload is required because the bell's SWR poll is 30 s (`hooks/use-notification-polling.ts:6`) and the page's mark-all (`notifications-page-client.tsx:63-73`) does not refetch it |

**Other soft patterns / notes:** 4 runtime `test.skip(`. `expect(url.includes("notification") || dropdownVisible).toBeTruthy()` at 71 is a soft tautology. `.flex-1` text-length check in test 4 (113-117) is vacuous; use h1 "Notifications" (`app/(app)/org/[slug]/notifications/page.tsx:25`). `badgeText === "0" \|\| ""` is a soft check. 3 `waitForTimeout`. Product observation (not a blocker): the bell badge stays stale for up to 30 s after "Mark all as read" on /notifications.

**Expected outcome after conversion:** tests 1, 2 and 4 should pass. Tests 3 and 5 depend on Bob having unread notifications. With a deterministic API-created notification they should pass. Without one, test 3 takes the "No notifications" branch and test 5 must branch.

---

#### notifications/notification-preferences.spec.ts — 5 sites (baseline: ✓2, -1)

| line | test | usage | guarded locator | class | evidence | replacement |
|---|---|---|---|---|---|---|
| 32 | View preference categories, 22 | C | link /preferences/ | c | /notifications has no preferences link, tab or button (`app/(app)/org/[slug]/notifications/page.tsx`, `components/notifications/notifications-page-client.tsx`). Preferences live at Settings › Connections › Notifications (finding 4) | Delete the probing. `await page.goto(\`${base}/settings/connections?tab=notifications\`); await expect(page.getByRole("heading", { level: 1, name: "Notification Preferences" })).toBeVisible();` |
| 33 | 〃 | C | tab /preferences/ | c | none | Delete |
| 34 | 〃 | C | button /preferences/ | c | none on /notifications | Delete |
| 69 | Disable email for a category, 59 | C | `a[href*=preferences]…` | c | none | Direct goto as above (the fallback `/settings/notifications` already 308s there) |
| 105 | Re-enable email for a category, 96 | C | same | c | none | same |

**Other soft patterns / notes:** 5 runtime `test.skip(`. Test 1 skips at baseline via the body "404" guard (46-49, finding 1). `toggles = 'button[role="switch"], input[type="checkbox"]'` `.count()` guard at 83/118. `toggles.first()` is the **In-App** switch of the first type, not Email. **Neither test clicks "Save preferences"** (`components/notifications/notification-preferences-form.tsx:184-188`), so nothing persists and test 3's reload asserts nothing. Real flow: `page.locator("#email-<TYPE>")` (the email switches have **no accessible name**: the `Label htmlFor` targets only the in-app id, `:152-176`, which is an a11y defect but not a blocker) → click → `getByRole("button", { name: "Save preferences" })` → `expect(getByText("Preferences saved successfully."))` → reload → `toHaveAttribute("aria-checked","false")`. Test 3 then re-enables. Tests are order-coupled in one worker (config is not fullyParallel). 5 `waitForTimeout`. `.flex-1` at 54.

**Expected outcome after conversion:** all 3 run. Test 1 should pass. Tests 2 and 3 become real persist checks and should pass. Pick the type from the rendered DOM, not a hardcoded enum.

---

#### navigation/command-palette.spec.ts — 7 sites (baseline: ✓3)

| line | test | usage | guarded locator | class | evidence | replacement |
|---|---|---|---|---|---|---|
| 33 | Cmd+K opens palette, 24 | B | `[role=dialog]…[cmdk-root]` | a | `CommandDialog` title "Command Palette" `components/command-palette-dialog.tsx:56-62`, input placeholder "Search pages, settings...". Shortcut is `(metaKey\|\|ctrlKey) && key==="k"` (`components/command-palette-provider.tsx:37`) | `const palette = page.getByRole("dialog", { name: "Command Palette" }); await expect(async () => { await page.keyboard.press("Meta+k"); await expect(palette).toBeVisible({ timeout: 1_000 }); }).toPass({ timeout: 10_000 });` (toPass covers pre-hydration keypress). Then `await expect(palette.getByPlaceholder("Search pages, settings...")).toBeVisible()` |
| 43 | 〃 | B | Control+K retry | c | Dead fallback: Meta and Ctrl share one handler | Delete (the retry is the toPass above) |
| 65 | Search navigates to page, 58 | C | palette | a | as 33 | as 33 |
| 75 | 〃 | B | palette | a | as 33 | Delete the duplicate guard |
| 88 | 〃 | C | `[cmdk-item], [role=option]` .first() | a | Page item "Fee notes" → `/invoices` (`lib/nav-items.ts:189`, dialog `:80-90`) | `const item = palette.getByRole("option", { name: "Fee notes" }); await expect(item).toBeVisible(); await item.click(); await expect(page).toHaveURL(/\/invoices/);` (drop the Enter fallback) |
| 107 | Escape closes palette, 100 | B | palette | a | as 33 | as 33, then Escape → `await expect(palette).toBeHidden()` |
| 115 | 〃 | B | Control+K retry | c | dead fallback | Delete |

**Other soft patterns / notes:** 3 runtime `test.skip(`. **8 `waitForTimeout`**, all removable with web-first waits. Optional deterministic opener: sidebar button "Search, Command K" (`components/desktop-sidebar.tsx:89`).

**Expected outcome after conversion:** all 3 run and should pass. The hydration race is covered by `toPass`.

---

#### projects/project-budget.spec.ts — 4 sites (baseline: ✓3)

| line | test | usage | guarded locator | class | evidence | replacement |
|---|---|---|---|---|---|---|
| 130 | 1. View budget tab on project, 121 | B | getByRole("tab", "Fee estimate") | c | "Fee estimate" is the menuitem `tab-item-budget` in group `finance` ("Fees") (`lib/constants/matter-tab-groups.ts:53-63`), not a tab | `await openGroupedTab(page, "finance", "budget"); await expect(page.getByText("No fee estimate set")).toBeVisible();` (`components/budget/budget-panel.tsx:87-92`, `lib/messages/en/empty-states.json:67-71`) |
| 135 | 〃 | B | getByText("Budget").first() | c | Matches the **matter name** "Budget Test Project xxxx" in the header, so baseline ✓ was a click on the title (vacuous) | Delete branch |
| 180 | 2. Set project budget via API…, 151 | C | tab "Fee estimate" | c | as 130 | `openGroupedTab(page, "finance", "budget")`, then assert the `h4` "Hours" and `h4` "Amount" sections and the "Fee estimate" StatCard (`budget-panel.tsx:151-196`). Optionally assert the 100 h / R 50 000 values via `formatDuration`/`formatCurrency` |
| 221 | 3. Budget shows usage after logging time, 190 | C | tab "Fee estimate" | c | as 130 | `openGroupedTab(...)`, then assert the "Consumed" StatCard shows 2 h (`budget-panel.tsx:163`) |

**Other soft patterns / notes:** 1 runtime `test.skip(`. 3 `waitForTimeout`. All three UI checks end with a vacuous `body not.toContainText("Something went wrong")`. Carol membership is `if (carol)`-guarded in `beforeAll` (95-107). If Carol is not found, test 3's time entry may 403 and the API assertion fails. Not a site; flag only.

**Expected outcome after conversion:** all 3 already run, and the UI assertions become real. They should pass. Test 1 fails if a budget panel other than the empty state renders: the matter is fresh, so it should not.

---

#### projects/project-crud.spec.ts — 4 sites (baseline: ✓7, -1)

| line | test | usage | guarded locator | class | evidence | replacement |
|---|---|---|---|---|---|---|
| 96 | 1. Create project with customer, 78 | C | dialog getByLabel(/Description/) | a | FormLabel "Description (optional)" + Textarea `components/projects/create-project-dialog.tsx:162-180` | `await expect(descField).toBeVisible(); await descField.fill(...)` |
| 104 | 〃 | C | dialog `select` .first() | a | First select is "Client (optional)" `:198-218`. Options load **async** via `fetchActiveCustomers` (`:71-75`), so the non-waiting `acmeOption.count()` at 108 can be 0 and silently create a no-client matter | `const clientSelect = dialog.getByLabel(/^Client/); await expect(clientSelect.locator("option", { hasText: "Acme Corp" })).toHaveCount(1); await clientSelect.selectOption({ label: "Acme Corp" });` (Acme is ACTIVE from seed) |
| 164 | 3. Edit project name, 152 | B | button /Edit/i | c | No Edit button. Edit is a **menuitem "Edit Matter"** behind `data-testid="overflow-actions-trigger"` (`components/projects/overflow-actions-menu.tsx:103-131`; rendered at `M/[id]/page.tsx:431`) | `await page.getByTestId("overflow-actions-trigger").click(); await page.getByRole("menuitem", { name: "Edit Matter" }).click(); const dlg = page.getByRole("dialog", { name: "Edit Matter" });` then `dlg.getByLabel("Name", { exact: true })` and `dlg.getByRole("button", { name: "Save Changes" })` (`components/projects/edit-project-dialog.tsx:127,137,274`) |
| 214 | 4. Project detail tabs load, 190 | C | getByRole("tab", name) ×5 | c | Only "Activity" is a role=tab (group label). Tasks/Time recording/Documents/Fee estimate are menuitems (finding 5) | Replace the loop with `openGroupedTab(page,"work","tasks")`, `("finance","time")`, `("work","documents")`, `("finance","budget")`, `("activity","activity")`. Each helper call asserts `aria-selected` on the group |

**Other soft patterns / notes:** 3 runtime `test.skip(` (167, 200, 262). Test 3's skip is the baseline `-`. Tests 4 and 5 then fall back to `PROJECT_WITH_CUSTOMER`, so they pass even though the edit never happened. `.count() > 0` at 108 (async race). 2 `waitForTimeout`. Test 4's "no assertion failure" comment admits vacuity.

**Expected outcome after conversion:** test 3 now really edits. It should pass, and test 5 then finds `EDITED_NAME`. Test 4 really opens the tabs and should pass. Test 1 should assert the client was linked (e.g. the list row shows "Acme Corp").


**Group C:** no product blockers. Product defects noted (not blocking; follow-ups): Email switches on notification preferences have no accessible name (`CMP/notifications/notification-preferences-form.tsx:169-176` → use `#email-<TYPE>`); "Mark all as read" on /notifications does not refresh the bell badge (30 s SWR poll, `hooks/use-notification-polling.ts:6`) → reload before asserting the badge.

## File Plan

**Create**
| Path | Content |
|---|---|
| `compose/seed/lib/trust.sh` | `seed_trust_account` (see Seed design §1) |
| `compose/seed/lib/information-requests.sh` | `seed_information_request` (§3) |
| `compose/seed/lib/statements.sh` | `seed_statement` (§4) |

**Modify (seed / config / tooling)**
| Path | Change |
|---|---|
| `compose/seed/seed.sh` | Step 7 resolves `MATTER_ID` GET-first, by name "Website Redesign". It also stops duplicating the matter on `e2e-reseed.sh`; today Step 7 always POSTs (`seed.sh:216-228`). Then source `lib/common.sh` and the libs, add Steps 8–12 (§0), move the portal resync last, and extend the summary banner. Keep `#!/bin/sh` + `set -eu` and stay POSIX (busybox ash in `alpine:3.19`, `compose/seed/Dockerfile`). |
| `compose/seed/lib/invoices.sh` | Add `seed_boot_fee_notes` (§2). Keep `seed_invoices` and its signature (rich-seed). Narrow its Acme lookup from `select(.clientName == "Acme Corp")` (`:14`) to its own notes marker `"Legacy migration completed work"`, so rich-seed does not adopt a boot fee note (OD-8). |
| `compose/seed/lib/common.sh` | Add `api_patch_json <path> <body> [jwt]`. This is a new name: `scripts/regression-test.sh:20-33` defines its own body-less `api_patch(path, jwt)` after sourcing, so a common `api_patch` would collide. Add `check_2xx <step> [status]` for transitions: the existing `check_status` accepts **409** as OK (`:113-120`), which would silently pass a refused approve or send. Existing helper signatures stay unchanged, so REGRESSION is untouched. |
| `frontend/e2e/playwright.config.ts` | Add the legal suites to `testIgnore` in **both** project branches (project-level `testIgnore` replaces config-level). Add the KC scripts per OD-2. See "Scripts & config". |
| `frontend/package.json` | Add 4 scripts: `test:e2e:projects\|customers\|settings\|navigation` ("Scripts & config"). |
| `frontend/eslint.config.mjs` | Widen the rule block (`:27-41`) to `files: ["e2e/**/*.ts"]` and update the comment. |
| `frontend/__tests__/eslint-rules/no-isvisible-catch.test.ts` | Flip the wiring pins (`:155-163`) — "ESLint rule wiring". |
| `frontend/e2e/AUTOMATION-NOTES.md` | (1) Assertion rules: replace the scope paragraph (`:628-629`, "It is scoped … 628F widens it to `e2e/**`") with "applies to every file under `e2e/**`". (2) "Seed Data State After Stack Start" (`:70-90`) is stale ("Customer Acme ONBOARDING", "Projects None", "Invoices None"). Rewrite it to the real boot seed: Acme ACTIVE, matter Website Redesign, 2 fee notes (1 DRAFT, 1 SENT, `INV-` numbered), trust account, 1 SENT info request, 1 shared SoA, cadence BIWEEKLY. Delete the "CRITICAL: Customer Lifecycle" block. (3) New "Running a subset" rule (P-1): filters are path regexes; anchor with `e2e/tests/<dir>/` or use the per-suite scripts. Never `-- projects`, which matches `/Projects/` in the checkout path. (4) Product-blocker tag: `628F-BLOCKER` alongside `628E-BLOCKER`. |
| `frontend/CLAUDE.md` | ESLint bullet (`:194`): "in the e2e files it is scoped to" → "in every file under `e2e/**`". |
| `frontend/e2e/README.md` | Optional: list the four per-suite scripts next to `:57-63`. |

**Modify (specs)**: the 28 census files plus one depth-ii file. Each gets the conversions in its census table.
- `customers/`: customer-crud, customer-lifecycle, customer-data-protection
- `projects/`: project-tasks, project-time, project-budget, project-crud
- `settings/`: packs (PACK-01 only; 0 sites), custom-fields, tags, roles-capabilities, tax-settings, general-settings, data-protection-settings
- `navigation/`: command-palette
- `notifications/`: notification-bell, notification-preferences
- `documents/`: document-generation, template-management
- `information-requests/`: information-request-crud
- `invoices/`: billing-runs, invoice-crud, invoice-lifecycle
- `automations/`: automation-crud, automation-triggers
- `compliance/`: compliance-overview
- `finance/`: profitability, reports
- `demo-full-90day`, `demo-recording`, `debug-kc-invite` (OD-2)
- `legal-depth-ii/day-14-conveyancing.spec.ts:142-143` (bare `isVisible()` after a click). Replace it with the deterministic per-item flag table that the lifecycle already uses (`legal-lifecycle/day-01-03-onboarding.spec.ts:82-92`, loop `:165-189`): `{ name, requiresDocument }`, `await expect(needsDoc).toHaveCount(requiresDocument ? 1 : 0)` **before** "Mark Complete", then `if (requiresDocument)`. The "Requires document:" text is static row content, rendered before the click. Hand-off alternative: an `a.or(b)` wait (OD-7).

New helper (optional, recommended): `e2e/helpers/seed.ts`. It would export `SEED = { client: "Acme Corp", matter: "Website Redesign", contactEmail: "contact@acme.example.com" }` and `acmeId()` / `websiteRedesignId()` via the API (`GET /api/clients`, `GET /api/matters`, by name), so the census re-targets share one lookup (X4). This replaces six ad-hoc `customers.find(...)` copies.

## Seed design

### §0 Wiring in `seed.sh` (after Step 7)

```sh
# -- Step 7: Create matter (GET-first: e2e-reseed.sh re-runs this script on a populated tenant) ----
MATTER_ID=$(curl -sf "${BACKEND_URL}/api/matters" -H "Authorization: Bearer ${ALICE_JWT}" \
  | jq -r '.[] | select(.name == "Website Redesign") | .id' | head -1)
if [ -z "$MATTER_ID" ] || [ "$MATTER_ID" = "null" ]; then
  MATTER_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${BACKEND_URL}/api/matters" \
    -H "Content-Type: application/json" -H "Authorization: Bearer ${ALICE_JWT}" \
    -d "{\"name\": \"Website Redesign\", \"description\": \"E2E seed project for testing\", \"clientId\": \"${CUSTOMER_ID}\"}")
  check_status "Create project" "$(echo "$MATTER_RESPONSE" | tail -1)"
  MATTER_ID=$(echo "$MATTER_RESPONSE" | sed '$d' | jq -r '.id')
fi
# -- Steps 8-12: Phase 88 628F rows (APIs only — never SQL; memory feedback_no_sql_shortcuts_in_qa) --
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
. "${SCRIPT_DIR}/lib/common.sh"     # from here on check_status = common.sh's (stderr, `return 1`; set -e exits)
_ALICE_JWT="$ALICE_JWT"             # prime get_jwt's cache
ACME_ID="$CUSTOMER_ID"; WEBSITE_REDESIGN_ID="$MATTER_ID"
. "${SCRIPT_DIR}/lib/trust.sh";                seed_trust_account          # Step 8
. "${SCRIPT_DIR}/lib/invoices.sh";             seed_boot_fee_notes         # Step 9
. "${SCRIPT_DIR}/lib/information-requests.sh"; seed_information_request    # Step 10 (Day 3)
. "${SCRIPT_DIR}/lib/statements.sh";           seed_statement              # Step 11 (Day 60, after the fee notes so the SoA lists them)
api_patch_json "/api/settings/portal-digest-cadence" '{"portalDigestCadence":"BIWEEKLY"}' "$ALICE_JWT" > /dev/null
check_2xx "Portal digest cadence BIWEEKLY (Day 85)"                         # Step 12
# -- Step 13: Portal resync (existing Step 8, moved last so the read model sees every row) --
```

- The container's `WORKDIR` is `/seed` and the image copies `lib/` (`compose/seed/Dockerfile`), so `${SCRIPT_DIR}/lib` resolves.
- When run on the host, `seed.sh` defaults `BACKEND_URL` to `http://backend:8080` (`:4`), so host runs need `BACKEND_URL=http://localhost:8081 MOCK_IDP_URL=http://localhost:8090`.
- `common.sh`'s environment detection keeps an already-set `BACKEND_URL` (`common.sh:6-12`).
- **Failure mode:** the seed container exits non-zero, and `e2e-up.sh:112-119` only prints `WARNING: Seed exited with code N`. The gate therefore needs the log check in "Build & Verify".

### §1 Trust account — `lib/trust.sh` `seed_trust_account`

- **Endpoint:** `POST /api/trust-accounts`.
  - Controller: `$B/verticals/legal/trustaccounting/TrustAccountingController.java:45-51`, `@RequiresCapability("MANAGE_TRUST")` (owner has it).
  - Service: `TrustAccountService.java:126-165`, which does `moduleGuard.requireModule("trust_accounting")` (`:26,:127`; on for legal-za).
- **DTO:** `CreateTrustAccountRequest` (`TrustAccountService.java:49-59`): `@NotBlank accountName, bankName, branchCode, accountNumber`; optional `accountType` (default GENERAL), `isPrimary` (default **true**, `:129`), `requireDualApproval` (default false), `paymentApprovalThreshold`, `openedDate` (default `LocalDate.now()`), `notes`.
- **Payload.** It must equal `TRUST_ACCOUNT` in `frontend/e2e/helpers/legal-lifecycle.ts:286-291` and the depth-ii create at `legal-depth-ii/day-05-disbursements.spec.ts:210-213`:
  `{"accountName":"Mathebula & Partners Trust Account","bankName":"Standard Bank","branchCode":"051001","accountNumber":"0123456789","accountType":"GENERAL","isPrimary":true,"requireDualApproval":false}`
  - These match the UI dialog's defaults (`CMP/trust/CreateTrustAccountDialog.tsx:50-62`: GENERAL, isPrimary true, dual approval false, openedDate today).
  - **branchCode and accountNumber are load-bearing:** `legal-lifecycle/day-45-recon-prescription.spec.ts:68` asserts the option text `Standard Bank - 0123456789`.
- **Preconditions:** none beyond the module. **Uniqueness:** `V1__baseline.sql:2218` has a unique index on primary GENERAL (`WHERE is_primary AND account_type='GENERAL'`).
- **Idempotency:** `GET /api/trust-accounts` (bare list, `:33-37`), then `jq --arg n "$NAME" '.[] | select(.accountName == $n) | .id'`.
  - If found → `[skip]`.
  - If not found, but an ACTIVE `accountType == "GENERAL"` account exists → `[FAIL]` with its name, because the unique index would reject the POST.
  - Do not match on `isPrimary` in the response: Jackson may serialise the record's `boolean isPrimary` as `primary`.
- **Consumers:**
  - `legal-lifecycle/day-00-firm-setup.spec.ts:242-260` (`ensureTrustAccount` → the "exists" branch; asserts the name on `/settings/fees?tab=trust-accounting`);
  - Day 0 LPFF rate (adds a rate to this account);
  - Day 14 deposits, Day 30 fee transfer, Day 45 recon `account-select`, Day 60 interest and investments;
  - depth-ii `day-05:196-217` (create-if-absent → skip).
  - Portal Day 21 `/trust` (628G) wants a **deposit for Acme**, which is **not** in 628.14 → flag for 628G (OD-10).
- **Risks:** none if the fields are identical. A different accountNumber breaks `day-45:68`.

### §2 Fee notes — `lib/invoices.sh` `seed_boot_fee_notes`

- **Endpoints** (`$B/invoice/InvoiceController.java`, all `@RequiresCapability("INVOICING")`):
  - create `POST /api/invoices` (`:56-63`);
  - line `POST /api/invoices/{id}/lines` (`:138-144`);
  - approve `POST /api/invoices/{id}/approve` (`:183-188`, no body);
  - send `POST /api/invoices/{id}/send` with body `SendInvoiceRequest(boolean overrideWarnings)` (`:190-195`);
  - list `GET /api/invoices?clientId=` (bare array, `:111-118`).
- **DTOs:**
  - `CreateInvoiceRequest(@NotNull UUID clientId, @NotBlank @Size(3) String currency, timeEntryIds, expenseIds, disbursementIds, dueDate, notes, paymentTerms, poNumber, taxType, billingPeriodStart, billingPeriodEnd)`.
  - `AddLineItemRequest(UUID matterId, String description, @NotNull @Positive quantity, @PositiveOrZero unitPrice, sortOrder, taxRateId, tariffItemId)`.
- **Preconditions:**
  - Create runs `validateInvoicePrerequisites` → INVOICE_GENERATION structural check (addressLine1/city/country; `InvoiceCreationService.java:155,794-812`). Acme is created with an address (`seed.sh:125-134`).
  - Approve needs ≥1 line (`InvoiceTransitionService.java:112-116`) and assigns the number `INV-%04d` (`:133`, `InvoiceNumberService.java:49`).
  - Send validation is CRITICAL for org name, client required fields, and tax number **for COMPANY/TRUST only** (`InvoiceValidationService.java:81-93`). Acme has no `entityType`, so it is INDIVIDUAL, and `"overrideWarnings": false` should pass.
  - If send answers 422, read the checks in the body and fix the seed client data (e.g. add `taxNumber`). **Do not** paper over it with `overrideWarnings: true` without a reason in the PR body. `regression-test.sh:297/317` uses `true`.
- **Payloads** (both on Website Redesign so the portal shows them under the matter):
  - **A, draft:** `{"clientId":ACME,"currency":"ZAR","notes":"E2E seed fee note — draft","paymentTerms":"Net 30"}`, then line `{"matterId":WR,"description":"Consultation — website redesign mandate","quantity":2,"unitPrice":1500.00,"sortOrder":0}`.
  - **B, sent:** `{"clientId":ACME,"currency":"ZAR","notes":"E2E seed fee note — sent","paymentTerms":"Net 30"}`, then line `{"matterId":WR,"description":"Drafting — services agreement","quantity":3,"unitPrice":2500.00,"sortOrder":0}`, then `/approve` `{}`, then `/send` `{"overrideWarnings":false}`.
- **Idempotency:** list by `clientId` and select by `.notes == marker`.
  - A: create if absent.
  - B: create if absent, then act on `.status`: `DRAFT` → approve + send; `APPROVED` → send; `SENT`/`PAID`/… → skip.
  - Use `check_2xx`, not `check_status`, for approve and send (409 = refused transition).
- **Consumers:**
  - `settings/tax-settings.spec.ts:104` (`INV-\d{4}` link, class d);
  - `finance/reports.spec.ts` invoice-aging (non-empty run, optional);
  - lifecycle A5 `day-90-review.spec.ts:96-112` (≥2 rows; additive, id-scoped);
  - portal Day 45 "invoice detail / pay" (628G: the pay button needs a PSP; out of scope).
- **Risks** (checked):
  - The seed takes **INV-0001**. No spec asserts a specific number: grep `INV-0` across `frontend/e2e`, `portal/e2e` and `scripts/regression-test.sh` finds none. Lifecycle Day 30 asserts only `numbers[0] != numbers[1]` and id-scoped rows (`day-30-billing.spec.ts:292-308`). depth-ii looks fee notes up by href or id (`day-75-85-closure.spec.ts:178-208`).
  - Fee-note list counts are `>= 2` (poll) only.
  - PACK-01 test 2 is safe (see Baseline #1).
  - Sending emails Acme's address (Mailpit); no magic-link token is minted.

### §3 Information request (Day 3) — `lib/information-requests.sh` `seed_information_request`

- **Endpoints** (`$B/informationrequest/InformationRequestController.java`):
  - create `POST /api/information-requests` (`:32-39`, `CUSTOMER_MANAGEMENT`, 201);
  - send `POST /api/information-requests/{id}/send` (`:61-65`, no body);
  - list `GET /api/information-requests?customerId=&projectId=&status=` (`:41-47`; **the param names are `customerId`/`projectId`**, bare array).
  - Portal contact: `GET /api/clients/{clientId}/portal-contacts` (`$B/customer/CustomerController.java:325-329`) → `PortalContactSummary(id, displayName, email)` (`$B/portal/PortalContactSummary.java:6`). The contact is auto-provisioned synchronously on client create (`PortalContactAutoProvisioner.java:42-44`, `@EventListener` on `CustomerCreatedEvent`).
- **DTO:**
  - `CreateInformationRequestRequest(UUID requestTemplateId, @NotNull UUID clientId, UUID matterId, @NotNull UUID portalContactId, Integer reminderIntervalDays, LocalDate dueDate, @Valid List<AdHocItemRequest> items)`.
  - `AdHocItemRequest(@NotBlank name, description, @NotNull ResponseType responseType, boolean required, fileTypeHints, int sortOrder)` (`informationrequest/dto/InformationRequestDtos.java:19-37`).
  - `ResponseType` = `FILE_UPLOAD | TEXT_RESPONSE`.
  - `RequestStatus` = `DRAFT | SENT | IN_PROGRESS | COMPLETED | CANCELLED`.
- **Preconditions:** the client exists; the portal contact belongs to the client (`validatePortalContact`, `InformationRequestService.java:~226`); send requires ≥1 item (`:363-364`). The number is `REQ-%04d`.
- **Payload:** `{"clientId":ACME,"matterId":WR,"portalContactId":<contact@acme.example.com id>,"items":[{"name":"Certified ID copy","description":"Upload a certified copy of your ID document","responseType":"FILE_UPLOAD","required":true,"fileTypeHints":"PDF, JPG, PNG","sortOrder":0}]}`.
  - Omit `dueDate` (nullable). busybox `date -d @…` arithmetic works in the container but not on a BSD/macOS host.
- **Idempotency:** list for (Acme, WR) and pick a request whose `items[].name == "Certified ID copy"`.
  - `SENT`/`IN_PROGRESS` → skip.
  - `DRAFT` → send.
  - None, or only `COMPLETED`/`CANCELLED` → create + send. This keeps portal Day 3 re-runnable on a reseeded stack (OD-8).
- **Side effect:** send emails the contact and mints **one** magic-link token (`InformationRequestEmailEventListener.java:73-79` → `MagicLinkService.generateTokenOnly`, cap 3 per contact per 5 min, `MagicLinkService.java:29,112-116`). 628G's portal `globalSetup` exchange spends another; `regression-test.sh`'s portal section spends one more → note for 628G.
- **Consumers:**
  - `information-requests/information-request-crud.spec.ts:81, :128` (class d; re-targeted to `matters/{WR}?tab=requests`; row link `^REQ-\d{4}$`, `CMP/information-requests/request-list.tsx:58-63`);
  - portal Day 0 home "Pending info requests 1" (`portal/app/(authenticated)/home/page.tsx:64-88`) and Day 3 upload + submit (`portal/app/(authenticated)/requests/[id]/page.tsx:190,210`; 628G).
- **Risks:** lifecycle Day 75 creates REQ-000N for Moroka and asserts `toHaveCount(1)` inside **Moroka's** client panel plus `/^REQ-\d+$/` (`day-75-complex.spec.ts:242-268`) → safe.

### §4 Statement of Account (Day 60) — `lib/statements.sh` `seed_statement`

- **Endpoint:** `POST /api/matters/{matterId}/statements` (`$B/verticals/legal/statement/StatementController.java:38-46`, `GENERATE_STATEMENT_OF_ACCOUNT`, 201). List: `GET …/statements` returns a Spring `Page` (VIA_DTO shape `{content:[…], page:{totalElements}}`, `frontend/CLAUDE.md` › Paginated Responses).
- **DTO:** `GenerateStatementRequest(@NotNull LocalDate periodStart, @NotNull LocalDate periodEnd, UUID templateId, Boolean shareWithClient, Boolean notifyClient)`, with `periodEnd >= periodStart` (`dto/GenerateStatementRequest.java:20-31`).
  - `shareWithClient` saves the paired Document with `Visibility.PORTAL`; otherwise it is INTERNAL (`StatementService.java:164-171`).
  - `notifyClient` emails the contact and implies share.
- **Preconditions:**
  - module `disbursements` (`StatementService.java:63,111`; on for legal-za);
  - the system template slug `statement-of-account` from the legal-za template pack (`:64,341-361`);
  - the matter exists.
- **Payload:** `{"periodStart":"<YYYY>-01-01","periodEnd":"<today>","shareWithClient":true,"notifyClient":false}`, with `$(date +%Y)-01-01` and `$(date +%Y-%m-%d)` (portable). `notifyClient:false` mints no extra token and sends no extra email.
- **Output:** file name `statement-of-account-website-redesign-<periodEnd>.pdf` (`StatementService.java:384-388`), which matches the portal's `/\.pdf/i`. Generated-documents row: template name from the pack.
- **Idempotency:** `GET …/statements?size=1` → `jq '.page.totalElements // (.content | length) // 0'` > 0 → skip.
- **Consumers:** `documents/document-generation.spec.ts:229, :282, :298` (class d, `?tab=generated`); portal Day 60 `/matters` → Website Redesign → Documents (628G).
- **Risks:**
  - PDF render time counts against `e2e-up.sh`'s 120 s seed wait (`:102`) → OD-9.
  - depth-ii's "no statements yet" check is on its own run matter (`day-30-45-soa-writeoff.spec.ts:22`) → safe.
  - It adds a "documents generated" uninstall reason → PACK-01 tolerant.

### §5 Portal digest cadence (Day 85) — in `seed.sh`

- **Endpoint:** `PATCH /api/settings/portal-digest-cadence` (`$B/settings/OrgSettingsController.java:35,181-187`, `TEAM_OVERSIGHT`; the service also requires admin/owner, `OrgSettingsService.java:1235-1245`).
- **Body:** `UpdatePortalDigestCadenceRequest(@NotNull PortalDigestCadence portalDigestCadence)`, enum `WEEKLY | BIWEEKLY | OFF` (`settings/PortalDigestCadence.java`; default WEEKLY).
- **Idempotent:** it is a PATCH.
- **Consumer:** portal Day 85 reads "Your firm sends bi-weekly digests." (`portal/app/(authenticated)/settings/notifications/page.tsx:83-84`; 628G).
- **Risks:** no frontend spec reads the cadence (grep `digest|weekly` in `frontend/e2e/tests` → none).

## Scripts & config

**How the suites are invoked today** (`frontend/package.json:14-16`):
- `"test:e2e": "playwright test --config e2e/playwright.config.ts"`
- `"test:e2e:legal-lifecycle": "playwright test --config e2e/playwright.legal-lifecycle.config.ts"`
- `"test:e2e:legal-depth-ii": "playwright test --config e2e/playwright.legal-depth-ii.config.ts"`

The default config (`frontend/e2e/playwright.config.ts`, full text below) uses `testDir: "./tests"`, which contains the legal dirs. In mock mode the only `testIgnore` is `["**/keycloak/**"]`. Both legal configs throw unless `E2E_AUTH_MODE` is mock, use `workers: 1`, and set `retries: 0` (lifecycle) or `CI ? 1 : 0` (depth-ii). Each sets its own `testDir`. No vitest test and no CI workflow pins these scripts. The only reference is `frontend/e2e/README.md`.

`frontend/e2e/playwright.config.ts` (current, complete):
```ts
import { defineConfig, devices } from "@playwright/test";

const authMode = process.env.E2E_AUTH_MODE || "mock";

export default defineConfig({
  testDir: "./tests",
  globalTimeout: 600_000,
  timeout: authMode === "keycloak" ? 60_000 : 30_000,
  retries: process.env.CI ? 1 : 0,
  workers: authMode === "keycloak" ? 1 : undefined,
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL || "http://localhost:3000",
    screenshot: "only-on-failure",
    trace: "on-first-retry",
  },
  projects:
    authMode === "keycloak"
      ? [
          // Onboarding writes /tmp/e2e-keycloak-state.json which member-invite-rbac
          // reads at describe-collection time. Run it as a setup project so the
          // state file exists before any dependent specs are collected.
          {
            name: "kc-setup",
            use: { ...devices["Desktop Chrome"] },
            testMatch: /keycloak\/onboarding\.spec\.ts/,
          },
          {
            name: "chromium",
            use: { ...devices["Desktop Chrome"] },
            testIgnore: /keycloak\/onboarding\.spec\.ts/,
            dependencies: ["kc-setup"],
          },
        ]
      : [
          {
            name: "chromium",
            use: { ...devices["Desktop Chrome"] },
            testIgnore: ["**/keycloak/**"],
          },
        ],
});
```

**Proposed change.** A project-level `testIgnore` replaces the config-level one, so add the legal suites to **both** `chromium` projects. The legal configs refuse KC mode, so the KC branch must never pick them up either.
```ts
// Phase 88 628F (P-1, U22): the legal suites run under their own configs
// (playwright.legal-*.config.ts) with run state and serial chains; the default config must never
// re-run them against a populated tenant (e.g. via an unanchored filter such as `-- projects`).
const LEGAL_SUITES = ["**/legal-lifecycle/**", "**/legal-depth-ii/**"];
…
            testIgnore: [/keycloak\/onboarding\.spec\.ts/, ...LEGAL_SUITES],   // KC chromium
…
            testIgnore: ["**/keycloak/**", ...LEGAL_SUITES /* , …KC_ONLY_SCRIPTS per OD-2 (b) */],   // mock chromium
```

**Expected `--list` after the change** (measured on 43e8fddb2: total 326 tests / 57 files, of which legal-lifecycle 104, legal-depth-ii 43, KC scripts 3):
- mock: **179 tests**, or **176** if OD-2 removes or ignores the three KC scripts. Confirm with `npx playwright test --config e2e/playwright.config.ts --list | tail -1`; this needs no browser and no stack.
- Directory breakdown today: admin-audit-30day 8, audit-log 2, auth 12, automations 7, compliance 2, customers 20, documents 8, finance 7, information-requests 5, invoices 21, navigation 16, notifications 8, portal 2, projects 25, settings 30, smoke 3, KC scripts 3.

**`frontend/package.json` — four per-suite scripts (P-1).** These use directory filters. Playwright's positional args are regexes matched against the absolute file path, and `--grep` matches test titles, not paths. The scripts:
```json
    "test:e2e:projects": "playwright test --config e2e/playwright.config.ts e2e/tests/projects/",
    "test:e2e:customers": "playwright test --config e2e/playwright.config.ts e2e/tests/customers/",
    "test:e2e:settings": "playwright test --config e2e/playwright.config.ts e2e/tests/settings/",
    "test:e2e:navigation": "playwright test --config e2e/playwright.config.ts e2e/tests/navigation/",
```
- Verified with `--list` on 43e8fddb2: projects **25 tests / 4 files**; customers **20 / 3**; settings **30 / 8**; navigation **16 / 2**.
- The P-1 reproduction: `--list projects` → **326 / 57**. It matches every file because the checkout path contains `/Projects/` (R10 §D, `tasks/phase88-research/R10-playwright-carried-failures.md:133-158`).
- Extra Playwright flags go straight after the script name, with **no `--`**: `pnpm test:e2e:projects --reporter=list` (628E R-13).

## ESLint rule wiring

**Current wiring** — `frontend/eslint.config.mjs:27-41`. The plugin is registered for `**/*` at `:20-26`.
```js
  // Playwright strict assertions (Phase 88 628E, U22, R10 §0 M1): no `.catch()` on isVisible & co.,
  // no ignored `isVisible({ timeout })`. See e2e/AUTOMATION-NOTES.md › Assertion rules. Scoped to the
  // two legal suites + shared helpers/fixtures (R-2 / OD-2 (b)); 628F widens it to e2e/** with the
  // default-config sweep.
  {
    files: [
      "e2e/tests/legal-lifecycle/**/*.ts",
      "e2e/tests/legal-depth-ii/**/*.ts",
      "e2e/helpers/**/*.ts",
      "e2e/fixtures/**/*.ts",
    ],
    rules: {
      "kazi-frontend-local/no-isvisible-catch": "error",
    },
  },
```
**Target:**
```js
  // Playwright strict assertions (Phase 88 628E/628F, U22, R10 §0 M1): no `.catch()` on isVisible & co.,
  // no ignored `isVisible({ timeout })`, in every e2e file. See e2e/AUTOMATION-NOTES.md › Assertion rules.
  {
    files: ["e2e/**/*.ts"],
    rules: {
      "kazi-frontend-local/no-isvisible-catch": "error",
    },
  },
```
**Wiring-test pins to flip.** The current text is `frontend/__tests__/eslint-rules/no-isvisible-catch.test.ts:136-164`. The full file is under Reference patterns.
- Move `"e2e/tests/settings/__probe__.spec.ts"` from the "is not (yet) enforced" list into the `"is enforced in %s"` list.
- Add `"e2e/tests/__probe__.spec.ts"` (top-level spec dir, which holds `smoke`/`demo-*`), `"e2e/tests/customers/__probe__.spec.ts"`, `"e2e/tests/documents/__probe__.spec.ts"` and `"e2e/__probe__.ts"`.
- Keep a **negative** pin so the rule never leaks into product code. Rename the block to "is not enforced outside e2e/ in %s" and give it `components/__probe__.tsx`, `app/__probe__.tsx` and `lib/__probe__.ts`.
- Replace the "Deliberate scope (… 628F widens …) Flip these assertions when widening." comment (`:155-157`) with a one-liner: "628F: the rule covers every file under e2e/; product code is out of scope."

The rule module (`frontend/e2e/eslint/no-isvisible-catch.mjs`) needs **no change**. Its RuleTester half (`:30-123`) stays as-is.

**Lint gate:** the widened config fails `pnpm lint` until all 187 sites are converted, or until their files are deleted or excluded per OD-2. If the slice is split across two builders (OD-1), the first builder widens the `files:` list directory by directory, and the last builder lands `e2e/**/*.ts`.

## Reference patterns (FULL code)

### Seed shared library — `compose/seed/lib/common.sh` (complete)
```sh
#!/bin/sh
# compose/seed/lib/common.sh — Shared helpers for seed scripts
# Source this file; do not execute directly.

# ── Environment detection ───────────────────────────────────────────
if curl -sf http://backend:8080/actuator/health > /dev/null 2>&1; then
  BACKEND_URL="${BACKEND_URL:-http://backend:8080}"
  MOCK_IDP_URL="${MOCK_IDP_URL:-http://mock-idp:8090}"
else
  BACKEND_URL="${BACKEND_URL:-http://localhost:8081}"
  MOCK_IDP_URL="${MOCK_IDP_URL:-http://localhost:8090}"
fi

API_KEY="${API_KEY:-e2e-test-api-key}"
ORG_ID="e2e-test-org"
ORG_SLUG="e2e-test-org"

# Temp file for API responses (cleaned up on exit)
_API_TMPFILE=$(mktemp)
trap 'rm -f "$_API_TMPFILE" "$_API_TMPFILE.body"' EXIT

# ── JWT cache ───────────────────────────────────────────────────────
_ALICE_JWT=""
_BOB_JWT=""
_CAROL_JWT=""

get_jwt() {
  user_id="$1"
  org_role="$2"

  case "$user_id" in
    user_e2e_alice) [ -n "$_ALICE_JWT" ] && echo "$_ALICE_JWT" && return ;;
    user_e2e_bob)   [ -n "$_BOB_JWT" ]   && echo "$_BOB_JWT"   && return ;;
    user_e2e_carol) [ -n "$_CAROL_JWT" ] && echo "$_CAROL_JWT" && return ;;
  esac

  token=$(curl -sf -X POST "${MOCK_IDP_URL}/token" \
    -H "Content-Type: application/json" \
    -d "{\"userId\":\"${user_id}\",\"orgId\":\"${ORG_ID}\",\"orgSlug\":\"${ORG_SLUG}\",\"orgRole\":\"${org_role}\"}" \
    | jq -r '.access_token')

  if [ -z "$token" ] || [ "$token" = "null" ]; then
    echo "FATAL: Failed to get JWT for ${user_id}" >&2
    exit 1
  fi

  case "$user_id" in
    user_e2e_alice) _ALICE_JWT="$token" ;;
    user_e2e_bob)   _BOB_JWT="$token" ;;
    user_e2e_carol) _CAROL_JWT="$token" ;;
  esac

  echo "$token"
}

# ── API helpers ─────────────────────────────────────────────────────
# Write HTTP status to $_API_TMPFILE, body to stdout.
# Read status after call with: last_status

api_post() {
  _p="$1"; _b="$2"; _j="${3:-}"
  if [ -n "$_j" ]; then
    curl -s -o "$_API_TMPFILE.body" -w "%{http_code}" \
      -X POST "${BACKEND_URL}${_p}" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer ${_j}" \
      -d "$_b" > "$_API_TMPFILE"
  else
    curl -s -o "$_API_TMPFILE.body" -w "%{http_code}" \
      -X POST "${BACKEND_URL}${_p}" \
      -H "Content-Type: application/json" \
      -d "$_b" > "$_API_TMPFILE"
  fi
  cat "$_API_TMPFILE.body"
}

api_get() {
  _p="$1"; _j="${2:-}"
  if [ -n "$_j" ]; then
    curl -s -o "$_API_TMPFILE.body" -w "%{http_code}" \
      -X GET "${BACKEND_URL}${_p}" \
      -H "Authorization: Bearer ${_j}" > "$_API_TMPFILE"
  else
    curl -s -o "$_API_TMPFILE.body" -w "%{http_code}" \
      -X GET "${BACKEND_URL}${_p}" > "$_API_TMPFILE"
  fi
  cat "$_API_TMPFILE.body"
}

api_put() {
  _p="$1"; _b="$2"; _j="${3:-}"
  if [ -n "$_j" ]; then
    curl -s -o "$_API_TMPFILE.body" -w "%{http_code}" \
      -X PUT "${BACKEND_URL}${_p}" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer ${_j}" \
      -d "$_b" > "$_API_TMPFILE"
  else
    curl -s -o "$_API_TMPFILE.body" -w "%{http_code}" \
      -X PUT "${BACKEND_URL}${_p}" \
      -H "Content-Type: application/json" \
      -d "$_b" > "$_API_TMPFILE"
  fi
  cat "$_API_TMPFILE.body"
}

last_status() {
  cat "$_API_TMPFILE"
}

# ── Status check (always to stderr so $() capture isn't polluted) ───

check_status() {
  step="$1"
  status="${2:-$(last_status)}"
  case "$status" in
    2[0-9][0-9]|409) echo "    [ok] ${step} (HTTP ${status})" >&2 ;;
    *) echo "    [FAIL] ${step} (HTTP ${status})" >&2; return 1 ;;
  esac
}

# ── Idempotency helper ──────────────────────────────────────────────

# find_existing <list_path> <jq_filter> [jwt]
# Returns the ID if found, empty string if not.
find_existing() {
  _fe_path="$1"
  _fe_filter="$2"
  _fe_jwt="${3:-}"

  api_get "${_fe_path}?size=200" "$_fe_jwt" | jq -r "$_fe_filter | .id" 2>/dev/null
}

# complete_checklists <customer_id> <jwt>
# Completes all checklist items for a customer to allow ONBOARDING -> ACTIVE transition.
complete_checklists() {
  _cc_customer_id="$1"
  _cc_jwt="$2"

  checklists=$(api_get "/api/clients/${_cc_customer_id}/checklists" "$_cc_jwt")

  item_ids=$(echo "$checklists" | jq -r '
    [.[] | .items[]? | select(.status != "COMPLETED" and .status != "SKIPPED") | .id] | .[]
  ' 2>/dev/null)

  for item_id in $item_ids; do
    api_put "/api/checklist-items/${item_id}/complete" '{"notes":"seed"}' "$_cc_jwt" > /dev/null
    _s=$(last_status)
    case "$_s" in
      2[0-9][0-9]) ;;
      *) api_put "/api/checklist-items/${item_id}/skip" '{"reason":"seed"}' "$_cc_jwt" > /dev/null ;;
    esac
  done
  echo "    [ok] Checklists completed for customer ${_cc_customer_id}" >&2
}
```

### Existing seed lib file — `compose/seed/lib/invoices.sh` (complete; `seed_invoices` is rich-seed's — keep its signature, add `seed_boot_fee_notes` below it)
```sh
#!/bin/sh
# compose/seed/lib/invoices.sh — Create invoices with lines
# Requires: lib/common.sh, customer IDs, project IDs

seed_invoices() {
  echo ""
  echo "==> Seeding invoices"
  jwt=$(get_jwt user_e2e_alice owner)

  # Check existing invoices (bare array response)
  existing=$(api_get "/api/invoices?size=200" "$jwt")

  # ── Invoice 1: Acme Corp (manual lines) ─────────────────────────
  acme_invoice=$(echo "$existing" | jq -r '.[] | select(.clientName == "Acme Corp") | .id' 2>/dev/null | head -1)
  if [ -n "$acme_invoice" ] && [ "$acme_invoice" != "null" ]; then
    echo "    [skip] Acme Corp invoice exists (${acme_invoice})"
    ACME_INVOICE_ID="$acme_invoice"
  else
    body=$(api_post "/api/invoices" "{
      \"clientId\": \"${ACME_ID}\",
      \"currency\": \"ZAR\",
      \"notes\": \"Legacy migration completed work\",
      \"paymentTerms\": \"Net 30\"
    }" "$jwt")
    check_status "Create Acme Corp invoice" || return 1
    ACME_INVOICE_ID=$(echo "$body" | jq -r '.id')

    # Add manual lines
    api_post "/api/invoices/${ACME_INVOICE_ID}/lines" '{
      "description": "Database schema migration — 6 hrs",
      "quantity": 6,
      "unitPrice": 150.00,
      "sortOrder": 0
    }' "$jwt" > /dev/null
    check_status "  Line: schema migration"

    api_post "/api/invoices/${ACME_INVOICE_ID}/lines" '{
      "description": "Data validation scripts — 2 hrs",
      "quantity": 2,
      "unitPrice": 120.00,
      "sortOrder": 1
    }' "$jwt" > /dev/null
    check_status "  Line: data validation"

    api_post "/api/invoices/${ACME_INVOICE_ID}/lines" '{
      "description": "Project management and coordination",
      "quantity": 1,
      "unitPrice": 250.00,
      "sortOrder": 2
    }' "$jwt" > /dev/null
    check_status "  Line: project management"
  fi

  # ── Invoice 2: Bright Solutions (manual lines, NOT time entries) ─
  bright_invoice=$(echo "$existing" | jq -r '.[] | select(.clientName == "Bright Solutions") | .id' 2>/dev/null | head -1)
  if [ -n "$bright_invoice" ] && [ "$bright_invoice" != "null" ]; then
    echo "    [skip] Bright Solutions invoice exists (${bright_invoice})"
    BRIGHT_INVOICE_ID="$bright_invoice"
  else
    body=$(api_post "/api/invoices" "{
      \"clientId\": \"${BRIGHT_ID}\",
      \"currency\": \"ZAR\",
      \"notes\": \"Mobile App and SEO Audit work\",
      \"paymentTerms\": \"Net 15\"
    }" "$jwt")
    check_status "Create Bright Solutions invoice" || return 1
    BRIGHT_INVOICE_ID=$(echo "$body" | jq -r '.id')

    # Add manual lines
    api_post "/api/invoices/${BRIGHT_INVOICE_ID}/lines" '{
      "description": "React Native project setup — 3 hrs",
      "quantity": 3,
      "unitPrice": 120.00,
      "sortOrder": 0
    }' "$jwt" > /dev/null
    check_status "  Line: React Native setup"

    api_post "/api/invoices/${BRIGHT_INVOICE_ID}/lines" '{
      "description": "OAuth integration — 4 hrs",
      "quantity": 4,
      "unitPrice": 120.00,
      "sortOrder": 1
    }' "$jwt" > /dev/null
    check_status "  Line: OAuth integration"

    api_post "/api/invoices/${BRIGHT_INVOICE_ID}/lines" '{
      "description": "SEO audit and report — 6 hrs",
      "quantity": 6,
      "unitPrice": 150.00,
      "sortOrder": 2
    }' "$jwt" > /dev/null
    check_status "  Line: SEO audit"
  fi

  export ACME_INVOICE_ID BRIGHT_INVOICE_ID

  echo ""
  echo "    Invoices seeded:"
  echo "      Acme Corp:        ${ACME_INVOICE_ID}"
  echo "      Bright Solutions: ${BRIGHT_INVOICE_ID}"
}
```

Regression's strict transition idiom (`scripts/regression-test.sh:283-306,333-340`): `api_post "/api/invoices/${INV_ID}/approve" "{}" "$ALICE" > /dev/null` then `api_post "/api/invoices/${INV_ID}/send" "{\"overrideWarnings\":true}" "$ALICE"`; it asserts HTTP via `last_status`. `seed.sh` itself (Steps 1–8, `:1-247`) is quoted by line in Seed design §0; its own `check_status` (`:13-21`) **exits** on non-2xx/409.

### Grouped tabs — `frontend/e2e/helpers/grouped-tabs.ts` (complete)
```ts
/**
 * Grouped tab bars (matter and client detail pages).
 *
 * Only the group triggers are `role="tab"` (`data-testid="tab-group-<group>"`); the sub-tabs are
 * menu items (`tab-item-<tab>`) and the tab panel has no accessible name, so
 * `getByRole("tab", { name: "Tasks" })` / `getByRole("tabpanel", { name: "Tasks" })` match nothing.
 * Group ids: `frontend/lib/constants/matter-tab-groups.ts`, `client-tab-groups.ts`.
 *
 * Lifted verbatim from `legal-depth-ii.ts` (Phase 88 628E), which re-exports it.
 */
import { expect, type Page } from "@playwright/test";

/**
 * Opens a sub-tab of a grouped tab bar (matter or client detail, e.g. Finance → Statements).
 * Clicking an inactive group activates its first sub-tab; the sub-tab menu is
 * opened by clicking the (now active) group trigger.
 */
export async function openGroupedTab(page: Page, groupId: string, tabId: string): Promise<void> {
  const group = page.getByTestId(`tab-group-${groupId}`);
  const item = page.getByTestId(`tab-item-${tabId}`);
  await expect(group).toBeVisible({ timeout: 15_000 });
  // The group trigger toggles its sub-tab menu; whether the first click opens the
  // menu depends on whether the group was already active, so click until it is open.
  for (let attempt = 0; attempt < 3; attempt++) {
    await group.click();
    const menuOpen = await item.waitFor({ state: "visible", timeout: 2_000 }).then(
      () => true,
      () => false
    );
    if (menuOpen) {
      await item.click();
      await expect(group).toHaveAttribute("aria-selected", "true");
      return;
    }
  }
  throw new Error(`Sub-tab menu for tab-group-${groupId} never opened (looking for ${tabId})`);
}
```

### The ESLint rule — `frontend/e2e/eslint/no-isvisible-catch.mjs` (complete; no change needed)
```js
/**
 * Phase 88 628E (U22, R10 §0 M1) — forbid swallowing Playwright locator state checks.
 *
 * Playwright's locator state checks (playwright-core 1.59 `types.d.ts` + `server/frames.js`):
 *   - `isVisible()` / `isHidden()` do NOT wait: they answer for the DOM at call time (no match →
 *     false / true) and ignore their deprecated `timeout` option. They reject only on a strict-mode
 *     violation (the locator matched 2+ elements) or a closed page.
 *   - `isEnabled()` / `isDisabled()` / `isChecked()` / `isEditable()` DO wait for the locator to
 *     match, up to `timeout` (default: the `actionTimeout`, else none), but do not retry the state
 *     itself. They reject on that timeout (the element never appeared), a strict-mode violation, or
 *     the wrong kind of element (isChecked on a non-checkbox, isEditable on a non-input).
 * Either way a `.catch(() => false)` turns "missing / ambiguous / wrong element" into a plain
 * boolean — the soft assertion then fails for the wrong reason, or the guarded step is silently
 * skipped and the test passes having done nothing. Write `await expect(locator.first()).toBeVisible()`
 * (web-first: retries the state, strict-safe) or a role locator; for genuinely optional UI, wait
 * for either state first:
 *   await expect(a.or(b).first()).toBeVisible();
 *   if (await a.isVisible()) { … }
 * See frontend/e2e/AUTOMATION-NOTES.md › Assertion rules.
 *
 * Reports:
 *   (i)   `.catch(…)` on one of `methods` — directly or further down a chain of one-argument
 *         `.then(…)` / `.finally(…)` links (`x.isVisible().then(f).catch(r)`), which pass the
 *         rejection through;
 *   (ii)  the same via a two-argument `.then(onFulfilled, onRejected)`;
 *   (iii) `isVisible({ timeout })` / `isHidden({ timeout })` (option `checkTimeout`, default on).
 *
 * Options: `{ methods?: string[], checkTimeout?: boolean }` — `methods` replaces the default set.
 *
 * Registered in eslint-rules/index.mjs (plugin `kazi-frontend-local`); scoped to e2e files in
 * eslint.config.mjs.
 */

const DEFAULT_METHODS = [
  "isVisible",
  "isHidden",
  "isEnabled",
  "isDisabled",
  "isChecked",
  "isEditable",
];
const TIMEOUT_IGNORED = new Set(["isVisible", "isHidden"]);

/** Strips an optional-chaining wrapper (`a?.b()` parses as ChainExpression → CallExpression). */
function unwrapChain(node) {
  return node && node.type === "ChainExpression" ? node.expression : node;
}

/**
 * Walks back from a promise expression through the links that pass a rejection on unchanged —
 * one-argument `.then(onFulfilled)` and `.finally(…)` — and returns the call they started from.
 * A two-argument `.then` stops the walk (it handles the rejection itself and is reported on its own).
 */
function rejectionSource(node) {
  let current = unwrapChain(node);
  while (current?.type === "CallExpression") {
    const callee = unwrapChain(current.callee);
    if (callee?.type !== "MemberExpression" || callee.computed) break;
    if (callee.property?.type !== "Identifier") break;
    const link = callee.property.name;
    const passesRejection = link === "finally" || (link === "then" && current.arguments.length < 2);
    if (!passesRejection) break;
    current = unwrapChain(callee.object);
  }
  return current;
}

/** Returns the method name if `node` is a call `<expr>.<method>(…)` with method in `methods`. */
function stateMethodOf(node, methods) {
  const call = unwrapChain(node);
  if (!call || call.type !== "CallExpression") return null;
  const callee = unwrapChain(call.callee);
  if (!callee || callee.type !== "MemberExpression" || callee.computed) return null;
  if (callee.property?.type !== "Identifier") return null;
  return methods.has(callee.property.name) ? callee.property.name : null;
}

/** True for an object-literal key spelled `timeout` or `"timeout"`. */
function isTimeoutKey(key) {
  return (
    (key.type === "Identifier" && key.name === "timeout") ||
    (key.type === "Literal" && key.value === "timeout")
  );
}

/** True if any object-literal argument of `call` sets `timeout` (`page.isVisible(sel, { timeout })` too). */
function hasTimeoutOption(call) {
  return call.arguments.some(
    (arg) =>
      arg.type === "ObjectExpression" &&
      arg.properties.some((p) => p.type === "Property" && !p.computed && isTimeoutKey(p.key))
  );
}

const rule = {
  meta: {
    type: "problem",
    docs: {
      description:
        "Disallow `.catch()` / two-argument `.then()` on Playwright locator state checks (isVisible etc.), and the ignored `timeout` option on isVisible/isHidden.",
      recommended: false,
    },
    schema: [
      {
        type: "object",
        additionalProperties: false,
        properties: {
          methods: { type: "array", items: { type: "string" } },
          checkTimeout: { type: "boolean" },
        },
      },
    ],
    messages: {
      swallowed:
        "`.{{method}}()` rejection swallowed with `.{{handler}}()`. It rejects only when the check cannot answer — a strict-mode violation (2+ matches), or (isEnabled/isDisabled/isChecked/isEditable) an element that never appeared or is the wrong kind — so this turns missing/ambiguous into a plain boolean. Use a web-first assertion (`await expect(locator.first()).toBeVisible()`, `toBeEnabled()`, `toBeChecked()` …) or a role locator; for optional UI wait on `a.or(b)` first (e2e/AUTOMATION-NOTES.md › Assertion rules).",
      timeoutIgnored:
        "`.{{method}}({ timeout })` — Playwright ignores `timeout` here; the check does not wait. Use `await expect(locator).toBeVisible({ timeout })` / `toBeHidden({ timeout })`.",
    },
  },
  create(context) {
    const options = context.options[0] || {};
    const methods = new Set(options.methods ?? DEFAULT_METHODS);
    const timeoutMethods = new Set([...methods].filter((m) => TIMEOUT_IGNORED.has(m)));
    const checkTimeout = options.checkTimeout ?? true;
    return {
      CallExpression(node) {
        const callee = unwrapChain(node.callee);
        if (
          callee?.type === "MemberExpression" &&
          !callee.computed &&
          callee.property?.type === "Identifier"
        ) {
          const handler = callee.property.name;
          const swallows =
            handler === "catch" || (handler === "then" && node.arguments.length >= 2);
          if (swallows) {
            const method = stateMethodOf(rejectionSource(callee.object), methods);
            if (method) {
              context.report({ node, messageId: "swallowed", data: { method, handler } });
            }
          }
        }
        if (checkTimeout) {
          const method = stateMethodOf(node, timeoutMethods);
          if (method && hasTimeoutOption(node)) {
            context.report({ node, messageId: "timeoutIgnored", data: { method } });
          }
        }
      },
    };
  },
};

export default rule;
```

### Rule test + wiring test — `frontend/__tests__/eslint-rules/no-isvisible-catch.test.ts` (complete; flip `:155-163`)
```ts
// @vitest-environment node
import path from "node:path";
import { describe, it, expect } from "vitest";
import { ESLint, RuleTester } from "eslint";
import rule from "../../e2e/eslint/no-isvisible-catch.mjs";

// Phase 88 628E (U22, R10 §0 M1): Playwright locator state checks (isVisible & co.) reject only
// when they cannot answer (strict-mode violation; for isEnabled & co. also a missing or wrong-kind
// element), so `.catch(() => false)` on them hides "matched twice" / "never appeared" and turns
// guarded flow steps into silent no-ops. Part 1 tests the rule module (RuleTester, pattern
// no-aschild-adjacency.test.ts); part 2 lints probe snippets through the real flat config, so it
// proves the rule is wired to the e2e files it is scoped to (pattern no-terminology-imports.test.ts).

// ESLint's RuleTester defaults to mocha-style globals; bind to vitest so
// the test cases register with vitest's runner.
RuleTester.describe = describe;
RuleTester.it = it;
RuleTester.itOnly = it.only;

const ruleTester = new RuleTester({
  languageOptions: { ecmaVersion: "latest", sourceType: "module" },
});

const swallowed = (method: string, handler = "catch") => ({
  messageId: "swallowed",
  data: { method, handler },
});
const timeoutIgnored = (method: string) => ({ messageId: "timeoutIgnored", data: { method } });

ruleTester.run("no-isvisible-catch", rule as never, {
  valid: [
    // Web-first assertion — the replacement the rule points to.
    { code: `await expect(page.getByText("x").first()).toBeVisible();` },
    // A bare state check (the sanctioned branch after a deterministic wait) is not flagged.
    { code: `if (await a.isVisible()) {}` },
    { code: `await expect(a.or(b).first()).toBeVisible(); if (await a.isVisible()) {}` },
    // waitFor() waits and honours its timeout; a bounded retry on it is legitimate (openGroupedTab).
    {
      code: `await item.waitFor({ state: "visible", timeout: 2000 }).then(() => true, () => false);`,
    },
    // Unrelated promises.
    { code: `promise.catch(() => false);` },
    { code: `fetch(url).then((r) => r.json(), () => null);` },
    // page.isVisible(selector) without a handler or options.
    { code: `page.isVisible("sel");` },
    // Only exact method names.
    { code: `foo.isVisibleish().catch(() => false);` },
    // One-argument then() does not swallow a rejection.
    { code: `x.isVisible().then((v) => v);` },
    { code: `x.isVisible().then((v) => v).finally(() => {});` },
    // A handler on an unrelated chain.
    { code: `loc.textContent().then((t) => t).catch(() => "");` },
    { code: `x.isEnabled();` },
    // isEnabled & co. honour their timeout (they wait for the element); only isVisible/isHidden ignore it.
    { code: `await x.isEnabled({ timeout: 1000 });` },
    // Computed access is not resolved.
    { code: `loc["isVisible"]().catch(() => false);` },
    // `methods` option narrows the checked set.
    { code: `x.isChecked().catch(() => false);`, options: [{ methods: ["isVisible"] }] },
    // `checkTimeout: false` disables (iii).
    { code: `await loc.isVisible({ timeout: 5000 });`, options: [{ checkTimeout: false }] },
  ],
  invalid: [
    {
      code: `const v = await loc.isVisible().catch(() => false);`,
      errors: [swallowed("isVisible")],
    },
    {
      // The multi-line form the legal-lifecycle suite used everywhere.
      code: [
        "const has = await page",
        "  .getByText(/x/i)",
        "  .isVisible({ timeout: 3000 })",
        "  .catch(() => false);",
      ].join("\n"),
      errors: [swallowed("isVisible"), timeoutIgnored("isVisible")],
    },
    {
      code: `expect.soft(await loc.isVisible().catch(() => false)).toBeTruthy();`,
      errors: [swallowed("isVisible")],
    },
    {
      code: `loc.isVisible().then(() => true, () => false);`,
      errors: [swallowed("isVisible", "then")],
    },
    { code: `loc?.isVisible()?.catch(() => false);`, errors: [swallowed("isVisible")] },
    // The rejection still reaches a handler further down a then()/finally() chain.
    {
      code: `const v = await loc.isVisible().then((x) => x).catch(() => false);`,
      errors: [swallowed("isVisible")],
    },
    {
      code: `loc.isChecked().finally(() => {}).catch(() => false);`,
      errors: [swallowed("isChecked")],
    },
    {
      code: `loc.isHidden().then((h) => !h).then((v) => v, () => false);`,
      errors: [swallowed("isHidden", "then")],
    },
    {
      code: `loc?.isVisible()?.then((x) => x)?.catch(() => false);`,
      errors: [swallowed("isVisible")],
    },
    { code: `loc.isEnabled().catch(() => false);`, errors: [swallowed("isEnabled")] },
    { code: `loc.isDisabled().catch(() => false);`, errors: [swallowed("isDisabled")] },
    { code: `loc.isChecked().catch(() => null);`, errors: [swallowed("isChecked")] },
    { code: `loc.isEditable().catch(() => false);`, errors: [swallowed("isEditable")] },
    { code: `loc.isHidden().catch(() => true);`, errors: [swallowed("isHidden")] },
    { code: `await loc.isVisible({ timeout: 5000 });`, errors: [timeoutIgnored("isVisible")] },
    { code: `await loc.isHidden({ "timeout": 1 });`, errors: [timeoutIgnored("isHidden")] },
    {
      code: `loc.isVisible({ timeout: 1 }).catch(() => false);`,
      options: [{ checkTimeout: false }],
      errors: [swallowed("isVisible")],
    },
    {
      // The option replaces the default set.
      code: `x.isVisible().catch(() => false); x.isChecked().catch(() => false);`,
      options: [{ methods: ["isChecked"] }],
      errors: [swallowed("isChecked")],
    },
  ],
});

// ── Part 2: wiring through eslint.config.mjs ─────────────────────────────────────────────────
const FRONTEND_ROOT = path.resolve(__dirname, "../..");
const RULE_ID = "kazi-frontend-local/no-isvisible-catch";
const PROBE = 'await page.getByText("x").isVisible().catch(() => false);\n';
const eslint = new ESLint({ cwd: FRONTEND_ROOT }); // discovers eslint.config.mjs

async function ruleMessages(file: string, code = PROBE): Promise<string[]> {
  const [result] = await eslint.lintText(code, { filePath: path.join(FRONTEND_ROOT, file) });
  return result.messages.filter((m) => m.ruleId === RULE_ID).map((m) => m.messageId ?? m.message);
}

describe("no-isvisible-catch — eslint.config.mjs wiring", { timeout: 30_000 }, () => {
  it.each([
    "e2e/tests/legal-lifecycle/day-00-firm-setup.spec.ts",
    "e2e/tests/legal-lifecycle/day-01-03-onboarding.spec.ts",
    "e2e/tests/legal-lifecycle/day-07-14-work-trust.spec.ts",
    "e2e/tests/legal-lifecycle/day-30-billing.spec.ts",
    "e2e/tests/legal-lifecycle/day-45-recon-prescription.spec.ts",
    "e2e/tests/legal-lifecycle/day-60-interest.spec.ts",
    "e2e/tests/legal-lifecycle/day-75-complex.spec.ts",
    "e2e/tests/legal-lifecycle/day-90-review.spec.ts",
    "e2e/tests/legal-lifecycle/matter-team.spec.ts",
    "e2e/tests/legal-lifecycle/__probe__.spec.ts",
    "e2e/tests/legal-depth-ii/__probe__.spec.ts",
    "e2e/helpers/__probe__.ts",
    "e2e/fixtures/__probe__.ts",
  ])("is enforced in %s", async (file) => {
    expect(await ruleMessages(file)).toEqual(["swallowed"]);
  });

  // Deliberate scope (Phase 88 628E R-2 / OD-2 (b)): the rule covers the two legal suites, helpers
  // and fixtures; 628F widens it to e2e/** when it sweeps the default-config specs. Flip these
  // assertions when widening.
  it.each(["e2e/tests/settings/__probe__.spec.ts", "components/__probe__.tsx"])(
    "is not (yet) enforced in %s",
    async (file) => {
      expect(await ruleMessages(file)).toEqual([]);
    }
  );
});
```

### Conversion exemplars (already strict, 628E) 

**Client create + onboarding — `e2e/helpers/legal-depth-ii.ts:150-238`** (`openClient`, `NewClient`, `createClient`, `startClientOnboarding`; reuse for CUST-01/CUST-02/customer-data-protection):
```ts

/**
 * Opens a client from the Clients list by clicking its link and waits until the client
 * page is ready (heading visible).
 */
export async function openClient(page: Page, clientName: string): Promise<void> {
  // Phase 87 S4 (ADR-344): route rename
  await page.goto(`${BASE}/clients`);
  const link = page.getByRole("link", { name: clientName, exact: true });
  await expect(link, `client "${clientName}" should be listed`).toBeVisible({ timeout: 15_000 });
  await clickThrough(link, page.getByRole("heading", { level: 1, name: clientName }));
  // Phase 87 S4 (ADR-344): route rename (610D — post-redirect URL)
  await expect(page).toHaveURL(/\/clients\/[0-9a-f-]{36}/);
}

/**
 * Opens Legal → Disbursements filtered to one matter (the list's "Matter filter"). The
 * list shows one page (50 rows); on a stack reused across runs the unfiltered list can
 * push this run's rows off that page, so every lookup narrows to the run's own matter.
 */
export async function openDisbursementList(page: Page, matterName: string): Promise<void> {
  await page.goto(`${BASE}/legal/disbursements`);
  await expect(page.getByTestId("disbursements-list-page")).toBeVisible({ timeout: 15_000 });
  await selectNativeOption(page.getByLabel("Matter filter"), matterName);
  // The filtered refetch has landed once no row of another matter is listed
  await expect(
    page.locator('[data-testid^="disbursement-row-"]').filter({ hasNotText: matterName })
  ).toHaveCount(0, { timeout: 15_000 });
}

export interface NewClient {
  name: string;
  type: "Individual" | "Company" | "Trust";
  email: string;
  /** SA ID / passport number (step 2 intake field) — individuals only. */
  idNumber?: string;
  /** Tax number — required to send a fee note (auto-filled from the ID number for individuals). */
  taxNumber?: string;
}

/**
 * Creates a client through the New Client dialog (with the billing address fee
 * notes require) and leaves the browser on the new client's page.
 */
export async function createClient(page: Page, client: NewClient): Promise<void> {
  // Phase 87 S4 (ADR-344): route rename
  await page.goto(`${BASE}/clients`);
  await page.getByRole("button", { name: "New Client" }).click();

  // The dialog title changes from "Create Client" (step 1) to "Additional Information" (step 2)
  const dialog = page.getByRole("dialog", { name: /^(Create Client|Additional Information)$/ });
  await expect(dialog).toBeVisible();
  await dialog.getByLabel("Name", { exact: true }).fill(client.name);
  await selectNativeOption(dialog.getByLabel("Type", { exact: true }), client.type);
  await dialog.getByLabel("Email", { exact: true }).fill(client.email);
  await dialog.locator('input[name="addressLine1"]').fill("12 Rivonia Road");
  await dialog.locator('input[name="city"]').fill("Sandton");
  await dialog.locator('input[name="postalCode"]').fill("2196");
  await dialog.getByLabel(/^Country/).selectOption("ZA");
  if (client.taxNumber) {
    await dialog.getByLabel(/^Tax Number/).fill(client.taxNumber);
  }
  await dialog.getByRole("button", { name: "Next" }).click();

  // Step 2: SA Legal client intake fields
  await expect(dialog.getByText("Step 2 of 2")).toBeVisible();
  if (client.idNumber) {
    await dialog.getByLabel(/ID \/ Passport Number/).fill(client.idNumber);
  }
  // Creating the client navigates to its page
  await clickThrough(
    dialog.getByRole("button", { name: "Create Client" }),
    page.getByRole("heading", { level: 1, name: client.name })
  );
  // Phase 87 S4 (ADR-344): route rename (610D — post-redirect URL)
  await expect(page).toHaveURL(/\/clients\/[0-9a-f-]{36}/);
}

/** PROSPECT → ONBOARDING from the client page (new matters cannot be opened for a prospect). */
export async function startClientOnboarding(page: Page): Promise<void> {
  const badge = page.getByTestId("lifecycle-badge");
  await expect(badge).toHaveText("Prospect");
  await page.getByRole("button", { name: "Start Onboarding" }).first().click();
  const confirm = page.getByRole("alertdialog", { name: "Start Onboarding" });
  await confirm.getByRole("button", { name: "Start Onboarding" }).click();
  await expect(confirm).toBeHidden({ timeout: 15_000 });
  await expect(badge).toHaveText("Onboarding", { timeout: 15_000 });
}

```

**Sanctioned optional-UI branch + log time on a task — `e2e/helpers/legal-lifecycle.ts:221-318`** (`logTimeOnTask` for PROJ-03; `ensureTrustAccount` = the `a.or(b)` wait-then-branch pattern; `TRUST_ACCOUNT` = the seed's exact values):
```ts

/**
 * Logs time on one task of the open matter (Work → Tasks → row "Log Time"). Time is logged per
 * task; there is no matter-level Log Time. `rate` (e.g. "2 500,00") asserts the billing-rate
 * preview, i.e. that the Day 0 rate card applies to this member.
 */
export async function logTimeOnTask(
  page: Page,
  opts: {
    task: string;
    hours: number;
    minutes: number;
    description: string;
    date: string;
    rate?: string;
  }
): Promise<void> {
  await openGroupedTab(page, "work", "tasks");
  const taskRow = page
    .getByRole("tabpanel")
    .getByRole("row")
    .filter({ has: page.getByRole("button", { name: `Open task detail for ${opts.task}` }) });
  await expect(taskRow, `task "${opts.task}" should be listed`).toHaveCount(1, {
    timeout: 15_000,
  });
  await taskRow.getByRole("button", { name: "Log Time" }).click();

  const dialog = page.getByRole("dialog", { name: "Log Time" });
  await expect(dialog).toBeVisible();
  // Hours / minutes have no accessible name (the "Duration" label has no htmlFor) — product
  // follow-up: label them (components/tasks/log-time-dialog.tsx:180-205); ids until then
  await dialog.locator("#time-hours").fill(String(opts.hours));
  await dialog.locator("#time-minutes").fill(String(opts.minutes));
  await dialog.getByLabel("Date", { exact: true }).fill(opts.date);
  await dialog.getByRole("textbox", { name: /^Description/ }).fill(opts.description);
  await expect(dialog.getByRole("checkbox", { name: "Billable" })).toBeChecked();
  if (opts.rate) {
    await expect(dialog.getByTestId("rate-preview")).toContainText(
      new RegExp(`Billing rate: R[\\s\\u00a0\\u202f]?${randPattern(opts.rate).source}/hr`),
      { timeout: 15_000 }
    );
  }
  await dialog.getByRole("button", { name: "Log Time" }).click();
  await expect(dialog).toBeHidden({ timeout: 15_000 });
}

/**
 * Runs a conflict check from `/conflict-check` and returns the result panel (its heading is the
 * verdict: "No Conflict" | "Potential Conflict" | "Conflict Found").
 */
export async function runConflictCheck(page: Page, name: string): Promise<Locator> {
  await page.goto(`${BASE}/conflict-check`);
  await expect(page.getByRole("heading", { level: 1, name: "Conflict Check" })).toBeVisible({
    timeout: 15_000,
  });
  const form = page.getByTestId("conflict-check-form");
  await form.getByLabel(/Name to Check/).fill(name);
  await form.getByRole("button", { name: "Run Conflict Check" }).click();
  const result = page.getByTestId("conflict-check-result");
  await expect(result).toBeVisible({ timeout: 15_000 });
  await expect(result).toContainText(`Checked "${name}"`);
  return result;
}

/** The firm's trust account (scenario 0.62; the 628F seed uses the identical name and bank). */
export const TRUST_ACCOUNT = {
  name: "Mathebula & Partners Trust Account",
  bank: "Standard Bank",
  branchCode: "051001",
  accountNumber: "0123456789",
} as const;

/**
 * Creates the primary GENERAL trust account if the firm has none (a second primary GENERAL account
 * is rejected by a unique index). Deterministic: waits for either state before branching.
 */
export async function ensureTrustAccount(page: Page): Promise<void> {
  await page.goto(`${BASE}/trust-accounting`);
  await expect(page.getByRole("heading", { level: 1, name: "Trust Accounting" })).toBeVisible({
    timeout: 15_000,
  });
  const addAccount = page.getByRole("button", { name: "Add Account" });
  const recordTransaction = page.getByRole("link", { name: "Record Transaction" });
  await expect(addAccount.or(recordTransaction).first()).toBeVisible({ timeout: 15_000 });
  if (await addAccount.isVisible()) {
    await addAccount.click();
    const dialog = page.getByRole("dialog", { name: "Add Trust Account" });
    await expect(dialog).toBeVisible();
    await dialog.getByLabel("Account Name").fill(TRUST_ACCOUNT.name);
    await dialog.getByLabel("Bank Name").fill(TRUST_ACCOUNT.bank);
    await dialog.getByLabel("Branch Code").fill(TRUST_ACCOUNT.branchCode);
    await dialog.getByLabel("Account Number").fill(TRUST_ACCOUNT.accountNumber);
    await dialog.getByRole("button", { name: "Create Account" }).click();
    await expect(dialog).toBeHidden({ timeout: 15_000 });
  }
  await expect(recordTransaction).toBeVisible({ timeout: 15_000 });
}

```

**Deterministic per-item flags instead of a bare state read — `e2e/tests/legal-lifecycle/day-01-03-onboarding.spec.ts:82-92, 163-189`** (the shape for the depth-ii `day-14:142-143` conversion):
```ts
const FICA_ITEMS: { name: string; requiresDocument: boolean }[] = [
  { name: "Proof of Identity", requiresDocument: true },
  { name: "Proof of Address", requiresDocument: true },
  { name: "Beneficial Ownership Declaration", requiresDocument: true },
  { name: "Source of Funds Declaration", requiresDocument: true },
  { name: "Engagement Letter Signed", requiresDocument: true },
  { name: "Conflict Check Performed", requiresDocument: false },
  { name: "Power of Attorney Signed", requiresDocument: true },
  { name: "FICA Risk Assessment", requiresDocument: false },
  { name: "Sanctions Screening", requiresDocument: false },
];
// …
    const panel = page.getByRole("tabpanel");
    await expect(panel).toContainText("Legal Individual Client Onboarding", { timeout: 15_000 });
    for (const { name, requiresDocument } of FICA_ITEMS) {
      // The checklist item row has no role / test id — product follow-up: a `data-testid` (or
      // role="listitem") on components/compliance/ChecklistInstanceItemRow.tsx:136; class until then
      const item = panel
        .locator("div.rounded-lg.border.p-3")
        .filter({ has: page.getByText(name, { exact: true }) });
      const needsDoc = item.getByText(/^Requires document:/);
      await expect(needsDoc).toHaveCount(requiresDocument ? 1 : 0);
      await item.getByRole("button", { name: "Mark Complete" }).click();
      if (requiresDocument) {
        // Link the certified FICA pack uploaded above
        const documentPicker = item.getByRole("combobox");
        await documentPicker.click();
        await page.getByRole("option", { name: ficaPack() }).click();
        await expect(documentPicker).toContainText(ficaPack());
        await expect(page.getByRole("listbox")).toBeHidden();
      }
      const confirmItem = item.getByRole("button", { name: "Confirm" });
      await expect(confirmItem).toBeEnabled();
      await confirmItem.click();
      await expect(item.getByText("Completed", { exact: true }), name).toBeVisible({
        timeout: 15_000,
      });
    }
    await expect(panel).toContainText("9/9 completed");
```

**The depth-ii site to convert — `e2e/tests/legal-depth-ii/day-14-conveyancing.spec.ts:65-75, 130-158`** (current):
```ts
const FICA_ITEMS = [
  "Proof of Identity",
  "Proof of Address",
  "Beneficial Ownership Declaration",
  "Source of Funds Declaration",
  "Engagement Letter Signed",
  "Conflict Check Performed",
  "Power of Attorney Signed",
  "FICA Risk Assessment",
  "Sanctions Screening",
];
// …
  test("Alice (Thandi): Complete Khumalo's FICA checklist → client becomes ACTIVE", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    await openClient(page, clientName());
    await openGroupedTab(page, "compliance", "onboarding");
    const panel = page.getByRole("tabpanel");
    await expect(panel).toContainText("Legal Individual Client Onboarding", { timeout: 15_000 });
    for (const itemName of FICA_ITEMS) {
      const item = panel
        .locator("div.rounded-lg.border.p-3")
        .filter({ has: page.getByText(itemName, { exact: true }) });
      await item.getByRole("button", { name: "Mark Complete" }).click();
      if (await item.getByText(/^Requires document:/).isVisible()) {
        // Link the certified FICA pack uploaded in part 1
        const documentPicker = item.getByRole("combobox");
        await documentPicker.click();
        await page.getByRole("option", { name: ficaPack() }).click();
        await expect(documentPicker).toContainText(ficaPack());
        await expect(page.getByRole("listbox")).toBeHidden();
      }
      const confirmItem = item.getByRole("button", { name: "Confirm" });
      await expect(confirmItem).toBeEnabled();
      await confirmItem.click();
      await expect(item.getByText("Completed", { exact: true }), itemName).toBeVisible({
        timeout: 15_000,
      });
    }
    await expect(panel).toContainText("9/9 completed");
```

## Conventions (verbatim)

### `frontend/CLAUDE.md` › ESLint + Testing (`:189-204`)

#### ESLint

- Flat config (`eslint.config.mjs`)
- Extends `eslint-config-next/core-web-vitals` and `eslint-config-next/typescript`
- `no-restricted-imports` bans the deleted terminology modules (ADR-338); keep all restricted paths in that one config object (a later flat-config object setting the same rule replaces, not merges)
- Playwright: `kazi-frontend-local/no-isvisible-catch` (`e2e/eslint/no-isvisible-catch.mjs`) bans `.catch()` on `isVisible()` & co. and the ignored `isVisible({ timeout })` in the e2e files it is scoped to — use `expect(locator).toBeVisible()`; see `e2e/AUTOMATION-NOTES.md` › Assertion rules
- Run with `pnpm run lint`

#### Testing

- **Vitest** with `@testing-library/react` and `happy-dom`
- Config in `vitest.config.ts` — needs `resolve.alias` for `@/*` path alias
- Test files: colocated (`*.test.tsx`) or in `__tests__/` directory
- Radix UI components leak DOM between tests — always add `afterEach(() => cleanup())` in test files using Dialog/AlertDialog
- Use unique trigger text per test file to avoid "multiple elements found" errors
- If any tests are failing, make sure they pass before merging, even if you think they are existing issues

### `frontend/CLAUDE.md` › Anti-Patterns (`:206-219`)

#### Anti-Patterns — Never Do This

- Never use `@radix-ui/react-*` packages — use the unified `radix-ui` package instead
- Never put `"use client"` on a component unless it uses hooks, event handlers, or browser APIs
- Never call `getAuthContext()` in client components — it's server-only; use `useAuth()` hook instead
- Never access `params` without `await` — Next.js 16 params are Promises
- Never import from `@clerk/nextjs` — Clerk has been fully removed; use `lib/auth/` abstraction
- Never use `npm` — this project uses `pnpm`
- Never use olive/neutral/zinc/gray color classes — use the **slate** scale instead
- Never use indigo for accents — use **teal** instead
- Never import `motion` in server components — it's client-only. Only import in `"use client"` files.
- Never pass functions or component references as props from Server Components to `"use client"` components — Next.js 16 throws a runtime serialization error. Pass serializable data (strings, objects) instead.
- Never add a runtime terminology/vocabulary substitution layer (`useTerminology`, `t("Project")`, `<TerminologyText>`, a terminology map) — write the legal word (ADR-338)
- Never place two `<DialogTrigger asChild>` / `<AlertDialogTrigger asChild>` Radix siblings adjacent in the same parent JSX — they collide under React 19 + Radix `Slot` reconciliation and only one trigger wires up its `onClick`. See _Dialog Trigger Composition_ below.

### `frontend/e2e/AUTOMATION-NOTES.md` › Assertion rules + Lifecycle run state (`:620-673`) — the scope paragraph at `:628-629` is the one to update

#### Assertion rules (Phase 88 628E — lint-enforced)

`kazi-frontend-local/no-isvisible-catch` (`e2e/eslint/no-isvisible-catch.mjs`) fails `pnpm lint` on:

- `.catch(…)` (or a two-argument `.then(…, onRejected)`) on `isVisible`, `isHidden`, `isEnabled`,
  `isDisabled`, `isChecked`, `isEditable` — directly or after one-argument `.then(…)` / `.finally(…)` links
  (`x.isVisible().then(f).catch(r)`);
- `isVisible({ timeout })` / `isHidden({ timeout })` — Playwright ignores the option.

It is scoped (`eslint.config.mjs`) to the legal suites, `e2e/helpers/**` and `e2e/fixtures/**`; Phase 88
628F widens it to `e2e/**` when it sweeps the default-config specs.

Why (R10 §0 M1): these checks reject only when they cannot answer. `isVisible` / `isHidden` do not wait at
all (no match → `false` / `true` immediately after a navigation) and reject only on a strict-mode violation —
the locator matched two or more elements. `isEnabled` / `isDisabled` / `isChecked` / `isEditable` wait for
the element to appear (up to their `timeout`) but not for the state, and also reject when it never appears
or is the wrong kind of element. `getByText(/vat/i).isVisible().catch(() => false)` therefore returns
`false` exactly when the text is on screen twice. In the legal lifecycle suite 272 such guards hid flows the
product never had: the tests "passed" having done nothing.

Write instead:

| Intent                                                | Write                                                                                                                                                                                               |
| ----------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| element must be there                                 | `await expect(page.getByRole("button", { name: "Add Line" })).toBeVisible()`                                                                                                                        |
| text may match several nodes                          | `await expect(page.getByText("VAT").first()).toBeVisible()` — or scope it: `section.getByRole("row", { name: /VAT/ })`                                                                              |
| element must be absent (RBAC)                         | `await expect(locator).toHaveCount(0)` / `.not.toBeVisible()` — never `expect(await x.isVisible()).toBe(false)`                                                                                     |
| column/table check                                    | `getByRole("columnheader", { name: "Revenue", exact: true }).first()`                                                                                                                               |
| dialog closes on success                              | `await expect(dialog).toBeHidden({ timeout: 15_000 })` — never "press Escape if still open"                                                                                                         |
| genuinely optional UI (state may differ between runs) | wait for either state first, then branch: `await expect(a.or(b).first()).toBeVisible(); if (await a.isVisible()) { … }`                                                                             |
| step depends on a feature that may be off             | it is not optional on the legal-za stack — assert it. `test.skip` only for an explicit opt-in (env flag) or a documented environment limitation; a reproduced product bug is a `test.fixme` (below) |

Never guard a flow step with `if (visible) { act }`: a dead locator then turns the step into a silent no-op.
A bare `if (await loc.isVisible())` is allowed only right after a deterministic wait for either state (the
lint cannot see that — the reviewer checks it). The one other acceptable bare state read is on
server-rendered state that a prior awaited action has already settled (e.g. `fixtures/keycloak-auth.ts`:
once `fill` on the Keycloak username field resolves, the server-rendered form is complete, so the password
field is either there or not); the code must carry a comment saying why the state cannot change after that
point. Never use `waitForTimeout` to wait for UI — use a web-first `expect`.

**Product blockers.** A flow blocked by a product defect you have reproduced is
`test.fixme(true, "628E-BLOCKER: <short> — <product file:line>")` as the first statement of that test, with
the intended real steps kept below it, so removing the fixme yields a real test. The reason string is the
grep tag later slices use (`grep -rn 628E-BLOCKER e2e/tests`). The depth-ii precedent — a
`// FIXME(<suite>:<tag>)` comment citing file:line above `test.fixme(...)` — is equivalent.

#### Lifecycle run state

The legal-lifecycle spec files share entity names and ids through `helpers/legal-lifecycle.ts` (a JSON file
in the OS temp dir, written by the first Day 0 test), the same way `helpers/legal-depth-ii.ts` does for
depth-II. Look records up by their exact run-scoped name (`openMatter(page, siphoMatter())`) or recorded id
(`clientId("moroka")`), never by a loose regex + `.first()` — on a reused stack that picks an older run's
record.


### `frontend/e2e/AUTOMATION-NOTES.md` › Tab Navigation (`:689-701`)

#### Tab Navigation (grouped tabs — matter / client detail)

Only the group triggers are `role="tab"` (`data-testid="tab-group-<group>"`); sub-tabs are menu items
(`tab-item-<tab>`) and the tab panel has no accessible name. Use the helper (group ids:
`lib/constants/matter-tab-groups.ts`, `client-tab-groups.ts`):

```typescript
import { openGroupedTab } from "../../helpers/grouped-tabs";
await openGroupedTab(page, "work", "tasks"); // Work → Tasks
const panel = page.getByRole("tabpanel"); // unnamed
```

`getByRole("tab", { name: "Tasks" })` / `getByRole("tabpanel", { name: "Tasks" })` match nothing.

### `frontend/e2e/AUTOMATION-NOTES.md` › Seed Data State (`:68-90`, STALE — rewrite per File Plan)

---

#### Seed Data State After Stack Start

| Entity               | State                       | Notes                                                        |
| -------------------- | --------------------------- | ------------------------------------------------------------ |
| Org                  | `e2e-test-org` (Pro plan)   | 3 members synced                                             |
| Customer "Acme Corp" | **ONBOARDING** (not ACTIVE) | Seed fails to auto-transition; checklist items 0/4 completed |
| Projects             | **None**                    | Must be created in tests                                     |
| Tasks                | **None**                    | Must be created in tests                                     |
| Rates                | **Not set**                 | All members show "Not set"                                   |
| Invoices             | **None**                    |                                                              |
| Retainers            | **None**                    |                                                              |

#### CRITICAL: Customer Lifecycle

The seed creates Acme Corp and transitions to ONBOARDING, but **cannot auto-transition to ACTIVE** because the 4 checklist items are not completed. Tests that need an ACTIVE customer must either:

1. Complete all 4 checklist items via the UI (see Onboarding Checklist below), OR
2. Use the backend API directly to complete checklist items

---


#### Root `CLAUDE.md` / phase rules that bind this slice (paraphrase-free extracts)

- §3: "PASS means the agent **ran** the verification end-to-end … Inferring PASS from "code looks right" or "unit test green" is forbidden."
- §4: "Agents must reproduce a bug locally before writing the fix." This applies to every `628F-BLOCKER`.
- §6: "Editing `qa/testplan/demos/*.md` to make a scenario "match the product" is a **product decision**". This slice does not do it.
- §7: "One fix per PR … Architectural decisions … are orchestrator/user calls, not agent calls."
- Phase PR rules (`tasks/phase88-demo-ready-root-cause-closure.md:195-197`):
  - "Any Playwright spec a slice breaks is amended **in the same PR** (D13 / lesson B-7); the strict-assertion rule applies from 628E onward and to every new spec before it."
  - "`compose/seed` and `frontend/e2e` changes carry the frontend marker."
- Hotspot rule (`:217`): "`compose/seed/seed.sh` + `lib/*.sh` | 628F only (invoices, trust account, Day 3/60/85 portal rows) | REGRESSION script untouched unless a seed helper signature changes". Consequence: **628G cannot touch the seed** (see OD-10).
- Memory `feedback_no_sql_shortcuts_in_qa`: the seed uses APIs only.
- Memory `env_playwright_legal_configs_dirty_tree`: Playwright runs rewrite tracked `frontend/test-results` and `documentation/screenshots`. Restore only those artefact paths after every run.

## Open Decisions (orchestrator rules before the builder starts)

**OD-1 — Slice size: one builder or two?**
- **Evidence.** The scope is about 25 spec files rewritten (83 class-c sites, plus non-site tautologies, `waitForTimeout`s and runtime `test.skip`s surfacing behind them), 4 seed files plus `seed.sh`, config, scripts, the rule and the docs. That is about 40 files. 628E needed two sequential builders for 9 files.
- **Options.**
  - (a) One builder.
  - (b) **One PR, two sequential builders.**
    - B1: seed + `common.sh` helpers + `playwright.config.ts` + `package.json` + `e2e/helpers/seed.ts` + the P-1 suites `customers/`, `projects/`, `settings/`, `navigation/` (including the 6 baseline failures PACK-01, CUST-01, CUST-02, PROJ-02, PROJ-03). The rule widens to those dirs (plus legal/helpers/fixtures).
    - B2: `documents/`, `information-requests/`, `invoices/`, `automations/`, `compliance/`, `finance/`, `notifications/`, the KC scripts (OD-2), depth-ii `day-14:142-143`; the rule goes to `e2e/**/*.ts`; the pins flip; AUTOMATION-NOTES and `CLAUDE.md`; then B2 opens the PR.
  - (c) Two PRs: 628F-1 = seed/config/scripts/6 failures; 628F-2 = the sweep.
- **Recommendation: (b).** It follows the 628E R-1 precedent. B1's half is observable on its own: E2E-LEGAL plus the four per-suite scripts on a fresh stack.

**OD-2 — Keycloak-only scripts `demo-full-90day.spec.ts` (19 sites), `demo-recording.spec.ts` (2), `debug-kc-invite.spec.ts` (2).**
- **Evidence.**
  - They target org `mathebula-partners` and real KC credentials, or Mailpit `:8025` on the dev stack.
  - They fail or "pass" only on the mock stack (baseline: 2 failures at `keycloak-auth.ts:14`; debug "passes" via `try/catch`).
  - Substantive edits stopped with #1029 (2026-04-14); only mechanical renames since.
  - No gate runs them. Phase 88's capstone records wow moments via Playwright MCP on KC-STACK (629 row), not through these scripts.
  - Every flow step is an `if (visible)` guard, and they cannot be verified on the mock stack.
- **Options.**
  - (a) **Delete all three.** Git keeps them recoverable.
  - (b) Keep them. Add `"**/demo-*.spec.ts", "**/debug-kc-invite.spec.ts"` to the mock `testIgnore`, and add an explicit `ignores:` for them in the ESLint block (an authorised exception, stated in the PR body).
  - (c) Keep them and convert all 23 sites without being able to run them (unverifiable).
- **Recommendation: (a)** for `debug-kc-invite` (a probe with no assertions) and `demo-recording` (superseded). For `demo-full-90day`: (a), unless the user still uses it as a video tool, in which case (b). Never (c).

**OD-3 — Tests whose title claims behaviour the product does not have (title honesty, 628E R-12).**
Each case below shows the evidence and then the recommendation. "(Census B/C/A)" points to the per-site table.
- **roles-capabilities 2–3.** System roles cannot be edited (`APP/settings/firm/_tabs/roles.tsx:64-104`).
  - **Recommendation:** re-scope test 2 to the system role cards plus the Capability Reference, and test 3 to custom-role CRUD (create → toggle → save → delete). Census C.
- **automation-triggers ×3** ("fires" / "does NOT fire" assert nothing).
  - **Recommendation:** retitle to what they really check (the rule list, the execution log reachable), and file "automation firing not covered by e2e" as a follow-up. The alternative is a real trigger via the API (client transition), then poll the Execution Log. That is more work, and the scout did not verify it.
- **invoice-lifecycle "VOID releases time entries"** (`expect(true)`, no time entries).
  - **Recommendation:** delete it. The alternative is to rewrite it with `timeEntryIds` created via the API.
- **customer-lifecycle :227 "PROSPECT blocked from creating invoice".** This is a stale premise: GAP-L-60 allows fee notes for PROSPECT (`$B/compliance/CustomerLifecycleGuard.java:49-61`). It passes only on the 422 address prerequisite.
  - **Recommendation:** retitle to "Fee note blocked for a client without a billing address", and assert `422` plus a violation on `address_line1`.
- **CUST-01 "Customer list pagination".** There is no pagination.
  - **Recommendation:** retitle to "Client list renders rows".
- **template-management "Create" never submits; "Edit" opens a PLATFORM (read-only) template.**
  - **Recommendation:** real create (submit, then assert the edit URL or row). Edit should target the clone made in test 3. A run must observe the form defaults.
- **custom-fields "value persists"** (no value is ever set).
  - **Recommendation:** retitle to "Custom fields tab renders on a client". A real set-and-reload is optional.
- **General rule:** real flow where the product supports it; retitle where the check is legitimate but the title over-claims; `test.fixme` **only** for a reproduced product blocker. List every rename in the PR body.

**OD-4 — Product blockers found from source (each must be reproduced in the run before it is fixme'd).**
1. **Dead header "Complete Offboarding" button** for OFFBOARDING clients. `CMP/customers/client-header-card.tsx:44-45,174-184` renders it with `onClick={onPrimaryAction}`, but `client-header-card-with-lifecycle.tsx:70` passes `undefined`, because `APP/clients/[id]/page.tsx:434-470` computes a target only for PROSPECT and ONBOARDING. Blocks CUST-02 `:337`.
2. **No firm UI transitions a client into OFFBOARDING** (or reactivates DORMANT/OFFBOARDED). `LifecycleTransitionDropdown.tsx` has had no importer since #1395, and the overflow menu has no transition items (`client-overflow-menu.tsx:101-177`). Blocks CUST-02 `:305`. ACTIVE→DORMANT **is** reachable, via Compliance → "Check for Dormant Clients" → "Mark as Dormant" (`CMP/compliance/DormancyCandidateList.tsx:155-161`).
3. **Client-level "Generate Document" is a dead navigation.** `client-overflow-menu.tsx:120` pushes `?action=generate-document`, and nothing reads `action`. Affects document-generation `:65`, `:164`.
4. **Same dead-button class, not blocking a test:** header "Edit" for ACTIVE/DORMANT, and "Activate Client" while the checklist is incomplete.

Recommendation:
- Fixme CUST-02 `:305` and `:337` with `628F-BLOCKER` tags citing the file:lines above.
- Re-target document-generation tests 1–2 to the **matter** page, where the real "Generate Document" dropdown exists (`CMP/projects/overflow-actions-menu.tsx:83-92`). Add **one** fixme'd test "Generate a document from the client page", so the client-path regression stays grep-able.
- List all four in the PR body as product follow-ups.

**OD-5 — May default specs mutate the seed rows?**
- **Evidence.** Several rewritten tests would act on Acme or Website Redesign: send the seeded info request, anonymize, tag the matter, change brand colour, log a DSAR on Acme. The portal 90-day (628G) and REGRESSION read the same rows, and default-config files run in parallel.
- **Recommendation.** Treat the seed rows as **read-only**.
  - A test that changes state creates its own entity: its own DRAFT info request to send, its own client to anonymize, its own tag.
  - A shared setting it must change is restored in an `afterAll`: brand colour, notification preferences.
  - The DSAR log on Acme is additive. Accept it, but never assert global counts.

**OD-6 — Shared `e2e/helpers/seed.ts`** (`SEED` names plus `acmeId()` / `websiteRedesignId()` via the API). **Recommendation: yes.** It removes six `customers.find(lifecycleStatus === "ACTIVE")` copies (X4).

**OD-7 — depth-ii `day-14:142-143`.**
- **Options.**
  - (a) Hand-off wording: `await expect(item.getByText(/^Requires document:/).or(item.getByRole("button", { name: "Confirm" })).first()).toBeVisible()`, then branch. Note that "Confirm" renders in both cases, so the `.or` adds little.
  - (b) **The per-item `{ name, requiresDocument }` table** already used by the lifecycle (`day-01-03-onboarding.spec.ts:82-92,172`). Assert `needsDoc` `toHaveCount(0|1)` before clicking.
- **Recommendation: (b)**, the same pack and the same flags. Optionally hoist the table to a helper that both specs import.

**OD-8 — Seed idempotency details.**
- (i) Step 7 resolves "Website Redesign" GET-first. Today `e2e-reseed.sh` creates a duplicate matter on every reseed (`seed.sh:216-228` always POSTs).
- (ii) The info request is re-created when the previous one was submitted, completed or cancelled. This keeps portal Day 3 re-runnable after a reseed.
- (iii) Narrow rich-seed's `seed_invoices` Acme lookup to its own notes marker (`lib/invoices.sh:14`).
- **Recommendation:** all three. None changes a helper signature, so REGRESSION is untouched.

**OD-9 — `compose/scripts/e2e-up.sh` seed handling.**
- **Evidence.** It waits at most 120 s for the seed (`:102`). A non-zero seed exit only prints `WARNING` (`:112-119`). The SoA PDF render adds seed time.
- **Recommendation.** Do not edit `e2e-up.sh` in this slice; it is not in the file list. The builder and the gate measure the seed duration (`docker compose -f compose/docker-compose.e2e.yml logs seed`, first and last timestamps) and **require** `E2E Boot-Seed Complete!` in that log before trusting any E2E run. If the seed takes more than 90 s, raise it with the orchestrator, who decides whether `SEED_TIMEOUT` moves here. "Fail e2e-up on seed failure" is a follow-up.

**OD-10 — Portal 90-day needs that 628.14 does not list, while seed edits are 628F-only (hotspot rule).**
- Day 21 `/trust` expects "at least one trust-deposit transaction" for the portal contact's client (`portal/e2e/tests/portal-client-90day/day-14-30.spec.ts:10-11`). 628.14 seeds the account but no Acme deposit.
- Day 45 "pay invoice" needs a sandbox PSP. That is not seedable here.
- Magic-link budget: the info-request send mints 1 token for `contact@acme.example.com` (cap 3 per 5 min). 628G's `globalSetup` and REGRESSION each mint 1 more.

Options for the deposit:
- (a) Add an Acme deposit now: `POST /api/trust-accounts/{id}/transactions/deposit`, `RecordDepositRequest(clientId, matterId, amount, reference, description, transactionDate)` (`TrustTransactionService.java:92-98`; controller `TrustTransactionController.java:60-66`).
  - **Risk:** Acme then has a trust balance at Day 60. The lifecycle's allocation assertions filter by client id (`day-60-interest.spec.ts:122-130`) and survive. The fixme'd Bob test asserts `"Interest Credit"` rows `toHaveCount(2)` across the whole transactions page (`:183-189`); it would see 3 once un-fixme'd. Fix that assertion to filter by client when it is un-fixme'd.
- (b) Seed no deposit. 628G asserts the zero-balance `/trust` state, as a 628G/scenario call.

**Recommendation: (b)** keeps 628F at its specified scope. The orchestrator should decide this before 628G, because 628G cannot edit the seed.

## Build & Verify

### Frontend gate (builder)

Run from `/Users/rakheendama/Projects/2026/worktree-epic-628F/frontend`. The worktree has no `node_modules` yet.
```bash
NODE_OPTIONS="" /opt/homebrew/bin/pnpm install > /dev/null 2>&1
rm -rf .next
NODE_OPTIONS="" SHELL=/bin/bash /opt/homebrew/bin/pnpm lint          > /tmp/lint-628F.log 2>&1   || tail -60 /tmp/lint-628F.log
NODE_OPTIONS="" /opt/homebrew/bin/pnpm format:check                    > /tmp/format-628F.log 2>&1 || tail -40 /tmp/format-628F.log
NODE_OPTIONS="" /opt/homebrew/bin/pnpm test                            > /tmp/test-628F.log 2>&1   || tail -80 /tmp/test-628F.log
NODE_OPTIONS="" /opt/homebrew/bin/pnpm build                           > /tmp/build-628F.log 2>&1  || tail -60 /tmp/build-628F.log
```
- **FE-GATE** = `rm -rf .next && pnpm lint && pnpm format:check && pnpm test && pnpm build`, all green. Prettier is a separate CI step (memory `feedback_frontend_verify_includes_prettier`): run `pnpm format` on touched files before committing.
- Last recorded FE-GATE on main (628E, 1a00394ad): **434 files / 3535 passed / 2 skipped**, lint 0 errors. Expect the same file count; the test count changes only by the probes added to `no-isvisible-catch.test.ts`.
- Rule test alone (inner loop): `NODE_OPTIONS="" /opt/homebrew/bin/pnpm exec vitest run __tests__/eslint-rules/no-isvisible-catch.test.ts`. The gate is the **full** `pnpm test`, not a path filter (root `CLAUDE.md` §5).
- Lint census oracle while sweeping. Before the config is widened, use `NODE_OPTIONS="" ./node_modules/.bin/eslint --no-warn-ignored --rule '{"kazi-frontend-local/no-isvisible-catch":"error"}' e2e`. It must reach **0 problems**. Baseline: 187 `swallowed` + 65 `timeoutIgnored`.
- Static `--list` oracle (no stack, no browser):
  - `NODE_OPTIONS="" ./node_modules/.bin/playwright test --config e2e/playwright.config.ts --list | tail -1` → 179 tests (176 if OD-2 (a)/(b)). It must contain no `legal-` paths: check `--list | grep -c "legal-"` = 0.
  - Each per-suite script: `pnpm test:e2e:projects --list | tail -1` = 25 tests / 4 files, customers 20/3, settings 30/8, navigation 16/2. Adjust these if specs are added or deleted, and state the new numbers.
  - Legal configs unchanged: `--config e2e/playwright.legal-lifecycle.config.ts --list` → 104 tests; depth-ii → 43.

### Seed scripts (builder)

```bash
cd /Users/rakheendama/Projects/2026/worktree-epic-628F/compose/seed
for f in seed.sh rich-seed.sh lib/*.sh; do sh -n "$f" || echo "SYNTAX: $f"; done
/opt/homebrew/bin/shellcheck -s sh seed.sh rich-seed.sh lib/*.sh      # baseline on main: clean
bash -n ../../scripts/regression-test.sh                               # untouched, still parses with the new common.sh
```

The seed runs in `alpine:3.19` busybox `sh` with `curl` and `jq`. Avoid bash-isms: no `[[`, arrays, `local`, `$(< f)`, or `date -d "+14 days"` (busybox needs `-d @epoch`; BSD needs `-v`). `jq --arg` is fine.

### E2E (orchestrator-owned gate — the builder does NOT run Playwright against :3001 unless told to)

1. **Fresh stack (gate-grade)**, from the worktree root. The seed image is rebuilt by `e2e-up`'s `--build`. `e2e-reseed.sh` would reuse a stale seed image and does not wipe.
   ```bash
   SHELL=/bin/bash bash compose/scripts/e2e-down.sh && SHELL=/bin/bash bash compose/scripts/e2e-up.sh
   docker compose -f compose/docker-compose.e2e.yml logs seed | tee /tmp/628F/seed.log | tail -40
   grep -q "E2E Boot-Seed Complete!" /tmp/628F/seed.log || echo "SEED FAILED — stop"
   ```
2. **Seed rows observed through the API.** Read-only, Alice JWT from `:8090/token`:
   - `GET :8081/api/trust-accounts` → 1 account named "Mathebula & Partners Trust Account".
   - `GET :8081/api/invoices?clientId=<acme>` → 2, one `SENT` with an `INV-` number.
   - `GET :8081/api/information-requests?customerId=<acme>` → 1 `SENT`.
   - `GET :8081/api/matters/<wr>/statements` → `totalElements` 1.
   - `GET :8081/api/settings` → `portalDigestCadence` `BIWEEKLY`.
   - Read-only `docker exec e2e-postgres psql -U postgres -d app -c "SELECT …"` is allowed **only** as corroboration, never as the seeding mechanism.
3. **E2E-LEGAL**, from `frontend/`, in order. The legal suites are serial chains.
   ```bash
   NODE_OPTIONS="" E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 /opt/homebrew/bin/pnpm test:e2e:legal-lifecycle --update-snapshots=changed --reporter=list > /tmp/628F/gate/lifecycle.log 2>&1
   NODE_OPTIONS="" E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 /opt/homebrew/bin/pnpm test:e2e:legal-depth-ii   --update-snapshots=changed --reporter=list > /tmp/628F/gate/depth.log 2>&1
   ```
   Expect **lifecycle 101 / 0 / 3** (the three `628E-BLOCKER`s) and **depth-ii 39 / 0 / 4**. With the seed, Day 0 and depth-ii Day 5 take the "account exists" branch. Day 45 must still find the `Standard Bank - 0123456789` option.
4. **E2E-DEFAULT** on the same stack, after the legal suites:
   ```bash
   NODE_OPTIONS="" E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 /opt/homebrew/bin/pnpm test:e2e --update-snapshots=changed --reporter=list > /tmp/628F/gate/default.log 2>&1
   ```
   Expect **0 failed**. Every skip and fixme is listed with its reason in the PR body: the 628F-BLOCKERs plus `admin-audit-30day` ×8, `auth-change-password` (KC), `portal-data-isolation` ×2 (no `KGOSI_EMAIL`). No test may pass vacuously.
5. **Per-suite scripts observed** (P-1): `pnpm test:e2e:projects|customers|settings|navigation --reporter=list` each runs only its directory.
6. **Tree hygiene** after every run: `git checkout -- frontend/test-results documentation/screenshots 2>/dev/null; git clean -fdq frontend/e2e/screenshots frontend/test-results documentation/screenshots` (keep `.gitkeep`). Never `git checkout -- .`. Never commit test-results, screenshots or new baselines.
7. **Optional REGRESSION** (only if a `common.sh` helper signature changed; it should not): `bash scripts/regression-test.sh` against the same stack.
8. **Marker:** `.claude/markers/verify-frontend.json` (`compose/seed` and `frontend/e2e` carry the frontend marker). PR body: cite the three run logs, the seed log, the seed-row API reads, the census counts (61/14/83/6 + 23 KC), every spec rename, every `628F-BLOCKER` with its reproduction log path, and the 628E hand-off items closed (rule widened, pins flipped, depth-ii `day-14:142-143`, trust seed values).

## Environment

- pnpm `/opt/homebrew/bin/pnpm`; always `NODE_OPTIONS=""` before pnpm or node (the user shell sets `--openssl-legacy-provider`).
- `SHELL=/bin/bash` for docker and the compose scripts (a zoxide alias breaks `cd`).
- The worktree has no `node_modules`. The main checkout `/Users/rakheendama/Projects/2026/b2b-strawman/frontend` does, and is identical at 43e8fddb2; the scout ran its read-only `eslint` / `--list` oracles there.
- E2E mock stack:
  - frontend http://localhost:3001 (mock login `/mock-login`);
  - backend http://localhost:8081 (inside the docker network: `http://backend:8080`, which the seed uses);
  - mock IDP http://localhost:8090 (`POST /token {userId, orgId, orgSlug, orgRole}`);
  - Mailpit http://localhost:8026;
  - Postgres localhost:5433 (`postgres` / `changeme`, db `app`).
  - Org slug `e2e-test-org`.
  - Users: Alice `user_e2e_alice` (owner), Bob `user_e2e_bob` (admin), Carol `user_e2e_carol` (member).
  - Seed client **Acme Corp** (`contact@acme.example.com`, ACTIVE, address 1 Long Street, Cape Town 8001 ZA); matter **Website Redesign**; vertical profile `legal-za`.
- The orchestrator owns the e2e Docker stack (ports 3001/8081/8090/5433/8026). Do not run `e2e-up/down/reseed` or Playwright against it unless the orchestrator hands it over.
- Next free tenant migration is still **V10**. This slice has no migration and no backend code.

## Orchestrator Rulings (BINDING — supersede the recommendations above where they differ)

- **R-1 (OD-1) — one PR, two sequential builders (b).** B1 = seed (`seed.sh`, `lib/{common,invoices,trust,information-requests,statements}.sh`), `playwright.config.ts`, `package.json` scripts, `e2e/helpers/seed.ts`, the P-1 dirs `customers/`, `projects/`, `settings/`, `navigation/` (incl. the 6 baseline failures PACK-01, CUST-01, CUST-02, PROJ-02, PROJ-03), depth-ii `day-14:142-143` (R-7), OD-2 config/deletion. B1 widens the ESLint `files:` to legal + helpers + fixtures + `e2e/tests/{customers,projects,settings,navigation}/**`. B1 commits + pushes the branch, does NOT open the PR, and writes `/tmp/628F/b1-handoff.md`. B2 = `documents/`, `information-requests/`, `invoices/`, `automations/`, `compliance/`, `finance/`, `notifications/`; rule → `e2e/**/*.ts`; wiring pins flipped; AUTOMATION-NOTES + `frontend/CLAUDE.md`; FE-GATE; opens the PR.
- **R-2 (OD-2) — delete `debug-kc-invite.spec.ts` (assertion-less probe). KEEP `demo-full-90day.spec.ts` and `demo-recording.spec.ts`** (possible founder video tooling — not ours to delete in an unattended run): add them to the **mock** `testIgnore` and to an explicit ESLint `ignores` entry in the rule block, with a comment "KC-only demo recording scripts; cannot run on the mock stack; not converted (628F R-2) — follow-up: convert under KC-STACK or delete". List as an authorised exception + follow-up in the PR body. Do not convert them.
- **R-3 (OD-3) — general rule accepted:** real flow where the product supports it; retitle where the check is legitimate but over-claims; `test.fixme` only for a reproduced product blocker. Specific: roles 2–3 re-scoped as recommended (system cards + Capability Reference; custom-role CRUD create→toggle→save→delete); automation-triggers retitled to what they check + follow-up "automation firing not covered"; invoice-lifecycle "VOID releases time entries" → **rewrite as a real flow** (time entry created via API setup, fee note created from it, approve, void via UI, assert the entry is unbilled again via API/UI); if the product refuses, reproduce and fixme as 628F-BLOCKER — do not delete; customer-lifecycle :227 → retitle "Fee note blocked for a client without a billing address" + assert 422 + `address_line1`; CUST-01 "pagination" → "Client list renders rows"; template-management real create + edit on the clone; custom-fields "value persists" → **real set → reload → assert** (product supports it). Every rename old → new listed in the PR body.
- **R-4 (OD-4) — accepted.** Each blocker MUST be reproduced in a run (screenshot/log under `qa_cycle/evidence/phase88-rewalk/628F/builder/`) before its `test.fixme(true, "628F-BLOCKER: … — <file:line>")`. document-generation tests 1–2 re-targeted to the matter page; one fixme'd "Generate a document from the client page" test.
- **R-5 (OD-5) — seed rows are read-only for default specs.** State-changing tests create their own run-unique entities; shared settings changed by a test are restored in `afterAll`; never assert global counts.
- **R-6 (OD-6) — yes, `e2e/helpers/seed.ts`.**
- **R-7 (OD-7) — (b)** per-item `{ name, requiresDocument }` table with `toHaveCount(0|1)` before clicking.
- **R-8 (OD-8) — all three accepted** (GET-first matter, info-request re-create when consumed, rich-seed lookup narrowed). No existing helper signature changes; new helpers only (`api_patch_json`, `check_2xx`). If `check_status` semantics are needed differently, add a new helper — never change an existing one.
- **R-9 (OD-9) — no `e2e-up.sh` change.** Measure seed duration from the seed container log timestamps; `E2E Boot-Seed Complete!` must appear. If the seed exceeds 90 s, STOP and report (do not edit e2e-up.sh). Send with `overrideWarnings:false`; if it 422s, fix the seed client data, don't override.
- **R-10 (OD-10) — (b) no Acme deposit in 628F.** 628G handles Day 21 via its own in-spec setup or a scenario call. Note it in the PR body under "Hand-offs to 628G" along with the magic-link budget note.
- **R-11 — builders RUN Playwright (authorised; the slice is unverifiable otherwise).** The e2e stack (:3001/:8081) is yours during your run; nothing else is using it.
  - **B1** first does a fresh `SHELL=/bin/bash bash compose/scripts/e2e-down.sh && SHELL=/bin/bash bash compose/scripts/e2e-up.sh` **from the worktree root** (so the seed image is built from the worktree), saves the seed log, verifies every seed row through the API (Seed design › Build & Verify step 2), then runs E2E-LEGAL (lifecycle then depth-ii) once — must be 101/0/3 and 39/0/4 — then iterates on its four dirs via the per-suite scripts until each is 0 failed, and runs each of the four dirs **twice in a row** green at the end (parallel-worker flake check). Seed iteration may re-run `e2e-down/up` as needed.
  - **B2** reuses the stack as B1 left it (do not wipe unless needed; if you wipe, re-run E2E-LEGAL first to rebuild state), iterates on its seven dirs until 0 failed, runs them twice green, then runs the full `pnpm test:e2e` once and reports the summary.
  - Every Playwright command: from `frontend/`, `NODE_OPTIONS="" E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001`, `--reporter=list`, output to a log file under `/tmp/628F/`, only the summary into context. Foreground with explicit Bash timeout ≤600000; a full `pnpm test:e2e` (~3–5 min) fits.
  - Tree hygiene after every run (Build & Verify step 6). Never commit test-results/screenshots/baselines.
- **R-12 — no product code** (`frontend/app|components|lib|hooks`, backend, portal). No `qa/testplan/demos/*.md`. No soft paths, no lint-evading swaps (`.catch` removed but the soft `if (await x.isVisible())` kept on an absent target is FORBIDDEN — a bare `isVisible()` probe is allowed only for a genuinely conditional (b) target whose state the test cannot know, and must be preceded by an `a.or(b)` wait). No `waitForTimeout`. Every test title describes what it does.
- **R-13 — evidence:** `qa_cycle/evidence/phase88-rewalk/628F/builder/` — seed log, seed-row API reads, run summaries, blocker repros. Small text/png only; no traces/videos.
