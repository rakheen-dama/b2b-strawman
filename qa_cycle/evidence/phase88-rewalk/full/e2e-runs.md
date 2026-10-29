# 629.9 — E2E runs on a fresh mock-auth stack (:3001 / :8081 / portal :3002) — 2026-10-29/30

**Result: FAIL.** The E2E-LEGAL lifecycle leg failed (82 passed / 4 failed / 1 skipped / 17 did not run, against the 628I baseline of 101/0/3 on byte-identical code). Run 1 is the scored result (629C ruling R-1; the 629C rulings are listed in `gaps.md` § "629C orchestrator rulings"); there was no re-run to get green. The other five legs equal their 628I baselines. A diagnostic lifecycle re-run (below), on a fresh stack with the Playwright host on `TZ=UTC` as the one controlled change, went **101/0/3**: it confirms the zone dependency of G88-F48 / F49 and ran the 17 run-1 "did not run" tests (15 passed, 2 named fixmes skipped). It is unscored (629C ruling R-3): it does not change run 1's scored FAIL, the 17 tests stay **DEFERRED** for 629.9 (629C ruling R-1), and the former carried six are scored on run 1 only (629C ruling R-6: 4 of 6 resolved, `day-90-review.spec.ts:95` still carried, `:192` DEFERRED). The DEFERRED and still-carried items are re-scored by the lifecycle run of the G88-F48 follow-up fix PR (`gaps.md` § final arithmetic).

## Provenance

- **Stack SHA.** The images were built from worktree HEAD `20559c3d6849d9575b706bf142ff75454dc35909` (branch `phase88/12C-suites-markers-gaps`, = origin/main at chain start; `logs/629C-provenance.txt`).
  - `git diff --stat cb10c5d63..HEAD -- backend frontend portal gateway packages compose` printed nothing, so the code is the 628I gate code (`logs/629C-provenance.txt`, section "code diff vs 628I gate head cb10c5d63 (expect empty)").
  - Builder 1's docs commit `49784f360` landed at 22:07:29Z, during the run: after the three frontend legs and before the three portal legs. It touches only `qa_cycle/` (`git diff --stat 20559c3d6..49784f360 -- backend frontend portal gateway packages compose` is empty, re-run by builder 2). The portal legs serve the portal from the worktree, so they ran on that HEAD with the same product tree. The end-of-run provenance records `HEAD=49784f360…` for that reason.
- **Built from.** `e2e-up.sh` logged `Building from source at: /Users/rakheendama/Projects/2026/worktree-epic-629C` (`/tmp/629C-e2e/e2e-up.txt:3`, copied into `logs/629C-provenance.txt:36`).
- **Fresh stack.**
  - `e2e-down.sh` removed all seven containers and both volumes: `Volume docteams-e2e_e2e_postgres_data Removed` and `Volume docteams-e2e_e2e_localstack_data Removed` (`/tmp/629C-e2e/e2e-down.txt:33-34`).
  - The Docker build cache was 0 B at the start, so `e2e-up.sh` was a full image build: 21:52:58Z → 22:00:09Z (`logs/629C-chain.txt`).
  - **Seed:** `e2e-up.sh` printed `Seed completed (container exited)`. That branch (`compose/scripts/e2e-up.sh:105-108`) fires when the seed container has exited and been removed, and it does not read an exit code, so no numeric seed exit code was captured. The seed log ends `E2E Boot-Seed Complete!` and has 0 lines matching `error|fail` (`/tmp/629C-e2e/seed.txt`).
  - All six containers were healthy after `e2e-up` (`/tmp/629C-e2e/e2e-ps.txt`).
- **Window.** The chain started at 21:52:45Z and ended at 22:23:55Z (`logs/629C-chain.txt`; `logs/629C-provenance.txt` `start=` / `end=`).
  - Lifecycle ran 22:00:09Z → 22:03:28Z. The host / Chromium zone is SAST (UTC+2), so that is **00:00:09 → 00:03:28 SAST on 2026-10-30**. The e2e containers run in UTC with `TZ` unset, so for them it was still 2026-10-29.
  - The chain ran 23:52:45 SAST (2026-10-29) → 00:23:55 SAST (2026-10-30); **all six legs ran inside the 00:00–02:00 SAST window** in which the browser date and the server date differ (the first leg started at 00:00:09 SAST, after the 7-minute image build). This is the common cause of the lifecycle failures (`logs/629C-lifecycle-date-boundary-diagnosis.md` §0).
- **Power.** `pmset -g batt` showed "Now drawing from 'AC Power'", 100 %, charged, at both the start and the end (`logs/629C-provenance.txt`).
- **Statuses** are observed runs. Counts are copied from Playwright's own summary lines (`logs/629C-summary.txt`), not computed. Local retries are 0, so no leg can report "flaky".

## Commands

These are verbatim from `logs/629C-chain.txt` `BEGIN` lines. The env is inline only (the 628I harness lesson: nothing is exported). The chain set only `SHELL=/bin/bash NODE_OPTIONS=""` globally.

| Leg | cwd | Command |
|---|---|---|
| lifecycle | `frontend/` | `env E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-lifecycle --update-snapshots=all --reporter=list` |
| depth-ii | `frontend/` | `env E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-depth-ii --update-snapshots=all --reporter=list` |
| default | `frontend/` | `env E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e --update-snapshots=all --reporter=list` |
| login-bare | `portal/` | `env -u PLAYWRIGHT_BASE_URL -u E2E_AUTH_MODE -u BACKEND_URL -u PORTAL_CONTACT_EMAIL pnpm test:e2e:login-bare --reporter=list` |
| isolation | `frontend/` | `env KGOSI_EMAIL=contact@acme.example.com BACKEND_URL=http://localhost:8081 PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e e2e/tests/portal/ --reporter=list` |
| `test:e2e:portal` | `portal/` | `env -u PLAYWRIGHT_BASE_URL -u E2E_AUTH_MODE -u BACKEND_URL pnpm test:e2e:portal --update-snapshots=all --reporter=list` |

**Visual regression is not scored by these legs.** No baseline PNG is committed (`git ls-files frontend/e2e/screenshots portal/e2e/screenshots` lists 0 PNGs), and the snapshot directories are cleaned after each run (§ "Restore + stack state"). So `toHaveScreenshot()` (`frontend/e2e/helpers/screenshot.ts:65-67`) has no committed baseline to compare against, and the logs show it writing actuals (`A snapshot doesn't exist at … writing actual`: lifecycle 15, depth-ii 23, portal 32 lines). The task file prescribes `--update-snapshots=all` for exactly this reason (`tasks/phase88-demo-ready-root-cause-closure.md:938`: the baselines are untracked, `.gitkeep` only, and without the flag every missing baseline fails — 624I run 0). The 628E / 628F gate logs used `--update-snapshots=changed` (`qa_cycle/evidence/phase88-rewalk/628E/gate-final/lifecycle.log:3`, `…/628F/gate-final/depth.txt:3`); neither mode can fail a test on a screenshot difference. The PASS counts are functional results only. Visual quality is E.2b, scored from the walk (`exit-checkpoints.md`).

## Results

| Suite | Passed | Failed | Skipped | Flaky | Did not run | Exit | 628I baseline | Delta | Verdict | Log |
|---|---|---|---|---|---|---|---|---|---|---|
| E2E-LEGAL lifecycle | 82 | 4 | 1 | 0 | 17 | 1 | 101/0/3 | −19 passed, +4 failed, −2 skipped, +17 did not run (104 tests either way) | **FAIL** → G88-F48, G88-F49 (the 4th failure cascades from F48) | `logs/629C-lifecycle.txt` |
| E2E-LEGAL depth-ii | 39 | 0 | 4 | 0 | 0 | 0 | 39/0/4 | none | **PASS** (= baseline) | `logs/629C-depth-ii.txt` |
| E2E-DEFAULT | 159 | 0 | 18 | 0 | 0 | 0 | 159/0/18/0 | none | **PASS** (= baseline) | `logs/629C-default.txt` |
| E2E-PORTAL login-bare | 1 | 0 | 0 | 0 | 0 | 0 | 1/0 | none | **PASS** (= baseline) | `logs/629C-login-bare.txt` |
| E2E-PORTAL isolation (`portal-data-isolation.spec.ts`, `KGOSI_EMAIL`) | 2 | 0 | 0 | 0 | 0 | 0 | 2/0 | none | **PASS** (= baseline) | `logs/629C-isolation.txt` |
| E2E-PORTAL `test:e2e:portal` (chromium/responsive + portal-client-90day) | 32 | 0 | 1 | 0 | 0 | 0 | 32/0/1 | none | **PASS** (= baseline) | `logs/629C-portal.txt` |

Lifecycle: Playwright reported "Running 104 tests". 82 + 4 + 1 + 17 = 104, which equals 628I's 101 + 0 + 3. The unscored diagnostic re-run (Playwright host `TZ=UTC`) reported 101 passed / 0 failed / 3 skipped of 104 (`logs/629C-diag-summary.txt`; § "Diagnostic re-run").

The five baseline-equal legs also ran inside the split-date window (22:03Z → 22:24Z) and still equal their baselines. Builder 2 did not inspect why they are unaffected; that is not claimed here.

## Failures → gaps

Each failing title is listed with its Playwright error and its `error-context.md` excerpt (`/tmp/629C-e2e/artefacts/lifecycle/frontend_test-results/<dir>/error-context.md`; not committed). The root cause is in `logs/629C-lifecycle-date-boundary-diagnosis.md`. Builder 2 re-verified every cited file:line at HEAD and re-ran the SQL read-only (`logs/629C-lifecycle-db-reverify.txt`).

1. **`day-07-14-work-trust.spec.ts:167` "Alice: My Work lists today's Moroka time entry" → G88-F49 (LOW).**
   - Error: `getByText('Estate Late Peter Moroka mvu36zag0e84 — Report to Master of the High Court — initial filing')` not found (`:175`).
   - error-context: "Time Today" `0m` `/ 8h` (`:165-168`); the Today card shows `heading "No time recordings yet"` (`:573`); the weekly strip shows `button "Fri: 1.0 hours logged"` (`:188`); the Upcoming link is `diary?view=list&from=2026-10-30` (`:176`), i.e. `addDays(today, 1)` with a server "today" of 2026-10-29.
   - DB: the entry is dated `2026-10-30` (browser) and was created at `2026-10-29 22:01:20Z`.
2. **`day-07-14-work-trust.spec.ts:306` "Carol: Log additional time on the Sipho matter (letter of demand)" → G88-F49 (LOW).**
   - Error: `getByText('Ndlovu v RAF mvu36zag0e84 — Research case law — personal injury precedents')` not found (`:323`).
   - error-context: "Time Today" `0m` (`:140-142`); `button "Fri: 2.3 hours logged"` (`:163`); `heading "No time recordings yet"` (`:289`).
   - DB: both Sipho entries are dated `2026-10-30`. Side effect: `:330` "Trust dashboard overview" did not run (serial describe).
3. **`day-60-interest.spec.ts:96` "Alice: Interest run — create for the last 30 days and calculate allocations at the 6.5% LPFF rate" → G88-F48 (HIGH).**
   - Error: the Moroka row was expected to contain `7 790,32`, and Playwright received `"…R 7 516,13R 41,49ArrangementR 20,75R 20,74"` (`:128`).
   - error-context: `"Period: 2026-09-30 to 2026-10-30"` (`:222`); the Moroka row reads `R 7 516,13 R 41,49 Arrangement R 20,75 R 20,74` (`:242-245`); the QuickCollect row reads `R 1 451,61 R 8,01` (`:235-238`), which matches the expected value. QuickCollect is the control: it has no server-dated posting.
   - DB: Moroka DEPOSIT R 250 000 is dated `2026-10-30` (browser); its FEE_TRANSFER R 8 500 is dated `2026-10-29` (server `LocalDate.now()`, UTC). Moroka's balance as of 2026-10-29, computed with the product's own `calculateClientBalanceAsOfDate` rules, is **−8 500,00**.
4. **`day-90-review.spec.ts:95` "Alice: Fee notes list shows the run's five fee notes" → cascade of G88-F48 (no own id).**
   - Error: `Run-state value "siphoFeeNote2" was not recorded` (`helpers/legal-lifecycle.ts:166`, called from `day-90-review.spec.ts:108`).
   - `siphoFeeNote2` is recorded only at `day-60-interest.spec.ts:269`, inside test `:252` ("Second billing cycle"). That test sits in the same `test.describe.serial` block (`:84`) as failure 3, so it did not run.
   - The fee-notes-list assertion itself was **not exercised**.

**Harness reading (629C ruling R-2).** The spec dates every entry with `isoDate()` (`frontend/e2e/helpers/legal-depth-ii.ts:94-100`, host-local `new Date()`), and the lifecycle config sets no `timezoneId` (`frontend/e2e/playwright.legal-lifecycle.config.ts:26-39`). That mirrors the product's own browser defaults:
- Log Time: `components/tasks/log-time-dialog.tsx:58` `new Date().toLocaleDateString("en-CA")`;
- Record Deposit: `components/trust/RecordDepositDialog.tsx:48-57`, browser-local date.

A real SA user's browser produces the same dates. The defect is the product's server-side "today" (`my-work/page.tsx:109`, `TrustTransactionService.java:622`), which disagrees with its own client defaults. So this is **not a harness defect**. Pinning `timezoneId` would hide F48 / F49, not fix them (diagnosis, "Harness hardening").

## Skipped tests vs the named 628I sets (ruling O-14)

The 628I sets are at `qa_cycle/evidence/phase88-rewalk/628I/gate.md:23-31`. Test lines below are the ones Playwright prints; the fixme call sits a few lines lower in each test body. **0 soft-skips.** No skip falls outside the named sets.

| Suite | 628I set | Run 1 | Tests (run 1) | Match | Gap row |
|---|---|---|---|---|---|
| lifecycle | 3 `628E-BLOCKER` fixmes | **1 skipped** + 2 in "did not run" | Skipped: `day-07-14-work-trust.spec.ts:284` (fixme `:292`). The other two named fixme tests, `day-60-interest.spec.ts:159` (fixme `:160`) and `day-90-review.spec.ts:118` (fixme `:123`), sit in serial blocks after failures 3 and 4, so Playwright listed them under "did not run" | **differs only by the cascade** — no new skip; the diagnostic re-run skipped exactly the 3 named fixmes (§ "Diagnostic re-run") | G88-F38 (`:292`), G88-F39 (`:160`), G88-F40 (`:123`) |
| depth-ii | 4: the `day-30-45-soa-writeoff.spec.ts:292` fixme + 3 opt-in null-prefetch probes | 4 | `day-30-45-soa-writeoff.spec.ts:292`; `null-prefetch-probe.spec.ts:74` ×3 (Matters list → matter, Reports → report detail, Sidebar /projects → /dashboard) | = set | none (the `:292` fixme is an E2E environment limitation, gaps.md § "Cited open register items") |
| default | 18: 7 `628F-BLOCKER` + 8 `admin-audit-30day` 510A scaffolds + 1 Keycloak-only + 2 env-gated isolation | 18 | 628F-BLOCKER (7): `customers/customer-data-protection.spec.ts:47`, `:75`, `:105`; `customers/customer-lifecycle.spec.ts:157`, `:178`; `documents/document-generation.spec.ts:85`; `settings/roles-capabilities.spec.ts:53`. Scaffolds (8): `admin-audit-30day/admin-audit-lifecycle.spec.ts:33`, `:52`, `:70`, `:88`, `:108`, `:125`, `:143`, `:159`. Keycloak-only (1): `auth/auth-change-password.spec.ts:35`. Env-gated (2): `portal/portal-data-isolation.spec.ts:36`, `:48` (they ran and passed in the isolation leg) | = set | G88-F43 (`:47`), G88-F44 (`:75`, `:105`), G88-F41 (`:157`), G88-F42 (`:178`), G88-F46 (`document-generation :85`), G88-F45 (`roles :53`) |
| `test:e2e:portal` | 1: the Day 75 `628G-BLOCKER` fixme | 1 | `portal-client-90day/day-45-75.spec.ts:163` (fixme `:166`, the dormant deadline sync) | = set | SB-11 → register §5b row 19 (no new id, ruling O-5) |
| login-bare, isolation | 0 | 0 | — | = set | — |

## Former carried six (617C) — status scored on run 1; diagnostic re-run alongside

**Tally: 4 of 6 resolved by 628E (#1707) — confirmed by run 1** (629C ruling R-6). `day-90-review.spec.ts:95` FAILED in run 1 and is **still carried**; `day-90-review.spec.ts:192` did not run in run 1 and is **DEFERRED**. Both passed in the unscored TZ=UTC diagnostic run; that is recorded alongside and does not close them (task rule: drop an item only if **629.9** passes it; 629C rulings R-3, R-6).

Titles were re-grepped at HEAD (`grep -n '^\s*test(' frontend/e2e/tests/legal-lifecycle/<file>`) and match the brief's HEAD locations.

| 617C location | HEAD location | Title | Run 1 (scored) | Status |
|---|---|---|---|---|
| `day-00-firm-setup.spec.ts:282` | `day-00-firm-setup.spec.ts:242` + `:262` | "Trust account — Mathebula & Partners Trust Account (GENERAL, Standard Bank) exists" + "LPFF rate 6.5% — added on Trust › Interest, listed on the trust settings tab" | PASSED (#13, #14) | **resolved by 628E (#1707) — confirmed by run** |
| `day-30-billing.spec.ts:32` | `day-30-billing.spec.ts:90` | "Generate Sipho's fee note from unbilled time, add an LSSA tariff item and a sheriff disbursement line (VAT 15%)" | PASSED (#51) | **resolved by 628E (#1707) — confirmed by run** |
| `day-30-billing.spec.ts:159` | `day-30-billing.spec.ts:141` | "Create Apex fee note — fixed fee R35,000 + VAT = R40,250" | PASSED (#52) | **resolved by 628E (#1707) — confirmed by run** |
| `day-45-recon-prescription.spec.ts:41` | `day-45-recon-prescription.spec.ts:50` | "Start a bank reconciliation — select the trust account and period end, reach the statement upload step" | PASSED (#60) | **resolved by 628E (#1707) — confirmed by run** |
| `day-90-review.spec.ts:63` (624I carried item) | `day-90-review.spec.ts:95` | "Fee notes list shows the run's five fee notes" | FAILED (#84) on a missing precondition (`siphoFeeNote2` not recorded — cascade of G88-F48) | **FAILED in run 1 (G88-F48 cascade; its own assertion not exercised) — still carried** (624I `gate.md:36`). Passed in the unscored TZ=UTC diagnostic run (#84) |
| `day-90-review.spec.ts:179` | `day-90-review.spec.ts:192` | "Profitability report — revenue, cost and margin columns for this month's matters" | did not run (#89, serial after #84) | **did not run in run 1 — DEFERRED**. Passed in the unscored diagnostic run (#89) |

## E2E-PORTAL magic-link budget (observed)

The caps are documented in `portal/playwright.portal.config.ts:50-70`: per contact 3 tokens / 5 min (DB rows), and per email 3 request-link calls / 15 min (in memory). Token state comes from `logs/629C-tokens.txt`, and restart times from `logs/629C-chain.txt` (`StartedAt`, health 200).

| Point | Tokens total | In last 5 min | Newest token age | e2e-backend restart (StartedAt) | Leg result |
|---|---|---|---|---|---|
| after `e2e-up` (22:00:09Z) | 1 (boot seed) | 1 | 3 s | — | — |
| before login-bare (waittok 22:11:19Z) | 3 (+2 during the frontend legs) | 0 | 316 s | 22:11:20.83Z | login-bare 1/0 |
| after login-bare | 4 (+1) | 1 | 4 s | — | — |
| before isolation (waittok 22:17:15Z) | 4 | 0 | 322 s | 22:17:16.57Z | isolation 2/0 |
| after isolation | 6 (+2) | 2 | 0 s | — | — |
| before `test:e2e:portal` (waittok 22:22:51Z) | 6 | 0 | 317 s | 22:22:52.01Z | portal 32/0/1 |
| after `test:e2e:portal` | 8 (+2) | 2 | 18 s | — | — |

Each portal leg spent exactly the brief's budget: login-bare 1, isolation 2, portal 2 (globalSetup + the Day 0 info-request send). No leg started with a token younger than 310 s, and each started on a freshly restarted backend, so neither cap could fire.

**624I carried item, login-bare comment (ruling O-1): closed by citation.** 628G #1709 already landed it:
- `portal/e2e/tests/login-bare.spec.ts:13-22` names both caps (per email 3 / 15 min, `PortalLoginRateLimiter`, cleared by a restart, 4th call 429; per contact 3 tokens / 5 min, `MagicLinkService`, cleared only by time, "answers 200 but no email is sent");
- the assertion message at `:100-104` correctly names only the per-email cap, because only that cap returns 429.

Re-read at HEAD by builder 2. No portal edit.

## Non-vacuity

Three of the 90 written snapshots were opened with the Read tool from `/tmp/629C-e2e/artefacts/screens/`. They are not committed. None is a login page.

1. **`portal_e2e_screenshots_portal-v2/portal-client-90day/day-00-home-landing.png`.** The portal Home for "Acme Corp", signed in under "E2E Test Organization". The sidebar has Home · Matters · Trust statement · Diary dates · Fee notes · Documents & requests · Activity, with Profile / Logout at the foot. Tiles show "Pending info requests 2", "Upcoming deadlines 0 · Next 14 days", "Recent fee notes INV-0001 R 8 625,00" and "Last trust movement · No recent activity". The Next.js dev indicator is visible (the `pnpm dev` server).
2. **`portal_e2e_screenshots_portal-v2/chromium/md-matters-list.png`** (768 px responsive). Portal "Your matters" with All / Active / Past chips and two Active cards: "Proj Renamed emmq" (an earlier-run matter, "17 minutes ago") and "Website Redesign" (the boot-seed matter, "1 document").
3. **`frontend_e2e_screenshots/legal-lifecycle/day-30-trust-fee-transfer-approved.png`.** The firm app as Bob Admin. Fee note INV-0002, "Partially Paid", Client "Moroka Family Trust mvu36zag0e84", **"Issued: 29 Oct 2026"**. Banner: "Part payment received — Paid R 8 500,00 · Balance due R 125,00". Payment History: "Trust transfer · FEE/MOROKA/mvu36zag0e84 · R 8 500,00 · **30 Oct 2026**".
   - One screen shows two different calendar dates written within the same minute of the run. That is consistent with the server-vs-browser date split behind G88-F48 / F49. Builder 2 did not trace which layer stamps each of the two displayed dates, so this is an observation only.

## Restore + stack state

- **Before the restore** (`/tmp/629C-e2e/dirty-before-restore.txt`): `portal/next-env.d.ts` modified (porcelain status `M`), plus untracked PNGs under `documentation/screenshots/legal-vertical/`, `frontend/e2e/screenshots/legal-{lifecycle,depth-ii}/` and `portal/e2e/screenshots/portal-v2/**`, plus the two untracked `.629C-*.sh` files.
- **Scoped restore:** `git checkout -- documentation/screenshots portal/next-env.d.ts` and `git clean -fdq -- documentation/screenshots frontend/e2e/screenshots portal/e2e/screenshots frontend/e2e/test-results`. No repo-wide clean.
- **After the restore** (`/tmp/629C-e2e/dirty-after-restore.txt`, `logs/629C-chain.txt` 22:23:55Z): only `?? .629C-diag-rerun.sh` and `?? .629C-e2e-chain.sh`. Both are untracked orchestrator scripts and are not committed.
- **KC portal on :3002:**
  - stopped at 22:06:47Z (`svc.sh stop portal`, PID 71782; ":3002 free");
  - restarted at 22:23:54Z (`svc.sh start portal`, "ready (3s)"), with ":3002 200" at 22:23:55Z;
  - the main checkout's `portal/next-env.d.ts` was restored at 22:23:54Z (`logs/629C-chain.txt`).
- **e2e stack:** left UP (ruling O-12): six containers healthy, fresh from HEAD plus this run's data (`logs/629C-provenance.txt`, end section). `e2e-backend` shows "Up About a minute" there because of the 22:22:52Z cap restart. The diagnostic re-run then replaced it with a fresh stack (§ "Diagnostic re-run").
- The KC dev stack (backend :8080, gateway :8443, frontend :3000) was not touched (`svc.sh status` at the start: 4 × yes / yes).

## Evidence files

- **Committed** (`logs/`, `git add -f`):
  - `629C-lifecycle.txt`, `629C-depth-ii.txt`, `629C-default.txt`, `629C-login-bare.txt`, `629C-isolation.txt`, `629C-portal.txt`: the chain's `redacted/` copies. They are byte-identical to the raw logs; there was nothing to redact.
  - `629C-summary.txt`, `629C-provenance.txt`, `629C-tokens.txt`, `629C-chain.txt` (= `chain.log`).
  - `629C-lifecycle-date-boundary-diagnosis.md`: the orchestrator's root-cause diagnosis.
  - `629C-lifecycle-db-reverify.txt`: builder 2's read-only SELECTs and container clocks, 22:27:51Z.
- **Redaction grep** (`token=`, `/accept/<token>`, `eyJ`, `X-Amz-`) over every file above: 0 hits.
- **Not committed:** screenshots, `error-context.md` and traces, all under `/tmp/629C-e2e/artefacts/`.

## Diagnostic re-run (lifecycle, browser zone aligned to UTC)

**Result: 101 passed / 0 failed / 3 skipped (= the 628I baseline), exit 0.** This run is diagnostic evidence only. Run 1 remains the scored lifecycle result (629C rulings R-1, R-3), so the lifecycle leg, E.11 and the exit test stay **FAIL**.

- **Method** (`logs/629C-diag-rerun.sh.txt`; orchestrator script, run by the orchestrator). Fresh stack: `e2e-down.sh` removed all seven containers and both volumes (`/tmp/629C-e2e-diag/e2e-down.txt:33-34`). `e2e-up.sh` then logged `Building from source at: …/worktree-epic-629C` and `Seed completed (container exited)` (`/tmp/629C-e2e-diag/e2e-up.txt:3`, `:348`). Lifecycle only, with run 1's command plus **one controlled change** in what the suite reads (env parity: annotation at the top of `logs/629C-diag-rerun.sh.txt`): the Playwright host process ran with `TZ=UTC` (`env TZ=UTC E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-lifecycle --update-snapshots=all --reporter=list`). The lifecycle config pins no `timezoneId`, so Chromium and the node helpers' `isoDate()` both follow the host zone.
- **Provenance** (`logs/629C-diag-provenance.txt`, `logs/629C-diag-chain.txt`). HEAD `239df0f13`. `git diff --stat cb10c5d63..HEAD -- backend frontend portal gateway packages compose` printed nothing, so the product tree is byte-identical to the 628I gate code. AC power, 100 %. Run 22:33:36Z → 22:37:21Z; lifecycle 22:34:17Z → 22:37:21Z.
- **Window.** That is 00:33–00:37 SAST on 2026-10-30, so the run was **still inside the 00:00–02:00 SAST window** in which run 1 failed. The script's header comment describes an earlier plan (run after 00:00Z); the actual control is the zone, not the clock. Clocks at lifecycle start: container `Thu Oct 29 22:34:17 UTC 2026`, Playwright host (`TZ=UTC`) `Thu Oct 29 22:34:17 UTC 2026`, host shell `Fri Oct 30 00:34:17 SAST 2026`. So the browser date and the server date were both 2026-10-29.
- **Interpretation (629C rulings R-2, R-3).** The only variable changed was the browser / helper zone, and all four run-1 failures disappear with it. That confirms G88-F48 and G88-F49 are zone-mismatch defects (browser-local date vs container-UTC date), as the diagnosis predicted. It does **not** make the product correct: real SA users' browsers are on SAST, so **F48 (HIGH) and F49 (LOW) stand unchanged**.
- **Reproduction recipe for the follow-up fix PR (red-first).** Run the converse: give the Playwright host a zone ahead of UTC whose calendar date has already rolled over at run time. `TZ=Africa/Johannesburg` does so 22:00–24:00Z (00:00–02:00 SAST, run 1); `TZ=Pacific/Kiritimati` (UTC+14) does so 10:00–24:00Z. A zone alone cannot cover 00:00–10:00Z; that needs a clock override.
- **Run-1 failures in this run:** `day-07-14-work-trust.spec.ts:167` and `:306` (F49), `day-60-interest.spec.ts:96` (F48) and `day-90-review.spec.ts:95` (F48 cascade) all **PASSED** (#40, #48, #65, #84). Their scored 629.9 status stays FAIL (run 1).
- **The 17 run-1 "did not run" tests.** Scored 629.9 status: **did not run → DEFERRED** (629C ruling R-1). Diagnostic outcome, recorded alongside and not scored: 15 **PASS (diagnostic run, TZ=UTC)** and the 2 named fixmes skipped as expected.
  - PASS: `day-07-14-work-trust.spec.ts:330` "Trust dashboard overview"; `day-60-interest.spec.ts:193` "Navigate to Investment Register", `:200` "Place investment — Moroka R200,000 Standard Bank Fixed Deposit (s86(3))", `:229` "Place investment — QuickCollect R30,000 Nedbank Call Account (s86(4))", `:252` "Second billing cycle — log new Sipho time and generate its fee note", `:278` "Run the timesheet report for this month", `:299` "Check profitability report"; `day-90-review.spec.ts:149` "Trust reports — Client Trust Balances", `:160` "Trust reports — Investment Register with s86 basis", `:173` "Trust reports — Receipts & Payments", `:192` "Profitability report — revenue, cost and margin columns for this month's matters", `:218` "Home shows the owner dashboard", `:232` "Generate an engagement letter for Sipho's matter", `:256` "Compliance overview page", `:269` "Sipho's diary shows the POSTPONED motion, its rescheduled SCHEDULED date and the prescription tracker".
  - Skipped (named `628E-BLOCKER` fixmes): `day-60-interest.spec.ts:159` "Approve and post the interest run to the client ledgers" (fixme `:160`, G88-F39) and `day-90-review.spec.ts:118` "Generate Section 35 Data Pack report" (fixme `:123`, G88-F40).
- **Skip set = the named 628I set:** the 3 skipped are exactly `day-07-14-work-trust.spec.ts:284` (fixme `:292`, G88-F38), `day-60-interest.spec.ts:159` and `day-90-review.spec.ts:118`. 0 soft-skips.
- **Carried set (629C ruling R-6):** `day-90-review.spec.ts:95` (624I carried item) passed here, and its run-1 failure is explained as a G88-F48 cascade (missing `siphoFeeNote2`, recorded only by `day-60-interest.spec.ts:252`, which did not run). It is nevertheless **still carried**: a carried item is dropped only when the scored 629.9 run passes it. `day-90-review.spec.ts:192` passed here too; it did not run in run 1, so it is **DEFERRED**. Scored tally: **4 of 6 resolved by 628E (#1707) — confirmed by run 1**.
- **Stack state after this run:** fresh from `239df0f13` plus diagnostic-run data, left UP (six containers healthy at 22:38Z). The script's scoped restore left the worktree with only the two untracked `.629C-*.sh` files (`logs/629C-diag-chain.txt`).
- **Evidence** (`logs/`, `git add -f`): `629C-diag-provenance.txt`, `629C-diag-chain.txt`, `629C-diag-summary.txt`, `629C-diag-lifecycle.txt`, `629C-diag-rerun.sh.txt`. The redaction grep (`token=`, `/accept/`, `eyJ`, `X-Amz-`) found 0 hits, so they are verbatim copies — except that `629C-diag-rerun.sh.txt` carries a 13-line `REVIEW ANNOTATION` header added after review; the script below that header is verbatim. `e2e-down.txt`, `e2e-up.txt` and `test-results/` stay in `/tmp/629C-e2e-diag/` and are not committed.
