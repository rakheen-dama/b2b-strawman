# Phase 93 exit — the four suites on fresh stacks (660A, task 660.4)

Every suite ran on an e2e mock-auth stack recreated for it (`bash compose/scripts/e2e-down.sh &&
bash compose/scripts/e2e-up.sh` from worktree-epic-660A). The container creation times prove that each
stack was new: `runs/stack-*-containers.txt`. The orchestrator ran stacks A, B and C in one
unattended sequence at PR head `bc0eea2f6` (`runs/orchestrator-progress.txt`: head line, then `up
rc=0` and the suite `rc=0` for each). The slice builder ran the final take-on run of record at the
PR head after CodeRabbit round 1, on its own fresh stack (`../README.md`, `../run-of-record/`).
Browser: Chromium (Playwright 1.59), nvm node v20.11.1, `workers: 1`. The commands are the ones in
the 660A brief §10, with flags passed without `--`. The `> playwright test --config … --project …`
line at the top of each log shows the config and project that ran.

## Results

| Stack | Suite (command, from `frontend/`) | Head | Containers created (SAST) | Result | Log |
|---|---|---|---|---|---|
| A | take-on: `PLAYWRIGHT_BASE_URL=http://localhost:3001 MAILPIT_URL=http://localhost:8026 TAKE_ON_EVIDENCE_DIR=… pnpm test:e2e:take-on --reporter=list` (orchestrator, independent re-run) | `bc0eea2f6` (16 tests) | 2026-11-25 04:14:00 | **16 passed** in 2.1 min, exit 0 (2 with DEFERRED sub-checks: CashDigest, dormancy job — G93-2/G93-3) | `runs/A-take-on.txt`, `runs/stack-A-containers.txt` |
| A′ | take-on, the same command (builder, **run of record**) | `e092ecf74` + the CodeRabbit round-1 spec / helper edits, committed right after as the PR head (17 tests) | 2026-11-25 04:35:22 | **17 passed** in 2.7 min, exit 0, first attempt on that stack (2 with DEFERRED sub-checks: CashDigest, dormancy job — G93-2/G93-3) | `runs/A-take-on-final.txt` (= `../run-of-record/take-on-run.txt`), `runs/stack-A-final-containers.txt` |
| B | legal-lifecycle: `PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-lifecycle --reporter=list` (`--project chromium`; `take-on` and `exit-month` are `testIgnore`d) | `bc0eea2f6` | 2026-11-25 04:16:45 | **105 passed** in 3.2 min, exit 0 | `runs/B-lifecycle.txt`, `runs/stack-B-containers.txt` |
| B | legal-depth-ii: `PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-depth-ii --reporter=list`, after lifecycle on the same stack (the 653.1 / per-slice pattern) | `bc0eea2f6` | 2026-11-25 04:16:45 | **40 passed, 4 skipped** in 2.0 min, exit 0 | `runs/B-depth-ii.txt` |
| C | exit-month: portal dev server on :3002 first (`NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081 pnpm dev` in `portal/`), then `PLAYWRIGHT_BASE_URL=http://localhost:3001 PORTAL_BASE_URL=http://localhost:3002 pnpm test:e2e:exit-month --reporter=list` | `bc0eea2f6` | 2026-11-25 04:22:39 | **16 passed** in 1.5 min, exit 0 | `runs/C-exit-month.txt`, `runs/stack-C-containers.txt` |

The 4 depth-ii skips are the known ones: 1 × `test.fixme` (the statements PDF download,
`day-30-45-soa-writeoff.spec.ts:361`) and 3 × the opt-in `null-prefetch-probe.spec.ts` (`:74`).
They are listed in `runs/B-depth-ii.txt`.

## Against the baseline

| Suite | 653.1 baseline (`qa_cycle/evidence/phase93/baseline/README.md`) | Latest per-slice floor | Exit | At or above? |
|---|---|---|---|---|
| take-on | — (new in 660A) | — | 16 / 16 (`bc0eea2f6`); **17 / 17** (run of record, final head) | new |
| legal-lifecycle | 105 passed | 105 (658A) | **105 passed** | yes (equal) |
| legal-depth-ii | not valid on `main` at 653.1. Reference: 40 passed / 4 skipped (653A run) | 40 / 4 (657D) | **40 passed / 4 skipped** | yes (equal) |
| exit-month | not captured on `main` (mock-IDP outage). Observed 16 passed on every trust slice (657B, 657C, 657D) | 16 (657D) | **16 passed** | yes (equal) |

## Why B and C, run at `bc0eea2f6`, stay valid for the final head

After `bc0eea2f6`, this PR changed only the take-on spec and its helper
(`frontend/e2e/tests/legal-lifecycle/take-on.spec.ts`, `frontend/e2e/helpers/take-on.ts`), the
fixture README and build script under `qa/fixtures/take-on/`, the e2e README text, the evidence and
the task docs. No product code changed. Suites B and C do not import any of the changed files: only
the `take-on` project loads `take-on.spec.ts` (the `chromium` project `testIgnore`s it), and
`helpers/take-on.ts` is imported by nothing else. B and C share four e2e files with the take-on
spec. None of the four changed between `bc0eea2f6` and the final head. The command below, run at
the final head, prints nothing:

```
$ git diff bc0eea2f6..HEAD -- frontend/e2e/helpers/trust-balanced-month.ts frontend/e2e/helpers/mailpit.ts frontend/e2e/playwright.legal-lifecycle.config.ts frontend/e2e/playwright.legal-depth-ii.config.ts
$
```

(empty output). B and C therefore carry over to the final head without a re-run.

## Notes

- The take-on spec commits data at every step, so each run needs a stack recreated with down and
  up (`e2e-reseed.sh` does not wipe). Step 0 fails fast on a stack that has been used.
- The run of record measured 2.7 min against the config's 10 min `globalTimeout` (D-263). The
  round-1 edits added about 19 s (nine more Matters tabs, step 3) and 8 s (the Mailpit settle
  around Alice's sign-off, step 7).
- No suite was re-run to get a green result. Every run listed above is the only run on its stack.
