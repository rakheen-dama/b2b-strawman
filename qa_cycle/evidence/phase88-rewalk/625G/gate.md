# 625G — Epic 625 gate (task 625.16)

Orchestrator run, 2026-10-27 18:21–18:42Z (20:21–20:42 SAST), worktree `worktree-epic-625G`, branch `phase88/08G-epic-625-gate`, on **main `977419fa8`** (= origin/main, contains 625A–625F). No product code changes in this slice, no PR (618D precedent: gate-only docs commit).

Source trees at this head are identical to the heads earlier slices verified (`git diff --quiet` exit 0):
- `backend/src` + `backend/pom.xml` = 625F verified head `295fcbd74` (only `backend/CLAUDE.md` differs, one bullet, non-code)
- `frontend/` + `packages/` = 625F head `84c2123b8`

## Verdicts

| Gate | Result | Baseline | Verdict | Evidence |
|---|---|---|---|---|
| **BE-VERIFY** (`./mvnw spotless:apply` then `bash scripts/verify.sh -q`: preflight + `caffeinate -is ./mvnw verify`, fresh worktree, no `target/`) | exit 0, 15:44; 726 report files / **6006 testcases** / 0 failures / 0 errors / 26 skipped; spotless no diff | 625F 6006 | **PASS** | `logs/be-verify-status.txt`, `.claude/markers/verify-backend.json` |
| **FE-GATE** (`rm -rf .next`, lint, format:check, vitest, build) | lint 0 errors / 92 warnings; format clean; **419 files / 3192 passed / 2 skipped**; build OK | 625F 419/3192/2, 92 warnings | **PASS** | `logs/fe-*-tail.txt`, `.claude/markers/verify-frontend.json` |
| **E2E-LEGAL** lifecycle (fresh stack: `e2e-down.sh` + `e2e-up.sh` from the worktree; `--update-snapshots=all`, baselines untracked) | **74 passed / 4 failed / 5 skipped / 14 did not run** | 625D 74/4/5/14 | **= baseline** | `logs/e2e-lifecycle.txt` |
| **E2E-LEGAL** depth-ii (same stack, after lifecycle) | **35 passed / 0 failed / 8 skipped** | 625D 35/0/8 | **= baseline** | `logs/e2e-depth.txt` |
| **MARKERS** | backend + frontend written on `977419fa8` (Epic 625 touches no `portal/`) | — | **DONE** | `.claude/markers/verify-{backend,frontend}.json` |

## E2E failing tests (all carried; set not grown)

legal-lifecycle 4 failures, matched by R10 id (`tasks/phase88-research/R10-playwright-carried-failures.md`) and by failing line:
- **A1** `day-00-firm-setup.spec.ts:282` Trust account settings page loads (`locator.fill` on a non-input; LPFF locator drift)
- **A2** `day-30-billing.spec.ts:32` Create fee note for Sipho Ndlovu (soft asserts `:141`, `:151`; flow drift)
- **A4** `day-45-recon-prescription.spec.ts:41` Start new bank reconciliation (soft asserts; flow drift)
- **A6** `day-90-review.spec.ts:179` Profitability dashboard (strict-mode `getByText` soft asserts `:204`, `:212`, `:220`)

The A6 failing lines `:204`/`:212`/`:220` are identical to the 625D run (`/tmp/625D/e2e-lifecycle.log`). 625D's `e2e-legal.md` named only `:204`/`:212`. A5 passes, so the serial run reaches A6 (as in 625D). Epic 625 touches no fee-note, trust or profitability UI.

depth-ii: 0 failures. Both specs amended in 625D are green: `day-30-45-soa-writeoff.spec.ts:336` (write-off audit event), `day-75-85-closure.spec.ts:382` (override audit event). `day-75-85-closure.spec.ts:325` Bob (Admin) → Step 3 is green.

## Run notes
- BE-VERIFY ran concurrently with the e2e image build and FE-GATE; E2E-LEGAL ran only after BE-VERIFY finished (no JVM contention with Playwright timings).
- After the E2E runs the worktree was restored (`git clean` on `documentation/screenshots`, `frontend/e2e/screenshots`, `frontend/test-results`), so `git status` was clean before this docs commit.
- e2e stack state after the gate: fresh stack built from `977419fa8`, then lifecycle + depth-ii suite data.

**Epic 625 Done** (625A #1679, 625B #1680, 625C #1681, 625D #1682, 625E #1683, 625F #1684, 625G gate).
