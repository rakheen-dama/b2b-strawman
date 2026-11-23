# Phase 92 exit gate, re-run after the follow-ups: gates on `main` and the five e2e suites, each on its own fresh stack (2026-11-23)

This folder holds two runs of the same scripted exit gate. Both were green at every stage.

| Run | `main` head | Follow-ups on it | Runner | Evidence | Status |
|---|---|---|---|---|---|
| **2: gate of record** | **`cb803686c`** (`cb803686cf904d92313a52fb244a35a28558f444`) | #1823–#1845 | [`rerun-cb803686c/exit-gate.sh`](rerun-cb803686c/exit-gate.sh) (worktree `wt-exit2`) | [`rerun-cb803686c/runs/`](rerun-cb803686c/runs/) | **Gate of record.** Every rc=0 |
| 1 | `ef0563fe3` (`ef0563fe31b13685b35b7d78ea6644ee9b6a9ac0`) | #1823–#1842 | [`exit-gate.sh`](exit-gate.sh) (worktree `wt-exit`) | [`runs/`](runs/) | Every rc=0, but not the gate of record (below) |

**Why run 2 is the gate of record.** Run 1 was green. But the independent review of #1843 (REQUEST_CHANGES @`616d915`) found
that exit-bar item (3) was not yet met then (H-1): the follow-up builders had found MEDIUM-class residuals that were never
registered. Those residuals were listed in `tasks/trust-accounting-open-issues-2026-11-23.md` (#1844), the founder ruled
on them, and all of them were fixed in #1845 (`f4923eb29`). They are now section R of
[`../defect-register.md`](../defect-register.md). Run 2 re-ran the whole gate on the `main` that holds #1845.

**Runner.** Both scripts are 652F's recipe ([`../suites/run-suites.sh`](../suites/run-suites.sh)) with two changes. First,
every stage's return code is checked and logged. Second, a suite is skipped if its stack is unhealthy, meaning `e2e-up`
failed or the health wait gave up. Those were the two gaps recorded in [`../suites/README.md`](../suites/README.md) § "The
runner script is evidence, not a gate". The two scripts differ only in the worktree path (`WT`) and the output folder
(`OUT`). In both runs **every rc was 0, and no suite was skipped**.
- Run 2 timeline: [`rerun-cb803686c/runs/runner.txt`](rerun-cb803686c/runs/runner.txt), `2026-11-23T08:48:14Z` head → `09:34:57Z` ALL DONE.
- Run 1 timeline: [`runs/runner.txt`](runs/runner.txt), `2026-11-22T23:50:09Z` head → `2026-11-23T00:35:02Z` ALL DONE.

**Runtime:** nvm node v20.11.1 (the CI pin), pnpm by absolute path `/opt/homebrew/bin/pnpm`, `NODE_OPTIONS=""`,
`SHELL=/bin/bash`, under `caffeinate`. In each run FE-GATE and PORTAL-GATE ran after BE-VERIFY had finished, on an idle
machine, so neither overlapped the verify (652F's F-652F-1).
**Baselines:** the 652F run on `main` `b08a8916a` ([`../suites/README.md`](../suites/README.md)).

## Verdict (gate of record, run 2 on `cb803686c`)

- **Gates on `main` (BE-VERIFY, harness check, FE-GATE, PORTAL-GATE): all green, every count at or above the 652F baseline.**
- **E2E suites: all five green, each on its own fresh stack, every count at or above its 652F baseline.**

## Results

### Run 2 results (`cb803686c`): gate of record

The commands are run 1's (table below). `R` = `rerun-cb803686c/runs/`.

| Gate | `main` SHA | Result | Run 1 (`ef0563fe3`) | 652F baseline (`b08a8916a`) | Verdict | Log |
|---|---|---|---|---|---|---|
| **BE-VERIFY** | `cb803686c` | **spotless rc=0; verify exit 0; 7 576 `<testcase>` / 0 failures / 0 errors / 26 skipped** | 7 557 / 0 / 0 / 26 | 7 420 / 0 / 0 / 26 | PASS | [`R/spotless.txt`](rerun-cb803686c/runs/spotless.txt), [`R/be-verify-counts.txt`](rerun-cb803686c/runs/be-verify-counts.txt) |
| **Harness check** | `cb803686c` | **70 / 70 PASS** (0 FAIL, 0 SKIPPED, 0 ABSENT; testcases read: 7 576) | 70 / 70 | 69 / 69 | PASS | [`R/harness-check.txt`](rerun-cb803686c/runs/harness-check.txt) |
| **FE-GATE** | `cb803686c` | rc=0. Lint: 0 errors, 77 warnings. **vitest 487 files, 4 654 passed / 2 skipped / 0 failed.** Build OK | 4 633 / 2 / 0, 486 files | 4 591 / 2 / 0, 482 files | PASS | [`R/fe-gate-summary.txt`](rerun-cb803686c/runs/fe-gate-summary.txt) |
| **PORTAL-GATE** | `cb803686c` | rc=0. Lint: 0 errors, 4 warnings. **vitest 51 files, 292 passed.** Build OK | 292 | 292 | PASS | [`R/portal-gate-summary.txt`](rerun-cb803686c/runs/portal-gate-summary.txt) |
| **E2E exit month** (stack A, portal on :3002) | `cb803686c` | **16 / 16 passed** (1.4 min). Read-only psql re-check: per account, Σ `cashbook_effect` = Σ `ledger_effect` to 2026-10-31, difference 0,00 on each (trust account 101 630,00; Khumalo s86(4) 20 095,00; Ndlovu s86(4) 0,00) | 16 / 16 | 16 / 16 | PASS | [`R/A-exit-month.txt`](rerun-cb803686c/runs/A-exit-month.txt), [`R/A-exit-month-psql-output.txt`](rerun-cb803686c/runs/A-exit-month-psql-output.txt) |
| **E2E-LEGAL lifecycle** (stack B) | `cb803686c` | **105 / 105 passed** (3.1 min) | 105 / 105 | 105 / 105 | PASS | [`R/B-lifecycle.txt`](rerun-cb803686c/runs/B-lifecycle.txt) |
| **E2E-LEGAL depth-ii** (stack C) | `cb803686c` | **40 passed / 4 skipped / 0 failed** (2.0 min) | 40 / 4 | 40 / 4 | PASS | [`R/C-depth-ii.txt`](rerun-cb803686c/runs/C-depth-ii.txt) |
| **E2E-DEFAULT** (stack D, `KGOSI_EMAIL` set) | `cb803686c` | **168 passed / 0 failed / 12 skipped** (1.4 min) | 168 / 0 / 12 | 168 / 0 / 12 | PASS | [`R/D-default.txt`](rerun-cb803686c/runs/D-default.txt) |
| **E2E-PORTAL** (stack E) | `cb803686c` | **34 / 34 passed** (37.1 s) | 34 / 34 | 34 / 34 | PASS | [`R/E-portal.txt`](rerun-cb803686c/runs/E-portal.txt) |

### Run 1 results (`ef0563fe3`)

| Gate | Command (from the worktree root unless stated) | `main` SHA | Result | 652F baseline (`b08a8916a`) | Verdict | Log |
|---|---|---|---|---|---|---|
| **BE-VERIFY** | `cd backend && ./mvnw -q spotless:check && bash scripts/verify.sh -q` (full suite, no `-Dtest`) | `ef0563fe3` | **spotless rc=0; verify exit 0; 7 557 `<testcase>`, 0 failures, 0 errors, 26 skipped** (no report file contains `<failure` or `<error`) | 7 420 / 0 / 0 / 26 | PASS | [`runs/spotless.txt`](runs/spotless.txt), [`runs/be-verify-counts.txt`](runs/be-verify-counts.txt) |
| **Harness check** | `bash qa_cycle/evidence/phase92-exit/harness-check.sh backend/target/surefire-reports` (on the BE-VERIFY reports above) | `ef0563fe3` | **70 / 70 PASS** (0 FAIL, 0 SKIPPED, 0 ABSENT; testcases read: 7 557), §16.9.2 paths 1–24 | 69 / 69 | PASS: the extra row is FU-27's (#1842) | [`runs/harness-check.txt`](runs/harness-check.txt) |
| **FE-GATE** | `cd frontend && rm -rf .next && pnpm lint && pnpm typecheck && pnpm format:check && pnpm test && pnpm build` (idle machine) | `ef0563fe3` | rc=0. Lint: 0 errors, 77 warnings. **vitest 486 files, 4 633 passed / 2 skipped / 0 failed.** Build OK | 4 591 / 2 / 0, 482 files | PASS | [`runs/fe-gate-summary.txt`](runs/fe-gate-summary.txt) |
| **PORTAL-GATE** | `cd portal && rm -rf .next && pnpm lint && pnpm typecheck && pnpm format:check && pnpm test && pnpm build` | `ef0563fe3` | rc=0. Lint: 0 errors, 4 warnings. **vitest 51 files, 292 passed.** Build OK | 292, 51 files | PASS | [`runs/portal-gate-summary.txt`](runs/portal-gate-summary.txt) |
| **E2E exit month** (stack A) | Portal first: `cd portal && NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081 pnpm dev` (:3002, against :8081). Then `cd frontend && EXIT_MONTH_EVIDENCE_DIR=… E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 PORTAL_BASE_URL=http://localhost:3002 PLAYWRIGHT_HTML_OUTPUT_DIR=… PLAYWRIGHT_HTML_OPEN=never pnpm test:e2e:exit-month --reporter=line` | `ef0563fe3` | **16 / 16 passed** (1.5 min). Read-only psql re-check: per account, Σ `cashbook_effect` = Σ `ledger_effect` to 2026-10-31, difference 0,00 on each (trust account 101 630,00; Khumalo s86(4) 20 095,00; Ndlovu s86(4) 0,00); the three locked October lists hold the totals | 16 / 16 | PASS | [`runs/A-exit-month.txt`](runs/A-exit-month.txt), [`runs/A-exit-month-psql-output.txt`](runs/A-exit-month-psql-output.txt) |
| **E2E-LEGAL lifecycle** (stack B) | `cd frontend && E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-lifecycle --reporter=line` | `ef0563fe3` | **105 / 105 passed** (3.2 min) | 105 / 105 | PASS | [`runs/B-lifecycle.txt`](runs/B-lifecycle.txt) |
| **E2E-LEGAL depth-ii** (stack C) | `cd frontend && E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-depth-ii --reporter=line` | `ef0563fe3` | **40 passed / 4 skipped / 0 failed** (2.1 min) | 40 / 4 | PASS | [`runs/C-depth-ii.txt`](runs/C-depth-ii.txt) |
| **E2E-DEFAULT** (stack D) | `cd frontend && KGOSI_EMAIL=contact@acme.example.com E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e --reporter=line` | `ef0563fe3` | **168 passed / 0 failed / 12 skipped** (1.3 min; `KGOSI_EMAIL` set, so the 2 `portal-data-isolation` tests run instead of skipping) | 168 / 0 / 12 | PASS | [`runs/D-default.txt`](runs/D-default.txt) |
| **E2E-PORTAL** (stack E) | `cd portal && pnpm test:e2e:portal --reporter=line` (`E2E_VISUAL` unset; projects chromium + portal-client-90day; the script serves :3002 itself) | `ef0563fe3` | **34 / 34 passed** (35.1 s) | 34 / 34 | PASS | [`runs/E-portal.txt`](runs/E-portal.txt) |

Run 1's runner wrote its logs to `/tmp/exit92/runs/` and run 2's to `/tmp/exit92b/runs/` on the run host. The files in [`runs/`](runs/) and [`rerun-cb803686c/runs/`](rerun-cb803686c/runs/) are copies of them. The
BE-VERIFY, FE-GATE and PORTAL-GATE rows in both runs link their count summaries, not the full Maven and pnpm logs.

## Fresh stacks

- **One suite per stack, in both runs.** Before each of A–E the runner ran `bash compose/scripts/e2e-down.sh && bash compose/scripts/e2e-up.sh` from the worktree. `e2e-down` wipes the volumes; `e2e-reseed.sh` does not wipe, so it was not used. The runner then waited until `:8081/actuator/health` and `:3001/` answered, and it would have skipped the suite if either check failed. Every `stack X e2e-up rc=0` is in both `runner.txt` files, and neither has a `SKIPPED` or `health wait FAILED` line.
- **Why one stack each.** The exit month can run only once per stack. It locks October's lists, and a second October reconciliation is refused (652E D-11, 652C). Fresh stacks also keep each suite's seed data independent of the suite before it.
- **Port :3002** was freed before every stack, and again after stack A and stack E. Only stack A runs `portal pnpm dev`. Stack E's `test:e2e:portal` serves the portal itself.
- **After each suite** the runner restored the tree: `git checkout -- frontend/test-results documentation/screenshots`, then `git clean` of the Playwright output folders. A final `e2e-down` ran after stack E.
- **No suite overlapped a BE-VERIFY.** The stages ran one after another: BE-VERIFY → harness → install → FE-GATE → PORTAL-GATE → stacks A–E (both `runner.txt` files).

## Related

- [`../defect-register.md`](../defect-register.md): updated 2026-11-23. It has 232 rows, including section R (the residuals closed by #1845). **0 open HIGH / MEDIUM** by source severity. Every row the follow-ups closed names its PR and merge SHA.
- `tasks/phase92-trust-books-provably-right.md` § Closure report, C.0: the re-run verdict, exit-bar item by item.
- `tasks/trust-accounting-open-issues-2026-11-23.md` (#1844): the residuals, the founder questions and the LOW follow-ups.
- [`../harness.md`](../harness.md): the §16.9.2 path inventory. It still says "69 rows", a LOW follow-up from FU-27 D-1.
