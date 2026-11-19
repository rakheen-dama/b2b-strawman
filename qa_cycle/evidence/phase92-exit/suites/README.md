# 652F — Exit gates on `main` and the five e2e suites, each on its own fresh stack (task 652.32)

**Head under test:** `main` @ **`b08a8916a`** (`b08a8916a44cc27f8c45b9171de8c71ceb278ac0`, 2026-11-19). It holds every Phase 92 slice PR: #1786–#1821, plus the process PR #1805.
**Where:** worktree `worktree-epic-652F`, branch `epic-652F/exit-gate`. Its only commit above `b08a8916a` is evidence under `qa_cycle/evidence/phase92-exit/`: `git diff --name-only b08a8916a HEAD -- backend frontend portal packages compose gateway` is empty. Every stack and every gate therefore ran the `main` product tree.
**Runtime:** nvm node v20.11.1 (the CI pin), pnpm by absolute path `/opt/homebrew/bin/pnpm`, `NODE_OPTIONS=""`, `SHELL=/bin/bash`, under `caffeinate`.
**Baselines:** the 645.1 baseline (`qa_cycle/evidence/phase92/baseline/README.md`, `main` `251100d54`, 2026-11-16) covers BE, FE, portal, lifecycle and depth-ii. 645.1 recorded no E2E-DEFAULT or E2E-PORTAL count, so those two rows use the last recorded clean run, named in the row.

## Verdict

- **Gates on `main` (BE-VERIFY, FE-GATE, PORTAL-GATE): all green, every count at or above the 645.1 baseline.** The FE-GATE number is run 2's (see F-652F-1 below).
- **E2E suites:** **Met: all five suites green, each on its own fresh stack, every count at or above its baseline** (exit month 16 / 16; lifecycle 105 / 105; depth-ii 40 / 4; default 168 / 0 / 12 vs 165 / 0 / 14; portal 34 / 0 / 0). The suites are not a second failing item.

## Results

| Gate | Command (from the worktree root unless stated) | `main` SHA | Result | Baseline | Verdict | Log |
|---|---|---|---|---|---|---|
| **BE-VERIFY** | `cd backend && ./mvnw spotless:apply && bash scripts/verify.sh -q` (full suite, no `-Dtest`; the verify exit code is in the summary, spotless output in `/tmp/652F/spotless.txt` on the run host) | `b08a8916a` | **exit 0, 2 241 s; 7 420 `<testcase>`, 0 failures, 0 errors, 26 skipped** (858 report files; no file contains `<failure` or `<error`) | 6 871 / 0 / 26 (645.1). The last slice verify was 7 420 (652H @`d9f42136e`) | PASS: +549 vs 645.1, equal to the last slice verify | [`runs/be-verify-summary.txt`](runs/be-verify-summary.txt) |
| **Harness check** (652.33) | `bash qa_cycle/evidence/phase92-exit/harness-check.sh backend/target/surefire-reports` (on the BE-VERIFY reports above) | `b08a8916a` | **69 / 69 PASS** (0 FAIL, 0 SKIPPED, 0 ABSENT), §16.9.2 paths 1–24 | — | PASS | [`runs/harness-check.txt`](runs/harness-check.txt); [`../harness.md`](../harness.md) |
| **FE-GATE** static | `cd frontend && rm -rf .next && pnpm lint && pnpm typecheck && pnpm format:check && pnpm build` | `b08a8916a` | all exit 0. Lint: 0 errors, 77 warnings. Prettier clean. Build OK | — | PASS | [`runs/fe-portal-static-gates.txt`](runs/fe-portal-static-gates.txt) |
| **FE-GATE** vitest, run 1 | `cd frontend && pnpm test` (full) | `b08a8916a` | 482 files: **1 failed** / 4 590 passed / 2 skipped. The failure was a timeout under a concurrent BE-VERIFY (F-652F-1) | — | not the gate number | [`runs/fe-test-run1.txt`](runs/fe-test-run1.txt) |
| **FE-GATE** vitest, run 2 | `cd frontend && pnpm test` (full, machine idle) | `b08a8916a` | **482 files, 4 591 passed / 2 skipped, 0 failed** | 4 357 / 2, 478 files (645.1) | PASS: +234 | [`runs/fe-test-run2.txt`](runs/fe-test-run2.txt) |
| **PORTAL-GATE** | `cd portal && pnpm lint && pnpm typecheck && pnpm format:check && pnpm test && pnpm build` | `b08a8916a` | all exit 0. Lint: 0 errors, 4 warnings. **vitest 51 files, 292 passed** | 284, 51 files (645.1) | PASS: +8 | [`runs/fe-portal-static-gates.txt`](runs/fe-portal-static-gates.txt), [`runs/portal-test.txt`](runs/portal-test.txt) |
| **E2E exit month** (stack A) | Portal first: `cd portal && NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081 pnpm dev` (:3002, against :8081). Then `cd frontend && EXIT_MONTH_EVIDENCE_DIR=/tmp/652F/exit-month-evidence E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 PORTAL_BASE_URL=http://localhost:3002 PLAYWRIGHT_HTML_OUTPUT_DIR=/tmp/652F/exit-month-report PLAYWRIGHT_HTML_OPEN=never pnpm test:e2e:exit-month --reporter=line,html` | `b08a8916a` | **16 / 16 passed, 0 failed, 0 retries** (1.5 min; stack A; `suites/runs/A-exit-month.txt`; read-only psql re-check `suites/runs/A-exit-month-psql-output.txt`: G 101 630,00 / I-B 20 095,00 / I-A 0,00, difference 0,00 on each) | 16 / 16, 0 retries (652E run 6, plus the orchestrator's two re-runs on further fresh stacks, 16/16 each) | see Verdict | `runs/A-exit-month.txt` |
| **E2E-LEGAL lifecycle** (stack B) | `cd frontend && E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-lifecycle --reporter=line` (`--project chromium`; the exit month is not in it, 652E D-12) | `b08a8916a` | **105 / 105 passed** (3.1 min; stack B; `suites/runs/B-lifecycle.txt`) | 105 / 105 (645.1; also 652H) | see Verdict | `runs/B-lifecycle.txt` |
| **E2E-LEGAL depth-ii** (stack C) | `cd frontend && E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-depth-ii --reporter=line` | `b08a8916a` | **40 passed / 4 skipped / 0 failed** (1.9 min; stack C; `suites/runs/C-depth-ii.txt`) | 40 passed / 4 skipped (645.1; also 652H) | see Verdict | `runs/C-depth-ii.txt` |
| **E2E-DEFAULT** (stack D) | `cd frontend && KGOSI_EMAIL=contact@acme.example.com E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e --reporter=line` | `b08a8916a` | **168 passed / 0 failed / 12 skipped / 0 did not run** (1.2 min; stack D; `KGOSI_EMAIL` set, so the 2 isolation tests run instead of skipping; `suites/runs/D-default.txt`) | Not in 645.1. Last clean runs: **165 passed / 0 failed / 14 skipped**, without `KGOSI_EMAIL` (Phase 90 exit, `qa_cycle/evidence/phase90-ui/suites/README.md:20`; Phase 89 exit, `qa_cycle/evidence/phase89-rewalk/suites/default.md:12-13`). With `KGOSI_EMAIL` set, the two `portal-data-isolation` tests run instead of skipping (Phase 89: 2 / 0 / 0) | see Verdict | `runs/D-default.txt` |
| **E2E-PORTAL** (stack E) | `cd portal && pnpm test:e2e:portal --reporter=line` (`E2E_VISUAL` unset; projects chromium + portal-client-90day; the script self-serves :3002) | `b08a8916a` | **34 passed / 0 failed / 0 skipped** (27.9 s; stack E; `suites/runs/E-portal.txt`) | Not in 645.1. **34 passed / 0 failed / 0 skipped / 0 flaky** (648D, `qa_cycle/evidence/phase92/648D/gates/README.md:9`) | see Verdict | `runs/E-portal.txt` |

The E2E log paths are where the runner's output goes once it is copied into this folder. On the run host the runner writes it to `/tmp/652F/suites/<same name>`, with `runner.txt` as the timeline.

## Fresh stacks

- **One suite per stack.** Before each of A–E: `bash compose/scripts/e2e-down.sh && bash compose/scripts/e2e-up.sh` from the worktree. `e2e-down` wipes the volumes; `e2e-reseed.sh` does not wipe, so it was not used. The stack is built from the worktree's source, which is the `main` `b08a8916a` product tree. The runner then waits until `:8081/actuator/health` and `:3001/` answer.
- **Why one stack each.** The exit month can run only once per stack. It locks October's lists, and a second October reconciliation is refused (652E D-11, 652C). Fresh stacks also keep each suite's seed data independent of the suite before it.
- **Port :3002** is freed before stack A and stack E, and again after each. Only stack A runs `portal pnpm dev`; stack E's `test:e2e:portal` serves the portal itself.
- **After each suite** the tree is restored: `git checkout -- frontend/test-results documentation/screenshots`, then `git clean` of the Playwright output folders. The legal configs dirty the tree otherwise.
- **No suite overlapped a BE-VERIFY.** The suites started after BE-VERIFY and both vitest runs had finished.

## F-652F-1: FE-GATE run 1 overlapped BE-VERIFY

FE-GATE run 1 was started while the exit BE-VERIFY was still running (load average about 70). One CPU-bound case timed out at vitest's default 5 s: `__tests__/dark-mode-contrast-guard.test.ts:213`, `D-3: every native select/textarea …`. It comes from Phase 90, and no Phase 92 change touches it. Run 2 ran on an idle machine after the verify finished, and gave 482 files, 4 591 passed, 2 skipped, 0 failed. **The FE-GATE number is run 2's.** Root cause, severity (LOW, test-only, not trust) and the proposed fix are in [`../found.md`](../found.md) § F-652F-1. Gate practice: run FE-GATE and BE-VERIFY one after the other, not side by side.

## Related audits (652.33)

- [`../harness.md`](../harness.md): 24 of 24 §16.9.2 paths have a test; 69 / 69 green in the BE-VERIFY above. No `_pinsKnownDefect` test method remains; 2 Javadoc lines still mention the name.
- [`../markers.md`](../markers.md): 36 PRs checked with `scripts/check-verify-markers.sh --committed-only`, all exit 0. Caveat: on 652C #1817, the backend marker is one test-only commit behind the PR head.
