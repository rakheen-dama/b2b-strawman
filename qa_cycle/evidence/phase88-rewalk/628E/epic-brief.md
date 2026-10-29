# Implementation Brief: Slice 628E — Strict Playwright sweep + legal real flows

Branch `phase88/11E-strict-playwright` · worktree `/Users/rakheendama/Projects/2026/worktree-epic-628E` · base `origin/main` 2b442c0b8 (628D docs).
Scout: read-only, 2026-10-29. Paths are relative to the worktree unless absolute. `$E` = `frontend/e2e/tests`, `APP` = `frontend/app/(app)/org/[slug]`, `CMP` = `frontend/components`.
Baseline run read from `/tmp/628E/base/*.log` (orchestrator, fresh stack @2b442c0b8): **lifecycle 75 passed / 4 failed / 5 skipped / 14 did-not-run** (A1 day-00:282, A2 day-30:32, A4 day-45:41, A6 day-90:179), **depth-ii 38 / 0 / 5**, default config (run after the legal suites on the same stack, all dirs) **197 / 15 failed / 56 skipped / 52 did-not-run**. `dirty.txt` was empty after that run.

## Scope

**In:** E2E specs under `$E/legal-lifecycle/**` and `$E/legal-depth-ii/**`, shared e2e helpers (`frontend/e2e/helpers/**`), ONE new ESLint rule (`frontend/e2e/eslint/`) + its vitest + its config wiring, `frontend/e2e/AUTOMATION-NOTES.md`. Optionally one bullet in `frontend/CLAUDE.md` › ESLint (OD-9).
**Out (do not do):** product code (any `frontend/app|components|lib`, backend, portal) — if a real flow is blocked by a product defect, stop and report it (root `CLAUDE.md` §4/§7); scenario docs `qa/testplan/demos/*.md` (628H, CLAUDE.md §6); `compose/seed/*`, `frontend/e2e/playwright.config.ts`, `frontend/package.json` scripts, default-config spec fixes PACK-01/CUST-01/CUST-02/PROJ-02/PROJ-03 (all **628F**); portal 90-day (**628G**).

**The headline finding (read before planning).** The legal-lifecycle suite is not "four carried failures on top of 75 passes". It holds **272** `isVisible(…).catch(…)` sites (the depth-ii suite holds **0**). **189** of them are `if (has) { act }` branches around flow steps, **49** are `test.skip` / `return` guards, **19** are soft asserts. The scouts traced the targets of every guarded step to product source. Roughly two thirds of the "passing" lifecycle tests are **vacuous**: the guarded step targets UI that does not exist (a matter-level "Log Time" button, a `<form>` on the recon page, a "Change Status" button deleted in Epic 560A, a template combobox in the plain New Matter dialog, `input[type=text]` on an `<Input>` that has no `type` attribute, …), so every step is skipped and the test passes having done nothing. Concretely, on a fresh stack today: no trust account is created, Sipho never leaves PROSPECT, all four lifecycle matters are client-less and untemplated (so they have no tasks), no time entry is logged, no court date or prescription tracker exists, no fee note is created, no deposit is recorded. A strict sweep makes every one of those steps real, so it is effectively a **rewrite of the ~95-test lifecycle suite** onto the depth-ii helper style. This is the root cause the R10 M2 note names ("the lifecycle specs model flows the product never had"), and it is why OD-1 asks to split the slice.

## ORCHESTRATOR RULINGS (binding — supersede the scout's recommendations where they differ)

- **R-1 (OD-1): ONE PR, ONE slice (628E), TWO sequential builders.** Builder 1 (B1) = the scout's "E-1" scope: rule + vitest + config wiring, `grouped-tabs.ts` lift, `legal-lifecycle.ts` run-state helper, `fixtures/keycloak-auth.ts` site, AUTOMATION-NOTES + `frontend/CLAUDE.md` bullet, Day 0 (`day-00-firm-setup`), Day 1–3 (`day-01-03-onboarding`), Day 7–14 (`day-07-14-work-trust`), `matter-team` only if imports move, the OD-7 FICA probe. B1 commits locally (may push the branch) but does **NOT** open the PR. Builder 2 (B2) = "E-2": Day 30, 45, 60, 75, 90 incl. A2–A6, widens the rule to all of `e2e/tests/legal-lifecycle/**`, opens the PR. ~20 files total; far under 300.
- **R-2 (OD-2): accepted (b).** Rule wired with `files:` scoped to the legal suites + `e2e/helpers/**` + `e2e/fixtures/**`. B1 scopes it to the files B1 has converted (+ legal-depth-ii, helpers, fixtures); B2 widens to all of `legal-lifecycle/**`. **628F widens it to `e2e/**` and owns the 188 default-config sites** — the orchestrator records this hand-off in the task file. State it in the PR body as an authorised deviation from "suite-wide". Never do a lint-evading cosmetic swap (removing `.catch` but keeping a soft `if`).
- **R-3 (OD-3): accepted.** 628E lifts `openGroupedTab` verbatim to `frontend/e2e/helpers/grouped-tabs.ts`; `legal-depth-ii.ts` re-exports it.
- **R-4 (OD-4): accepted (b).** Day 0 creates the trust account only if absent, via the deterministic `.or()` wait-then-branch pattern. Name **"Mathebula & Partners Trust Account"**, Type **GENERAL**, Bank **Standard Bank** (the orchestrator relays this to 628F).
- **R-5 (OD-5): accepted.** NEW `frontend/e2e/helpers/legal-lifecycle.ts` run state (tmpdir JSON), name builders, `record*` setters; may host small lifecycle helpers. Fixes the 619E CodeRabbit `.first()` item and the 627K lookups. `matter-team.spec.ts` stays self-contained.
- **R-6 (OD-6): accepted (i)+(ii)+(iii)** with an overridable `methods` option. No `isPresent()` helper. The sanctioned optional-UI pattern is: deterministic `expect(a.or(b).first()).toBeVisible()` then branch. Do NOT add new `waitForTimeout`; remove existing ones in the tests you rewrite unless a comment justifies one (e.g. a debounce). Negative checks use `toHaveCount(0)` / `not.toBeVisible()`.
- **R-7 (OD-7):**
  1. FICA: B1 probes the depth-ii `day-14-conveyancing` FICA `test.fixme` (FIXME `depth-ii:checklist-stale-after-complete`) **5×** with the fixme removed (whole depth-ii config each time on a fresh-or-depth-ii-compatible stack — depth-ii uses run-unique names). 5/5 green → un-fixme it AND drive lifecycle Day 1 `:199` FICA for real. Anything less → restore the depth-ii fixme and `test.fixme` Day 1 `:199` with the same FIXME tag. Record every probe result (pass/fail + log path) for the PR body. Either way Sipho reaches ONBOARDING via `startClientOnboarding`.
  2–3. Fee transfer + Place Investment: real flows; capture UUIDs from URLs into run state. Raw-UUID inputs = UX follow-up for the PR body.
  4. Day 14 "Approve deposit" → re-target to "deposit is RECORDED"; the real approval assertion moves to the Day 30 fee transfer (Bob approves; self-approval blocked).
  5. Day 45 "create new SCHEDULED" → assert Postponed + one new Scheduled row (backend auto-creates).
  6. A4: drive wizard steps 1→2, assert the upload step (`bank-statement-upload`, hidden `file-input` attached, "Choose CSV File"); no CSV upload/auto-match; keep the `// GAP:` comment.
- **R-8 (OD-8): accepted.** A5 asserts ≥2 real fee-note rows from the lifecycle's own data → "resolved by amendment" if green.
- **R-9 (OD-9): accepted both** — AUTOMATION-NOTES rule section + replace the stale Tab Navigation snippet; one ESLint bullet in `frontend/CLAUDE.md` › ESLint naming the e2e rule.
- **R-10 (OD-10): accepted.** Correct spec titles/comments in-spec (D13); list every rename (old → new title) in the PR body; no `qa/testplan/demos/*.md` edits; flag "LSSA auto-populated" wording as a 628H candidate in the PR body.
- **R-11 Product blockers.** Never edit product code (`frontend/app|components|lib|hooks`, backend, portal). If a real flow is blocked by a product defect you have **reproduced in the browser/run**, use `test.fixme(true, "628E-BLOCKER: <short> — <product file:line>")` for that test only, and add it to a blockers list (file:line, symptom, evidence log path) that goes in the PR body. Never a soft path, never a weaker assertion that passes either way.
- **R-12 Acceptance.** On a fresh stack (`e2e-down` + `e2e-up`): legal-lifecycle **0 failed** and legal-depth-ii **0 failed**; every skip/fixme is listed with its reason. No test may pass vacuously: each test's title must describe what it actually does. The orchestrator runs the final gate.
- **R-13 Stack + tree hygiene.** The builders own the e2e stack (ports 3001/8081/8090/5433) exclusively while they run. Fresh stack: from the worktree root, `SHELL=/bin/bash bash compose/scripts/e2e-down.sh && SHELL=/bin/bash bash compose/scripts/e2e-up.sh` (≈2–5 min; run via nohup + marker polling). Product code = main, so no image rebuild is needed. Run suites from `frontend/` with `NODE_OPTIONS="" E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 /opt/homebrew/bin/pnpm test:e2e:legal-lifecycle --update-snapshots=changed --reporter=list` (NO `--` before flags). Single file: `npx playwright test --config e2e/playwright.legal-lifecycle.config.ts e2e/tests/legal-lifecycle/<file> --update-snapshots=changed`. The legal suites are serial chains, so a later day needs the earlier days run first on the same stack. Runs dirty tracked artefacts: after every run restore ONLY artefact paths (`git checkout -- frontend/test-results documentation/screenshots 2>/dev/null; git clean -fdq frontend/e2e/screenshots frontend/test-results documentation/screenshots` — keep `.gitkeep`), never `git checkout -- .` over uncommitted spec work. Never commit test-results / screenshots / baselines. Copy evidence logs to `/tmp/628E/b1/` (B1) or `/tmp/628E/b2/` (B2).
- **R-14 Timeouts.** If the rewritten lifecycle exceeds `globalTimeout` in `playwright.legal-lifecycle.config.ts`, raise it there (only that knob) and state why. Long tests use `test.setTimeout(...)`.
- **R-15 Keep existing strict tests intact** (627K Day 5 header test `day-01-03-onboarding.spec.ts:365`, Day 1 `:339` Add-Carol, `matter-team.spec.ts`) — only switch their name lookups to the run-state builders.

## Tasks (full text + acceptance)

**628.12 — Strict-assertion sweep + legal flows** (verbatim): "NEW ESLint rule under `frontend/e2e/eslint/` forbidding `.isVisible().catch(` (test in `$F/__tests__/eslint-rules/no-isvisible-catch.test.ts`, pattern `no-aschild-adjacency.test.ts`); suite-wide replacement with `expect(locator.first()).toBeVisible()` or role locators; `$E/legal-lifecycle/*`: A1 LPFF rate on `/trust-accounting/interest` "Add Rate" (`day-00-firm-setup.spec.ts:326`), A2/A3 real fee-note path — client Fee notes tab → Generate → Fetch unbilled → Create draft → Add line / Add tariff items (`day-30-billing.spec.ts:32,:159`), A4 five-step reconciliation wizard (`day-45-recon-prescription.spec.ts:41` — bank CSV assertion untouched), A5 on the 628F seed, A6 `columnheader` locators (`day-90-review.spec.ts:61,:177`); `$E/legal-depth-ii/*` same sweep; `frontend/e2e/AUTOMATION-NOTES.md` (the rule)". Tests: "E2E-LEGAL both configs **0 failed** on a fresh stack". Pattern refs: "R10 §A verdicts (cited per carried location in the PR body); lesson B-7; D13". Note: "A5 (seed gap) is green only after 628F — run order in 628.19."
Line drift since the spec was written: A1 test now starts at `day-00-firm-setup.spec.ts:282` (the failing `.fill` is `:304`); A6 test starts at `day-90-review.spec.ts:179`; A5 at `:63`.

**628.13 — E2E-LEGAL (slice)** (verbatim): "**E2E-LEGAL** fresh stack; results table (file:line → resolved by amendment / seed-pending)". "Interim; the carried-six disposition table is the PR body."

**Architecture acceptance (§13.3.24 U22, observed):** "On a fresh `e2e-down && e2e-up`: legal-lifecycle and legal-depth-ii 0 failed (the `fixme`s from U14/U10/U17/U18 un-set) …". §13.8.6 strict-assertion rule: "no `.isVisible().catch(...)`; every soft check is `expect(locator.first()).toBeVisible()` or a role locator; path-anchored filters only". The two remaining depth-ii `test.fixme`s are **not** from U14/U10/U17/U18 (see "Other skipped/fixme tests" below).

**Authorisations in hand:** register **D13** (U22): "Authorise Playwright locator/flow amendments in-slice (611B lesson B-7) and the seed additions (invoices, trust account, Day 3/60/85 portal rows)? — **Yes.**" Spec-only locator/flow changes therefore need no §5 row. Any change to `qa/testplan/demos/*.md` still needs a §5 row and belongs to 628H.

**Lesson B-7 (611B, cuts both ways):** each slice amends the Playwright locators its own change breaks; amend a locator only when the string it matches actually changed; a soft-skip fallback can match a *different* element; a rename can turn a pass into a skip (628A BUDGET-01 revert). In this slice the risk is inverted: removing a guard turns a vacuous **pass** into a real **fail**. Every such fail must be resolved by driving the real flow (or by an OD-ruled `test.fixme` with a cited product blocker), never by a new soft path.

**Hand-offs carried into 628E:**
- 628A (#1703 body): "Left to 628E … (soft or dead locators): `finance/profitability.spec.ts:40,60`, `settings/tags.spec.ts:87`, `documents/document-generation.spec.ts:199`, `customers/customer-data-protection.spec.ts:199`." All four are **default-config** files → they fall under OD-2.
- 619E (#1650, CodeRabbit Minor triaged to 628E): `$E/legal-lifecycle/day-07-14-work-trust.spec.ts:43` `page.getByRole("link", { name: /Ndlovu|Sipho|RAF/i }).first()` can pick an older run's matter on a reused stack. CodeRabbit asks for Day 1's exact name or id instead of a file-local `RUN_ID`. Same broad pattern at Day-7 `:102`, `:140`, `:225` (`/Moroka|Estate/`), Day-45 `:133`. Fix = OD-5 run state.
- 627K: the Day 5 header test is `day-01-03-onboarding.spec.ts:365` inside the Day 1 describe (it needs that file's `RUN_ID`). Later `/Ndlovu|RAF/` `.first()` lookups land on the RAF matter → same OD-5 fix.
- 628B: `settings/packs.spec.ts:94` strict-mode failure is **628F's** (do not touch).

## Open Decisions (orchestrator rules before the builder starts)

**OD-1 — Slice size: one PR or two?**
- Evidence: 272 lifecycle sites; 189 conditional flow steps; most targets dead (per-test notes below); the carried six need preconditions that do not exist today (trust account, ONBOARDING clients with a billing address, template matters linked to clients, time logged on tasks with billing rates, an approved disbursement for Apex). `day-30-billing.spec.ts` alone needs ~4 new setup flows.
- Options: (a) one PR: rule + helpers + all nine lifecycle files; (b) two sequential PRs: **628E-1** = rule (scoped to converted files, OD-2) + helpers (OD-3/OD-5) + AUTOMATION-NOTES + Day 0, Day 1–3, Day 7–14, matter-team; **628E-2** = Day 30, 45, 60, 75, 90 incl. A2–A6, then the rule's scope widens to all of `legal-lifecycle/**`; (c) one PR, but every vacuous non-carried test becomes `test.fixme("628E: <flow> not automated — R10 M2")` and only the carried six plus their preconditions get real flows.
- **Recommendation: (b).** Each half is reviewable and keeps "0 failed" observable on its own fresh-stack run. (c) is honest but drops coverage the suite only pretended to have, and the Day 30–90 flows need the Day 0–14 preconditions anyway. Use `test.fixme` only where a **product** blocker is cited file:line (OD-7). Branch for E-2: `phase88/11E2-…` or as the orchestrator names it.

**OD-2 — Rule scope vs default-config files (188 sites in 29 files).**
- Evidence: the default config already runtime-skips 56 tests (`/tmp/628E/base/default.log`). Many of those skips are these very guards (`settings/tags`, `roles-capabilities`, `tax-settings`, `information-request-crud`, `customer-lifecycle`, `automation-*`, `billing-runs`, …; table below). Converting them to strict assertions turns skips into failures. 628F owns "E2E-DEFAULT 0 failed".
- Options: (a) suite-wide in 628E, rewriting the 188 default sites too (outcomes change in files 628F is fixing in parallel); (b) the rule is wired with `files: ["e2e/tests/legal-lifecycle/**", "e2e/tests/legal-depth-ii/**", "e2e/helpers/**", "e2e/fixtures/**"]` in 628E, and **628F widens it to `e2e/**`** when it sweeps the default dirs (628F's row then grows by the 29 files); (c) suite-wide rule now, with each default site converted to an explicit `test.fixme("628F: …")` / `test.skip(reason)` and no behaviour change claimed.
- **Recommendation: (b)**, recorded as an authorised deviation from "suite-wide" in the PR body. A mechanical default rewrite without fixing the flows is either (c) (coverage theatre in reverse) or a lint-evading cosmetic swap (`.catch` removed, soft `if` kept). That is the loophole root `CLAUDE.md` §9 forbids. `fixtures/keycloak-auth.ts:21-24` (1 site, KC-only, a real optional branch: combined vs split login form) can be converted in 628E with the sanctioned `.or()` pattern (§ESLint rule).

**OD-3 — Who lifts `openGroupedTab` to `frontend/e2e/helpers/grouped-tabs.ts`?** The 628E row lists `grouped-tabs.ts (lifted)`; 628.14 (628F) describes the lift for PROJ-02/03.
- **Recommendation: 628E lifts it** (it lands first, and the lifecycle rewrite uses it in ~15 places). Move the function verbatim (`frontend/e2e/helpers/legal-depth-ii.ts:244-263`) to `grouped-tabs.ts`, and have `legal-depth-ii.ts` re-export it (`export { openGroupedTab } from "./grouped-tabs";`), so the 6 existing importers keep compiling. Alternatively update all 6 imports. 628F then only consumes it.
- Note: the helper's `item.waitFor({state:"visible",timeout:2_000}).then(() => true, () => false)` is a bounded retry that ends in a `throw`. It is legitimate, and the proposed rule does not flag it (`waitFor` is not an `is*` method).

**OD-4 — Who creates the trust account: Day 0 UI (scenario 0.62) or the 628F seed (`compose/seed/lib/trust.sh`)?**
- Evidence: `backend/src/main/resources/db/migration/tenant/V1__baseline.sql:2218` `CREATE UNIQUE INDEX … idx_trust_accounts_primary_general ON trust_accounts (account_type) WHERE is_primary AND account_type='GENERAL'`. The "Add Trust Account" dialog defaults `isPrimary: true` (`CMP/trust/CreateTrustAccountDialog.tsx:56-58`), and the service defaults primary to true (`backend/…/verticals/legal/trustaccounting/TrustAccountService.java:129`). A second primary GENERAL account therefore cannot be created. The scenario `qa/testplan/demos/legal-za-90day-keycloak.md:185` 0.62 is a UI step: "Create trust account: Name = **Mathebula & Partners Trust Account**, Type = **GENERAL**, Bank = **Standard Bank**". depth-ii already creates exactly that account when missing (`$E/legal-depth-ii/day-05-disbursements.spec.ts:203-217`).
- Options: (a) Day 0 always creates it in the UI; 628F must not seed a primary GENERAL account; (b) Day 0 uses the depth-ii deterministic branch (`addAccount.or(recordTransaction)` → create only if absent), and 628F seeds the account with the **identical** name and bank, so the downstream assertions hold either way; (c) 628F seeds it and Day 0 only asserts.
- **Recommendation: (b)**, with the orchestrator relaying the exact name and bank to 628F. Without a trust account today, A1 (no "Add Rate" button), A4 (no `new-reconciliation-btn`), Day 14 deposits, Day 30 fee transfer, Day 60 interest/investments and Day 90 trust reports all have nothing to drive (`APP/trust-accounting/interest/page.tsx:106,194`; `APP/trust-accounting/reconciliation/page.tsx:106`; `APP/trust-accounting/transactions/page.tsx:225`).

**OD-5 — Cross-file run state for the lifecycle suite.**
- Evidence: `RUN_ID` is per file (`day-01-03-onboarding.spec.ts:23-28` comment admits it; `day-75-complex.spec.ts:25`; `matter-team.spec.ts:24`). Later files find Day 1–3 records by loose regex + `.first()` (the 619E CodeRabbit item). depth-ii solved this with a tmpdir run-state JSON (`frontend/e2e/helpers/legal-depth-ii.ts:28-87`).
- **Recommendation:** NEW `frontend/e2e/helpers/legal-lifecycle.ts`, a mirror of the depth-ii run-state:
  - `startLifecycleRun()`, called by the first Day 0 test;
  - `currentLifecycleRun()` / `attemptRunId()`;
  - name builders `siphoClient()`, `siphoMatter()` (`Ndlovu v RAF ${id}`), `apexClient()`, `apexMatter()`, `morokaClient()`, `morokaMatter()`, `qcClient()`, `qcMatter()`;
  - a `record*` setter for any id the later days need (client / fee-note UUIDs for the UUID-only dialogs, OD-7).
  - File: `path.join(tmpdir(), "kazi-legal-lifecycle-run.json")`.
- `matter-team.spec.ts` stays self-contained.

**OD-6 — Rule breadth and the sanctioned "genuinely optional UI" pattern.**
- Proposal: rule `no-isvisible-catch` reports:
  - (i) `.catch(…)` chained directly on `isVisible|isHidden|isEnabled|isDisabled|isChecked|isEditable` (these never wait; they reject only on a strict-mode violation or a closed page, so `.catch` exists only to hide strict mode);
  - (ii) the same via `.then(onFulfilled, onRejected)`;
  - (iii) `isVisible({ timeout })` / `isHidden({ timeout })`, because Playwright ignores the option (message `timeoutIgnored`).
- No helper is added. The sanctioned optional-UI pattern (used in depth-ii `day-05-disbursements.spec.ts:206-207`) is "wait deterministically for either state, then branch":
  ```ts
  await expect(addAccount.or(recordTransaction).first()).toBeVisible({ timeout: 15_000 });
  if (await addAccount.isVisible()) { /* create */ }
  ```
- `textContent().catch(() => "")` (5 lifecycle sites: `day-00:38,:323`, `day-90:~319,~345,~369`) is removed by the sweep. It is not added to the rule.
- **Recommendation:** (i)+(ii)+(iii), with option `methods` overridable. The AUTOMATION-NOTES text forbids bare `if (await loc.isVisible())` without a preceding deterministic wait. That cannot be linted cheaply; the reviewer checks it.

**OD-7 — Lifecycle tests whose real flow is blocked or awkward. Real flow vs `test.fixme`?**
1. Day 1 `:199` "Complete FICA checklist → auto-ACTIVE". Needs:
   - a client-document upload first (5 required items need a linked document: `backend/src/main/resources/compliance-packs/legal-za-individual-onboarding/pack.json`);
   - an address/city/country on the client;
   - and the depth-ii equivalent is `test.fixme` FIXME `depth-ii:checklist-stale-after-complete` (`$E/legal-depth-ii/day-14-conveyancing.spec.ts:124-167`).

   **Finding:** that FIXME's mechanism ("server action returned 200 … the router never applied it") matches the next@16.2.6 React lost-ping bug (memory `next_16_2_6_lost_react_ping`; `frontend/CLAUDE.md` › Patched dependencies: "do not go below Next 16.3"). `frontend/package.json` is on `next 16.3.8`. **Recommendation:** when the orchestrator frees the stack, re-probe the depth-ii FICA test 5× with the fixme removed. If 5/5 green, un-fixme it and drive Day 1 FICA for real (upload pattern `day-14-conveyancing.spec.ts:102-120`). Otherwise `test.fixme` Day 1 `:199` with the same FIXME tag. Either way Sipho must reach ONBOARDING via `startClientOnboarding` (fee notes and deposits do not need lifecycle ACTIVE: `backend/…/compliance/CustomerLifecycleGuard.java:19-60` only blocks CREATE_PROJECT/CREATE_TASK for PROSPECT).
2. Day 30 `:237` fee transfer: `CMP/trust/RecordFeeTransferDialog.tsx:98-149` has raw-UUID "Client ID" / "Fee note ID" text inputs. It needs a Moroka fee note in APPROVED/SENT (`TrustTransactionService.java:587-599`) and creates AWAITING_APPROVAL. Approval must be by a second user (self-approval blocked, `:787`).
3. Day 60 `:124`/`:242` Place Investment: "Client ID" is a raw UUID (`CMP/trust/PlaceInvestmentDialog.tsx:121-123`).

   For 2 and 3, UUIDs are capturable from `/clients/{uuid}` / `/invoices/{uuid}` URLs into run state (OD-5). They are not product blockers, only poor UX (report as follow-ups).

   **Recommendation:** real flows for 2 and 3; FICA per the probe outcome.
4. Day 14 `:352` "Approve Moroka trust deposit": deposits land RECORDED, never AWAITING_APPROVAL (depth-ii asserts "RECORDED", `day-05-disbursements.spec.ts:236`; approve UI only for AWAITING_APPROVAL, `APP/trust-accounting/transactions/page.tsx:417-425`). The test as titled is dead. **Recommendation:** re-title/re-target to "deposit is RECORDED" (spec-only amendment under D13), or move the approval assertion onto the Day 30 fee transfer (Bob approves), which is the product's real approval path.
5. Day 45 `:163` "create new SCHEDULED": the backend auto-creates the rescheduled date on Postpone (`backend/…/courtcalendar/CourtCalendarService.java:252-275`, description "Rescheduled from <date>"). The manual create step is redundant. **Recommendation:** replace it with an assertion that a Postponed badge and one new Scheduled row exist.
6. Day 45 `:41` A4: "bank CSV assertion untouched". **Recommendation:** read as "do not add CSV upload / auto-match". Drive steps 1→2 and assert the upload step (`bank-statement-upload` + hidden `file-input` attached + "Choose CSV File" button). Keep the existing `// GAP: Bank CSV upload…` comment. Drop the `/balanced|matched|reconciled/` soft assert, which has no reachable target without a CSV. Alternative if the orchestrator wants step 3+: add a tiny CSV fixture. The scouts did not verify the parser's column format; the builder must read `BankStatement*` backend parsing first.

**OD-8 — A5 dependency.** The spec says "A5 on the 628F seed". If A2/A3 (+ QuickCollect) become real, the lifecycle creates ≥2 fee notes itself before Day 90.
- **Recommendation:** A5 asserts ≥2 fee-note rows with a precise locator. It must pass from the lifecycle's own data; the 628F seed is additive. Record it as "resolved by amendment", not "seed-pending", if it is green on the 628E fresh-stack run.

**OD-9 — Doc touch-ups.** `AUTOMATION-NOTES.md` › "Tab Navigation" (`frontend/e2e/AUTOMATION-NOTES.md:633-640`) still teaches `getByRole("tab", { name: "Tasks" })` + `getByRole("tabpanel", { name: "Tasks" })`. That is the exact dead pattern behind PROJ-02/03 (R10 B2).
- **Recommendation:** replace it with `openGroupedTab` in the same edit as the new rule section.
- Optionally add one bullet to `frontend/CLAUDE.md` › ESLint (`:189-194`) naming the e2e rule. It is a one-line convention note and not in the task's file list, so it needs a ruling.

**OD-10 — Test titles that state behaviour the product does not have.**
- Examples: Day 30 "Verify LSSA tariff auto-population" (`day-30-billing.spec.ts:6`, comment `:133`); Day 0 "4 matter templates" (the pack has **6**: `backend/src/main/resources/project-template-packs/legal-za.json`); Day 0 "create VAT 15%" (already seeded as "VAT — Standard", `V1__baseline.sql:2340-2344` + `VerticalProfileReconciliationSeeder.java:146-170`); Day 14 "Approve deposit"; Day 45 "create new SCHEDULED".
- Spec titles and comments are not scenario docs. **Recommendation:** correct titles/comments in-spec under D13 and list each rename in the PR body. Results tables key on file:line + new title. Do not edit `qa/testplan/demos/*.md` (the "auto-populated" wording is not a register §5 row; flag it to 628H if wanted).


## Site Census

Method: Python multi-line regex `\.isVisible\(([^()]|\([^()]*\))*\)\s*\.catch\(` over `frontend/e2e/**/*.ts` (catches both `.isVisible().catch(` on one line and the `.isVisible({ timeout })\n  .catch(` split form). Each site was classified by how its result is used: an `expect(…)` → **A**; `if (!x) { test.skip | return }` → **B**; `if (x) { act }` / inline `if (await …)` → **C**. Statuses come from the orchestrator's fresh-stack baseline logs `/tmp/628E/base/{lifecycle,default}.log` (@2b442c0b8). Scratch data: `/tmp/628E/scout/sites3.json`.

### Totals

| Scope | sites | files | A soft-assert | B skip/return guard | C conditional branch |
|---|---|---|---|---|---|
| `legal-lifecycle/**` | **272** | 8 (all but `matter-team.spec.ts`) | 19 | 49 (37 are dead `<main>` guards) | 204 (6 `<main>`, 9 "dialog still open → Escape", 189 flow steps) |
| `legal-depth-ii/**` | **0** | 0 | — | — | — |
| default config (`$E/*` other dirs + `fixtures/`) | **188** | 29 | 16 | 38 | 134 |
| **all** | **460** | 37 | 35 | 87 | 338 |

The task's pre-count of "~361 single-line sites + ~99 continuation lines" is the same 460 (99 sites span lines).

### Sibling soft patterns (same effect, different syntax)

| Pattern | legal-lifecycle | legal-depth-ii | default | Note |
|---|---|---|---|---|
| `isVisible({ timeout })` (timeout ignored) | most of the 272 | 0 | most of the 188 | 328 total; OD-6 (iii) |
| `textContent({…}).catch(() => "")` | 5 (`day-00:38`, `:323`; `day-90:~319,~345,~369`) | 0 | 0 | swallow + empty string; rewrite |
| `test.skip(` (runtime) | 54 | 2 (`day-05:147` deterministic after `.or()` wait — OK; `null-prefetch-probe:71` opt-in — OK) | 130 | lifecycle ones all go |
| `expect.soft(` | 22 | 0 | 0 | → hard `expect` |
| `.count() >= 1` on a broad `[data-testid*=…]` union | 3 (`day-90:41,:58,:73`) | 0 | 1 (`finance/reports`) | A5 passes vacuously through this |
| `page.waitForTimeout(…)` fixed sleeps | 146 | 0 | many | replace with web-first waits as each step is rewritten |
| bare `if (await x.isVisible())` (no catch) | 0 | 3 (`day-05:147,:207`; `day-14:153`), all after a deterministic wait | 8 in `demo-recording.spec.ts` | the sanctioned form only after `expect(a.or(b))` |
| `try { … } catch {}` swallowing an `expect` | 0 | 0 | 0 | — |
| `.catch(() => false)` on `waitFor`/`isEnabled`/`isChecked` | 0 | 0 | 0 | — |

### Default-config files (owned for fixes by 628F — see OD-2)

"SKIP" in the status column conflates runtime `test.skip` with serial "did not run" (the list reporter prints `-` for both).


| file | sites | A | B | C | tests w/ sites: baseline status (fresh stack, run after legal suites) |
|---|---|---|---|---|---|
| `settings/data-protection-settings.spec.ts` | 20 | 0 | 0 | 20 | PASS 6, SKIP 3 |
| `demo-full-90day.spec.ts` | 19 | 0 | 0 | 19 | FAIL 1, not in run 1 |
| `customers/customer-data-protection.spec.ts` | 17 | 0 | 3 | 14 | PASS 2, SKIP 2 |
| `documents/document-generation.spec.ts` | 17 | 4 | 5 | 8 | PASS 1, SKIP 3 |
| `documents/template-management.spec.ts` | 17 | 7 | 9 | 1 | PASS 4 |
| `settings/custom-fields.spec.ts` | 11 | 0 | 0 | 11 | PASS 1, SKIP 2 |
| `settings/tags.spec.ts` | 9 | 0 | 0 | 9 | SKIP 2 |
| `notifications/notification-bell.spec.ts` | 8 | 1 | 4 | 3 | PASS 4 |
| `navigation/command-palette.spec.ts` | 7 | 0 | 1 | 6 | PASS 3 |
| `settings/roles-capabilities.spec.ts` | 7 | 0 | 0 | 7 | SKIP 2 |
| `customers/customer-crud.spec.ts` | 6 | 3 | 0 | 3 | FAIL 1, SKIP 3 |
| `information-requests/information-request-crud.spec.ts` | 6 | 0 | 3 | 3 | SKIP 3 |
| `notifications/notification-preferences.spec.ts` | 5 | 0 | 0 | 5 | PASS 2, SKIP 1 |
| `settings/tax-settings.spec.ts` | 5 | 0 | 2 | 3 | SKIP 2 |
| `projects/project-budget.spec.ts` | 4 | 0 | 2 | 2 | PASS 3 |
| `projects/project-crud.spec.ts` | 4 | 0 | 1 | 3 | PASS 2, SKIP 1 |
| `settings/general-settings.spec.ts` | 4 | 0 | 2 | 2 | PASS 2 |
| `customers/customer-lifecycle.spec.ts` | 3 | 0 | 3 | 0 | SKIP 3 |
| `finance/profitability.spec.ts` | 3 | 0 | 0 | 3 | PASS 3 |
| `finance/reports.spec.ts` | 3 | 0 | 1 | 2 | PASS 1, SKIP 1 |
| `debug-kc-invite.spec.ts` | 2 | 0 | 0 | 2 | PASS 1 |
| `demo-recording.spec.ts` | 2 | 0 | 0 | 2 | FAIL 1 |
| `invoices/billing-runs.spec.ts` | 2 | 0 | 0 | 2 | SKIP 1 |
| `invoices/invoice-lifecycle.spec.ts` | 2 | 0 | 1 | 1 | PASS 2 |
| `fixtures/keycloak-auth.ts` | 1 | 0 | 0 | 1 | not in run 1 |
| `automations/automation-crud.spec.ts` | 1 | 0 | 1 | 0 | SKIP 1 |
| `automations/automation-triggers.spec.ts` | 1 | 0 | 0 | 1 | SKIP 1 |
| `compliance/compliance-overview.spec.ts` | 1 | 0 | 0 | 1 | PASS 1 |
| `invoices/invoice-crud.spec.ts` | 1 | 1 | 0 | 0 | PASS 1 |


### legal-lifecycle — per-site table (272 rows)

Categories: **A** = soft assert; **B** = skip/return guard; **B-main**/**C-main** = guard on `page.locator("main")` (always rendered → dead, delete; assert the page h1 instead); **C** = `if (visible) { act }` around a flow step; **C-dialog** = "dialog still open → press Escape" (every dialog in these flows closes on success → `expect(dialog).toBeHidden()`). "Default replacement" is the mechanical rule; the **Per-test real-flow notes** below give the real target, or say that the step is dead and must be deleted or re-targeted. Baseline status is per test (SKIP/DNR = runtime skip or serial did-not-run).

| # | file:line | test (start, baseline) | cat | locator | default replacement |
|---|---|---|---|---|---|
| 1 | `day-00-firm-setup:76` | Alice: Settings — set brand colour #1B3A4B (:64, PASS) | C | `page .locator( 'input[type="color"], input[name*="brand"], input[name*="colour"], input[name…` | strict step on real target, or DELETE if dead (test notes) |
| 2 | `day-00-firm-setup:82` | Alice: Settings — set brand colour #1B3A4B (:64, PASS) | C | `page.getByRole("button", { name: /save/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 3 | `day-00-firm-setup:110` | Alice: Settings — verify billing rate creati (:100, PASS) | B | `page .getByRole("button", { name: /new.*(rate\|tariff)\|add.*(rate\|tariff)/i }) .first()` | strict expect + act; delete `test.skip` |
| 4 | `day-00-firm-setup:122` | Alice: Settings — verify billing rate creati (:100, PASS) | C | `page.getByRole("dialog").first()` | strict step on real target, or DELETE if dead (test notes) |
| 5 | `day-00-firm-setup:127` | Alice: Settings — verify billing rate creati (:100, PASS) | C | `page.getByRole("textbox", { name: /name\|member\|user/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 6 | `day-00-firm-setup:136` | Alice: Settings — verify billing rate creati (:100, PASS) | C | `page .getByRole("textbox", { name: /amount\|rate/i }) .or(page.getByRole("spinbutton", { nam…` | strict step on real target, or DELETE if dead (test notes) |
| 7 | `day-00-firm-setup:160` | Alice: Settings — create cost rates for Alic (:150, PASS) | C | `page.getByRole("tab", { name: /cost/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 8 | `day-00-firm-setup:185` | Alice: Settings — create VAT 15% (:178, PASS) | C | `page.getByRole("button", { name: /new.*tax\|add.*tax\|create.*tax/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 9 | `day-00-firm-setup:192` | Alice: Settings — create VAT 15% (:178, PASS) | C | `page.getByRole("textbox", { name: /name/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 10 | `day-00-firm-setup:201` | Alice: Settings — create VAT 15% (:178, PASS) | C | `page .getByRole("textbox", { name: /rate\|percentage/i }) .or(page.getByRole("spinbutton", {…` | strict step on real target, or DELETE if dead (test notes) |
| 11 | `day-00-firm-setup:207` | Alice: Settings — create VAT 15% (:178, PASS) | C | `page.getByRole("button", { name: /create\|save\|add/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 12 | `day-00-firm-setup:216` | Alice: Settings — create VAT 15% (:178, PASS) | B | `page .getByText(/15%\|VAT/)` | strict expect + act; delete `test.skip` |
| 13 | `day-00-firm-setup:245` | Alice: Custom fields — legal field packs loa (:234, PASS) | C | `page .getByText(new RegExp(field.replace("_", "[_ ]"), "i"))` | strict step on real target, or DELETE if dead (test notes) |
| 14 | `day-00-firm-setup:270` | Alice: 4 matter templates listed (:258, PASS) | C | `page .getByText(new RegExp(template, "i")) .first()` | strict step on real target, or DELETE if dead (test notes) |
| 15 | `day-00-firm-setup:292` | Alice: Trust account settings page loads (:282, FAIL) | C | `page .getByRole("button", { name: /create.*trust\|new.*trust\|add.*account/i }) .first()` | strict step on real target, or DELETE if dead (test notes) |
| 16 | `day-00-firm-setup:303` | Alice: Trust account settings page loads (:282, FAIL) | C | `page .locator('input[name*="lpff"], input[name*="interest"], [data-testid*="lpff"]') .first()` | strict step on real target, or DELETE if dead (test notes) |
| 17 | `day-00-firm-setup:308` | Alice: Trust account settings page loads (:282, FAIL) | C | `page.getByRole("button", { name: /save/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 18 | `day-00-firm-setup:340` | Alice: Legal modules enabled (trust, court,  (:317, SKIP/DNR) | C-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 19 | `day-01-03-onboarding:43` | Bob: Conflict check for Sipho Ndlovu — CLEAR (:37, PASS) | B-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 20 | `day-01-03-onboarding:55` | Bob: Conflict check for Sipho Ndlovu — CLEAR (:37, PASS) | C | `page .getByRole("textbox", { name: /search\|name\|query/i }) .or(page.locator('input[type="s…` | strict step on real target, or DELETE if dead (test notes) |
| 21 | `day-01-03-onboarding:61` | Bob: Conflict check for Sipho Ndlovu — CLEAR (:37, PASS) | C | `page.getByRole("button", { name: /search\|check\|run/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 22 | `day-01-03-onboarding:73` | Bob: Conflict check for Sipho Ndlovu — CLEAR (:37, PASS) | C | `page .getByText(/clear\|no.*(conflict\|match)/i)` | strict step on real target, or DELETE if dead (test notes) |
| 23 | `day-01-03-onboarding:96` | Bob: Create client Sipho Ndlovu (Individual) (:86, PASS) | B | `page.getByRole("button", { name: /New (Customer\|Client)/i })` | strict expect + act; delete `test.skip` |
| 24 | `day-01-03-onboarding:110` | Bob: Create client Sipho Ndlovu (Individual) (:86, PASS) | C | `page.getByRole("textbox", { name: /email/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 25 | `day-01-03-onboarding:116` | Bob: Create client Sipho Ndlovu (Individual) (:86, PASS) | C | `page.getByRole("textbox", { name: /phone/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 26 | `day-01-03-onboarding:125` | Bob: Create client Sipho Ndlovu (Individual) (:86, PASS) | C | `page .locator('select[name*="entity"], [data-testid*="entity-type"]') .first()` | strict step on real target, or DELETE if dead (test notes) |
| 27 | `day-01-03-onboarding:132` | Bob: Create client Sipho Ndlovu (Individual) (:86, PASS) | C | `page.getByRole("button", { name: /Next/i })` | strict step on real target, or DELETE if dead (test notes) |
| 28 | `day-01-03-onboarding:153` | Bob: Create client Sipho Ndlovu (Individual) (:86, PASS) | C-dialog | `page .getByRole("dialog")` | `expect(dialog).toBeHidden()`; drop Escape |
| 29 | `day-01-03-onboarding:185` | Bob: Transition Sipho to ONBOARDING (:167, PASS) | C | `page.getByRole("button", { name: /Change Status/i })` | strict step on real target, or DELETE if dead (test notes) |
| 30 | `day-01-03-onboarding:190` | Bob: Transition Sipho to ONBOARDING (:167, PASS) | C | `page.getByRole("menuitem", { name: /Start Onboarding/i })` | strict step on real target, or DELETE if dead (test notes) |
| 31 | `day-01-03-onboarding:213` | Bob: Complete FICA checklist for Sipho — aut (:199, PASS) | C | `page.getByRole("tab", { name: /checklist\|fica\|onboarding/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 32 | `day-01-03-onboarding:234` | Bob: Complete FICA checklist for Sipho — aut (:199, PASS) | C | `page .getByText(/active/i) .first()` | strict step on real target, or DELETE if dead (test notes) |
| 33 | `day-01-03-onboarding:253` | Alice: Create Litigation matter for Sipho (:244, PASS) | B | `page.getByRole("button", { name: /New (Project\|Matter)/i })` | strict expect + act; delete `test.skip` |
| 34 | `day-01-03-onboarding:265` | Alice: Create Litigation matter for Sipho (:244, PASS) | C | `page.getByRole("textbox", { name: /name/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 35 | `day-01-03-onboarding:275` | Alice: Create Litigation matter for Sipho (:244, PASS) | C | `page .locator('[data-testid*="customer"], [data-testid*="client"]') .or(page.getByRole("comb…` | strict step on real target, or DELETE if dead (test notes) |
| 36 | `day-01-03-onboarding:280` | Alice: Create Litigation matter for Sipho (:244, PASS) | C | `page.getByRole("option", { name: new RegExp("Sipho\|Ndlovu", "i") }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 37 | `day-01-03-onboarding:291` | Alice: Create Litigation matter for Sipho (:244, PASS) | C | `page .getByRole("combobox", { name: /template/i }) .or(page.locator('[data-testid*="template…` | strict step on real target, or DELETE if dead (test notes) |
| 38 | `day-01-03-onboarding:296` | Alice: Create Litigation matter for Sipho (:244, PASS) | C | `page.getByRole("option", { name: /litigation/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 39 | `day-01-03-onboarding:310` | Alice: Create Litigation matter for Sipho (:244, PASS) | C-dialog | `page .getByRole("dialog")` | `expect(dialog).toBeHidden()`; drop Escape |
| 40 | `day-01-03-onboarding:323` | Alice: Create Litigation matter for Sipho (:244, PASS) | C | `page .getByText(new RegExp(`Ndlovu.*RAF\|RAF.*Ndlovu`, "i")) .first()` | strict step on real target, or DELETE if dead (test notes) |
| 41 | `day-01-03-onboarding:429` | Bob: Conflict check for Apex Holdings — CLEA (:423, PASS) | B-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 42 | `day-01-03-onboarding:437` | Bob: Conflict check for Apex Holdings — CLEA (:423, PASS) | C | `page.locator('input[type="search"], input[type="text"]').first()` | strict step on real target, or DELETE if dead (test notes) |
| 43 | `day-01-03-onboarding:460` | Bob: Create client Apex Holdings (Company) (:445, PASS) | C | `page.getByRole("textbox", { name: /email/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 44 | `day-01-03-onboarding:466` | Bob: Create client Apex Holdings (Company) (:445, PASS) | C | `page.getByRole("button", { name: /Next/i })` | strict step on real target, or DELETE if dead (test notes) |
| 45 | `day-01-03-onboarding:480` | Bob: Create client Apex Holdings (Company) (:445, PASS) | C-dialog | `page .getByRole("dialog")` | `expect(dialog).toBeHidden()`; drop Escape |
| 46 | `day-01-03-onboarding:504` | Bob: Conflict check for Moroka Family Trust  (:498, PASS) | B-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 47 | `day-01-03-onboarding:512` | Bob: Conflict check for Moroka Family Trust  (:498, PASS) | C | `page.locator('input[type="search"], input[type="text"]').first()` | strict step on real target, or DELETE if dead (test notes) |
| 48 | `day-01-03-onboarding:535` | Bob: Create client Moroka Family Trust (:520, PASS) | C | `page.getByRole("textbox", { name: /email/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 49 | `day-01-03-onboarding:541` | Bob: Create client Moroka Family Trust (:520, PASS) | C | `page.getByRole("button", { name: /Next/i })` | strict step on real target, or DELETE if dead (test notes) |
| 50 | `day-01-03-onboarding:555` | Bob: Create client Moroka Family Trust (:520, PASS) | C-dialog | `page .getByRole("dialog")` | `expect(dialog).toBeHidden()`; drop Escape |
| 51 | `day-01-03-onboarding:576` | Alice: Create Deceased Estate matter for Mor (:567, PASS) | B | `page.getByRole("button", { name: /New (Project\|Matter)/i })` | strict expect + act; delete `test.skip` |
| 52 | `day-01-03-onboarding:586` | Alice: Create Deceased Estate matter for Mor (:567, PASS) | C | `page.getByRole("textbox", { name: /name/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 53 | `day-01-03-onboarding:593` | Alice: Create Deceased Estate matter for Mor (:567, PASS) | C | `page.getByRole("combobox", { name: /template/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 54 | `day-01-03-onboarding:598` | Alice: Create Deceased Estate matter for Mor (:567, PASS) | C | `page.getByRole("option", { name: /deceased.*estate\|estate/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 55 | `day-01-03-onboarding:610` | Alice: Create Deceased Estate matter for Mor (:567, PASS) | C-dialog | `page .getByRole("dialog")` | `expect(dialog).toBeHidden()`; drop Escape |
| 56 | `day-01-03-onboarding:623` | Alice: Create Deceased Estate matter for Mor (:567, PASS) | C | `page .getByText(/Moroka\|Estate/i) .first()` | strict step on real target, or DELETE if dead (test notes) |
| 57 | `day-01-03-onboarding:648` | Bob: Conflict check for QuickCollect Service (:642, PASS) | B-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 58 | `day-01-03-onboarding:656` | Bob: Conflict check for QuickCollect Service (:642, PASS) | C | `page.locator('input[type="search"], input[type="text"]').first()` | strict step on real target, or DELETE if dead (test notes) |
| 59 | `day-01-03-onboarding:679` | Bob: Create client QuickCollect Services (Co (:664, PASS) | C | `page.getByRole("textbox", { name: /email/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 60 | `day-01-03-onboarding:685` | Bob: Create client QuickCollect Services (Co (:664, PASS) | C | `page.getByRole("button", { name: /Next/i })` | strict step on real target, or DELETE if dead (test notes) |
| 61 | `day-01-03-onboarding:699` | Bob: Create client QuickCollect Services (Co (:664, PASS) | C-dialog | `page .getByRole("dialog")` | `expect(dialog).toBeHidden()`; drop Escape |
| 62 | `day-01-03-onboarding:721` | Alice: Create initial Collections matter for (:711, PASS) | B | `page.getByRole("button", { name: /New (Project\|Matter)/i })` | strict expect + act; delete `test.skip` |
| 63 | `day-01-03-onboarding:731` | Alice: Create initial Collections matter for (:711, PASS) | C | `page.getByRole("textbox", { name: /name/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 64 | `day-01-03-onboarding:742` | Alice: Create initial Collections matter for (:711, PASS) | C-dialog | `page .getByRole("dialog")` | `expect(dialog).toBeHidden()`; drop Escape |
| 65 | `day-07-14-work-trust:56` | Carol: Log 90min time on Sipho matter (:35, PASS) | C | `page .getByRole("button", { name: /Log Time\|New Time\|Add Time\|Time Recording/i }) .first()` | strict step on real target, or DELETE if dead (test notes) |
| 66 | `day-07-14-work-trust:67` | Carol: Log 90min time on Sipho matter (:35, PASS) | C | `page .getByRole("textbox", { name: /duration\|hours\|minutes/i }) .or(page.getByRole("spinbu…` | strict step on real target, or DELETE if dead (test notes) |
| 67 | `day-07-14-work-trust:73` | Carol: Log 90min time on Sipho matter (:35, PASS) | C | `page.getByRole("textbox", { name: /description\|notes\|activity/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 68 | `day-07-14-work-trust:79` | Carol: Log 90min time on Sipho matter (:35, PASS) | C | `page.getByRole("button", { name: /save\|create\|log/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 69 | `day-07-14-work-trust:87` | Carol: Log 90min time on Sipho matter (:35, PASS) | C | `page.getByRole("tab", { name: /time/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 70 | `day-07-14-work-trust:103` | Bob: Log 120min on Sipho — letter of demand (:96, PASS) | C | `page.getByRole("link", { name: /Ndlovu\|Sipho\|RAF/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 71 | `day-07-14-work-trust:111` | Bob: Log 120min on Sipho — letter of demand (:96, PASS) | C | `page .getByRole("button", { name: /Log Time\|New Time\|Add Time\|Time Recording/i }) .first()` | strict step on real target, or DELETE if dead (test notes) |
| 72 | `day-07-14-work-trust:119` | Bob: Log 120min on Sipho — letter of demand (:96, PASS) | C | `page .getByRole("textbox", { name: /description\|notes\|activity/i }) .first()` | strict step on real target, or DELETE if dead (test notes) |
| 73 | `day-07-14-work-trust:125` | Bob: Log 120min on Sipho — letter of demand (:96, PASS) | C | `page.getByRole("button", { name: /save\|create\|log/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 74 | `day-07-14-work-trust:141` | Bob: Add comment on Sipho matter (:134, PASS) | C | `page.getByRole("link", { name: /Ndlovu\|Sipho\|RAF/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 75 | `day-07-14-work-trust:148` | Bob: Add comment on Sipho matter (:134, PASS) | C | `page.getByRole("tab", { name: /comment\|activity\|discussion/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 76 | `day-07-14-work-trust:159` | Bob: Add comment on Sipho matter (:134, PASS) | C | `page .getByRole("textbox", { name: /comment\|message/i }) .or(page.locator('textarea, [conte…` | strict step on real target, or DELETE if dead (test notes) |
| 77 | `day-07-14-work-trust:165` | Bob: Add comment on Sipho matter (:134, PASS) | C | `page.getByRole("button", { name: /send\|post\|submit\|comment/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 78 | `day-07-14-work-trust:182` | Alice: Create court date for Sipho (Motion,  (:175, PASS) | B-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 79 | `day-07-14-work-trust:192` | Alice: Create court date for Sipho (Motion,  (:175, PASS) | C | `page .getByRole("button", { name: /new.*date\|add.*date\|new.*event\|schedule/i }) .first()` | strict step on real target, or DELETE if dead (test notes) |
| 80 | `day-07-14-work-trust:200` | Alice: Create court date for Sipho (Motion,  (:175, PASS) | C | `page.getByRole("textbox", { name: /title\|name\|description/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 81 | `day-07-14-work-trust:206` | Alice: Create court date for Sipho (Motion,  (:175, PASS) | C | `page.getByRole("button", { name: /save\|create\|schedule/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 82 | `day-07-14-work-trust:226` | Alice: Log 60min on Moroka estate matter (:219, PASS) | C | `page.getByRole("link", { name: /Moroka\|Estate/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 83 | `day-07-14-work-trust:234` | Alice: Log 60min on Moroka estate matter (:219, PASS) | C | `page .getByRole("button", { name: /Log Time\|New Time\|Add Time\|Time Recording/i }) .first()` | strict step on real target, or DELETE if dead (test notes) |
| 84 | `day-07-14-work-trust:242` | Alice: Log 60min on Moroka estate matter (:219, PASS) | C | `page .getByRole("textbox", { name: /description\|notes\|activity/i }) .first()` | strict step on real target, or DELETE if dead (test notes) |
| 85 | `day-07-14-work-trust:248` | Alice: Log 60min on Moroka estate matter (:219, PASS) | C | `page.getByRole("button", { name: /save\|create\|log/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 86 | `day-07-14-work-trust:279` | Alice: Navigate to Trust Accounting (:273, PASS) | B-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 87 | `day-07-14-work-trust:295` | Alice: Deposit R250,000 into trust for Morok (:289, SKIP/DNR) | B-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 88 | `day-07-14-work-trust:305` | Alice: Deposit R250,000 into trust for Morok (:289, SKIP/DNR) | C | `page .getByRole("button", { name: /new.*deposit\|add.*deposit\|record.*deposit/i }) .first()` | strict step on real target, or DELETE if dead (test notes) |
| 89 | `day-07-14-work-trust:316` | Alice: Deposit R250,000 into trust for Morok (:289, SKIP/DNR) | C | `page .getByRole("textbox", { name: /amount/i }) .or(page.getByRole("spinbutton", { name: /am…` | strict step on real target, or DELETE if dead (test notes) |
| 90 | `day-07-14-work-trust:322` | Alice: Deposit R250,000 into trust for Morok (:289, SKIP/DNR) | C | `page.getByRole("textbox", { name: /description\|reference\|note/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 91 | `day-07-14-work-trust:329` | Alice: Deposit R250,000 into trust for Morok (:289, SKIP/DNR) | C | `page.getByRole("combobox", { name: /client\|customer/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 92 | `day-07-14-work-trust:334` | Alice: Deposit R250,000 into trust for Morok (:289, SKIP/DNR) | C | `page.getByRole("option", { name: /Moroka/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 93 | `day-07-14-work-trust:341` | Alice: Deposit R250,000 into trust for Morok (:289, SKIP/DNR) | C | `page.getByRole("button", { name: /save\|create\|submit\|deposit/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 94 | `day-07-14-work-trust:358` | Alice: Approve Moroka trust deposit (:352, PASS) | B-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 95 | `day-07-14-work-trust:367` | Alice: Approve Moroka trust deposit (:352, PASS) | C | `page.getByRole("button", { name: /approve/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 96 | `day-07-14-work-trust:380` | Alice: Verify Moroka trust ledger balance (:374, PASS) | B-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 97 | `day-07-14-work-trust:389` | Alice: Verify Moroka trust ledger balance (:374, PASS) | C | `page.getByText(/Moroka/i).first()` | strict step on real target, or DELETE if dead (test notes) |
| 98 | `day-07-14-work-trust:397` | Alice: Verify Moroka trust ledger balance (:374, PASS) | C | `page .getByText(/250[\s,.]?000/i)` | strict step on real target, or DELETE if dead (test notes) |
| 99 | `day-07-14-work-trust:415` | Alice: Deposit R45,000 into trust for QuickC (:409, PASS) | B-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 100 | `day-07-14-work-trust:425` | Alice: Deposit R45,000 into trust for QuickC (:409, PASS) | C | `page .getByRole("button", { name: /new.*deposit\|add.*deposit\|record.*deposit/i }) .first()` | strict step on real target, or DELETE if dead (test notes) |
| 101 | `day-07-14-work-trust:435` | Alice: Deposit R45,000 into trust for QuickC (:409, PASS) | C | `page .getByRole("textbox", { name: /amount/i }) .or(page.getByRole("spinbutton", { name: /am…` | strict step on real target, or DELETE if dead (test notes) |
| 102 | `day-07-14-work-trust:441` | Alice: Deposit R45,000 into trust for QuickC (:409, PASS) | C | `page.getByRole("textbox", { name: /description\|reference\|note/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 103 | `day-07-14-work-trust:447` | Alice: Deposit R45,000 into trust for QuickC (:409, PASS) | C | `page.getByRole("button", { name: /save\|create\|submit\|deposit/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 104 | `day-07-14-work-trust:462` | Bob: Conflict check  (:456, PASS) | B-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 105 | `day-07-14-work-trust:470` | Bob: Conflict check  (:456, PASS) | C | `page.locator('input[type="search"], input[type="text"]').first()` | strict step on real target, or DELETE if dead (test notes) |
| 106 | `day-07-14-work-trust:475` | Bob: Conflict check  (:456, PASS) | C | `page.getByRole("button", { name: /search\|check\|run/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 107 | `day-07-14-work-trust:486` | Bob: Conflict check  (:456, PASS) | C | `page .getByText(/match\|conflict\|amber\|red\|found/i)` | strict step on real target, or DELETE if dead (test notes) |
| 108 | `day-07-14-work-trust:502` | Alice: Add adverse party  (:496, PASS) | B-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 109 | `day-07-14-work-trust:510` | Alice: Add adverse party  (:496, PASS) | C | `page.getByRole("button", { name: /add\|new\|create/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 110 | `day-07-14-work-trust:517` | Alice: Add adverse party  (:496, PASS) | C | `page.getByRole("textbox", { name: /name/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 111 | `day-07-14-work-trust:523` | Alice: Add adverse party  (:496, PASS) | C | `page.getByRole("button", { name: /save\|create\|add/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 112 | `day-07-14-work-trust:547` | Alice: Trust dashboard overview (:541, PASS) | B-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 113 | `day-30-billing:40` | Alice: Create fee note for Sipho Ndlovu (hou (:32, FAIL) | B | `page.getByRole("button", { name: /New (Invoice\|Fee Note)/i }).first()` | strict expect + act; delete `test.skip` |
| 114 | `day-30-billing:52` | Alice: Create fee note for Sipho Ndlovu (hou (:32, FAIL) | C | `page.getByRole("combobox", { name: /customer\|client/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 115 | `day-30-billing:57` | Alice: Create fee note for Sipho Ndlovu (hou (:32, FAIL) | C | `page.getByRole("option", { name: /Sipho\|Ndlovu/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 116 | `day-30-billing:65` | Alice: Create fee note for Sipho Ndlovu (hou (:32, FAIL) | C | `page.getByRole("combobox", { name: /project\|matter/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 117 | `day-30-billing:70` | Alice: Create fee note for Sipho Ndlovu (hou (:32, FAIL) | C | `page.getByRole("option", { name: /Ndlovu\|RAF/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 118 | `day-30-billing:78` | Alice: Create fee note for Sipho Ndlovu (hou (:32, FAIL) | C | `page.getByRole("button", { name: /Create\|Save\|Generate/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 119 | `day-30-billing:87` | Alice: Create fee note for Sipho Ndlovu (hou (:32, FAIL) | C | `page.getByRole("button", { name: /add.*line\|new.*line\|add.*item/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 120 | `day-30-billing:94` | Alice: Create fee note for Sipho Ndlovu (hou (:32, FAIL) | C | `page.getByRole("textbox", { name: /description/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 121 | `day-30-billing:100` | Alice: Create fee note for Sipho Ndlovu (hou (:32, FAIL) | C | `page.getByRole("button", { name: /save\|add\|create/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 122 | `day-30-billing:111` | Alice: Create fee note for Sipho Ndlovu (hou (:32, FAIL) | C | `page.getByRole("textbox", { name: /description/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 123 | `day-30-billing:120` | Alice: Create fee note for Sipho Ndlovu (hou (:32, FAIL) | C | `page .getByRole("textbox", { name: /amount/i }) .or(page.getByRole("spinbutton", { name: /am…` | strict step on real target, or DELETE if dead (test notes) |
| 124 | `day-30-billing:126` | Alice: Create fee note for Sipho Ndlovu (hou (:32, FAIL) | C | `page.getByRole("button", { name: /save\|add\|create/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 125 | `day-30-billing:138` | Alice: Create fee note for Sipho Ndlovu (hou (:32, FAIL) | A | `expect .soft( await page .getByText(/tariff\|lssa/i)` | `expect(loc.first()).toBeVisible()` / role locator (test notes) |
| 126 | `day-30-billing:148` | Alice: Create fee note for Sipho Ndlovu (hou (:32, FAIL) | A | `expect .soft( await page .getByText(/15%\|vat/i)` | `expect(loc.first()).toBeVisible()` / role locator (test notes) |
| 127 | `day-30-billing:165` | Alice: Create fee note for Apex Holdings (fi (:159, SKIP/DNR) | B | `page.getByRole("button", { name: /New (Invoice\|Fee Note)/i }).first()` | strict expect + act; delete `test.skip` |
| 128 | `day-30-billing:177` | Alice: Create fee note for Apex Holdings (fi (:159, SKIP/DNR) | C | `page.getByRole("combobox", { name: /customer\|client/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 129 | `day-30-billing:182` | Alice: Create fee note for Apex Holdings (fi (:159, SKIP/DNR) | C | `page.getByRole("option", { name: /Apex/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 130 | `day-30-billing:189` | Alice: Create fee note for Apex Holdings (fi (:159, SKIP/DNR) | C | `page.getByRole("button", { name: /Create\|Save\|Generate/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 131 | `day-30-billing:197` | Alice: Create fee note for Apex Holdings (fi (:159, SKIP/DNR) | C | `page.getByRole("button", { name: /add.*line\|new.*line\|add.*item/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 132 | `day-30-billing:203` | Alice: Create fee note for Apex Holdings (fi (:159, SKIP/DNR) | C | `page.getByRole("textbox", { name: /description/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 133 | `day-30-billing:212` | Alice: Create fee note for Apex Holdings (fi (:159, SKIP/DNR) | C | `page .getByRole("textbox", { name: /amount/i }) .or(page.getByRole("spinbutton", { name: /am…` | strict step on real target, or DELETE if dead (test notes) |
| 134 | `day-30-billing:218` | Alice: Create fee note for Apex Holdings (fi (:159, SKIP/DNR) | C | `page.getByRole("button", { name: /save\|add\|create/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 135 | `day-30-billing:230` | Alice: Create fee note for Apex Holdings (fi (:159, SKIP/DNR) | A | `expect .soft( await page .getByText(/40[\s,.]?250/i)` | `expect(loc.first()).toBeVisible()` / role locator (test notes) |
| 136 | `day-30-billing:243` | Alice: Create trust FEE_TRANSFER for Moroka  (:237, SKIP/DNR) | B-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 137 | `day-30-billing:254` | Alice: Create trust FEE_TRANSFER for Moroka  (:237, SKIP/DNR) | C | `page .getByRole("button", { name: /fee.*transfer\|transfer\|new.*transaction/i }) .first()` | strict step on real target, or DELETE if dead (test notes) |
| 138 | `day-30-billing:264` | Alice: Create trust FEE_TRANSFER for Moroka  (:237, SKIP/DNR) | C | `page .getByRole("textbox", { name: /amount/i }) .or(page.getByRole("spinbutton", { name: /am…` | strict step on real target, or DELETE if dead (test notes) |
| 139 | `day-30-billing:270` | Alice: Create trust FEE_TRANSFER for Moroka  (:237, SKIP/DNR) | C | `page.getByRole("textbox", { name: /description\|reference\|note/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 140 | `day-30-billing:276` | Alice: Create trust FEE_TRANSFER for Moroka  (:237, SKIP/DNR) | C | `page.getByRole("button", { name: /save\|create\|transfer\|submit/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 141 | `day-30-billing:287` | Alice: Create trust FEE_TRANSFER for Moroka  (:237, SKIP/DNR) | C-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 142 | `day-30-billing:294` | Alice: Create trust FEE_TRANSFER for Moroka  (:237, SKIP/DNR) | A | `expect .soft( await page .getByText(/241[\s,.]?500/i)` | `expect(loc.first()).toBeVisible()` / role locator (test notes) |
| 143 | `day-30-billing:313` | Alice: Create fee note for QuickCollect (:307, SKIP/DNR) | C | `page.getByRole("button", { name: /New (Invoice\|Fee Note)/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 144 | `day-30-billing:320` | Alice: Create fee note for QuickCollect (:307, SKIP/DNR) | C | `page.getByRole("combobox", { name: /customer\|client/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 145 | `day-30-billing:325` | Alice: Create fee note for QuickCollect (:307, SKIP/DNR) | C | `page.getByRole("option", { name: /QuickCollect/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 146 | `day-30-billing:332` | Alice: Create fee note for QuickCollect (:307, SKIP/DNR) | C | `page.getByRole("button", { name: /Create\|Save\|Generate/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 147 | `day-30-billing:350` | Alice: Fee note lifecycle — DRAFT to APPROVE (:341, SKIP/DNR) | C | `page .locator('table tbody tr, [data-testid*="invoice-row"], [data-testid*="fee-note-row"]')…` | strict step on real target, or DELETE if dead (test notes) |
| 148 | `day-30-billing:358` | Alice: Fee note lifecycle — DRAFT to APPROVE (:341, SKIP/DNR) | C | `page.getByRole("button", { name: /approve/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 149 | `day-30-billing:365` | Alice: Fee note lifecycle — DRAFT to APPROVE (:341, SKIP/DNR) | C | `page.getByRole("button", { name: /confirm\|yes/i })` | strict step on real target, or DELETE if dead (test notes) |
| 150 | `day-30-billing:374` | Alice: Fee note lifecycle — DRAFT to APPROVE (:341, SKIP/DNR) | C | `page.getByRole("button", { name: /send\|mark.*sent/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 151 | `day-30-billing:380` | Alice: Fee note lifecycle — DRAFT to APPROVE (:341, SKIP/DNR) | C | `page.getByRole("button", { name: /confirm\|yes\|send/i })` | strict step on real target, or DELETE if dead (test notes) |
| 152 | `day-30-billing:392` | Alice: Fee note lifecycle — DRAFT to APPROVE (:341, SKIP/DNR) | A | `expect .soft( await page .getByText(/sent/i)` | `expect(loc.first()).toBeVisible()` / role locator (test notes) |
| 153 | `day-30-billing:414` | Alice: Set fee estimate on Apex matter (R150 (:407, SKIP/DNR) | C | `page.getByRole("link", { name: /Apex/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 154 | `day-30-billing:422` | Alice: Set fee estimate on Apex matter (R150 (:407, SKIP/DNR) | C | `page.getByRole("tab", { name: /budget\|fee.*estimate\|financials/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 155 | `day-30-billing:433` | Alice: Set fee estimate on Apex matter (R150 (:407, SKIP/DNR) | C | `page .getByRole("textbox", { name: /budget\|estimate\|amount/i }) .or(page.getByRole("spinbu…` | strict step on real target, or DELETE if dead (test notes) |
| 156 | `day-30-billing:438` | Alice: Set fee estimate on Apex matter (R150 (:407, SKIP/DNR) | C | `page.getByRole("button", { name: /save\|set\|update/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 157 | `day-45-recon-prescription:31` | Alice: Navigate to Trust Reconciliation page (:25, PASS) | B-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 158 | `day-45-recon-prescription:47` | Alice: Start new bank reconciliation (:41, FAIL) | C-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 159 | `day-45-recon-prescription:54` | Alice: Start new bank reconciliation (:41, FAIL) | B-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 160 | `day-45-recon-prescription:64` | Alice: Start new bank reconciliation (:41, FAIL) | C | `page .getByRole("button", { name: /new.*reconciliation\|start.*reconciliation\|reconcile/i }…` | strict step on real target, or DELETE if dead (test notes) |
| 161 | `day-45-recon-prescription:83` | Alice: Start new bank reconciliation (:41, FAIL) | A | `expect .soft( await page .locator('input[type="file"]') .first()` | `expect(loc.first()).toBeVisible()` / role locator (test notes) |
| 162 | `day-45-recon-prescription:94` | Alice: Start new bank reconciliation (:41, FAIL) | A | `expect .soft( await page .locator('form, [data-testid*="reconciliation"]') .first()` | `expect(loc.first()).toBeVisible()` / role locator (test notes) |
| 163 | `day-45-recon-prescription:101` | Alice: Start new bank reconciliation (:41, FAIL) | C | `page.getByRole("button", { name: /match\|auto.*match\|reconcile/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 164 | `day-45-recon-prescription:112` | Alice: Start new bank reconciliation (:41, FAIL) | A | `expect .soft( await page .getByText(/balanced\|matched\|reconciled/i)` | `expect(loc.first()).toBeVisible()` / role locator (test notes) |
| 165 | `day-45-recon-prescription:134` | Alice: Check prescription tracking for Sipho (:123, SKIP/DNR) | C | `page.getByRole("link", { name: /Ndlovu\|Sipho\|RAF/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 166 | `day-45-recon-prescription:144` | Alice: Check prescription tracking for Sipho (:123, SKIP/DNR) | C | `page .getByRole("tab", { name: /prescription\|deadline\|limitation/i }) .first()` | strict step on real target, or DELETE if dead (test notes) |
| 167 | `day-45-recon-prescription:153` | Alice: Check prescription tracking for Sipho (:123, SKIP/DNR) | C | `page .getByText(/prescription\|3.?year\|personal.?injury\|limitation/i)` | strict step on real target, or DELETE if dead (test notes) |
| 168 | `day-45-recon-prescription:172` | Alice: Court date lifecycle — SCHEDULED to P (:163, SKIP/DNR) | B-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 169 | `day-45-recon-prescription:181` | Alice: Court date lifecycle — SCHEDULED to P (:163, SKIP/DNR) | C | `page.getByText(/Ndlovu\|Motion\|RAF/i).first()` | strict step on real target, or DELETE if dead (test notes) |
| 170 | `day-45-recon-prescription:189` | Alice: Court date lifecycle — SCHEDULED to P (:163, SKIP/DNR) | C | `page.getByRole("button", { name: /postpone\|reschedule/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 171 | `day-45-recon-prescription:196` | Alice: Court date lifecycle — SCHEDULED to P (:163, SKIP/DNR) | C | `page.getByRole("button", { name: /confirm\|yes\|save/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 172 | `day-45-recon-prescription:207` | Alice: Court date lifecycle — SCHEDULED to P (:163, SKIP/DNR) | C | `page .getByRole("button", { name: /new.*date\|add.*date\|schedule/i }) .first()` | strict step on real target, or DELETE if dead (test notes) |
| 173 | `day-45-recon-prescription:213` | Alice: Court date lifecycle — SCHEDULED to P (:163, SKIP/DNR) | C | `page.getByRole("textbox", { name: /title\|name\|description/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 174 | `day-45-recon-prescription:219` | Alice: Court date lifecycle — SCHEDULED to P (:163, SKIP/DNR) | C | `page.getByRole("button", { name: /save\|create\|schedule/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 175 | `day-45-recon-prescription:236` | Alice: Record payment on Apex fee note (R40, (:229, SKIP/DNR) | C | `page.getByText(/Apex/i).first()` | strict step on real target, or DELETE if dead (test notes) |
| 176 | `day-45-recon-prescription:246` | Alice: Record payment on Apex fee note (R40, (:229, SKIP/DNR) | C | `page .getByRole("button", { name: /record.*payment\|mark.*paid\|receive.*payment/i }) .first()` | strict step on real target, or DELETE if dead (test notes) |
| 177 | `day-45-recon-prescription:257` | Alice: Record payment on Apex fee note (R40, (:229, SKIP/DNR) | C | `page .getByRole("textbox", { name: /amount/i }) .or(page.getByRole("spinbutton", { name: /am…` | strict step on real target, or DELETE if dead (test notes) |
| 178 | `day-45-recon-prescription:263` | Alice: Record payment on Apex fee note (R40, (:229, SKIP/DNR) | C | `page.getByRole("button", { name: /confirm\|record\|save/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 179 | `day-45-recon-prescription:275` | Alice: Record payment on Apex fee note (R40, (:229, SKIP/DNR) | A | `expect .soft( await page .getByText(/paid/i)` | `expect(loc.first()).toBeVisible()` / role locator (test notes) |
| 180 | `day-60-interest:31` | Alice: Navigate to Trust Interest page (:25, PASS) | B-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 181 | `day-60-interest:47` | Alice: Run interest calculation with LPFF sp (:41, SKIP/DNR) | B-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 182 | `day-60-interest:58` | Alice: Run interest calculation with LPFF sp (:41, SKIP/DNR) | C | `page .getByRole("button", { name: /run.*interest\|calculate.*interest\|new.*interest/i }) .f…` | strict step on real target, or DELETE if dead (test notes) |
| 183 | `day-60-interest:66` | Alice: Run interest calculation with LPFF sp (:41, SKIP/DNR) | C | `page.locator('input[name*="lpff"], input[name*="rate"]').first()` | strict step on real target, or DELETE if dead (test notes) |
| 184 | `day-60-interest:75` | Alice: Run interest calculation with LPFF sp (:41, SKIP/DNR) | C | `page .getByRole("button", { name: /calculate\|run\|execute\|confirm/i }) .first()` | strict step on real target, or DELETE if dead (test notes) |
| 185 | `day-60-interest:86` | Alice: Run interest calculation with LPFF sp (:41, SKIP/DNR) | A | `expect .soft( await page .getByText(/interest.*credit\|credit/i)` | `expect(loc.first()).toBeVisible()` / role locator (test notes) |
| 186 | `day-60-interest:94` | Alice: Run interest calculation with LPFF sp (:41, SKIP/DNR) | A | `expect .soft( await page .getByText(/lpff\|legal.*practitioners/i)` | `expect(loc.first()).toBeVisible()` / role locator (test notes) |
| 187 | `day-60-interest:114` | Alice: Navigate to Investment Register (:108, PASS) | B-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 188 | `day-60-interest:132` | Alice: Place investment — Moroka R200,000 St (:124, SKIP/DNR) | B-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 189 | `day-60-interest:142` | Alice: Place investment — Moroka R200,000 St (:124, SKIP/DNR) | C | `page .getByRole("button", { name: /new.*investment\|place.*investment\|add.*investment/i }) …` | strict step on real target, or DELETE if dead (test notes) |
| 190 | `day-60-interest:153` | Alice: Place investment — Moroka R200,000 St (:124, SKIP/DNR) | C | `page .getByRole("textbox", { name: /amount/i }) .or(page.getByRole("spinbutton", { name: /am…` | strict step on real target, or DELETE if dead (test notes) |
| 191 | `day-60-interest:160` | Alice: Place investment — Moroka R200,000 St (:124, SKIP/DNR) | C | `page.getByRole("textbox", { name: /bank\|institution/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 192 | `day-60-interest:167` | Alice: Place investment — Moroka R200,000 St (:124, SKIP/DNR) | C | `page.getByRole("combobox", { name: /type/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 193 | `day-60-interest:172` | Alice: Place investment — Moroka R200,000 St (:124, SKIP/DNR) | C | `page.getByRole("option", { name: /fixed.*deposit/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 194 | `day-60-interest:183` | Alice: Place investment — Moroka R200,000 St (:124, SKIP/DNR) | C | `page .getByRole("textbox", { name: /rate\|interest/i }) .or(page.getByRole("spinbutton", { n…` | strict step on real target, or DELETE if dead (test notes) |
| 195 | `day-60-interest:193` | Alice: Place investment — Moroka R200,000 St (:124, SKIP/DNR) | C | `page .getByRole("textbox", { name: /term\|days\|period/i }) .or(page.getByRole("spinbutton",…` | strict step on real target, or DELETE if dead (test notes) |
| 196 | `day-60-interest:202` | Alice: Place investment — Moroka R200,000 St (:124, SKIP/DNR) | C | `page .getByRole("combobox", { name: /basis\|section.*86\|authority/i }) .first()` | strict step on real target, or DELETE if dead (test notes) |
| 197 | `day-60-interest:207` | Alice: Place investment — Moroka R200,000 St (:124, SKIP/DNR) | C | `page.getByRole("option", { name: /firm.*discretion\|86\(3\)/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 198 | `day-60-interest:215` | Alice: Place investment — Moroka R200,000 St (:124, SKIP/DNR) | C | `page.getByRole("combobox", { name: /client\|customer/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 199 | `day-60-interest:220` | Alice: Place investment — Moroka R200,000 St (:124, SKIP/DNR) | C | `page.getByRole("option", { name: /Moroka/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 200 | `day-60-interest:227` | Alice: Place investment — Moroka R200,000 St (:124, SKIP/DNR) | C | `page.getByRole("button", { name: /save\|create\|place/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 201 | `day-60-interest:250` | Alice: Place investment — QuickCollect R30,0 (:242, PASS) | B-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 202 | `day-60-interest:260` | Alice: Place investment — QuickCollect R30,0 (:242, PASS) | C | `page .getByRole("button", { name: /new.*investment\|place.*investment\|add.*investment/i }) …` | strict step on real target, or DELETE if dead (test notes) |
| 203 | `day-60-interest:271` | Alice: Place investment — QuickCollect R30,0 (:242, PASS) | C | `page .getByRole("textbox", { name: /amount/i }) .or(page.getByRole("spinbutton", { name: /am…` | strict step on real target, or DELETE if dead (test notes) |
| 204 | `day-60-interest:277` | Alice: Place investment — QuickCollect R30,0 (:242, PASS) | C | `page.getByRole("textbox", { name: /bank\|institution/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 205 | `day-60-interest:286` | Alice: Place investment — QuickCollect R30,0 (:242, PASS) | C | `page .getByRole("combobox", { name: /basis\|section.*86\|authority/i }) .first()` | strict step on real target, or DELETE if dead (test notes) |
| 206 | `day-60-interest:293` | Alice: Place investment — QuickCollect R30,0 (:242, PASS) | C | `page .getByRole("option", { name: /client.*instruction\|86\(4\)/i }) .first()` | strict step on real target, or DELETE if dead (test notes) |
| 207 | `day-60-interest:301` | Alice: Place investment — QuickCollect R30,0 (:242, PASS) | C | `page.getByRole("combobox", { name: /client\|customer/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 208 | `day-60-interest:306` | Alice: Place investment — QuickCollect R30,0 (:242, PASS) | C | `page.getByRole("option", { name: /QuickCollect/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 209 | `day-60-interest:313` | Alice: Place investment — QuickCollect R30,0 (:242, PASS) | C | `page.getByRole("button", { name: /save\|create\|place/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 210 | `day-60-interest:329` | Alice: Create second billing cycle fee notes (:322, PASS) | C | `page.getByRole("button", { name: /New (Invoice\|Fee Note)/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 211 | `day-60-interest:336` | Alice: Create second billing cycle fee notes (:322, PASS) | C | `page.getByRole("combobox", { name: /customer\|client/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 212 | `day-60-interest:341` | Alice: Create second billing cycle fee notes (:322, PASS) | C | `page.getByRole("option", { name: /Sipho\|Ndlovu/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 213 | `day-60-interest:348` | Alice: Create second billing cycle fee notes (:322, PASS) | C | `page.getByRole("button", { name: /Create\|Save\|Generate/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 214 | `day-60-interest:367` | Alice: Generate time tracking report (:361, PASS) | B-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 215 | `day-60-interest:384` | Alice: Check profitability report (:377, PASS) | B-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 216 | `day-75-complex:38` | Alice: Create 2nd litigation matter for Siph (:29, PASS) | B | `page.getByRole("button", { name: /New (Project\|Matter)/i })` | strict expect + act; delete `test.skip` |
| 217 | `day-75-complex:49` | Alice: Create 2nd litigation matter for Siph (:29, PASS) | C | `page.getByRole("textbox", { name: /name/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 218 | `day-75-complex:56` | Alice: Create 2nd litigation matter for Siph (:29, PASS) | C | `page.getByRole("combobox", { name: /template/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 219 | `day-75-complex:61` | Alice: Create 2nd litigation matter for Siph (:29, PASS) | C | `page.getByRole("option", { name: /litigation/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 220 | `day-75-complex:73` | Alice: Create 2nd litigation matter for Siph (:29, PASS) | C-dialog | `page .getByRole("dialog")` | `expect(dialog).toBeHidden()`; drop Escape |
| 221 | `day-75-complex:88` | Alice: Add adverse parties to registry (RAF, (:82, PASS) | B-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 222 | `day-75-complex:99` | Alice: Add adverse parties to registry (RAF, (:82, PASS) | C | `page.getByRole("button", { name: /add\|new\|create/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 223 | `day-75-complex:106` | Alice: Add adverse parties to registry (RAF, (:82, PASS) | C | `page.getByRole("textbox", { name: /name/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 224 | `day-75-complex:112` | Alice: Add adverse parties to registry (RAF, (:82, PASS) | C | `page.getByRole("button", { name: /save\|create\|add/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 225 | `day-75-complex:128` | Bob: Conflict stress test — search  (:122, SKIP/DNR) | B-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 226 | `day-75-complex:136` | Bob: Conflict stress test — search  (:122, SKIP/DNR) | B | `page.locator('input[type="search"], input[type="text"]').first()` | strict expect + act; delete `test.skip` |
| 227 | `day-75-complex:145` | Bob: Conflict stress test — search  (:122, SKIP/DNR) | C | `page.getByRole("button", { name: /search\|check\|run/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 228 | `day-75-complex:158` | Bob: Conflict stress test — search  (:122, SKIP/DNR) | A | `expect .soft( await page .getByText(/match\|found\|existing.*client\|conflict/i)` | `expect(loc.first()).toBeVisible()` / role locator (test notes) |
| 229 | `day-75-complex:172` | Bob: Conflict stress test — search  (:164, PASS) | B-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 230 | `day-75-complex:180` | Bob: Conflict stress test — search  (:164, PASS) | B | `page.locator('input[type="search"], input[type="text"]').first()` | strict expect + act; delete `test.skip` |
| 231 | `day-75-complex:185` | Bob: Conflict stress test — search  (:164, PASS) | C | `page.getByRole("button", { name: /search\|check\|run/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 232 | `day-75-complex:198` | Bob: Conflict stress test — search  (:164, PASS) | A | `expect .soft( await page .getByText(/adverse\|match\|found\|conflict/i)` | `expect(loc.first()).toBeVisible()` / role locator (test notes) |
| 233 | `day-75-complex:210` | Bob: Conflict stress test — search  (:204, PASS) | B-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 234 | `day-75-complex:218` | Bob: Conflict stress test — search  (:204, PASS) | B | `page.locator('input[type="search"], input[type="text"]').first()` | strict expect + act; delete `test.skip` |
| 235 | `day-75-complex:223` | Bob: Conflict stress test — search  (:204, PASS) | C | `page.getByRole("button", { name: /search\|check\|run/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 236 | `day-75-complex:243` | Alice: Progress Moroka estate — mark action  (:236, PASS) | C | `page.getByRole("link", { name: /Moroka\|Estate/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 237 | `day-75-complex:251` | Alice: Progress Moroka estate — mark action  (:236, PASS) | C | `page.getByRole("tab", { name: /tasks\|action.*items\|checklist/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 238 | `day-75-complex:275` | Alice: Log 360min on Moroka estate matter (:268, PASS) | C | `page.getByRole("link", { name: /Moroka\|Estate/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 239 | `day-75-complex:284` | Alice: Log 360min on Moroka estate matter (:268, PASS) | C | `page .getByRole("button", { name: /Log Time\|New Time\|Add Time\|Time Recording/i }) .first()` | strict step on real target, or DELETE if dead (test notes) |
| 240 | `day-75-complex:292` | Alice: Log 360min on Moroka estate matter (:268, PASS) | C | `page .getByRole("textbox", { name: /description\|notes\|activity/i }) .first()` | strict step on real target, or DELETE if dead (test notes) |
| 241 | `day-75-complex:301` | Alice: Log 360min on Moroka estate matter (:268, PASS) | C | `page .getByRole("textbox", { name: /duration\|hours\|minutes/i }) .or(page.getByRole("spinbu…` | strict step on real target, or DELETE if dead (test notes) |
| 242 | `day-75-complex:307` | Alice: Log 360min on Moroka estate matter (:268, PASS) | C | `page.getByRole("button", { name: /save\|create\|log/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 243 | `day-75-complex:322` | Alice: Create information request for Moroka (:316, SKIP/DNR) | B-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 244 | `day-75-complex:330` | Alice: Create information request for Moroka (:316, SKIP/DNR) | C | `page.getByRole("button", { name: /new.*request\|create.*request/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 245 | `day-75-complex:337` | Alice: Create information request for Moroka (:316, SKIP/DNR) | C | `page.getByRole("textbox", { name: /title\|name\|subject/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 246 | `day-75-complex:343` | Alice: Create information request for Moroka (:316, SKIP/DNR) | C | `page.getByRole("button", { name: /save\|create\|send/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 247 | `day-75-complex:361` | Alice: Create 2nd Commercial matter for Apex (:352, PASS) | C | `page.getByRole("button", { name: /New (Project\|Matter)/i })` | strict step on real target, or DELETE if dead (test notes) |
| 248 | `day-75-complex:368` | Alice: Create 2nd Commercial matter for Apex (:352, PASS) | C | `page.getByRole("textbox", { name: /name/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 249 | `day-75-complex:379` | Alice: Create 2nd Commercial matter for Apex (:352, PASS) | C-dialog | `page .getByRole("dialog")` | `expect(dialog).toBeHidden()`; drop Escape |
| 250 | `day-90-review:86` | Alice: Generate Section 35 Data Pack report (:80, PASS) | B-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 251 | `day-90-review:99` | Alice: Generate Section 35 Data Pack report (:80, PASS) | C | `page .getByRole("button", { name: /section.*35\|data.*pack\|compliance.*report/i }) .first()` | strict step on real target, or DELETE if dead (test notes) |
| 252 | `day-90-review:115` | Alice: Generate Section 35 Data Pack report (:80, PASS) | C | `page.getByRole("link", { name: /section.*35/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 253 | `day-90-review:133` | Alice: Trust reports — Client Trust Balances (:127, PASS) | B-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 254 | `day-90-review:149` | Alice: Trust reports — Investment Register w (:143, PASS) | B-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 255 | `day-90-review:168` | Alice: Trust reports — Receipts & Payments (:162, PASS) | B-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 256 | `day-90-review:187` | Alice: Profitability dashboard — revenue, co (:179, FAIL) | B-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 257 | `day-90-review:201` | Alice: Profitability dashboard — revenue, co (:179, FAIL) | A | `expect .soft( await page .getByText(/revenue\|income\|billing/i)` | `expect(loc.first()).toBeVisible()` / role locator (test notes) |
| 258 | `day-90-review:209` | Alice: Profitability dashboard — revenue, co (:179, FAIL) | A | `expect .soft( await page .getByText(/cost\|expense/i)` | `expect(loc.first()).toBeVisible()` / role locator (test notes) |
| 259 | `day-90-review:217` | Alice: Profitability dashboard — revenue, co (:179, FAIL) | A | `expect .soft( await page .getByText(/margin\|profit/i)` | `expect(loc.first()).toBeVisible()` / role locator (test notes) |
| 260 | `day-90-review:245` | Alice: Generate document for Sipho matter (:238, SKIP/DNR) | C | `page.getByRole("link", { name: /Ndlovu\|Sipho\|RAF/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 261 | `day-90-review:255` | Alice: Generate document for Sipho matter (:238, SKIP/DNR) | C | `page .getByRole("button", { name: /generate.*document\|create.*document\|new.*document/i }) …` | strict step on real target, or DELETE if dead (test notes) |
| 262 | `day-90-review:266` | Alice: Generate document for Sipho matter (:238, SKIP/DNR) | C | `page .getByRole("option", { name: /letter\|document/i }) .or(page.locator('[data-testid*="te…` | strict step on real target, or DELETE if dead (test notes) |
| 263 | `day-90-review:273` | Alice: Generate document for Sipho matter (:238, SKIP/DNR) | C | `page.getByRole("button", { name: /generate\|create\|confirm/i }).first()` | strict step on real target, or DELETE if dead (test notes) |
| 264 | `day-90-review:289` | Alice: Compliance overview page (:283, SKIP/DNR) | B-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 265 | `day-90-review:307` | Alice: Court calendar shows SCHEDULED/POSTPO (:300, SKIP/DNR) | B-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 266 | `day-90-review:339` | Carol: Blocked from rate card settings (:331, PASS) | C-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 267 | `day-90-review:351` | Carol: Blocked from rate card settings (:331, PASS) | A | `page.getByRole("button", { name: /new.*rate\|add.*rate\|edit/i }).first()` | `expect(loc.first()).toBeVisible()` / role locator (test notes) |
| 268 | `day-90-review:364` | Carol: Blocked from trust account configurat (:357, PASS) | C-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 269 | `day-90-review:375` | Carol: Blocked from trust account configurat (:357, PASS) | A | `page.getByRole("button", { name: /save\|create\|edit\|configure/i }).first()` | `expect(loc.first()).toBeVisible()` / role locator (test notes) |
| 270 | `day-90-review:387` | Carol: Blocked from trust deposit approval (:381, PASS) | C-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |
| 271 | `day-90-review:394` | Carol: Blocked from trust deposit approval (:381, PASS) | A | `page .getByRole("button", { name: /approve/i }) .first()` | `expect(loc.first()).toBeVisible()` / role locator (test notes) |
| 272 | `day-90-review:415` | Bob: Has admin access to trust accounting (:409, PASS) | B-main | `page .locator("main")` | DELETE (dead guard: `<main>` always renders) → assert h1 |


### Per-test real-flow notes — legal-lifecycle (the "test notes" the per-site table points at)

These come from three read-only scout passes over the specs and product source. Status = the baseline run. "Vacuous" = passes without performing its titled action. Line refs are product source unless prefixed `spec`. The builder must re-verify each claim in the browser before relying on it (root `CLAUDE.md` §4). Two scout claims were already corrected here: (1) the trust deposit / fee-note client pickers use `listClients({ status: "ACTIVE" })`, but `status` is **ignored by the backend** (`frontend/lib/api/clients.ts:63-64`), so lifecycle-PROSPECT clients are listed; (2) a plain "New Matter" with no client succeeds for any client state, and the baseline Day 1 matter is created **client-less**, which is why Day 1 `:339`/`:365` pass.

**Shared building blocks** (all in `frontend/e2e/helpers/legal-depth-ii.ts`, reuse rather than re-implement):
- `createClient(page, {name,type,email,taxNumber?,idNumber?})` (`:194-225`): fills Name, Type, Email, address line 1 / city / postal code / Country=ZA, Next, "Step 2 of 2", Create Client, then waits for h1 + URL. The address is required by fee-note generation (`backend/…/prerequisite/StructuralPrerequisiteCheck.java:48-52`); without it "New fee note" opens the "Prerequisites: Fee note generation" modal (`CMP/prerequisite/prerequisite-modal.tsx:141,166-175`). COMPANY/TRUST need a Tax Number to activate or send (`:37-46,:65-70`).
- `startClientOnboarding(page)` (`:229-236`): badge "Prospect" → button "Start Onboarding" → alertdialog "Start Onboarding" → badge "Onboarding". Matters can be linked only to non-PROSPECT clients (`CustomerLifecycleGuard.java:19-24`).
- `openClient`, `openMatter` (exact h1 wait), `selectNativeOption`, `clickThrough`, `isoDate`, `escapeRegExp`, `openGroupedTab`.
- Template matter: "New from Template" → dialog `/New from Template/` → `option` `/^Litigation \(Personal Injury/` | `/^Deceased Estate Administration/` | `/^Collections \(Debt Recovery\)/` | `/^Commercial \(Corporate & Contract\)/` → "Next" → `getByLabel(/Matter name/)` → `selectNativeOption(getByLabel(/^Client/), name)` → "Create Matter" → `waitForURL(/\/matters\/[0-9a-f-]{36}/)` + h1 (`$E/legal-depth-ii/day-05-disbursements.spec.ts:115-136`). The plain "New Matter" → "Create Matter" dialog has **no template field** (`CMP/projects/create-project-dialog.tsx:139-323`).
- Matter tab groups (`frontend/lib/constants/matter-tab-groups.ts`): `details` (team, fields…), `overview`, `work` (tasks, documents, correspondence, generated), `finance` label "Fees" (time "Time recording", disbursements, budget "Fee estimate", rates, financials, statements, trust), `client` (clients, requests, customer-comments, adverse-parties), `schedule` "Diary" (court-dates), `activity` (activity, audit). Client groups: `frontend/lib/constants/client-tab-groups.ts` (`finance`/`invoices` = Fee notes; `compliance`/`onboarding`, `compliance`/`requests`).
- Time is logged **per task**. Use the row's "Log Time" button (`CMP/tasks/task-list-table-row.tsx:139-150`) → dialog "Log Time" with `#time-hours`, `#time-minutes`, `#time-date`, `#time-description`, `#time-billable`, submit "Log Time" (`CMP/tasks/log-time-dialog.tsx:174-309`). Pattern: `$E/legal-depth-ii/day-30-45-soa-writeoff.spec.ts:163-186`. Litigation (PI/General) tasks include "Initial consultation & case assessment" and "Letter of demand"; Deceased Estate includes "Report estate to Master of High Court" (`backend/src/main/resources/project-template-packs/legal-za.json:95,103,184`).
- Money format: `formatCurrency` → `Intl.NumberFormat("en-ZA", ZAR)` gives `R\u00a040\u00a0250,00` (NBSP after R and as the thousands separator, comma decimal; `packages/shared/src/format.ts:35-44`). Match with `/40[\s\u00a0\u202f]?250,00/`, as depth-ii does (`day-30-45-soa-writeoff.spec.ts:159`).

**Day 0 — `day-00-firm-setup.spec.ts`** (all PASS except A1 FAIL, :317/:351 DNR)
- `:32` sidebar. `nav, aside, …`.first() is the `<aside>`. Use `getByRole("navigation", { name: "Main navigation" })` (`CMP/desktop-sidebar.tsx:99-100`) and assert links "Matters", "Diary", "Conflict check". The Trust zone is collapsed (`frontend/lib/nav-items.ts:227-231`, `CMP/nav-zone.tsx:69-88`): click `button "Trust"` → link "Overview". Delete the `textContent().catch` and the `test.skip`.
- `:64` brand colour (vacuous-ish, works by luck). `getByLabel("Brand Color")` (the text input `id=brand-color`; the swatch has no label) `.fill("#1B3A4B")` → "Save Settings" → text "Settings saved successfully." (`CMP/settings/general-settings-form.tsx:160,303-344`).
- `:100` billing rate. There is no page-level Add button. Per member: `button "Add rate for Alice Owner"` (`CMP/rates/member-rates-table.tsx:265`) → dialog "Add Rate" (`CMP/rates/add-rate-dialog.tsx:122`) → `getByLabel("Hourly Rate")` → "Create Rate" → hidden → `getByTestId("billing-rate-owner")` contains `2 500,00` (NBSP-tolerant). Spec `:126` `textbox /name|member|user/` is dead. **Billing rates are a precondition for A6 revenue and for fee-note lines** (`null-rate-warning` disables unrated entries, `CMP/invoices/invoice-generation-dialog.tsx:171,604-606`): create rates for Alice, Bob and Carol here. Not idempotent: once a member has a rate the button becomes "Edit billing rate for …" (`:235`). Fine on a fresh stack. For a rerun, use the `.or()` branch.
- `:150` cost rates (vacuous, only clicks the tab). Tab "Cost Rates" → per member "Add rate for …" (cost mode) → `getByLabel("Hourly Cost")` 1000/500/200 → "Create Rate"; verify `cost-rate-owner|admin|member` (`member-rates-table.tsx:153-157,307,383-397`).
- `:178` VAT 15%: already seeded as "VAT — Standard" 15.00% default. Re-creating "VAT" works once and then 409s on rerun (`TaxRateService.java:230-234`). **Recommend** asserting `getByTestId("tax-rate-row").filter({ hasText: "VAT — Standard" })` contains "15.00%" and "Default" (`APP/settings/fees/_tabs/tax-rate-table.tsx:148-170`). Re-title (OD-10).
- `:234` custom fields (vacuous, `continue`-only). `getByTestId("field-group-legal_za_matter")` + `getByTestId("field-row").filter({ hasText: "Case Number" | "Court" | "Opposing Party" })` (`APP/settings/matters/_tabs/custom-fields-content.tsx:128-130,247`). `matter_type` and `court_name` do not exist.
- `:258` "4 matter templates" (vacuous). The pack has 6. Assert each name `getByText(name, { exact: true })` (`CMP/templates/TemplateList.tsx:114`). Re-title.
- `:282` **A1**, see the A1 section.
- `:317` modules (vacuous): same nav assertions as `:32`, plus optional `goto` + h1 per route.
- `:351` screenshot only; no change.

**Day 1–3 — `day-01-03-onboarding.spec.ts`** (all PASS in baseline; most vacuous)
- `:37`/`:423`/`:498`/`:642` conflict checks (vacuous). Spec `:60` `/search|check|run/` `.first()` hits the sidebar "Search, Command K" button. `input[type=text]` never matches (the shared `Input` has no `type`, `packages/ui/src/input.tsx:5-8`). Real: `getByLabel(/Name to Check/)` → button "Run Conflict Check" → `getByTestId("conflict-check-result")` heading "No Conflict" (`CMP/legal/conflict-check-form.tsx:120-122,237-239`; `conflict-check-result.tsx:21-30,73,88-90`). Fuzzy matching (`ConflictCheckService.java:30-31`) means "No Conflict" is only guaranteed on a fresh stack (and only before Day 2 creates more clients; check order).
- `:86`/`:445`/`:520`/`:664` create clients. Use `createClient` (address included). Types: Sipho Individual, Apex Company (+ taxNumber), Moroka Trust (+ taxNumber; Type select option "Trust"; `NewClient.type` is typed `"Individual" | "Company"`, so widen it), QuickCollect Company (+ taxNumber). Entity Type is a native select **without** a name attr (`CMP/customers/create-customer-dialog.tsx:613-625`). Spec `:122-128` is dead. `selectOption({ label: RegExp })` is invalid.
- `:167` Transition to ONBOARDING (vacuous: "Change Status" is in `CMP/compliance/LifecycleTransitionDropdown.tsx:140`, which nothing imports). → `openClient` + `startClientOnboarding`. Do the same for Apex, Moroka and QuickCollect (needed before their matters can be linked).
- `:199` FICA checklist → see OD-7 item 1. Spec `:212` `tab /checklist|fica|onboarding/` hits group trigger "FICA & compliance". Spec `:220` checkboxes do not exist.
- `:244` Litigation matter (creates a client-less, untemplated matter today). → template flow `/^Litigation \(Road Accident Fund/` or `/^Litigation \(Personal Injury/` (pick one; depth-ii uses PI/General) with Client = Sipho. Record the name in run state.
- `:339`, `:365`: already strict. They keep working once `:244` creates `siphoMatter()`. Switch `Ndlovu v RAF ${RUN_ID}` to the run-state builder.
- `:567` Moroka estate: template `/^Deceased Estate Administration/` + Client Moroka (needs ONBOARDING). Spec `:592` template combobox is dead.
- `:711` QuickCollect collections: template `/^Collections \(Debt Recovery\)/` + Client QC.
- `:756` 4 clients (vacuous): `getByRole("link", { name: <runName>, exact: true })` for each of the four.

**Day 7–14 — `day-07-14-work-trust.spec.ts`** (PASS except `:289` SKIP)
- `:28`/`:258`/`:532` My Work: assert h1 "My Work" (`APP/my-work/my-work-header.tsx:25`). `:532` "Log additional time entries" only loads the page: either add a real Log Time (Carol, on Sipho's "Initial consultation…" task) or re-title.
- `:35` Carol 90 min, `:96` Bob 120 min, `:219` Alice 60 min Moroka. All vacuous: there is no matter-level Log Time button. → `openMatter(siphoMatter())` → `openGroupedTab("work","tasks")` → row "Log Time" → hours/minutes. Carol must be on the matter team (Day 1 `:339` adds her; Org Members see only their matters). The `/Ndlovu|Sipho|RAF/` `.first()` lookups are the 619E CodeRabbit item → `openMatter(siphoMatter())`.
- `:134` Bob comment (vacuous). Internal comments live on the task detail sheet: button `Open task detail for <title>` (`CMP/tasks/task-list-table-row.tsx:75-79`) → tab "Comments" (`CMP/tasks/task-detail-sheet.tsx:345`) → textarea "Add a comment" → Visibility "Internal only" → "Post Comment" (`CMP/comments/add-comment-form.tsx:101-143`).
- `:175` court date (vacuous; Matter and Date never set). → `/diary` → button "New court date" (`APP/diary/diary-page-client.tsx:406-409`; use the name, not the shared testid) → dialog "Schedule Court Date": Matter (native select), Type "Motion", Date `isoDate(30)`, Court Name (required) → submit "Schedule Court Date" (`CMP/legal/create-court-date-dialog.tsx:168,360`; schema `frontend/lib/schemas/legal.ts:3-26`). Known pre-existing backend 500 when the matter has no client (628B follow-up `CourtCalendarService.toResponse:513`), so the matter must be client-linked (Day 1 fix).
- `:273`/`:541` trust overview: h1 "Trust Accounting" (`APP/trust-accounting/page.tsx:154-156`). With an account, links "Client Ledgers" / "Record Transaction" and `summary-cards`.
- `:289` deposit R250,000 Moroka (SKIP at spec `:347`: no account; the real control is a menuitem). → `getByTestId("record-transaction-button")` → menuitem "Record Deposit" → dialog "Record Deposit": client combobox `trust-deposit-customer-trigger` (option `^<name>(\s|$)`), matter `trust-deposit-matter-trigger`, "Amount", "Reference" (**required**), "Description (Optional)" → "Record Deposit" → row with the reference contains "RECORDED" (`CMP/trust/transaction-actions.tsx:40-51`; `CMP/trust/RecordDepositDialog.tsx:113-240`; pattern `day-05-disbursements.spec.ts:220-237`).
- `:352` approve deposit: dead (OD-7 item 4).
- `:374` Moroka ledger: `/trust-accounting/client-ledgers` → row `ledger-row-{clientId}` link → `client-summary` "Trust Balance" contains `250 000,00` (`APP/trust-accounting/client-ledgers/page.tsx:149,229-238`; `[clientId]/page.tsx:229-235`).
- `:409` deposit R45,000 QuickCollect: same as `:289` (the spec never selects a client today).
- `:456` conflict "Mokoena": no "Mokoena" adverse party exists until Day 75 `:82`. Either add Mokoena in `:496` first (re-order: add parties, then check) or expect "No Conflict". Asserting a match needs data that exists.
- `:496` add adverse party RAF: `/legal/adverse-parties` → `create-adverse-party-trigger` "Add Party" → dialog `adverse-party-dialog` "Add Adverse Party": `getByLabel(/^Name/)`, "Party Type *" native select (RAF → a non-natural type; option labels at `CMP/legal/adverse-party-dialog.tsx:40-63`) → "Create Party" → row in `adverse-party-table` (`:124-256`; `adverse-party-registry-client.tsx:144,189`). Duplicates by name are allowed (`AdversePartyService.java:223-232`), so use run-scoped names or assert by row count delta.

**Day 30 — `day-30-billing.spec.ts`** (A2 FAIL; rest DNR)
- `:32` A2 and `:159` A3: see the A2/A3 section.
- `:237` FEE_TRANSFER Moroka R8,500. Needs a Moroka fee note in APPROVED/SENT first (and Moroka unbilled time from Day 14 `:219`). Flow:
  - "Record Transaction" → menuitem "Record Fee Transfer" → dialog "Record Fee Transfer": "Client ID" (UUID), "Fee note ID" (UUID), "Amount", "Reference" → `dialog.getByRole("button", { name: "Record Fee Transfer" })` → row "AWAITING APPROVAL" (`CMP/trust/RecordFeeTransferDialog.tsx:85-176`; `APP/trust-accounting/transactions/page.tsx:372,402-404`).
  - Then Bob → `approval-actions-{id}` → "Approve" (`page.tsx:416-424`; `CMP/trust/approval-badge.tsx:102`). Self-approval is blocked (`TrustTransactionService.java:787`).
  - The `241 500` balance assertion follows from 250 000 − 8 500.
- `:307` QuickCollect fee note: needs a QC matter linked to the client with unbilled billable time (Day 14 add a QC time entry) or an approved disbursement. "Validate & Create Draft" is disabled at 0 items (`invoice-generation-dialog.tsx:306,314`).
- `:341` DRAFT→APPROVED→SENT: open the fee note by **href** (row `<tr>` clicks do nothing; link text is the number or "Draft", `APP/invoices/page.tsx:~302-312`). Then "Approve" (`CMP/invoices/invoice-header-actions.tsx:70-74`) → "Send fee note" (`:82-85`; depth-ii uses `"Send Fee Note"`, which matches case-insensitively). There are **no confirm dialogs**; spec `:364-384` is dead. On a validation warning the inline panel `send-override-dialog` → "Send Anyway" (`invoice-status-sections.tsx:53,78-80`). Assert `main.getByText("Sent", { exact: true })`.
- `:407` fee estimate Apex R150,000: matter `openGroupedTab("finance","budget")` → empty state button "Set fee estimate" → dialog "Set fee estimate" → `getByLabel("Fee estimate amount")` → "Save fee estimate" → heading "Fee estimate status" (`CMP/budget/budget-panel.tsx:87-120`; `budget-config-dialog.tsx:116-202`). Needs an Apex matter: create one in the A3 setup (OD-5 `apexMatter()`).

**Day 45 — `day-45-recon-prescription.spec.ts`** (A4 FAIL; rest DNR)
- `:25`: h1 "Reconciliation" (`APP/trust-accounting/reconciliation/page.tsx:99-101`).
- `:41` **A4**, see the A4 section.
- `:123` prescription (vacuous). There is no prescription tab and no tracker exists. Create one: `openGroupedTab("schedule","court-dates")` → `prescription-tracker-list` → button "New prescription tracker" → dialog "Create Prescription Tracker": Matter, "Cause of Action Date", Prescription Type "Delict (3 years)" → "Create Tracker" → row `prescription-tracker-row-*` (`CMP/legal/prescription-tracker-list.tsx:87-130`; `create-prescription-dialog.tsx:46-52,171,302`).
- `:163` court-date lifecycle: matter Diary tab → row actions (sr-only "Actions", `CMP/legal/court-date-list-view.tsx:200-209`) → menuitem "Postpone" → dialog `postpone-dialog` "Postpone Court Date": "New Date", "Reason" → "Postpone" (`CMP/legal/postpone-dialog.tsx:87-139`) → badges "Postponed" + a new "Scheduled" row (OD-7 item 5).
- `:229` record payment Apex R40,250 → PAID. Fee note must be SENT. Header "Record Payment" (`invoice-header-actions.tsx:91-94`) → **inline** form `record-payment-form` (Amount defaults to balance due, `payment-method-select`) → "Confirm Payment" → status "Paid" (`CMP/invoices/invoice-status-sections.tsx:91-232`).

**Day 60 — `day-60-interest.spec.ts`** (PASS/SKIP; `:41` FAILED in the default-config run on a populated stack)
- `:41` interest run. Needs the account + an LPFF rate **effective on or before the period start** (`InterestService.java:143-150`: "No LPFF rate configured"); overlapping periods 409 (`:137-141`). So A1's "Effective From" must be ≤ Day 60's Period Start. Flow:
  1. "New Interest Run" (`APP/trust-accounting/interest/InterestPageClient.tsx:32`) → `interest-run-wizard`: "Period Start"/"Period End" → "Create Run".
  2. "Calculate Interest" → `allocations-table` → "Approve".
  3. "Approve Run" → "Post to Ledger" (`CMP/trust/InterestRunWizard.tsx:173-438`).
- `:124`/`:242` Place Investment (`:124` SKIP; `:242` vacuous): "Place Investment" → dialog `place-investment-dialog`: "Client ID" (UUID), "Institution", "Account Number", "Principal", "Interest Rate %", "Maturity Date (optional)", radios "Firm (surplus trust funds)" s86(3) / "Client instruction" s86(4) → "Place Investment" (`CMP/trust/PlaceInvestmentDialog.tsx:106-304`). The client balance must cover the principal (`TrustInvestmentService.java:129`). Spec fields amount/type/term/basis-combobox/client-combobox are dead.
- `:322` second-cycle fee notes: real path as A2. Needs unbilled time logged after Day 30 (add a Log Time step first).
- `:361` time report (vacuous): `/reports` → card "Timesheet Report" (`/reports/timesheet`) → "Run Report" (`CMP/reports/report-parameter-form.tsx:143`).
- `:377` profitability: h1 + `profitability-cost-note` (`APP/reports/profitability/page.tsx:56-66`).

**Day 75 — `day-75-complex.spec.ts`** (PASS/SKIP)
- `:29` 2nd Sipho litigation: template flow + Client Sipho (vacuous today: client-less).
- `:82` adverse parties RAF/Mokoena/Pillay: the `:496` flow ×3.
- `:122` (SKIP at spec `:135-140` `input[type=search|text]`), `:164`, `:204` (vacuous `return`) conflict searches: real form. "Ndlovu" vs "Sipho Ndlovu <id>" is likely "Potential Conflict" (`ConflictCheckService.java:30-31,260-269`); assert `/Potential Conflict|Conflict Found/` in `conflict-check-result`. Verify on repro.
- `:236` mark Moroka tasks DONE: there are no checkboxes. Row buttons: "Claim" (OPEN, unassigned) → "Done" (IN_PROGRESS) (`CMP/tasks/task-list-table-row.tsx:60-67,154-187`). Template tasks assigned at creation stay OPEN without Claim; fallback is the detail-sheet status select (`task-status-select.tsx:13-24`). Needs the templated Moroka matter.
- `:268` 360 min Moroka: Log Time on a task row.
- `:316` info request (SKIP: `/information-requests` has no index page → Next 404 → the `<main>` guard). → client `openGroupedTab("compliance","requests")` (or matter `client`/`requests` when client-linked) → "New Request" → dialog "Create Information Request": "Add Item" (`add-ad-hoc-item-button`), `ad-hoc-item-name-0` → "Send Now" (`create-request-send-now`) or "Save as Draft" (`CMP/information-requests/create-request-dialog.tsx:221-497`). A portal contact is auto-created from the client email (`PortalContactAutoProvisioner.java:14-16`).
- `:352` Apex commercial matter: template `/^Commercial \(Corporate & Contract\)/` + Client Apex (ONBOARDING).

**Day 90 — `day-90-review.spec.ts`** (A6 FAIL; `:227-300` DNR; RBAC PASS)
- `:29` clients: `getByTestId("clients-table")` + run-name links (`CMP/clients/clients-table.tsx:253`). `:46` matters: `matters-table` + run-name links.
- `:63` **A5**, see the A5 section.
- `:80` Section 35: `/trust-accounting/reports` → `getByTestId("report-card-section-35-data-pack")` (a link card) → `/reports/section-35-data-pack` h1 → "Trust Account" picker + "Financial Year End" → "Run Report" → "Export CSV" enabled (`APP/trust-accounting/reports/page.tsx:46-96`; `CMP/reports/report-runner.tsx:108-125`).
- `:127`/`:143`/`:162` trust reports: testids `client-ledgers-page`, `investments-page` (+ `investment-basis-filter`), h1 "Transactions".
- `:179` **A6**, see the A6 section.
- `:227` dashboard: `getByTestId("home-page")` `data-audience="owner"` + cells `home-cell-*` (`APP/dashboard/page.tsx:163`; `frontend/lib/dashboard-home.ts:60-108`).
- `:238` generate document: `/Generate Document/` → **menuitem** (template, e.g. "Engagement Letter — Litigation") → dialog "Generate: …" → optional "Next" → "Save to Documents" → "Document saved successfully" (`CMP/templates/GenerateDocumentDropdown.tsx:83-129`; `GenerateDocumentDialog.tsx:253-365`; pattern `$E/legal-depth-ii/day-14-conveyancing.spec.ts:303-329`).
- `:283` compliance: h1 "Compliance" + tab "Overview".
- `:300` court calendar SCHEDULED/POSTPONED/HEARD: the Diary list renders no status. `/court/i` matches the sr-only "Court date". Assert badges on the matter Diary tab `court-date-list` (`CMP/legal/court-date-list-view.tsx:28-43`). "Heard" needs a "Record outcome" step. Either add one, or assert only Scheduled + Postponed and re-title.
- `:331` Carol rates: h1 "Rates & Currency" + denial text "You do not have permission to manage rates and currency settings. Only admins and owners can access this page." (`APP/settings/fees/_tabs/tariffs.tsx:15-31`).
- `:357` Carol trust config: `getByRole("status")` "You don't have access to Trust accounting — showing Rates instead." (`CMP/settings/settings-family-page.tsx:104-110`).
- `:381` Carol approval: `record-transaction-button` and `[data-testid^="approval-actions-"]` `toHaveCount(0)`. Replace the boolean `expect(canApprove).toBe(false)` (spec `:394`) with `toHaveCount(0)`: negative checks use `toHaveCount(0)` / `not.toBeVisible()`, never `isVisible() === false`.
- `:401`/`:409`/`:426` Bob/Alice access: h1 `/Rates & Currency/`, h1 "Trust Accounting", `trust-settings-page`.

### Other skipped / fixme tests in the two legal configs

| Config | Test | Kind | In 628E scope? |
|---|---|---|---|
| lifecycle | `day-07-14:289` Deposit R250,000 | runtime `test.skip` (spec `:347`) | **Yes**, the strict sweep removes it (real deposit flow) |
| lifecycle | `day-60:41` interest run | runtime skip (spec `:103`) | **Yes** (needs OD-4 account + A1 rate) |
| lifecycle | `day-60:124` Moroka investment | runtime skip (spec `:237`) | **Yes** |
| lifecycle | `day-75:122` conflict "Ndlovu" | runtime skip (spec `:135-140`) | **Yes** |
| lifecycle | `day-75:316` info request | runtime skip (404 → `<main>` guard) | **Yes** |
| lifecycle | 14 did-not-run (`day-00:317,:351`; `day-30:159-407`; `day-45:123-229`; `day-90:227-300`) | serial abort after A1/A2/A4/A6 | **Yes**, they run once the carried fails are real |
| depth-ii | `day-14-conveyancing:140` FICA → ACTIVE | `test.fixme` (product bug `depth-ii:checklist-stale-after-complete`) | Not U14/U10/U17/U18. Re-probe per OD-7; un-fixme only on 5/5 green |
| depth-ii | `day-30-45-soa-writeoff:292` Statements Download | `test.fixme` (e2e env: S3 presigner host unreachable from the container) | **No**, environment limitation |
| depth-ii | `null-prefetch-probe:71` ×3 | opt-in `test.skip(!NULL_PREFETCH_PROBE)` | **No** |


## Carried-six disposition table

The "carried six" are R10 §A A1–A6 (the 611F row listed seven locations; the seventh, depth-ii `day-05:51`, is green today: depth-ii 38/0/5). Status is the baseline at 2b442c0b8.

| ID | Spec file:line (now) | Baseline failure | R10 verdict (§A) | Planned resolution | Scenario wording / §5? |
|---|---|---|---|---|---|
| A1 | `day-00-firm-setup.spec.ts:282` (fails `:304`; spec cites `:326`) | **FAIL**: `locator.fill: Element is not an <input>` (resolved to `<div data-testid="lpff-rates-section">`) | LOCATOR/FLOW DRIFT ("spec-only fix … no scenario doc change needed") | **Real flow.** Day 0 creates or asserts the trust account (OD-4) → `/trust-accounting/interest` "Add Rate" → dialog "Add LPFF Rate" 6.5 % → `lpff-rates-table` row "6.50%" → settings tab `lpff-rates-section` shows it | None. D13 covers the spec change; scenario 0.62/0.63 already say "create trust account", "LPFF 6.5%" |
| A2 | `day-30-billing.spec.ts:32` (soft asserts `:138`/`:148`) | **FAIL**: both soft asserts `false` | FLOW DRIFT (+M1 strict mode). Secondary: "LSSA auto-populated" is a product expectation (scenario wording) | **Real flow**: client Fee notes tab → Generate fee note → Fetch Unbilled Time → Validate & Create Draft → Create Draft → open draft by new href → Add Tariff Items → Add Line (disbursement) or an approved disbursement → assert tariff row + VAT row | Spec title/comment "auto-population" corrected in-spec (OD-10). No §5 row exists for that wording, so no scenario doc change in 628E |
| A3 | `day-30-billing.spec.ts:159` (soft assert `:230`) | **DID NOT RUN** (serial, after A2); historically `false` at `:233` | FLOW DRIFT + currency-format regex | **Real flow + precondition**: an Apex matter (client-linked) with one unbilled item, so Create Draft is enabled → draft → Add Line "Fixed fee…" Qty 1 × Unit Price 35000, Tax Rate default "VAT — Standard (15%)" → total `/40[\s\u00a0\u202f]?250,00/`. If the precondition is an approved disbursement, delete that line ("Delete {desc}") before asserting 40 250 | None |
| A4 | `day-45-recon-prescription.spec.ts:41` (soft asserts `:83`/`:94`/`:112`) | **FAIL**: three soft asserts `false` | FLOW DRIFT; possible SEED GAP (trust account) | **Real flow** steps 1→2 of the 5-step wizard; assert the upload step; CSV upload / auto-match NOT added (OD-7 item 6) | None |
| A5 | `day-90-review.spec.ts:63` (`.count()` at `:73`) | **PASS — vacuous**: `[data-testid*="invoice"]` matches a non-row element even with zero fee notes | UPSTREAM/SEED GAP (downstream of A2/A3) | **Locator amendment**: count real fee-note rows (`main` table rows holding `a[href*="/invoices/"]`), expect ≥2 from A2/A3/QuickCollect. Green on 628E's own data (OD-8); the 628F seed is additive | None |
| A6 | `day-90-review.spec.ts:179` (soft asserts `:201`/`:209`/`:217`) | **FAIL**: three soft asserts `false` (strict mode) | LOCATOR DRIFT (strict mode), not a product bug | **Locator amendment** + data precondition: `expect(page).toHaveURL(/\/reports\/profitability/)`, h1 "Profitability", `getByRole("columnheader", { name: "Revenue", exact: true }).first()`, same for "Cost", "Margin". Requires this month's billable time with rates (Day 0 rates + Day 7/75 time); otherwise the EmptyState renders | None |

## A1–A6 flow specs

### A1 — LPFF rate (`day-00-firm-setup.spec.ts:282`)

**Current spec code** (`:280-313`):
```ts

  // ── 0.19-0.21: Trust account creation, LPFF rate ─────────────────
  test("Alice: Trust account settings page loads", async ({ page }) => {
    await loginAs(page, "alice");
    // Phase 87 S9 (ADR-344): /settings/trust-accounting → /settings/fees?tab=trust-accounting
    await page.goto(`${BASE}/settings/fees?tab=trust-accounting`);
    await expect(page.locator("main")).toBeVisible({ timeout: 10_000 });

    // Try to create a trust account if the UI has a create button
    const createBtn = page
      .getByRole("button", { name: /create.*trust|new.*trust|add.*account/i })
      .first();
    const hasCreate = await createBtn.isVisible({ timeout: 3000 }).catch(() => false);

    if (hasCreate) {
      await createBtn.click();
      await page.waitForTimeout(1000);
    }

    // Set LPFF rate 6.5% if the field is available
    const lpffField = page
      .locator('input[name*="lpff"], input[name*="interest"], [data-testid*="lpff"]')
      .first();
    const hasLpff = await lpffField.isVisible({ timeout: 3000 }).catch(() => false);

    if (hasLpff) {
      await lpffField.fill("6.5");
      const saveBtn = page.getByRole("button", { name: /save/i }).first();
      const hasSave = await saveBtn.isVisible({ timeout: 3000 }).catch(() => false);
      if (hasSave) {
        await saveBtn.click();
        await page.waitForTimeout(1000);
      }
    }
```

**Product UI facts**
- Settings tab `/settings/fees?tab=trust-accounting` (admin/owner): root `data-testid="trust-settings-page"`, h1 "Trust Accounting Settings", card `trust-accounts-section` with `<AddTrustAccountButton />`, and a **read-only** card `lpff-rates-section` titled "LPFF Rates" (`APP/settings/fees/_tabs/trust-accounting.tsx:53-80`, `:163-230`). There is no input. The spec's `[data-testid*="lpff"]` resolves to the card div, hence the `.fill` failure.
- Trust account dialog: button "Add Account" (`CMP/trust/AddTrustAccountButton.tsx:15`) → dialog "Add Trust Account" (`CMP/trust/CreateTrustAccountDialog.tsx:125`):
  - fields "Account Name" `:143`, "Bank Name" `:158`, "Branch Code" `:172`, "Account Number" `:187`;
  - "Account Type" Radix Select (General / Section 86) `:199-212`, default GENERAL `:56`;
  - "Opened Date" `:225`;
  - switch "Set as primary trust account" default on `:58,:237-251`;
  - "Require dual approval" `:270`;
  - submit "Create Account".
  - Proven sequence: `$E/legal-depth-ii/day-05-disbursements.spec.ts:203-217`.
- Interest page `/trust-accounting/interest`: `data-testid="interest-page"`, h1 "Interest" (`APP/trust-accounting/interest/page.tsx:83-87`).
  - Only with a primary account (`:54-65`) does it render "Interest Runs" (button "New Interest Run", `:116-123`) and `data-testid="lpff-rates-section"` "LPFF Rate History" (`:194-207`).
  - With `MANAGE_TRUST` there is an outline button **"Add Rate"** (`InterestPageClient.tsx:45-49`).
  - Empty state `empty-rates` "No LPFF rates configured. Add a rate to enable interest calculations." (`:210-215`).
  - Table `lpff-rates-table` with headers "Effective From", "Rate %", "LPFF Share %"; rows `rate-row-{id}`; the rate renders `(ratePercent*100).toFixed(2)%` (`:217-249`).
- Dialog `data-testid="lpff-rate-dialog"`, title "Add LPFF Rate" (`CMP/trust/LpffRateDialog.tsx:88-90`):
  - labels "Effective From" (date, default today `:36-45,103`), "Rate %" (number, placeholder "e.g. 8.5", `:117-123`), "LPFF Share %" (number, "e.g. 50", `:138-145`), "Notes (optional)" `:160`;
  - submit **"Add Rate"** (same name as the opener; scope to the dialog), "Cancel";
  - closes on success and refreshes (`:68-75`).
  - The action divides by 100 (`APP/trust-accounting/interest/actions.ts:85-86`), so 6.5 → 0.065 → "6.50%". Schema `ratePercent > 0` (`frontend/lib/schemas/trust.ts:100-101`).

**Proposed new steps**
1. Day 0 (a new test placed before A1, or the first half of A1). `goto /trust-accounting`; `await expect(addAccount.or(recordTransaction).first()).toBeVisible()`; if `addAccount` is visible, create "Mathebula & Partners Trust Account" / "Standard Bank" / "051001" / "0123456789" (OD-4); then `expect(recordTransaction).toBeVisible()`.
2. A1. `goto /settings/fees?tab=trust-accounting` → `expect(getByTestId("trust-settings-page")).toBeVisible()` and `expect(getByTestId("lpff-rates-section")).toBeVisible()`.
3. `goto /trust-accounting/interest` → `const section = page.getByTestId("lpff-rates-section")` → `section.getByRole("button", { name: "Add Rate" }).click()`.
4. `const dlg = page.getByTestId("lpff-rate-dialog")` → `dlg.getByLabel("Effective From").fill(<first day of current month, or earlier>)`. This date must be ≤ the Day 60 interest-run Period Start (`InterestService.java:143-150`).
5. `dlg.getByLabel("Rate %").fill("6.5")`, `dlg.getByLabel("LPFF Share %").fill(<scenario value; R10 gives none, so use e.g. "50" and state it>)`.
6. `dlg.getByRole("button", { name: "Add Rate" }).click()` → `expect(dlg).toBeHidden()`.
7. `expect(section.getByTestId("lpff-rates-table")).toContainText("6.50%")`.
8. Back on the settings tab, assert the read-only `lpff-rates-section` (`APP/settings/fees/_tabs/trust-accounting.tsx:163`) contains "6.50%" (cell `(rate.ratePercent * 100).toFixed(2)%`, `:213`); empty text before is "No LPFF rates configured. Add a rate to start calculating interest distributions." (`:182`).

### A2 — Sipho fee note (`day-30-billing.spec.ts:32`)

**Current spec code**: `:32-156` (quoted in full under "Reference Patterns › Current A2 spec", too long to repeat here).

**Product UI facts** (verified file:line):
- `/invoices` h1 "Fee notes"; button **"New fee note"** (`CMP/invoices/create-invoice-button.tsx:41-44`) is a cmdk Popover: input placeholder "Search clients...", options `role=option` (`:48-58`). Selecting one does `router.push('/org/{slug}/clients/{id}?tab=invoices')` (`:31-34`).
- Client Fee notes tab (`CMP/customers/customer-invoices-tab.tsx`): h2 "Fee notes" (`:36`); `tabpanel` button **"New fee note"** (`CMP/invoices/invoice-generation-dialog.tsx:74-81`), shown for admin + record status ACTIVE (`APP/clients/[id]/page.tsx:687-698`). Explicit route: `openClient` + `openGroupedTab(page, "finance", "invoices")` (depth-ii `day-75-85-closure.spec.ts:178`).
- Dialog title **"Generate fee note"** (step 1) / **"Select Unbilled Items"** (step 2) (`invoice-generation-dialog.tsx:87`):
  - labels "From Date" `#invoice-from-date`, "To Date" `#invoice-to-date`, "Currency" `#invoice-currency` (`:101-128`);
  - button **"Fetch Unbilled Time"** (`:143-149`);
  - step 2: matter checkbox aria-label "Select all entries for {matterName}" (`:370-377`); entries pre-selected (`CMP/invoices/use-invoice-generation.ts:104-120`); unrated entries disabled + `null-rate-warning` (`:171,:604-606`); approved disbursements in `disbursement-selection-section` (`:536`);
  - footer **"Validate & Create Draft"** (`:302-309`) → **"Create Draft"** / "Create Draft (N issues)" (`:311-321`, use `/^Create Draft/`); both disabled at 0 items (`:306,:314`).
- After Create Draft the dialog closes with **no navigation**. The new row's link text is "Draft" → `/invoices/{uuid}` (`customer-invoices-tab.tsx:96-101`). Find it by href diff (pattern `day-75-85-closure.spec.ts:182-215`).
- Draft detail: h1 **"Draft fee note"** (`CMP/invoices/invoice-header-actions.tsx:47-48`).
  - Button **"Add Line"** (`CMP/invoices/invoice-line-table.tsx:207-211`) → inline h3 **"Add Line Item"** (`CMP/invoices/invoice-line-editor.tsx:46`):
    - description: `getByPlaceholder("Line item description")` (`:49-58`; the label is not linked);
    - Quantity and Unit Price are unlabeled `input[type=number]` #1/#2 (`:61-82`);
    - Tax Rate is a Radix Select defaulting to the org default (`:84-95,:225-238`; option text `"${name} (${rate}%)"` = "VAT — Standard (15%)");
    - submit **"Add"**, disabled while the description is empty (`:97-104`).
  - Per-line "Edit {description}" / "Delete {description}" (`invoice-line-table.tsx:160,170`).
  - **"Add Tariff Items"** (`CMP/invoices/invoice-detail-client.tsx:165-185`, ModuleGate `lssa_tariff`) → `tariff-line-dialog` "Add Tariff Items" (`CMP/legal/tariff-line-dialog.tsx:151-154`):
    - search `tariff-item-search` placeholder "Search items..." (`CMP/legal/tariff-item-browser.tsx:117-122`);
    - items `tariff-item-{uuid}` (`:169`; beware `[data-testid^="tariff-item-"]` also matches `tariff-item-browser`/`tariff-item-search`);
    - qty `qty-input-{id}`;
    - submit **"Add to fee note"** `add-to-invoice-btn` (`tariff-line-dialog.tsx:254-260`).
    - The seed has no "Letter of Demand" item. Use e.g. "Instructions to sue or defend" (`backend/src/main/resources/tariff-seed/lssa-2024-2025-hc-pp.json`; verify the exact description).
  - "Add Disbursements" `add-disbursements-button` (`invoice-detail-client.tsx:188-214`).
  - Totals (`CMP/invoices/invoice-totals-section.tsx`): "Subtotal" `:15`; tax row `{rateName} ({ratePercent}%)` or `taxLabel ?? "Tax"` `:26-34`; "Total" `:47`.

**Proposed new steps**
1. Preconditions, all from earlier days on the run-state names: Sipho has an address (Day 1 `createClient`), is ONBOARDING, and owns the templated `siphoMatter()`; Carol/Bob/Alice logged billable time on its tasks (Day 7); billing rates exist (Day 0).
2. `loginAs alice` → `goto /invoices` → `getByRole("button", { name: "New fee note" }).click()` → `getByRole("option", { name: new RegExp("^" + escapeRegExp(siphoClient())) }).click()` → `waitForURL(/\/clients\/[0-9a-f-]{36}\?tab=invoices/)`. This also pins the popover path; alternatively `openClient` + `openGroupedTab`.
3. `panel = getByRole("tabpanel")`; snapshot the existing `a[href*="/invoices/"]` hrefs; `panel.getByRole("button", { name: "New fee note" }).click()`.
4. `gen = getByRole("dialog", { name: /Generate fee note|Select Unbilled Items/ })` → "Fetch Unbilled Time" → `expect(gen.getByText(siphoMatter()))` (matter group) → "Validate & Create Draft" → `/^Create Draft/` → hidden.
5. `expect.poll` new href count = 1 → `clickThrough(newLink, h1 "Draft fee note")`.
6. Tariff: "Add Tariff Items" → `tariff-line-dialog` → search → click the item → "Add to fee note" → hidden → `expect(main.getByRole("row", { name: /Instructions to sue/ }))` visible.
7. Sheriff disbursement R350: "Add Line" → description "Disbursement — Sheriff service fee" → Unit Price 350 → "Add" → row visible. (Or a real approved disbursement, depth-ii pattern `day-75-85-closure.spec.ts:135-170`. Prefer the real disbursement if the orchestrator wants scenario fidelity; note that disbursement lines may carry their own VAT treatment.)
8. Assert the VAT row `main.getByText(/VAT — Standard \(15%\)|^VAT$/)` `.first()` toBeVisible, and "Total" visible.
9. Record the fee-note href/UUID in run state (needed by Day 30 `:341`, Day 45 `:229`).

### A3 — Apex fixed fee (`day-30-billing.spec.ts:159`)

**Current spec code**: `:159-234`; the same soft chain as A2 with the `getByText(/40[\s,.]?250/i)` soft assert at `:226-233`.

**Product UI facts:** as A2. Additional facts:
- Apex has **no matter and no unbilled item** in the lifecycle today (only Day 2 client creation). "Create Draft" is disabled at 0 items, and the generate dialog is the only UI draft path (`APP/clients/[id]/invoice-actions.ts:67-73`).
- COMPANY needs a tax number to send (`StructuralPrerequisiteCheck.java:37-46`).
- Total format `R\u00a040\u00a0250,00`.

**Proposed new steps**
1. Precondition, chosen per orchestrator ruling:
   - (a) the Apex matter `apexMatter()` (created here or on Day 2 via template "Commercial") with one billable time entry; or
   - (b) an approved counsel disbursement on it (depth-ii `day-75-85-closure.spec.ts:96-170` is exactly this setup for Apex).
2. Generate + Create Draft as A2 → open the draft → if (b), "Delete {counsel desc}" (or keep and assert the arithmetic accordingly).
3. "Add Line" → description "Fixed fee — Shareholder agreement review and drafting" → `inputs.nth(0)` qty "1", `inputs.nth(1)` unit price "35000" → "Add".
4. Assert the Total row matches `/40[\s\u00a0\u202f]?250,00/` (scope to the totals section, not page-wide, to avoid strict mode).
5. Record the href (needed by Day 30 `:341` approve/send and Day 45 `:229` payment).

### A4 — Bank reconciliation wizard (`day-45-recon-prescription.spec.ts:41`)

**Current spec code** (`:41-120`):
```ts
  test("Alice: Start new bank reconciliation", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/trust-accounting/reconciliation/new`);

    const mainVisible = await page
      .locator("main")
      .isVisible({ timeout: 10_000 })
      .catch(() => false);
    if (!mainVisible) {
      // Try navigating to reconciliation list and clicking new
      await page.goto(`${BASE}/trust-accounting/reconciliation`);
      const mainVisible2 = await page
        .locator("main")
        .isVisible({ timeout: 10_000 })
        .catch(() => false);
      if (!mainVisible2) {
        test.skip(true, "Trust reconciliation page not accessible");
        return;
      }

      const newBtn = page
        .getByRole("button", { name: /new.*reconciliation|start.*reconciliation|reconcile/i })
        .first();
      const hasNew = await newBtn.isVisible({ timeout: 5000 }).catch(() => false);
      if (hasNew) {
        await newBtn.click();
        await page.waitForTimeout(2000);
      } else {
        test.skip(true, "New reconciliation button not found");
        return;
      }
    }

    // GAP: Bank CSV upload feature is not yet implemented. The test verifies that the
    // reconciliation page loads and the manual matching UI is accessible. Actual CSV import
    // and 3-way reconciliation (bank = cashbook = client ledger) will be tested once the
    // upload endpoint and parsing logic are built.
    expect
      .soft(
        await page
          .locator('input[type="file"]')
          .first()
          .isVisible({ timeout: 5000 })
          .catch(() => false)
      )
      .toBeTruthy();

    // Check for reconciliation form elements
    expect
      .soft(
        await page
          .locator('form, [data-testid*="reconciliation"]')
          .first()
          .isVisible({ timeout: 5000 })
          .catch(() => false)
      )
      .toBeTruthy();

    // If auto-matching is available, run it
    const matchBtn = page.getByRole("button", { name: /match|auto.*match|reconcile/i }).first();
    const hasMatch = await matchBtn.isVisible({ timeout: 3000 }).catch(() => false);
    if (hasMatch) {
      await matchBtn.click();
      await page.waitForTimeout(2000);
    }

    // Check for 3-way reconciliation result (bank = cashbook = client ledger)
    expect
      .soft(
        await page
          .getByText(/balanced|matched|reconciled/i)
          .isVisible({ timeout: 5000 })
          .catch(() => false)
      )
      .toBeTruthy();

    // Screenshot: bank reconciliation
    await captureScreenshot(page, "day-45-reconciliation-balanced");
    await captureScreenshot(page, "bank-reconciliation-matched", { curated: true });
  });
```

**Product UI facts** (`APP/trust-accounting/reconciliation/new/page.tsx`):
- The list page `/trust-accounting/reconciliation` has h1 "Reconciliation" (`reconciliation/page.tsx:99-101`) and a **link** (Button asChild) `data-testid="new-reconciliation-btn"` "New Reconciliation", shown only with `canManageTrust && accountId` (`:106-116`).
- Wizard h1 "New Reconciliation" (`new/page.tsx:326-328`).
- Step indicator `nav[aria-label="Wizard steps"]` / `step-indicator` with labels "Select Account", "Upload Statement", "Auto-Match", "Review & Match", "Complete" (`:39-45,:335-362`).
- Step 1 `step-select`:
  - "Trust Account" native select `#account-select` / `account-select`; options `"{accountName} ({bankName} - {accountNumber})"`; primary pre-selected after load (`:99-112,:395-417`);
  - "Period End Date" `#period-end` / `period-end-input` (date) (`:420-437`);
  - "Next" `step-next-btn`, disabled until both are set (`:440-446`).
- Step 2: `BankStatementUpload` card `bank-statement-upload` "Upload Bank Statement"; **hidden** `input[type=file][accept=".csv"]` `data-testid="file-input"`; button "Choose CSV File" (`CMP/trust/BankStatementUpload.tsx:67-91`); "Next" `step-next-btn` disabled until uploaded (`new/page.tsx:467-473`).
  - Because the input is `className="hidden"`, the old `input[type=file].isVisible()` could never be true.
- Step 3 `step-auto-match`: `auto-match-btn` "Run Auto-Match" → `auto-match-result` "Auto-matched N lines" (`:481-516`).
- Step 5 `step-complete` "Reconciliation Complete" (`:558-600`).

**Proposed new steps**
1. `goto /trust-accounting/reconciliation` → `clickThrough(getByTestId("new-reconciliation-btn"), getByRole("heading", { level: 1, name: "New Reconciliation" }))`.
2. `expect(getByTestId("step-indicator")).toContainText("Upload Statement")`.
3. `const acct = getByTestId("account-select")` → `expect(acct.locator("option", { hasText: "Mathebula & Partners Trust Account" })).toBeAttached()` → select it.
4. `getByTestId("period-end-input").fill(isoDate())` → `getByTestId("step-next-btn").click()`.
5. `expect(getByTestId("bank-statement-upload")).toBeVisible()`; `expect(getByTestId("file-input")).toBeAttached()`; `expect(getByTestId("file-input")).toHaveAttribute("accept", ".csv")`; `expect(getByRole("button", { name: "Choose CSV File" })).toBeVisible()`.
6. Keep the `// GAP: Bank CSV upload…` comment block. Drop the `/balanced|matched|reconciled/` soft assert (no target without a CSV; OD-7 item 6).
7. Screenshots: the curated name "bank-reconciliation-matched" now shows the upload step. Rename it, or keep it and note.

### A5 — Fee notes list (`day-90-review.spec.ts:63`)

**Current spec code** (`:63-77`):
```ts
  test("Alice: Fee notes list shows multiple fee notes", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/invoices`);
    await expect(page.locator("main")).toBeVisible({ timeout: 10_000 });
    await expect(page.locator("body")).not.toContainText("Something went wrong");

    // Soft-assert minimum fee note count (depends on data from prior days)
    const invoiceRows = page.locator(
      'table tbody tr, [data-testid*="invoice"], [data-testid*="fee-note"]'
    );
    expect.soft(await invoiceRows.count()).toBeGreaterThanOrEqual(1);

    // Screenshot: portfolio review
    await captureScreenshot(page, "day-90-portfolio-review-loaded");
  });
```

**Product UI facts:** `APP/invoices/page.tsx` renders a `<table>` with one `<tr>` per fee note (`:206-286`), whose first-cell link text is the number or "Draft" with href `/invoices/{uuid}` (`:~302-312`); otherwise `EmptyState` "No fee notes yet". The `[data-testid*="invoice"]` union matches non-row nodes, so the count is ≥1 even when empty (why it "passes").

**Proposed new steps:** `goto /invoices` → `expect(getByRole("heading", { level: 1, name: "Fee notes" }))` → `const rows = page.locator("main table tbody tr").filter({ has: page.locator('a[href*="/invoices/"]') })` → `await expect.poll(() => rows.count()).toBeGreaterThanOrEqual(2)`. Optionally assert the run's recorded fee-note hrefs are present: `expect(page.locator(\`main a[href$="${id}"]\`)).toBeVisible()`, which is exact and immune to seed rows.

### A6 — Profitability (`day-90-review.spec.ts:179`)

**Current spec code** (`:179-224`):
```ts
  test("Alice: Profitability dashboard — revenue, costs, margins for 4 clients", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/profitability`);

    const mainVisible = await page
      .locator("main")
      .isVisible({ timeout: 10_000 })
      .catch(() => false);
    if (!mainVisible) {
      test.skip(true, "Profitability page not accessible");
      return;
    }

    await expect(page.locator("body")).not.toContainText("Something went wrong");

    // Check for key profitability elements (soft assertions — elements may not all be present)
    expect
      .soft(
        await page
          .getByText(/revenue|income|billing/i)
          .isVisible({ timeout: 5000 })
          .catch(() => false)
      )
      .toBeTruthy();
    expect
      .soft(
        await page
          .getByText(/cost|expense/i)
          .isVisible({ timeout: 5000 })
          .catch(() => false)
      )
      .toBeTruthy();
    expect
      .soft(
        await page
          .getByText(/margin|profit/i)
          .isVisible({ timeout: 5000 })
          .catch(() => false)
      )
      .toBeTruthy();

    // Screenshot: profitability dashboard
    await captureScreenshot(page, "day-90-profitability-dashboard-loaded");
  });
```

**Product UI facts:**
- `/profitability` 308 → `/reports/profitability` (`frontend/lib/legacy-redirects.ts:45-47`).
- Page: h1 "Profitability", `profitability-cost-note` "Costs reflect logged expenses only; disbursements are shown as fee-note lines" (`APP/reports/profitability/page.tsx:56-66`). `PermissionDenied` without FINANCIAL_VISIBILITY (`:32-35`). Data is the **current month** (`:39`).
- When `utilization.members.length === 0 && profitability.matters.length === 0` it renders EmptyState "No profitability data yet" (`:69-77`). Otherwise `ProfitabilityContent`:
  - "Team Utilization" (headers Name / Total Hours / Billable / Non-Billable / Utilization %);
  - "Matter Profitability" (`CMP/profitability/project-profitability-table.tsx:190-256`: Matter, Client, Currency, Billable Hours, **Revenue**, **Cost**, **Margin**, **Margin %**, each a sort `<button>` inside `<th>`; empty "No matter profitability data for this period" `:184-187`);
  - "Client Profitability" (`customer-profitability-section.tsx:163-237`: Client, Currency, Revenue, Cost, Margin, Margin %).
- "Margin" vs "Margin %": use `exact: true`. "Revenue" appears in both tables: use `.first()`.

**Proposed new steps:**
1. `goto ${BASE}/profitability` → `expect(page).toHaveURL(/\/reports\/profitability/)`.
2. `expect(getByRole("heading", { level: 1, name: "Profitability" }))`.
3. For each of "Revenue", "Cost", "Margin": `await expect(page.getByRole("columnheader", { name: H, exact: true }).first()).toBeVisible()`.
4. Optionally `expect(getByRole("row", { name: new RegExp(escapeRegExp(siphoMatter())) }))` visible.

Precondition: the Day 7 billable time on rated members this month. Without it the page shows the EmptyState and A6 fails loudly, which is the correct signal.


## ESLint rule

### How rules are loaded today, and whether `e2e/` is linted
- `frontend/package.json:9` `"lint": "eslint"`. A bare `eslint` lints the cwd tree with `frontend/eslint.config.mjs` (flat config). The global ignores cover only `.next/**`, `out/**`, `build/**`, `next-env.d.ts`, so **`e2e/**` is linted today**. CI runs `pnpm run lint` then `pnpm run format:check` (`.github/workflows/*.yml`, frontend job).
- Custom rules live in `frontend/eslint-rules/*.mjs` and are exposed as the plugin `kazi-frontend-local` by `frontend/eslint-rules/index.mjs`. The config enables them in an object scoped to `**/*.{ts,tsx,js,jsx,mjs,cjs}`.
- Type checking: there is no `e2e/tsconfig.json`. `frontend/tsconfig.json` includes `**/*.ts`, so `e2e/**` is type-checked by `tsc -p` and by `next build`.
- vitest (`frontend/vitest.config.mts`) excludes `e2e/**` from discovery. Rule tests live in `frontend/__tests__/eslint-rules/`.
- A rule file placed under `frontend/e2e/eslint/` (the task's location) is loaded by importing it from `eslint-rules/index.mjs` (relative `../e2e/eslint/…`). ESLint does not discover rule folders by itself.

### Existing wiring — `frontend/eslint.config.mjs` (complete)
```js
import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";
import localRules from "./eslint-rules/index.mjs";

const NO_VOCABULARY_LAYER = "Phase 87: write the legal word; no runtime vocabulary layer (ADR-338)";

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  // Override default ignores of eslint-config-next.
  globalIgnores([
    // Default ignores of eslint-config-next:
    ".next/**",
    "out/**",
    "build/**",
    "next-env.d.ts",
  ]),
  // Local rules — Class-3 / OBS-2103 prevention. See eslint-rules/.
  {
    files: ["**/*.{ts,tsx,js,jsx,mjs,cjs}"],
    plugins: { "kazi-frontend-local": localRules },
    rules: {
      "kazi-frontend-local/no-aschild-adjacency": "error",
    },
  },
  // Single vocabulary (Phase 87 611D, ADR-338): the runtime terminology layer is deleted — write the
  // legal word. Keep every restricted import in THIS object: in flat config a later object that sets
  // the same rule REPLACES these options (no merge).
  {
    files: ["**/*.{ts,tsx,js,jsx,mjs,cjs}"],
    rules: {
      "no-restricted-imports": [
        "error",
        {
          paths: [
            { name: "@/lib/terminology", message: NO_VOCABULARY_LAYER },
            { name: "@/components/terminology-heading", message: NO_VOCABULARY_LAYER },
            { name: "@/components/terminology-text", message: NO_VOCABULARY_LAYER },
            { name: "@b2mash/shared/terminology-map", message: NO_VOCABULARY_LAYER },
            // 611E deleted packages/shared/src/terminology-map.ts; the barrel names stay banned so
            // the map cannot come back.
            {
              name: "@b2mash/shared",
              importNames: [
                "TERMINOLOGY",
                "TERMINOLOGY_BASE",
                "PORTAL_TERMINOLOGY",
                "auditTabLabel",
              ],
              message: NO_VOCABULARY_LAYER,
            },
          ],
        },
      ],
    },
  },
]);

export default eslintConfig;
```

### Existing plugin index — `frontend/eslint-rules/index.mjs` (complete)
```js
import noAschildAdjacency from "./no-aschild-adjacency.mjs";

const plugin = {
  meta: { name: "kazi-frontend-local", version: "1.0.0" },
  rules: {
    "no-aschild-adjacency": noAschildAdjacency,
  },
};

export default plugin;
```

### Existing rule — `frontend/eslint-rules/no-aschild-adjacency.mjs` (complete)
```js
/**
 * Class-3 / OBS-2103 prevention.
 *
 * Two adjacent `<*Trigger asChild>{children}</*Trigger>` siblings inside the
 * same parent JSX block collide under React 19 + Radix Slot reconciliation.
 * Both call `cloneElement` on the inner child at the same unkeyed sibling
 * position, so only one trigger wires up its `onClick`. Symptom: visible
 * button, unresponsive click.
 *
 * The fix is the dialog-owns-button pattern documented in
 * `frontend/CLAUDE.md` → "Dialog Trigger Composition".
 *
 * This rule reports any JSXElement / JSXFragment whose immediate children
 * include two or more `<*Trigger asChild>` JSX elements (whether direct
 * children, or wrapped in a `{cond && <X />}` / `{cond ? <X /> : <Y />}`
 * expression container that statically resolves to such a trigger).
 *
 * `TooltipTrigger` is excluded by default — tooltips are hover-only and
 * outside the click-loss class. Override via the `excludeTriggers` option.
 */

const DEFAULT_EXCLUDE = ["TooltipTrigger"];

function isAsChildAttribute(attr) {
  if (!attr || attr.type !== "JSXAttribute") return false;
  if (attr.name?.type !== "JSXIdentifier") return false;
  if (attr.name.name !== "asChild") return false;
  // Shorthand `<X asChild>` (no value) — true.
  if (attr.value === null || attr.value === undefined) return true;
  // Explicit `<X asChild={true}>` — true.
  if (
    attr.value.type === "JSXExpressionContainer" &&
    attr.value.expression?.type === "Literal" &&
    attr.value.expression.value === true
  ) {
    return true;
  }
  return false;
}

function triggerNameOf(jsxElement, excludeSet) {
  const opening = jsxElement.openingElement;
  if (!opening) return null;
  const name = opening.name;
  if (!name || name.type !== "JSXIdentifier") return null;
  if (!name.name.endsWith("Trigger")) return null;
  if (excludeSet.has(name.name)) return null;
  if (!opening.attributes.some(isAsChildAttribute)) return null;
  return name.name;
}

function unwrapExpression(expr, excludeSet) {
  if (!expr) return null;
  if (expr.type === "JSXElement") {
    const name = triggerNameOf(expr, excludeSet);
    return name ? { node: expr, name } : null;
  }
  if (expr.type === "LogicalExpression") {
    return unwrapExpression(expr.right, excludeSet);
  }
  if (expr.type === "ConditionalExpression") {
    return (
      unwrapExpression(expr.consequent, excludeSet) || unwrapExpression(expr.alternate, excludeSet)
    );
  }
  return null;
}

function unwrapChild(child, excludeSet) {
  if (!child) return null;
  if (child.type === "JSXElement") {
    const name = triggerNameOf(child, excludeSet);
    return name ? { node: child, name } : null;
  }
  if (child.type === "JSXExpressionContainer") {
    return unwrapExpression(child.expression, excludeSet);
  }
  return null;
}

// JSXFragments flatten at render time — their children sit at the same
// sibling-position layer as the fragment's parent's other children. So
// `<div><Trigger /><>{<Trigger />}</></div>` has two adjacent Triggers at
// runtime, even though the AST nests one inside a fragment. Flatten before
// counting matches.
function* flattenedChildren(children) {
  for (const child of children || []) {
    if (child?.type === "JSXFragment") {
      yield* flattenedChildren(child.children);
    } else {
      yield child;
    }
  }
}

function checkSiblings(parentNode, context, excludeSet, reported) {
  const matches = [];
  for (const child of flattenedChildren(parentNode.children)) {
    const m = unwrapChild(child, excludeSet);
    if (m && !reported.has(m.node)) matches.push(m);
  }
  if (matches.length < 2) return;
  for (const m of matches) {
    reported.add(m.node);
    context.report({
      node: m.node,
      messageId: "adjacency",
      data: { trigger: m.name },
    });
  }
}

const rule = {
  meta: {
    type: "problem",
    docs: {
      description:
        "Disallow two or more `<*Trigger asChild>` siblings in the same parent JSX block (Class-3 / OBS-2103 collision).",
      recommended: false,
    },
    schema: [
      {
        type: "object",
        additionalProperties: false,
        properties: {
          excludeTriggers: {
            type: "array",
            items: { type: "string" },
          },
        },
      },
    ],
    messages: {
      adjacency:
        "`<{{trigger}} asChild>` is one of two or more `*Trigger asChild` siblings in the same parent JSX. Two adjacent Radix `Slot`-wrapping triggers collide under React 19 reconciliation — only one wires up its onClick (Class-3 / OBS-2103). Use the dialog-owns-button pattern from frontend/CLAUDE.md → Dialog Trigger Composition.",
    },
  },
  create(context) {
    const options = context.options[0] || {};
    const excludeSet = new Set(options.excludeTriggers ?? DEFAULT_EXCLUDE);
    // Track triggers already reported by an ancestor's flatten pass so a
    // nested fragment visit doesn't double-report the same trigger node.
    const reported = new WeakSet();
    return {
      JSXElement(node) {
        checkSiblings(node, context, excludeSet, reported);
      },
      JSXFragment(node) {
        checkSiblings(node, context, excludeSet, reported);
      },
    };
  },
};

export default rule;
```

### Existing rule test (the pattern to copy) — `frontend/__tests__/eslint-rules/no-aschild-adjacency.test.ts` (complete)
```ts
import { describe, it } from "vitest";
import { RuleTester } from "eslint";
import rule from "../../eslint-rules/no-aschild-adjacency.mjs";

// ESLint's RuleTester defaults to mocha-style globals; bind to vitest so
// the test cases register with vitest's runner.
RuleTester.describe = describe;
RuleTester.it = it;
RuleTester.itOnly = it.only;

const ruleTester = new RuleTester({
  languageOptions: {
    ecmaVersion: "latest",
    sourceType: "module",
    parserOptions: {
      ecmaFeatures: { jsx: true },
    },
  },
});

ruleTester.run("no-aschild-adjacency", rule as never, {
  valid: [
    // Single Trigger asChild in a parent — no adjacency, fine.
    {
      code: `
        const x = (
          <Dialog>
            <DialogTrigger asChild>
              <Button>Open</Button>
            </DialogTrigger>
            <DialogContent>...</DialogContent>
          </Dialog>
        );
      `,
    },
    // Dialog-owns-button: no DialogTrigger at all, plain Button. The good pattern.
    {
      code: `
        const x = (
          <div className="flex gap-1">
            <Dialog>
              <Button onClick={() => {}}>Edit</Button>
              <DialogContent>...</DialogContent>
            </Dialog>
            <AlertDialog>
              <Button onClick={() => {}}>Delete</Button>
              <AlertDialogContent>...</AlertDialogContent>
            </AlertDialog>
          </div>
        );
      `,
    },
    // TooltipTrigger excluded by default — adjacency is fine for hover-only.
    {
      code: `
        const x = (
          <div>
            <TooltipTrigger asChild>
              <Button>Hover 1</Button>
            </TooltipTrigger>
            <TooltipTrigger asChild>
              <Button>Hover 2</Button>
            </TooltipTrigger>
          </div>
        );
      `,
    },
    // One Trigger asChild + one non-Slot button is fine — only one Slot in the row.
    {
      code: `
        const x = (
          <div className="flex">
            <Dialog>
              <DialogTrigger asChild>
                <Button>Open</Button>
              </DialogTrigger>
              <DialogContent>...</DialogContent>
            </Dialog>
            <Button onClick={() => {}}>Plain</Button>
          </div>
        );
      `,
    },
    // *Trigger without asChild — not a Slot composition, fine.
    {
      code: `
        const x = (
          <div>
            <DialogTrigger>Open</DialogTrigger>
            <AlertDialogTrigger>Delete</AlertDialogTrigger>
          </div>
        );
      `,
    },
    // Two triggers but in different parent JSX blocks — not adjacent.
    {
      code: `
        const x = (
          <div>
            <div>
              <DialogTrigger asChild>
                <Button>Edit</Button>
              </DialogTrigger>
            </div>
            <div>
              <AlertDialogTrigger asChild>
                <Button>Delete</Button>
              </AlertDialogTrigger>
            </div>
          </div>
        );
      `,
    },
  ],
  invalid: [
    // Canonical bad pattern: Dialog + AlertDialog adjacency in a flex row.
    {
      code: `
        const x = (
          <div className="flex justify-end gap-1">
            <DialogTrigger asChild>
              <Button>Edit</Button>
            </DialogTrigger>
            <AlertDialogTrigger asChild>
              <Button>Delete</Button>
            </AlertDialogTrigger>
          </div>
        );
      `,
      errors: [
        { messageId: "adjacency", data: { trigger: "DialogTrigger" } },
        { messageId: "adjacency", data: { trigger: "AlertDialogTrigger" } },
      ],
    },
    // Three Trigger asChild siblings — three diagnostics.
    {
      code: `
        const x = (
          <div>
            <DialogTrigger asChild><Button>A</Button></DialogTrigger>
            <DialogTrigger asChild><Button>B</Button></DialogTrigger>
            <PopoverTrigger asChild><Button>C</Button></PopoverTrigger>
          </div>
        );
      `,
      errors: [{ messageId: "adjacency" }, { messageId: "adjacency" }, { messageId: "adjacency" }],
    },
    // Inside a JSXFragment — same parent class as JSXElement, should fire.
    {
      code: `
        const x = (
          <>
            <DialogTrigger asChild><Button>A</Button></DialogTrigger>
            <AlertDialogTrigger asChild><Button>B</Button></AlertDialogTrigger>
          </>
        );
      `,
      errors: [
        { messageId: "adjacency", data: { trigger: "DialogTrigger" } },
        { messageId: "adjacency", data: { trigger: "AlertDialogTrigger" } },
      ],
    },
    // Conditional wrappers: {cond && <X asChild />} alongside another sibling.
    {
      code: `
        const x = (
          <div>
            {cond && (
              <DialogTrigger asChild><Button>Edit</Button></DialogTrigger>
            )}
            <AlertDialogTrigger asChild><Button>Delete</Button></AlertDialogTrigger>
          </div>
        );
      `,
      errors: [
        { messageId: "adjacency", data: { trigger: "DialogTrigger" } },
        { messageId: "adjacency", data: { trigger: "AlertDialogTrigger" } },
      ],
    },
    // Explicit asChild={true} (rather than shorthand) still matches.
    {
      code: `
        const x = (
          <div>
            <DialogTrigger asChild={true}><Button>A</Button></DialogTrigger>
            <AlertDialogTrigger asChild><Button>B</Button></AlertDialogTrigger>
          </div>
        );
      `,
      errors: [
        { messageId: "adjacency", data: { trigger: "DialogTrigger" } },
        { messageId: "adjacency", data: { trigger: "AlertDialogTrigger" } },
      ],
    },
    // Two triggers separated by a non-trigger sibling in the same parent.
    // Deliberately INVALID under this rule's "same parent JSX block" semantics
    // (audit-03 fix-spec line 51). A reviewer might argue these aren't at
    // colliding render positions; that's a tighter interpretation we're not
    // adopting yet (would risk missing real collisions if the bug mechanism
    // has any non-consecutive manifestation). Real false positives can be
    // silenced with eslint-disable-line + a justification comment.
    {
      code: `
        const x = (
          <div className="flex">
            <DialogTrigger asChild><Button>Edit</Button></DialogTrigger>
            <span>—</span>
            <AlertDialogTrigger asChild><Button>Delete</Button></AlertDialogTrigger>
          </div>
        );
      `,
      errors: [
        { messageId: "adjacency", data: { trigger: "DialogTrigger" } },
        { messageId: "adjacency", data: { trigger: "AlertDialogTrigger" } },
      ],
    },
    // Fragment flatten — `<div><Trigger /><>{<Trigger />}</></div>` has two
    // adjacent triggers at runtime once the fragment flattens. Per CodeRabbit
    // review on PR #1270.
    {
      code: `
        const x = (
          <div>
            <DialogTrigger asChild><Button>A</Button></DialogTrigger>
            <>
              <AlertDialogTrigger asChild><Button>B</Button></AlertDialogTrigger>
            </>
          </div>
        );
      `,
      errors: [
        { messageId: "adjacency", data: { trigger: "DialogTrigger" } },
        { messageId: "adjacency", data: { trigger: "AlertDialogTrigger" } },
      ],
    },
    // Fragment flatten with both triggers nested in the fragment — the outer
    // <div> flattens through the fragment, sees [Trigger, Trigger], reports.
    // The inner fragment visitor would see the same triggers, but the
    // WeakSet dedupe stops a second report on the same nodes.
    {
      code: `
        const x = (
          <div>
            <>
              <DialogTrigger asChild><Button>A</Button></DialogTrigger>
              <AlertDialogTrigger asChild><Button>B</Button></AlertDialogTrigger>
            </>
          </div>
        );
      `,
      errors: [
        { messageId: "adjacency", data: { trigger: "DialogTrigger" } },
        { messageId: "adjacency", data: { trigger: "AlertDialogTrigger" } },
      ],
    },
    // excludeTriggers option: when caller drops TooltipTrigger from the
    // exclusion list, two adjacent TooltipTrigger asChild ARE flagged.
    {
      code: `
        const x = (
          <div>
            <TooltipTrigger asChild><Button>A</Button></TooltipTrigger>
            <TooltipTrigger asChild><Button>B</Button></TooltipTrigger>
          </div>
        );
      `,
      options: [{ excludeTriggers: [] }],
      errors: [
        { messageId: "adjacency", data: { trigger: "TooltipTrigger" } },
        { messageId: "adjacency", data: { trigger: "TooltipTrigger" } },
      ],
    },
  ],
});
```

### Existing wiring test (pattern for the config-level test) — `frontend/__tests__/eslint-rules/no-terminology-imports.test.ts` (complete)
```ts
// @vitest-environment node
import path from "node:path";
import { describe, it, expect } from "vitest";
import { ESLint } from "eslint";

// Phase 87 611D.2 (ADR-338): eslint.config.mjs bans the deleted runtime terminology modules with
// no-restricted-imports. Unlike no-aschild-adjacency.test.ts (RuleTester on a custom rule module), this
// lints probe snippets through the real flat config, so it proves the rule is wired, not just that
// ESLint works.

const FRONTEND_ROOT = path.resolve(__dirname, "../..");
const MESSAGE = "Phase 87: write the legal word; no runtime vocabulary layer (ADR-338)";
const eslint = new ESLint({ cwd: FRONTEND_ROOT }); // discovers eslint.config.mjs

async function restrictedImportMessages(
  code: string,
  file = "components/__terminology_probe__.tsx"
): Promise<string[]> {
  const [result] = await eslint.lintText(code, { filePath: path.join(FRONTEND_ROOT, file) });
  return result.messages.filter((m) => m.ruleId === "no-restricted-imports").map((m) => m.message);
}

describe("no-restricted-imports — runtime terminology layer (ADR-338)", { timeout: 30_000 }, () => {
  it.each([
    ["@/lib/terminology", "useTerminology"],
    ["@/components/terminology-heading", "TerminologyHeading"],
    ["@/components/terminology-text", "TerminologyText"],
    ["@b2mash/shared/terminology-map", "TERMINOLOGY"],
  ])("flags an import from %s", async (specifier, name) => {
    const messages = await restrictedImportMessages(
      `import { ${name} } from "${specifier}";\nexport const x = ${name};\n`
    );
    expect(messages).toHaveLength(1);
    expect(messages[0]).toContain(MESSAGE);
  });

  it.each(["TERMINOLOGY", "TERMINOLOGY_BASE", "PORTAL_TERMINOLOGY", "auditTabLabel"])(
    "flags %s imported from the @b2mash/shared barrel",
    async (name) => {
      const messages = await restrictedImportMessages(
        `import { ${name} } from "@b2mash/shared";\nexport const x = ${name};\n`
      );
      expect(messages).toEqual([expect.stringContaining(MESSAGE)]);
    }
  );

  it("applies to test files too", async () => {
    const messages = await restrictedImportMessages(
      'import { TerminologyProvider } from "@/lib/terminology";\nexport const x = TerminologyProvider;\n',
      "__tests__/__terminology_probe__.test.tsx"
    );
    expect(messages).toEqual([expect.stringContaining(MESSAGE)]);
  });

  it("leaves formatting and module-gating imports alone", async () => {
    const messages = await restrictedImportMessages(
      [
        'import { formatDate } from "@b2mash/shared/format";',
        'import { useOrgProfile } from "@/lib/org-profile";',
        "export const x = [formatDate, useOrgProfile];",
        "",
      ].join("\n")
    );
    expect(messages).toEqual([]);
  });
});
```


### Proposed rule: `frontend/e2e/eslint/no-isvisible-catch.mjs`

Use `.mjs`, not `.js` as in the task's Key Files line: `frontend/package.json` has no `"type": "module"`, so a `.js` file would be CommonJS, and the existing local rules are ESM `.mjs`. Prettier and ESLint already cover `.mjs`. Wire it into the existing `kazi-frontend-local` plugin, so the rule id is `kazi-frontend-local/no-isvisible-catch`:

```js
// frontend/eslint-rules/index.mjs
import noAschildAdjacency from "./no-aschild-adjacency.mjs";
import noIsVisibleCatch from "../e2e/eslint/no-isvisible-catch.mjs";

const plugin = {
  meta: { name: "kazi-frontend-local", version: "1.1.0" },
  rules: {
    "no-aschild-adjacency": noAschildAdjacency,
    "no-isvisible-catch": noIsVisibleCatch,
  },
};
export default plugin;
```

```js
// frontend/eslint.config.mjs — add a new object AFTER the existing local-rules object (different rule id, so no
// replace-not-merge hazard). Scope per OD-2 (recommended (b)); 628F widens `files` to "e2e/**/*.{ts,tsx,js,mjs}".
  // Playwright strict assertions (Phase 88 628E, U22, R10 §0 M1). See e2e/AUTOMATION-NOTES.md › Assertion rules.
  {
    files: [
      "e2e/tests/legal-lifecycle/**/*.ts",
      "e2e/tests/legal-depth-ii/**/*.ts",
      "e2e/helpers/**/*.ts",
      "e2e/fixtures/**/*.ts",
    ],
    plugins: { "kazi-frontend-local": localRules },
    rules: { "kazi-frontend-local/no-isvisible-catch": "error" },
  },
```
Flat config: registering the same plugin object under the same namespace in two config objects is allowed (it is the same object reference). If ESLint complains about a duplicate plugin, drop the `plugins` key from the new object: the earlier object's plugin applies to all `**/*.{ts,…}` files already.

Rule source (proposed, complete):
```js
/**
 * Phase 88 628E (U22, R10 §0 M1) — forbid swallowing Playwright locator state checks.
 *
 * `locator.isVisible()` (and isHidden / isEnabled / isDisabled / isChecked / isEditable) do NOT wait:
 * they answer for the DOM as it is at call time, and Playwright ignores their `timeout` option. They
 * reject only on a strict-mode violation (the locator matched 2+ elements) or a closed page. So
 * `.catch(() => false)` exists only to turn "the text is on screen twice" into `false` — the soft
 * assertion then fails, or the guarded step is silently skipped and the test passes having done
 * nothing. Write `await expect(locator.first()).toBeVisible()` (web-first, waits, strict-safe) or a
 * role locator; for genuinely optional UI, wait for either state first:
 *   await expect(a.or(b).first()).toBeVisible();
 *   if (await a.isVisible()) { … }
 * See frontend/e2e/AUTOMATION-NOTES.md › Assertion rules.
 */

const DEFAULT_METHODS = ["isVisible", "isHidden", "isEnabled", "isDisabled", "isChecked", "isEditable"];
const TIMEOUT_IGNORED = new Set(["isVisible", "isHidden"]);

function unwrapChain(node) {
  return node && node.type === "ChainExpression" ? node.expression : node;
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

function hasTimeoutOption(call) {
  const arg = call.arguments?.[0];
  return (
    arg?.type === "ObjectExpression" &&
    arg.properties.some(
      (p) =>
        p.type === "Property" &&
        !p.computed &&
        ((p.key.type === "Identifier" && p.key.name === "timeout") ||
          (p.key.type === "Literal" && p.key.value === "timeout"))
    )
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
        "`.{{method}}()` rejection swallowed with `.{{handler}}()`. It only rejects on a strict-mode violation (2+ matches), so this turns visible-but-ambiguous into `false`. Use `await expect(locator.first()).toBeVisible()` or a role locator; for optional UI wait on `a.or(b)` first (e2e/AUTOMATION-NOTES.md › Assertion rules).",
      timeoutIgnored:
        "`.{{method}}({ timeout })` — Playwright ignores `timeout` here; the check does not wait. Use `await expect(locator).toBeVisible({ timeout })` / `toBeHidden({ timeout })`.",
    },
  },
  create(context) {
    const options = context.options[0] || {};
    const methods = new Set(options.methods ?? DEFAULT_METHODS);
    const checkTimeout = options.checkTimeout ?? true;
    return {
      CallExpression(node) {
        const callee = unwrapChain(node.callee);
        if (callee?.type === "MemberExpression" && !callee.computed && callee.property?.type === "Identifier") {
          const handler = callee.property.name;
          const swallows = handler === "catch" || (handler === "then" && node.arguments.length >= 2);
          if (swallows) {
            const method = stateMethodOf(callee.object, methods);
            if (method) {
              context.report({ node, messageId: "swallowed", data: { method, handler } });
            }
          }
        }
        if (checkTimeout) {
          const method = stateMethodOf(node, new Set([...methods].filter((m) => TIMEOUT_IGNORED.has(m))));
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
Notes for the builder:
- `await x.isVisible().catch()` parses as `await (x.isVisible().catch())`, so the `.catch` callee's object is the `isVisible` call. Handled.
- `(await x.isVisible())` used in an `if` is **not** flagged. That is deliberate (OD-6; the sanctioned pattern).
- `helpers/grouped-tabs.ts`' `item.waitFor(…).then(() => true, () => false)` is not flagged (`waitFor` is not in `methods`).

### Proposed test: `frontend/__tests__/eslint-rules/no-isvisible-catch.test.ts`

Two parts, mirroring both existing rule tests:
1. **RuleTester** (pattern `no-aschild-adjacency.test.ts`: bind `RuleTester.describe/it/itOnly` to vitest; `languageOptions: { ecmaVersion: "latest", sourceType: "module" }`; TS syntax is not needed in the snippets).
   - **valid:**
     - `await expect(page.getByText("x").first()).toBeVisible();`
     - `if (await a.isVisible()) {}`
     - `await expect(a.or(b).first()).toBeVisible(); if (await a.isVisible()) {}`
     - `await item.waitFor({ state: "visible", timeout: 2000 }).then(() => true, () => false);`
     - `promise.catch(() => false);`
     - `page.isVisible("sel")`
     - `foo.isVisibleish().catch(() => false)`
     - `x.isVisible().then((v) => v)` (one arg)
     - `x.isEnabled()`
     - with option `{ methods: ["isVisible"] }`: `x.isChecked().catch(() => false)` is valid.
   - **invalid:**
     - `await loc.isVisible().catch(() => false)` → `swallowed` {method: "isVisible", handler: "catch"};
     - the multi-line form `await page\n  .getByText(/x/i)\n  .isVisible({ timeout: 3000 })\n  .catch(() => false)` → `swallowed` + `timeoutIgnored` (2 errors);
     - `expect.soft(await loc.isVisible().catch(() => false)).toBeTruthy()` → `swallowed`;
     - `loc.isVisible().then(() => true, () => false)` → `swallowed` {handler: "then"};
     - `loc?.isVisible()?.catch(() => false)` → `swallowed` (ChainExpression);
     - `loc.isEnabled().catch(() => false)`, `loc.isChecked().catch(() => null)`, `loc.isHidden().catch(() => true)` → `swallowed` each;
     - `await loc.isVisible({ timeout: 5000 })` → `timeoutIgnored`;
     - `await loc.isHidden({ "timeout": 1 })` → `timeoutIgnored`;
     - with `{ checkTimeout: false }`: `loc.isVisible({ timeout: 1 }).catch(() => false)` → only `swallowed`.
2. **Wiring** (pattern `no-terminology-imports.test.ts`: `// @vitest-environment node`, `new ESLint({ cwd: FRONTEND_ROOT })`, `lintText(code, { filePath })`, `{ timeout: 30_000 }`):
   - `e2e/tests/legal-lifecycle/__probe__.spec.ts` with `await page.getByText("x").isVisible().catch(() => false);` → exactly one message with `ruleId === "kazi-frontend-local/no-isvisible-catch"`.
   - Same for `e2e/helpers/__probe__.ts`.
   - Per OD-2 (b): `e2e/tests/settings/__probe__.spec.ts` → 0 messages from this rule. This pins the deliberate scope, and 628F flips the assertion when it widens.
   - `components/__probe__.tsx` → 0 (not an e2e file).

Baseline proof for the PR (red-first): before the sweep, `NODE_OPTIONS="" pnpm exec eslint e2e/tests/legal-lifecycle` with the rule enabled reports 272 `swallowed` (plus the `timeoutIgnored` count); after, 0. Save both counts to the slice evidence.


## File Plan

Counts assume OD-1 (b) with OD-2 (b), OD-3 = 628E lifts, OD-5 = new run-state helper. The E-1/E-2 split is marked.

**Create (4):**
| File | Slice | What |
|---|---|---|
| `frontend/e2e/eslint/no-isvisible-catch.mjs` | E-1 | the rule (above) |
| `frontend/__tests__/eslint-rules/no-isvisible-catch.test.ts` | E-1 | RuleTester + wiring tests |
| `frontend/e2e/helpers/grouped-tabs.ts` | E-1 | `openGroupedTab` moved verbatim from `legal-depth-ii.ts:244-263` |
| `frontend/e2e/helpers/legal-lifecycle.ts` | E-1 | run state + name builders (OD-5); may also host small lifecycle-only helpers (`createTemplateMatter`, `logTimeOnTask`, `ensureTrustAccount`, `recordDeposit`, `generateDraftFeeNote` returning the href) |

**Modify:**
| File | Slice | What |
|---|---|---|
| `frontend/eslint-rules/index.mjs` | E-1 | register the rule |
| `frontend/eslint.config.mjs` | E-1 (scope) / E-2 (widen to all of legal-lifecycle) | new config object |
| `frontend/e2e/helpers/legal-depth-ii.ts` | E-1 | `export { openGroupedTab } from "./grouped-tabs";` (and widen `NewClient.type` to include `"Trust"` if Moroka uses `createClient`) |
| `frontend/e2e/tests/legal-lifecycle/day-00-firm-setup.spec.ts` | E-1 | 18 sites; A1; trust account; rates; VAT assert |
| `…/day-01-03-onboarding.spec.ts` | E-1 | 46 sites; createClient ×4, onboarding ×4, template matters ×3, conflict form ×4 |
| `…/day-07-14-work-trust.spec.ts` | E-1 | 48 sites; Log Time on tasks, comment, court date, deposits, ledger, adverse party |
| `…/matter-team.spec.ts` | E-1 (only if imports move) | 0 sites |
| `…/day-30-billing.spec.ts` | E-2 | 44 sites; A2, A3, fee transfer, QC, approve/send, fee estimate |
| `…/day-45-recon-prescription.spec.ts` | E-2 | 23 sites; A4, prescription, postpone, payment |
| `…/day-60-interest.spec.ts` | E-2 | 36 sites; interest run, investments ×2, 2nd cycle, report |
| `…/day-75-complex.spec.ts` | E-2 | 34 sites |
| `…/day-90-review.spec.ts` | E-2 | 23 sites; A5, A6, RBAC negatives `toHaveCount(0)` |
| `frontend/e2e/tests/legal-depth-ii/*.spec.ts` | E-1 | 0 sites. Only touched if OD-3 imports change (none with the re-export). Plus `day-14-conveyancing.spec.ts:140` un-fixme only if the OD-7 probe is 5/5 |
| `frontend/e2e/fixtures/keycloak-auth.ts` | E-1 | 1 site → `.or()` pattern (OD-2) |
| `frontend/e2e/AUTOMATION-NOTES.md` | E-1 | the rule section + Tab Navigation fix (OD-9) |
| `frontend/CLAUDE.md` | E-1 (if OD-9 approves) | one ESLint bullet |

**Size:** E-1 ≈ 13–15 files, E-2 ≈ 6–7 files (one PR: ≈ 20). Far under the 300-file cap. If OD-2 (a) is ruled, add 29 default-config files (≈ 50 total). Evidence (logs, screenshots) goes in the post-merge docs commit, not the PR. Do not commit `frontend/test-results/**`, `documentation/screenshots/**` or `frontend/e2e/screenshots/**` baselines (only `.gitkeep` is tracked there).

**Do not touch:** `frontend/e2e/playwright*.config.ts` (628F owns the default config; the legal configs need no change). One exception: if the rewritten lifecycle run exceeds `globalTimeout: 600_000` (`playwright.legal-lifecycle.config.ts:13`), raise it there and state why. The baseline run took 2.2 min; depth-ii's real flows took 1.8 min for 43 tests; a real lifecycle (~95 tests) plausibly runs 5–8 min. Long tests use `test.setTimeout(60_000)` (pattern `day-01-03-onboarding.spec.ts:340,370`).

## AUTOMATION-NOTES.md — draft text to add

Insert after "## Common UI Patterns for Automation" (`frontend/e2e/AUTOMATION-NOTES.md:620`) as a new top-level section. Replace the stale "### Tab Navigation" snippet (`:633-640`) with the grouped-tab text below.

```markdown
## Assertion rules (Phase 88 628E — lint-enforced)

`kazi-frontend-local/no-isvisible-catch` (`e2e/eslint/no-isvisible-catch.mjs`) fails `pnpm lint` on:

- `.catch(…)` (or a two-argument `.then(…, onRejected)`) chained on `isVisible`, `isHidden`, `isEnabled`,
  `isDisabled`, `isChecked`, `isEditable`;
- `isVisible({ timeout })` / `isHidden({ timeout })` — Playwright ignores the option.

Why (R10 §0 M1): these checks do not wait, and they reject only on a strict-mode violation — the locator
matched two or more elements. `getByText(/vat/i).isVisible().catch(() => false)` therefore returns `false`
exactly when the text is on screen twice, and returns immediately after a navigation. In the legal
lifecycle suite 272 such guards hid flows the product never had: the tests "passed" having done nothing.

Write instead:

| Intent | Write |
|---|---|
| element must be there | `await expect(page.getByRole("button", { name: "Add Line" })).toBeVisible()` |
| text may match several nodes | `await expect(page.getByText("VAT").first()).toBeVisible()` — or scope it: `section.getByRole("row", { name: /VAT/ })` |
| element must be absent (RBAC) | `await expect(locator).toHaveCount(0)` / `.not.toBeVisible()` — never `expect(await x.isVisible()).toBe(false)` |
| column/table check | `getByRole("columnheader", { name: "Revenue", exact: true }).first()` |
| dialog closes on success | `await expect(dialog).toBeHidden({ timeout: 15_000 })` — never "press Escape if still open" |
| genuinely optional UI (state may differ between runs) | wait for either state first, then branch: `await expect(a.or(b).first()).toBeVisible(); if (await a.isVisible()) { … }` |
| step depends on a feature that may be off | it is not optional on the legal-za stack — assert it. `test.skip` only for an explicit opt-in (env flag) or a documented environment limitation; product bugs are `test.fixme` with a `FIXME(<tag>)` comment citing file:line |

Never guard a flow step with `if (visible) { act }`: a dead locator then turns the step into a silent no-op.
Never use `waitForTimeout` to wait for UI — use a web-first `expect`.

### Grouped tabs (matter / client detail)

Only the group triggers are `role="tab"` (`data-testid="tab-group-<group>"`); sub-tabs are menu items
(`tab-item-<tab>`) and the tab panel has no accessible name. Use the helper:

    import { openGroupedTab } from "../../helpers/grouped-tabs";
    await openGroupedTab(page, "work", "tasks");      // Work → Tasks
    const panel = page.getByRole("tabpanel");         // unnamed

`getByRole("tab", { name: "Tasks" })` / `getByRole("tabpanel", { name: "Tasks" })` match nothing.

### Lifecycle run state

The legal-lifecycle spec files share entity names through `helpers/legal-lifecycle.ts` (a JSON file in the OS
temp dir, written by the first Day 0 test), the same way `helpers/legal-depth-ii.ts` does for depth-II. Look
records up by their exact run-scoped name (`openMatter(page, siphoMatter())`), never by a loose regex +
`.first()` — on a reused stack that picks an older run's record.
```

## Reference Patterns (FULL code)

### Shared helpers — `frontend/e2e/helpers/legal-depth-ii.ts` (complete; `openGroupedTab` at the end is the function to lift)
```ts
/**
 * Shared helpers for the Phase 67 `legal-depth-ii` Playwright suite.
 *
 * The suite is standalone: it needs only a freshly seeded E2E stack (legal-za
 * profile) and creates every client, matter and record it acts on through the UI.
 * It does not depend on the legal-lifecycle suite.
 *
 * The four spec files run serially (workers: 1) and share state: Day 5 creates
 * Sipho's client + litigation matter + its disbursements/trust activity, and the
 * Day 30-45 / Day 85 specs act on that same matter. Playwright restarts the worker
 * after a failure (re-evaluating module-level constants), so the run id and the
 * recorded entity names live in a small file in the OS temp dir rather than in
 * module constants. (`helpers/e2e-state.ts` is not reused: its schema is the
 * Keycloak onboarding state — owner credentials + org slug — not run-scoped
 * entity names.) The first Day 5 test always starts a fresh run, overwriting any
 * state left by an earlier run or an earlier stack; the later specs then fail fast
 * with a "should be listed" message naming the missing entity if Day 5 has not
 * run against the current stack.
 */
import { expect, test, type Locator, type Page } from "@playwright/test";
import { randomBytes } from "node:crypto";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";

export const ORG = "e2e-test-org";
export const BASE = `/org/${ORG}`;

const RUN_FILE = path.join(tmpdir(), "kazi-legal-depth-ii-run.json");

interface DepthIiRunState {
  runId: string;
  /** Exact name of the client Sipho's depth-II matter was opened for (created by Day 5). */
  siphoClient?: string;
}

function readRunState(): DepthIiRunState {
  if (!existsSync(RUN_FILE)) {
    throw new Error(
      `No legal-depth-ii run state at ${RUN_FILE} — run day-05-disbursements.spec.ts first`
    );
  }
  return JSON.parse(readFileSync(RUN_FILE, "utf-8")) as DepthIiRunState;
}

/**
 * Starts a new depth-II run (called by the first Day 5 test) and returns its id —
 * the single source of the run id for every spec in the suite. Millisecond time
 * plus 16 random bits, so ids never collide across runs on a reused stack.
 */
export function startDepthIiRun(): string {
  const runId = `${Date.now().toString(36)}${randomBytes(2).toString("hex")}`;
  writeFileSync(RUN_FILE, JSON.stringify({ runId } satisfies DepthIiRunState));
  return runId;
}

/** The id of the depth-II run started by Day 5 (throws if Day 5 has not run). */
export function currentDepthIiRun(): string {
  return readRunState().runId;
}

/**
 * Run id scoped to the current test attempt. A retried serial group (CI runs with
 * `retries: 1`) re-runs every test of the group with `retry > 0`, so names built from
 * this id stay unique when a group re-creates its records for the same run.
 */
export function attemptRunId(): string {
  const retry = test.info().retry;
  return retry ? `${currentDepthIiRun()}-r${retry}` : currentDepthIiRun();
}

/** Records the exact client name Sipho's depth-II matter belongs to. */
export function recordSiphoClient(name: string): void {
  writeFileSync(RUN_FILE, JSON.stringify({ ...readRunState(), siphoClient: name }));
}

/** Exact name of Sipho's client record for this run (recorded by Day 5). */
export function siphoClientName(): string {
  const name = readRunState().siphoClient;
  if (!name) throw new Error("Sipho's client was not recorded — run day-05 first");
  return name;
}

/** Sipho Ndlovu's litigation matter used by Day 5, Day 30-45 and Day 85. */
export function siphoMatterName(runId: string = currentDepthIiRun()): string {
  return `Ndlovu v RAF — Depth II ${runId}`;
}

export function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

/** YYYY-MM-DD for today offset by `days` (local time, matching `<input type="date">`). */
export function isoDate(days = 0): string {
  const d = new Date();
  d.setDate(d.getDate() + days);
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  return `${d.getFullYear()}-${mm}-${dd}`;
}

/**
 * Selects the first `<option>` of a native `<select>` whose text matches `label` and
 * returns the selected option's text. Waits for the option to exist first — several
 * selects load their options asynchronously ("Loading matters...").
 */
export async function selectNativeOption(select: Locator, label: string | RegExp): Promise<string> {
  const option = select.locator("option", { hasText: label }).first();
  await expect(option, `option ${String(label)} should load`).toBeAttached({ timeout: 15_000 });
  const value = await option.getAttribute("value");
  expect(value, `option ${String(label)} should have a value`).toBeTruthy();
  await select.selectOption(value!);
  await expect(select).toHaveValue(value!);
  return (await option.textContent())?.trim() ?? "";
}

/**
 * Performs a click that navigates client-side to another page and waits until that page
 * is ready for interaction: its `ready` element is visible. The destination is mounted
 * once per navigation (the org entrance animation is a CSS-only template), so it can be
 * acted on as soon as it is visible. No reload, no fixed sleep.
 */
export async function clickThrough(target: Locator, ready: Locator): Promise<void> {
  await target.click();
  await expect(ready).toBeVisible({ timeout: 15_000 });
}

/**
 * Opens a matter from the Matters list by clicking its row link and waits until the
 * matter page is ready (heading visible).
 */
export async function openMatter(
  page: Page,
  matterName: string,
  { closed = false }: { closed?: boolean } = {}
): Promise<void> {
  // Phase 87 S4 (ADR-344): route rename
  await page.goto(`${BASE}/matters`);
  if (closed) {
    // The list shows active matters by default; closed ones sit behind the "Closed" chip
    await page.getByTestId("project-status-filter").getByRole("link", { name: "Closed" }).click();
    await expect(page).toHaveURL(/[?&]status=CLOSED/);
  }
  const link = page.getByRole("link", { name: new RegExp(escapeRegExp(matterName)) }).first();
  await expect(link, `matter "${matterName}" should be listed`).toBeVisible({ timeout: 15_000 });
  await clickThrough(link, page.getByRole("heading", { level: 1, name: matterName }));
  // Phase 87 S4 (ADR-344): route rename (610D — post-redirect URL)
  await expect(page).toHaveURL(/\/matters\/[0-9a-f-]{36}/);
}

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
  type: "Individual" | "Company";
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

/** Opens the Matter-closure dialog from the matter header and waits for the gate report. */
export async function openClosureDialog(page: Page): Promise<Locator> {
  await page.getByTestId("close-matter-btn").click();
  const dialog = page.getByTestId("matter-closure-dialog");
  await expect(dialog).toBeVisible();
  await expect(dialog.getByTestId("matter-closure-report")).toBeVisible({ timeout: 15_000 });
  await expect(dialog.getByTestId("matter-closure-next-btn")).toBeEnabled();
  return dialog;
}
```

### Auth fixture — `frontend/e2e/fixtures/auth.ts` (complete)
```ts
import { Page } from "@playwright/test";

const MOCK_IDP_URL = process.env.MOCK_IDP_URL || "http://localhost:8090";

type SeedUser = "alice" | "bob" | "carol";

const USERS: Record<SeedUser, { userId: string; orgId: string; orgSlug: string; orgRole: string }> =
  {
    alice: {
      userId: "user_e2e_alice",
      orgId: "org_e2e_test",
      orgSlug: "e2e-test-org",
      orgRole: "org:owner",
    },
    bob: {
      userId: "user_e2e_bob",
      orgId: "org_e2e_test",
      orgSlug: "e2e-test-org",
      orgRole: "org:admin",
    },
    carol: {
      userId: "user_e2e_carol",
      orgId: "org_e2e_test",
      orgSlug: "e2e-test-org",
      orgRole: "org:member",
    },
  };

/**
 * Get a raw API bearer token for the given seed user.
 * Use this for direct backend API calls in tests (not browser-based).
 */
export async function getApiToken(user: SeedUser = "alice"): Promise<string> {
  const res = await fetch(`${MOCK_IDP_URL}/token`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(USERS[user]),
  });

  if (!res.ok) {
    throw new Error(`Failed to get token for ${user}: ${res.status} ${res.statusText}`);
  }

  const { access_token } = await res.json();
  return access_token;
}

export async function loginAs(page: Page, user: SeedUser): Promise<void> {
  const token = await getApiToken(user);

  await page.context().addCookies([
    {
      name: "mock-auth-token",
      value: token,
      domain: "localhost",
      path: "/",
      httpOnly: false,
      sameSite: "Lax",
    },
  ]);
}
```

### Screenshot helper — `frontend/e2e/helpers/screenshot.ts` (complete)
```ts
import { expect, Page, Locator } from "@playwright/test";
import { mkdirSync, writeFileSync } from "node:fs";
import path from "node:path";

const CURATED_DIR = path.resolve(
  __dirname,
  "..",
  "..",
  "..",
  "documentation",
  "screenshots",
  "legal-vertical"
);

export interface ScreenshotOptions {
  /** Save a curated screenshot for documentation (default: false = regression mode) */
  curated?: boolean;
  /** Capture the full scrollable page (default: false) */
  fullPage?: boolean;
  /** Capture a specific element instead of the full page */
  locator?: Locator;
}

/**
 * Validate that the screenshot name does not escape the target directory.
 */
function assertSafeName(name: string, baseDir: string): string {
  const resolved = path.resolve(baseDir, `${name}.png`);
  if (!resolved.startsWith(path.resolve(baseDir))) {
    throw new Error(`Screenshot name "${name}" would write outside curated directory`);
  }
  return resolved;
}

/**
 * Capture a screenshot in one of two modes:
 *
 * **Regression mode** (default): Uses Playwright's `toHaveScreenshot()` to compare
 * against committed baselines. Baselines are stored in `e2e/screenshots/legal-lifecycle/`.
 *
 * **Curated mode** (`curated: true`): Saves a standalone PNG to
 * `documentation/screenshots/legal-vertical/` for use in walkthroughs and blog posts.
 */
export async function captureScreenshot(
  page: Page,
  name: string,
  options?: ScreenshotOptions
): Promise<void> {
  const { curated = false, fullPage = false, locator } = options ?? {};

  if (curated) {
    mkdirSync(CURATED_DIR, { recursive: true });
    const filePath = assertSafeName(name, CURATED_DIR);

    if (locator) {
      const buffer = await locator.screenshot();
      writeFileSync(filePath, buffer);
    } else {
      const buffer = await page.screenshot({ fullPage });
      writeFileSync(filePath, buffer);
    }
  } else {
    // fullPage is only valid for page-level assertions, not locator assertions
    if (locator) {
      await expect(locator).toHaveScreenshot(`${name}.png`);
    } else {
      await expect(page).toHaveScreenshot(`${name}.png`, {
        fullPage: fullPage ? true : undefined,
      });
    }
  }
}
```

### Strict lifecycle reference — `matter-team.spec.ts:1-98` (619E) and Day 5 header test `day-01-03-onboarding.spec.ts:336-416` (627K)
```ts
/**
 * Matter team — Team tab (Phase 88, Epic 619E; U2: G-B3, N-3; ADR-346)
 *
 * Add, transfer and remove on Details → Team, with the canManage gate mirrored in the UI:
 *   - a template matter with no lead chosen auto-adds its creator as lead (619C)
 *   - the lead (Alice, Owner) adds Carol (Member)
 *   - Carol, a plain member, sees the team but no management controls
 *   - Bob (Admin, NOT on the matter) transfers the lead to Carol
 *   - Carol, now the lead, adds Bob and removes him again
 *
 * Self-contained: creates its own run-scoped matter (no client) and asserts only on it.
 * Runs in the legal-lifecycle config (workers: 1) after day-90; it needs only a seeded stack.
 *
 * Run:
 *   E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 \
 *     pnpm test:e2e:legal-lifecycle e2e/tests/legal-lifecycle/matter-team.spec.ts
 */
import { test, expect, type Locator, type Page } from "@playwright/test";
import { randomBytes } from "node:crypto";
import { loginAs } from "../../fixtures/auth";
import { BASE, escapeRegExp, openGroupedTab, openMatter } from "../../helpers/legal-depth-ii";

// Module-level: a failed serial group restarts the worker and re-runs the whole group with a new id.
const RUN_ID = `${Date.now().toString(36)}${randomBytes(2).toString("hex")}`;
const MATTER_NAME = `Team spec ${RUN_ID}`;

/** Opens this run's matter and its Details → Team sub-tab (clicked, not ?tab=team — 619D stickiness). */
async function openTeamTab(page: Page): Promise<Locator> {
  await openMatter(page, MATTER_NAME);
  await openGroupedTab(page, "details", "team");
  const panel = page.getByTestId("matter-team-panel");
  await expect(panel.first()).toBeVisible({ timeout: 15_000 });
  return panel;
}

function memberRow(panel: Locator, name: string): Locator {
  return panel.getByRole("row", { name: new RegExp(escapeRegExp(name)) });
}

/**
 * Adds an org member through the Add Member dialog and waits for their row.
 * Row changes commit with the revalidated RSC. The 15 s waits in this file stay
 * deliberately below the 30 s bell poll: a stall means the next@16.2.6 lost-ping
 * regression is back (fixed by next 16.3.8, #1651).
 */
async function addMember(page: Page, panel: Locator, name: string): Promise<void> {
  await panel.getByRole("button", { name: "Add Member" }).click();
  const dialog = page.getByRole("dialog", { name: "Add Member" });
  await expect(dialog).toBeVisible();
  await dialog.getByRole("option", { name: new RegExp(escapeRegExp(name)) }).click();
  await expect(dialog).toBeHidden({ timeout: 15_000 });
  await expect(memberRow(panel, name).first()).toBeVisible({ timeout: 15_000 });
}

test.describe.serial("Matter team — Team tab (add, transfer, remove)", () => {
  test("Alice: template matter auto-adds her as lead; she adds Carol", async ({ page }) => {
    test.setTimeout(60_000);
    await loginAs(page, "alice");
    await page.goto(`${BASE}/matters`);
    await page.getByRole("button", { name: "New from Template" }).click();
    const dialog = page.getByRole("dialog", { name: /New from Template/ });
    await expect(dialog).toBeVisible();
    await dialog.getByRole("option", { name: /Litigation \(Personal Injury/ }).click();
    await dialog.getByRole("button", { name: "Next" }).click();
    await dialog.getByLabel(/Matter name/).fill(MATTER_NAME);
    // Client "None", Matter lead "Unassigned" → the creator is auto-added as lead (619C)
    await dialog.getByRole("button", { name: "Create Matter" }).click();
    await page.waitForURL(/\/matters\/[0-9a-f-]{36}/, { timeout: 15_000 });
    await expect(page.getByRole("heading", { level: 1, name: MATTER_NAME })).toBeVisible({
      timeout: 15_000,
    });

    await openGroupedTab(page, "details", "team");
    const panel = page.getByTestId("matter-team-panel");
    await expect(panel.first()).toBeVisible({ timeout: 15_000 });
    const aliceRow = memberRow(panel, "Alice Owner");
    await expect(aliceRow.first()).toBeVisible();
    // The lead row has no row menu (Owner badge shows the org role, so lead-ness is the menu's absence)
    await expect(aliceRow.getByRole("button", { name: "Open menu" })).toHaveCount(0);

    await addMember(page, panel, "Carol Member");
    await expect(
      memberRow(panel, "Carol Member").getByText("Member", { exact: true }).first()
    ).toBeVisible();
  });

  test("Carol (Member): sees the matter and its team, but no management controls", async ({
    page,
  }) => {
    await loginAs(page, "carol");
    const panel = await openTeamTab(page);
    await expect(memberRow(panel, "Carol Member").first()).toBeVisible();
    await expect(memberRow(panel, "Alice Owner").first()).toBeVisible();
    await expect(panel.getByRole("button", { name: "Add Member" })).toHaveCount(0);
    await expect(panel.getByRole("button", { name: "Open menu" })).toHaveCount(0);
  });

  test("Bob (Admin, not on the matter): transfers the lead to Carol", async ({ page }) => {
```
```ts
  // ── Day 1 team setup (Phase 88 619E, ADR-346): the lead adds Carol to the matter team ──
  // Org Members see only matters they are on. Without this step Carol's Day 7
  // time-recording test found no Sipho matter and skipped (OQ #11).
  test("Alice: Add Carol to the Sipho matter team (Team tab)", async ({ page }) => {
    test.setTimeout(60_000);
    await loginAs(page, "alice");
    await openMatter(page, `Ndlovu v RAF ${RUN_ID}`);
    await openGroupedTab(page, "details", "team");
    const panel = page.getByTestId("matter-team-panel");
    await expect(panel.first()).toBeVisible({ timeout: 15_000 });

    await panel.getByRole("button", { name: "Add Member" }).click();
    const addDialog = page.getByRole("dialog", { name: "Add Member" });
    await expect(addDialog).toBeVisible();
    await addDialog.getByRole("option", { name: /Carol Member/ }).click();
    await expect(addDialog).toBeHidden({ timeout: 15_000 });
    // The row commits with the revalidated RSC. 15 s stays deliberately below the 30 s
    // bell poll: a stall here means the next@16.2.6 lost-ping regression is back
    // (fixed by next 16.3.8, #1651).
    await expect(panel.getByRole("row", { name: /Carol Member/ }).first()).toBeVisible({
      timeout: 15_000,
    });
  });

  // ── Day 5 (scenario 5.1–5.2): matter header facts strip (Phase 88 627K; §13.3.17 D9, G-B9) ──
  // Sits in the Day 1 group because it needs this file's run-scoped matter: a failure earlier in
  // the file restarts the worker with a new RUN_ID. The strip (627A) renders the legal-za header
  // slugs (HEADER_FIELD_SLUGS.PROJECT) only once they hold a value, and no seed or create path
  // sets them, so the test fills them on the Custom fields tab first (the only UI write path).
  test("Day 5 — Alice: Matter header shows case number, court and opposing party", async ({
    page,
  }) => {
    test.setTimeout(60_000);
    const caseNumber = "2026/12345";
    const court = "Gauteng Division, Pretoria";
    const opposingParty = "Road Accident Fund";

    await loginAs(page, "alice");
    await openMatter(page, `Ndlovu v RAF ${RUN_ID}`);

    const strip = page.getByTestId("matter-header-card").getByTestId("matter-header-fields");
    // A new matter has none of the three values, so the strip is not rendered at all
    await expect(strip).toHaveCount(0);

    await openGroupedTab(page, "details", "fields");
    const fieldsTab = page.getByTestId("matter-fields-tab");
    await expect(fieldsTab.getByText("SA Legal — Matter Details").first()).toBeVisible({
      timeout: 15_000,
    });
    await fieldsTab.getByLabel("Case Number", { exact: true }).fill(caseNumber);
    await fieldsTab.getByLabel("Court", { exact: true }).fill(court);
    await fieldsTab.getByLabel("Opposing Party", { exact: true }).fill(opposingParty);
    await fieldsTab.getByRole("button", { name: "Save Custom Fields" }).click();
    await expect(fieldsTab.getByText("Saved successfully")).toBeVisible({ timeout: 15_000 });

    // The save revalidates the matter page, so the header updates in place. 15 s stays below
    // the 30 s bell poll: a stall means the lost-ping regression is back (next 16.3.8, #1651).
    // Per-value assertions: the dt/dd text nodes have no whitespace between them, so the
    // strip's textContent is "Case no.2026/12345·Court…", not the visual "Case no. 2026/12345 · …".
    // toHaveText does not check visibility, so each value is also asserted visible
    const headerValues = strip.locator("dd");
    await expect(headerValues).toHaveText([caseNumber, court, opposingParty], {
      timeout: 15_000,
    });
    for (let i = 0; i < 3; i++) await expect(headerValues.nth(i)).toBeVisible();
    await expect(strip.getByTestId("matter-header-field-case_number").locator("dt")).toHaveText(
      "Case no."
    );

    // 5.1–5.2: a fresh load of the matter shows the persisted facts under the title
    await page.reload();
    await expect(
      page.getByRole("heading", { level: 1, name: `Ndlovu v RAF ${RUN_ID}` })
    ).toBeVisible({ timeout: 15_000 });
    await expect(headerValues).toHaveText([caseNumber, court, opposingParty]);
    for (let i = 0; i < 3; i++) await expect(headerValues.nth(i)).toBeVisible();
  });
});

// ═══════════════════════════════════════════════════════════════════
//  DAY 2 — Apex Holdings + Moroka Family Trust
```

### Strict fee-note generation → draft → approve → send — `legal-depth-ii/day-75-85-closure.spec.ts:94-224`
```ts
test.describe.serial("Day 75-85 — Matter Closure (Phase 67)", () => {
  // ── 75 pre-state: a clean matter (final bill sent, nothing open) ──
  test("[setup] Alice (Thandi): Create client Apex Holdings and start FICA onboarding", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    // Billing address + tax number: fee-note generation and sending require them
    await createClient(page, {
      name: apexClient(),
      type: "Company",
      email: `apex.holdings.${attemptRunId()}@example.com`,
      taxNumber: "9012345678",
    });
    // New matters cannot be opened for a PROSPECT client
    await startClientOnboarding(page);
  });

  test("[setup] Alice (Thandi): Open a clean commercial matter for Apex Holdings", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/matters`);
    // "New Matter" (not "New from Template"): no template → no open tasks
    await page.getByRole("button", { name: "New Matter" }).click();
    const dialog = page.getByRole("dialog", { name: "Create Matter" });
    await expect(dialog).toBeVisible();
    await dialog.getByLabel("Name", { exact: true }).fill(cleanMatterName());
    await dialog
      .getByLabel(/^Description/)
      .fill("Review of the Apex Holdings shareholders' agreement ahead of the 2026 AGM.");
    await selectNativeOption(dialog.getByLabel(/^Client/), apexClient());
    await dialog.getByLabel(/^Work Type/).fill("COMMERCIAL");
    await dialog.getByRole("button", { name: "Create Matter" }).click();
    await expect(dialog).toBeHidden({ timeout: 15_000 });

    await openMatter(page, cleanMatterName());
    await expect(page.locator("main")).toContainText(apexClient());
    await expect(page.locator("main")).toContainText("COMMERCIAL");
  });

  test("[setup] Bob: Record + approve counsel's fee disbursement on the Apex matter", async ({
    page,
  }) => {
    await loginAs(page, "bob");
    await openDisbursementList(page, cleanMatterName());
    await page.getByTestId("create-disbursement-trigger").click();
    const dialog = page.getByTestId("create-disbursement-dialog");
    await expect(dialog).toBeVisible();
    await selectNativeOption(dialog.getByLabel("Matter"), cleanMatterName());
    await selectNativeOption(dialog.getByLabel("Client"), apexClient());
    await dialog.getByLabel("Category").selectOption("COUNSEL_FEES");
    await expect(dialog.getByLabel("VAT Treatment")).toHaveValue("STANDARD_15");
    await dialog.getByLabel("Description").fill(counselFeeDescription());
    await dialog.getByTestId("disbursement-amount-input").fill("3500.00");
    await dialog.getByLabel("Incurred Date").fill(isoDate());
    await dialog.getByLabel("Supplier", { exact: true }).fill("Adv. N. Mokoena SC");
    await dialog.getByRole("button", { name: "Create Disbursement" }).click();
    await expect(dialog).toBeHidden({ timeout: 15_000 });

    const row = page
      .locator('[data-testid^="disbursement-row-"]')
      .filter({ hasText: counselFeeDescription() });
    await expect(row).toContainText("Draft", { timeout: 15_000 });
    // Ready once its detail page has rendered
    const detail = page.getByTestId("disbursement-detail");
    await clickThrough(row, detail.getByText(counselFeeDescription(), { exact: true }));
    await expect(page).toHaveURL(/\/legal\/disbursements\/[0-9a-f-]{36}$/);
    await detail.getByTestId("disbursement-submit-for-approval-button").click();
    await expect(detail.getByTestId("disbursement-approval-panel")).toBeVisible({
      timeout: 15_000,
    });
    await detail.getByTestId("disbursement-approve-button").click();
    const approve = page.getByTestId("disbursement-approve-dialog");
    await approve.getByTestId("disbursement-confirm-approve-button").click();
    await expect(approve).toBeHidden({ timeout: 15_000 });
    await expect(detail.getByText("Approved", { exact: true })).toBeVisible();
  });

  test("[setup] Alice (Thandi): Bill the Apex matter — fee note drafted, approved and sent", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    await openClient(page, apexClient());
    await openGroupedTab(page, "finance", "invoices");
    const panel = page.getByRole("tabpanel");
    const newFeeNote = panel.getByRole("button", { name: "New Fee Note" });
    await expect(newFeeNote).toBeVisible({ timeout: 15_000 });
    const feeNoteLinks = () =>
      panel
        .locator('a[href*="/invoices/"]')
        .evaluateAll((els) => els.map((e) => e.getAttribute("href")!));
    const before = new Set(await feeNoteLinks());

    await newFeeNote.click();
    // Phase 87 611B (ADR-338): label amendment
    const generate = page.getByRole("dialog", { name: /Generate fee note|Select Unbilled Items/ });
    await expect(generate).toBeVisible({ timeout: 15_000 });
    await generate.getByRole("button", { name: "Fetch Unbilled Time" }).click();
    await expect(generate.getByTestId("disbursement-selection-section")).toContainText(
      counselFeeDescription(),
      { timeout: 15_000 }
    );
    await generate.getByRole("button", { name: "Validate & Create Draft" }).click();
    await generate.getByRole("button", { name: /^Create Draft/ }).click();
    await expect(generate).toBeHidden({ timeout: 15_000 });

    // The created fee note is the one link that was not listed before (not "first Draft row")
    await expect
      .poll(async () => (await feeNoteLinks()).filter((href) => !before.has(href)), {
        timeout: 15_000,
      })
      .toHaveLength(1);
    const created = (await feeNoteLinks()).find((href) => !before.has(href))!;
    expect(created).toMatch(/\/invoices\/[0-9a-f-]{36}$/);
    const createdRow = panel.getByRole("row").filter({ has: page.locator(`a[href="${created}"]`) });
    await expect(createdRow).toContainText("Draft");
    await clickThrough(
      createdRow.locator(`a[href="${created}"]`).first(),
      page.locator("main").getByText(counselFeeDescription()).first()
    );
    await expect(page).toHaveURL(new RegExp(`${escapeRegExp(created)}$`));
    await expect(page.locator("main")).toContainText(cleanMatterName());

    await page.getByRole("button", { name: "Approve" }).click();
    await expect(page.getByRole("button", { name: "Send Fee Note" })).toBeVisible({
      timeout: 15_000,
    });
    await page.getByRole("button", { name: "Send Fee Note" }).click();
    await expect(page.locator("main").getByText("Sent", { exact: true })).toBeVisible({
      timeout: 15_000,
```

### Template matter + time on a task — `legal-depth-ii/day-05-disbursements.spec.ts:115-136`, `day-30-45-soa-writeoff.spec.ts:162-190`
```ts
  test("[setup] Alice (Thandi): Open Sipho's litigation matter from the Litigation template", async ({
    page,
  }) => {
    const matterName = siphoMatterName();
    await loginAs(page, "alice");
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/matters`);
    await page.getByRole("button", { name: "New from Template" }).click();

    const dialog = page.getByRole("dialog", { name: /New from Template/ });
    await expect(dialog).toBeVisible();
    await dialog.getByRole("option", { name: /Litigation \(Personal Injury/ }).click();
    await dialog.getByRole("button", { name: "Next" }).click();

    await dialog.getByLabel(/Matter name/).fill(matterName);
    await selectNativeOption(dialog.getByLabel(/^Client/), siphoClientName());
    await dialog.getByRole("button", { name: "Create Matter" }).click();

    // Phase 87 S4 (ADR-344): route rename (610D — post-redirect URL)
    await page.waitForURL(/\/matters\/[0-9a-f-]{36}/, { timeout: 15_000 });
    await expect(page.getByRole("heading", { level: 1, name: matterName })).toBeVisible();
    await expect(page.locator("main")).toContainText(siphoClientName());
```
```ts
  // ── Day 7 prerequisite: fee-earning time on Sipho's matter ────────
  test("[setup] Alice (Thandi): Log 2h on Sipho's matter — letter of demand", async ({ page }) => {
    await loginAs(page, "alice");
    await openMatter(page, siphoMatterName());
    await openGroupedTab(page, "work", "tasks");

    const taskRow = page.getByRole("row").filter({ hasText: "Letter of demand" }).first();
    await expect(taskRow).toBeVisible({ timeout: 15_000 });
    await taskRow.getByRole("button", { name: "Log Time" }).click();

    const dialog = page.getByRole("dialog", { name: "Log Time" });
    await expect(dialog).toBeVisible();
    await dialog.locator("#time-hours").fill("2");
    await dialog.locator("#time-minutes").fill("0");
    await dialog.locator("#time-date").fill(isoDate());
    await dialog.locator("#time-description").fill(timeDescription());
    await expect(dialog.locator("#time-billable")).toBeChecked();
    await dialog.getByRole("button", { name: "Log Time" }).click();
    await expect(dialog).toBeHidden({ timeout: 15_000 });

    // Finance → Time summarises the entry by task and by member (a retried group adds to
    // the first attempt's 2h; the entry itself is checked line-by-line in the statement)
    await openGroupedTab(page, "finance", "time");
    const timePanel = page.getByRole("tabpanel");
    const hours = test.info().retry === 0 ? "2h" : "\\d+h";
    await expect(timePanel).toContainText(new RegExp(`Letter of demand\\s*${hours}`), {
      timeout: 15_000,
    });
    await expect(timePanel).toContainText(new RegExp(`Alice Owner\\s*${hours}`));
```

### Trust account (create-if-absent, sanctioned `.or()` branch) + deposit — `legal-depth-ii/day-05-disbursements.spec.ts:196-237`
```ts
  // ── 5.9 prerequisite: approved trust payment on Sipho's matter ────
  test("[setup] Alice + Bob: Fund Sipho's trust ledger and approve a deeds-office trust payment", async ({
    page,
  }) => {
    // Alice (Thandi): make sure the firm has a trust account (the seed creates none; a
    // previous depth-II run on the same stack may already have added it)
    await loginAs(page, "alice");
    await page.goto(`${BASE}/trust-accounting`);
    const addAccount = page.getByRole("button", { name: "Add Account" });
    const recordTransaction = page.getByRole("link", { name: "Record Transaction" });
    await expect(addAccount.or(recordTransaction).first()).toBeVisible({ timeout: 15_000 });
    if (await addAccount.isVisible()) {
      await addAccount.click();
      const account = page.getByRole("dialog", { name: "Add Trust Account" });
      await account.getByLabel("Account Name").fill("Mathebula & Partners Trust Account");
      await account.getByLabel("Bank Name").fill("Standard Bank");
      await account.getByLabel("Branch Code").fill("051001");
      await account.getByLabel("Account Number").fill("0123456789");
      await account.getByRole("button", { name: "Create Account" }).click();
      await expect(account).toBeHidden({ timeout: 15_000 });
      await expect(recordTransaction).toBeVisible({ timeout: 15_000 });
    }

    // Alice: R5,000 client deposit into trust for Sipho's matter
    await openTrustTransactions(page);
    await page.getByTestId("record-transaction-button").click();
    await page.getByRole("menuitem", { name: "Record Deposit" }).click();
    const deposit = page.getByRole("dialog", { name: "Record Deposit" });
    await deposit.getByRole("combobox").first().click();
    await page
      .getByRole("option", { name: new RegExp(`^${escapeRegExp(siphoClientName())}(\\s|$)`) })
      .click();
    await deposit.getByRole("combobox").nth(1).click();
    await page.getByRole("option", { name: new RegExp(escapeRegExp(siphoMatterName())) }).click();
    await deposit.getByLabel("Amount").fill(TRUST_DEPOSIT_AMOUNT);
    await deposit.getByLabel("Reference").fill(`DEP/${currentDepthIiRun()}`);
    await deposit.getByLabel(/Description/).fill("Client deposit — RAF claim disbursements");
    await deposit.getByRole("button", { name: "Record Deposit" }).click();
    await expect(deposit).toBeHidden({ timeout: 15_000 });
    await expect(
      page.locator("main tbody tr", { hasText: `DEP/${currentDepthIiRun()}` })
    ).toContainText("RECORDED", { timeout: 15_000 });
```

### Current A2 spec — `day-30-billing.spec.ts:32-156` (to be replaced)
```ts
  test("Alice: Create fee note for Sipho Ndlovu (hourly + tariff + disbursement)", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/invoices`);
    await expect(page.locator("main")).toBeVisible({ timeout: 10_000 });

    const newBtn = page.getByRole("button", { name: /New (Invoice|Fee Note)/i }).first();
    const hasNew = await newBtn.isVisible({ timeout: 5000 }).catch(() => false);

    if (!hasNew) {
      test.skip(true, "New Fee Note button not found");
      return;
    }

    await newBtn.click();
    await page.waitForTimeout(1000);

    // Select client for the fee note
    const clientField = page.getByRole("combobox", { name: /customer|client/i }).first();
    const hasClient = await clientField.isVisible({ timeout: 3000 }).catch(() => false);
    if (hasClient) {
      await clientField.click();
      await page.waitForTimeout(500);
      const siphoOption = page.getByRole("option", { name: /Sipho|Ndlovu/i }).first();
      const hasSipho = await siphoOption.isVisible({ timeout: 3000 }).catch(() => false);
      if (hasSipho) {
        await siphoOption.click();
      }
    }

    // Select matter/project
    const projectField = page.getByRole("combobox", { name: /project|matter/i }).first();
    const hasProject = await projectField.isVisible({ timeout: 3000 }).catch(() => false);
    if (hasProject) {
      await projectField.click();
      await page.waitForTimeout(500);
      const matterOption = page.getByRole("option", { name: /Ndlovu|RAF/i }).first();
      const hasMatter = await matterOption.isVisible({ timeout: 3000 }).catch(() => false);
      if (hasMatter) {
        await matterOption.click();
      }
    }

    // Create the fee note
    const createBtn = page.getByRole("button", { name: /Create|Save|Generate/i }).first();
    const hasCreate = await createBtn.isVisible({ timeout: 3000 }).catch(() => false);
    if (hasCreate) {
      await createBtn.click();
      await page.waitForTimeout(3000);
    }

    // If we're on the fee note detail page, add line items
    // Look for "Add Line" or similar
    const addLineBtn = page.getByRole("button", { name: /add.*line|new.*line|add.*item/i }).first();
    const hasAddLine = await addLineBtn.isVisible({ timeout: 5000 }).catch(() => false);
    if (hasAddLine) {
      // Add tariff line (LSSA)
      await addLineBtn.click();
      await page.waitForTimeout(1000);

      const descField = page.getByRole("textbox", { name: /description/i }).first();
      const hasDesc = await descField.isVisible({ timeout: 3000 }).catch(() => false);
      if (hasDesc) {
        await descField.fill("LSSA Tariff — Letter of Demand (Item A)");
      }

      const saveLineBtn = page.getByRole("button", { name: /save|add|create/i }).first();
      const hasSaveLine = await saveLineBtn.isVisible({ timeout: 3000 }).catch(() => false);
      if (hasSaveLine) {
        await saveLineBtn.click();
        await page.waitForTimeout(1000);
      }

      // Add disbursement line (sheriff R350)
      await addLineBtn.click();
      await page.waitForTimeout(1000);

      const descField2 = page.getByRole("textbox", { name: /description/i }).first();
      const hasDesc2 = await descField2.isVisible({ timeout: 3000 }).catch(() => false);
      if (hasDesc2) {
        await descField2.fill("Disbursement — Sheriff service fee");
      }

      const amountField = page
        .getByRole("textbox", { name: /amount/i })
        .or(page.getByRole("spinbutton", { name: /amount/i }))
        .first();
      const hasAmount = await amountField.isVisible({ timeout: 3000 }).catch(() => false);
      if (hasAmount) {
        await amountField.fill("350");
      }

      const saveLineBtn2 = page.getByRole("button", { name: /save|add|create/i }).first();
      const hasSaveLine2 = await saveLineBtn2.isVisible({ timeout: 3000 }).catch(() => false);
      if (hasSaveLine2) {
        await saveLineBtn2.click();
        await page.waitForTimeout(1000);
      }
    }

    // Verify LSSA tariff amounts auto-populated (soft assertion — feature may not be implemented)
    expect
      .soft(
        await page
          .getByText(/tariff|lssa/i)
          .isVisible({ timeout: 3000 })
          .catch(() => false)
      )
      .toBeTruthy();

    // Verify VAT 15% calculation (soft assertion — feature may not be implemented)
    expect
      .soft(
        await page
          .getByText(/15%|vat/i)
          .isVisible({ timeout: 3000 })
          .catch(() => false)
      )
      .toBeTruthy();

    // Screenshot: fee note with tariff lines
    await captureScreenshot(page, "day-30-fee-note-tariff-completed");
    await captureScreenshot(page, "fee-note-tariff-disbursement", { curated: true });
  });
```

## Conventions

### `frontend/CLAUDE.md` › Testing (verbatim)
```
### Testing

- **Vitest** with `@testing-library/react` and `happy-dom`
- Config in `vitest.config.ts` — needs `resolve.alias` for `@/*` path alias
- Test files: colocated (`*.test.tsx`) or in `__tests__/` directory
- Radix UI components leak DOM between tests — always add `afterEach(() => cleanup())` in test files using Dialog/AlertDialog
- Use unique trigger text per test file to avoid "multiple elements found" errors
- If any tests are failing, make sure they pass before merging, even if you think they are existing issues

```

### `frontend/CLAUDE.md` › Anti-Patterns (verbatim)
```
## Anti-Patterns — Never Do This

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
```

### `frontend/CLAUDE.md` › ESLint (verbatim)
```
### ESLint

- Flat config (`eslint.config.mjs`)
- Extends `eslint-config-next/core-web-vitals` and `eslint-config-next/typescript`
- `no-restricted-imports` bans the deleted terminology modules (ADR-338); keep all restricted paths in that one config object (a later flat-config object setting the same rule replaces, not merges)
- Run with `pnpm run lint`

```

### `frontend/e2e/AUTOMATION-NOTES.md` excerpts (current, verbatim — note the stale Tab Navigation snippet)
```
## Authentication

### loginAs() Fixture (Preferred for Tests)

Use `e2e/fixtures/auth.ts` → `loginAs(page, 'alice' | 'bob' | 'carol')`.
This sets a JWT cookie directly — **no UI interaction needed**.

```typescript
import { loginAs } from "../fixtures/auth";

test("example", async ({ page }) => {
  await loginAs(page, "alice");
  await page.goto("/org/e2e-test-org/dashboard");
  // Now authenticated as Alice (owner)
});
```

```
```
## Common UI Patterns for Automation

### Dialog Pattern

All create/edit dialogs follow the same pattern:

1. Click trigger button (e.g., "New Customer", "Add Rate")
2. Dialog opens with `dialog "{Title}"` role
3. Fill form fields
4. Click primary action button (e.g., "Create Customer", "Create Rate")
5. Dialog auto-closes on success
6. Close button (`button "Close"` with X icon) always top-right

### Tab Navigation

Project detail and customer detail pages use tab panels:

```typescript
await page.getByRole("tab", { name: "Tasks" }).click();
await page.getByRole("tabpanel", { name: "Tasks" }).waitFor();
```

### Sidebar Navigation

```typescript
await page.getByRole("link", { name: "Projects" }).click();
```

Active link has `[active]` state. All links in `navigation "Main navigation"`.

### Empty State Pattern

Pages with no data show a centered empty state with:

- Icon image
- `heading` describing empty state
- `paragraph` with CTA text
- Often a `button` to create first item

### Filter Pills / Status Tabs

Many pages have filter options as link pills or tabs:

```typescript
// Link-based filters (Customers, Invoices, Retainers)
await page.getByRole("link", { name: "Active" }).click();

// Button-based filters (Recurring Schedules)
await page.getByRole("button", { name: "Active" }).click();

// Tab-based filters (Tasks "All" view)
await page.getByRole("tab", { name: "All" }).click();
```

### Date Inputs

Date fields use native HTML `<input type="date">` with format `YYYY/MM/DD`:

```typescript
await page.getByRole("textbox", { name: "Effective From" }).fill("2026-02-23");
```

### Combobox / Select Dropdowns

Two patterns:

1. **Native select** (e.g., Customer Type): `combobox "Type"` with `option` children
2. **Custom combobox** (e.g., Customer dropdown): `combobox` with text content, opens a listbox on click

---

## Known Issues & Gotchas

1. **Seed doesn't complete ACTIVE transition** — Acme Corp stays in ONBOARDING. Tests must complete the 4 checklist items manually.

2. **Console errors on page load** — Two React hydration errors appear (Minified React error #418, TypeError null reading). These are hydration mismatches and don't block functionality.

3. **"No org" badge in header** — The header shows "No org" text even though the org is active. This is a display issue with the mock auth provider not populating all org metadata.

4. **Date format varies** — Some date inputs show `yyyy/mm/dd` placeholder, others show `YYYY-MM-DD`. Use ISO format (`2026-02-23`) when filling.

5. **Create Retainer button disabled** — The "Create Retainer" button stays disabled until a customer is selected from the dropdown. The customer must be ACTIVE.

6. **Time is logged from task detail, not project Time tab** — The project Time tab is read-only (shows summaries). Time entry creation happens from within task detail panels.

---
```

### Root `CLAUDE.md` rules that bite here
- §1: frontend changes → `pnpm lint && pnpm build && pnpm test` green; plus `format:check` (CI runs it; memory `feedback_frontend_verify_includes_prettier`).
- §3: PASS means observed — the fresh-stack run log is the evidence; "exit 0 ≠ evidence" (617A lesson: soft-skips exit 0).
- §4: reproduce before fix. Each rewritten flow must be observed failing (or no-op'ing) before and passing after on :3001.
- §6: no edits to `qa/testplan/demos/*.md`.
- §7: one fix per PR; scope expansion (e.g. a product bug found while driving a real flow) → stop and report as an OD, do not patch product code.
- §9: no loopholes. Removing `.catch` but keeping `if (visible) { act }` is a loophole.

## Build & Verify

From `/Users/rakheendama/Projects/2026/worktree-epic-628E/frontend` (run `pwd` first: worktree, not the main checkout):
```bash
mkdir -p /tmp/628E
NODE_OPTIONS="" /opt/homebrew/bin/pnpm install > /dev/null 2>&1
NODE_OPTIONS="" /opt/homebrew/bin/pnpm run lint > /tmp/628E/lint.log 2>&1; echo LINT=$?
NODE_OPTIONS="" /opt/homebrew/bin/pnpm run format:check > /tmp/628E/fmt.log 2>&1; echo FMT=$?
NODE_OPTIONS="" /opt/homebrew/bin/pnpm test > /tmp/628E/test.log 2>&1; echo TEST=$?; tail -5 /tmp/628E/test.log
# the new rule's tests alone (inner loop)
NODE_OPTIONS="" /opt/homebrew/bin/pnpm exec vitest run __tests__/eslint-rules > /tmp/628E/rule-test.log 2>&1; echo RULE=$?
# rule on the legal dirs only (red-first count before the sweep, 0 after)
NODE_OPTIONS="" /opt/homebrew/bin/pnpm exec eslint e2e/tests/legal-lifecycle e2e/tests/legal-depth-ii e2e/helpers e2e/fixtures > /tmp/628E/e2e-lint.log 2>&1; echo E2ELINT=$?
# e2e has no own tsconfig; frontend/tsconfig.json includes **/*.ts, so type-check the e2e code via the project:
NODE_OPTIONS="" /opt/homebrew/bin/pnpm exec tsc --noEmit -p tsconfig.json > /tmp/628E/tsc.log 2>&1; echo TSC=$?
# the merge bar also needs `pnpm run build` (FE-GATE) — the orchestrator runs it (`next build` breaks in a worktree holding backend/target; memory 628C).
```
- `pnpm lint` = bare `eslint` → lints the whole `frontend/` tree with the flat config, **including `e2e/`**. The local-rules object matches `**/*.{ts,tsx,js,jsx,mjs,cjs}`. vitest excludes `e2e/**` from test discovery only (`vitest.config.mts:8`); the rule source under `e2e/eslint/` is imported by a test in `__tests__/`, which is fine.
- **Playwright:** the builder does NOT run the full legal suites (the orchestrator owns fresh-stack runs). A single spec may be run against the live :3001 stack **only if the orchestrator says the stack is free**:
  ```bash
  cd /Users/rakheendama/Projects/2026/worktree-epic-628E/frontend
  E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 MAILPIT_URL=http://localhost:8026 NODE_OPTIONS="" \
    /opt/homebrew/bin/pnpm test:e2e:legal-lifecycle e2e/tests/legal-lifecycle/day-00-firm-setup.spec.ts --update-snapshots=changed --reporter=list
  # one test: add  -g "Trust account"
  ```
  - Pass Playwright flags with **no `--`**: `pnpm test:e2e:legal-lifecycle --update-snapshots=changed`. With `--`, pnpm forwards it and zero tests run.
  - File filters are regexes matched against the absolute path. Always anchor them with `e2e/tests/<dir>/` (R10 §D P-1).
  - Legal suites are serial chains: one failure aborts the rest of its describe ("did not run"). Later files depend on earlier files' records via run state (OD-5), so a single later file only works after Day 0–3 ran on the same stack.
  - Screenshot baselines are not committed. `--update-snapshots=changed` writes missing ones.
  - Runs can dirty tracked files (`documentation/screenshots/legal-vertical/*.png` via curated captures, `frontend/test-results`) → `git checkout -- documentation/screenshots frontend/test-results` (or `git checkout -- .` if nothing else is uncommitted) before committing.
  - The legal configs reject `E2E_AUTH_MODE != mock` (`playwright.legal-*.config.ts:4-8`); depth-ii defaults `MAILPIT_URL` to :8026.
- **Playwright config facts:**

| config | testDir | timeout | globalTimeout | retries | workers | snapshots |
|---|---|---|---|---|---|---|
| `e2e/playwright.config.ts` | `./tests` (all dirs; `testIgnore: **/keycloak/**` in mock) | 30 s (60 s KC) | 600 s | CI 1 / local 0 | default (KC 1) | default path |
| `e2e/playwright.legal-lifecycle.config.ts` | `./tests/legal-lifecycle` | 30 s | 600 s | CI 1 / 0 | 1 | `{testDir}/../../screenshots/legal-lifecycle/{arg}{ext}`, `maxDiffPixelRatio 0.01`, animations disabled, DSF 2 |
| `e2e/playwright.legal-depth-ii.config.ts` | `./tests/legal-depth-ii` | 30 s | 600 s | CI 1 / 0 | 1 | `…/screenshots/legal-depth-ii/…`, same expect |

- **Slice evidence the orchestrator will want** (628.13): fresh `e2e-down && e2e-up` → lifecycle + depth-ii logs, with a results table (file:line → resolved by amendment / real flow / fixme (+blocker) / seed-pending), and the carried-six table above as the PR body (cite R10 §A per row).
- **Reviewer checklist** (for the A/B reviewers): (1) zero `if (has…)` guards around flow steps remain in converted files; (2) every removed `test.skip` either became a real flow or an OD-ruled `test.fixme` with a FIXME tag + file:line; (3) no `waitForTimeout` added; (4) RBAC negatives use `toHaveCount(0)`; (5) every lookup of an earlier day's record uses the run-state name; (6) B-7: every changed title is listed in the PR body.

## Environment
- pnpm `/opt/homebrew/bin/pnpm`; always `NODE_OPTIONS=""` (user shell sets `--openssl-legacy-provider`); `SHELL=/bin/bash` prefix if a script `cd`s (zoxide alias).
- e2e stack: frontend :3001, backend :8081, mock IDP :8090, Postgres :5433 (`postgres`/`changeme`, db `app`), Mailpit :8026. Users alice (owner), bob (admin), carol (member); org `e2e-test-org`. `loginAs(page, "alice")` sets the `mock-auth-token` cookie (`frontend/e2e/fixtures/auth.ts`).
- Seed today (`compose/seed/seed.sh`): org, 3 members, client "Acme Corp" (ACTIVE, FICA done), matter "Website Redesign"; **no** trust account, invoices or rates. 628F adds two invoices, a trust account (OD-4 naming) and the portal rows.
- The orchestrator's baseline worktree `/Users/rakheendama/Projects/2026/worktree-628E-base` and `/tmp/628E/base/*` must not be touched; the e2e Docker stack is orchestrator-owned.
