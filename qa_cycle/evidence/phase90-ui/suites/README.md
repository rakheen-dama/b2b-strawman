# 644A — Exit suites and gates on `main` (tasks 644.1–644.3)

**Head under test:** `main` @ **`2773ec9b6f00fa03d564d503f4df267d11fd6206`** (2026-11-13) — every Phase 90 code slice (P1 #1758, 637A–643C #1759–#1777) merged, plus the two trust-reconciliation wizard fixes #1778/#1779 that landed after 643C.
**Where:** detached worktree `worktree-epic-644A` (branch `phase90/44A-exit-suites` created at `origin/main`, no commits before this evidence).
**Runner:** [`run-gates.sh`](run-gates.sh) — one gate at a time on the machine (BE-VERIFY first, then the fresh e2e stack, then FE-GATE, then PORTAL-GATE; never E2E alongside a Maven verify), under `caffeinate -is`, node **v20.11.1** (nvm; CI pin), pnpm by absolute path `/opt/homebrew/bin/pnpm`, `NODE_OPTIONS=""`.
**Raw timeline:** [`summary.txt`](summary.txt) (UTC timestamps, exit codes, pass counts as printed by each tool).

## Verdict

**All five gates green on the merged `main` head; every count ≥ the 637.1 baseline; no failures, no flaky retries, nothing to triage (644.3 not triggered).** 644B may start.

## Results vs the 637.1 baseline (`qa_cycle/evidence/phase90-ui/baseline/README.md`, `main` @ `f7b7dbdf1`)

| Gate | Command (from the worktree) | Run (UTC, 2026-11-13) | Baseline (637.1) | Exit (644A) | Δ |
|---|---|---|---|---|---|
| **BE-VERIFY** | `cd backend && ./mvnw spotless:apply && bash scripts/verify.sh` | 19:55 → 20:15 (19:54 min) | not part of 637.1 (FE-only baseline) | **exit 0 — BUILD SUCCESS; 6871 tests, 0 failures, 0 errors, 26 skipped** (`<testcase>` count over surefire+failsafe XML = 6871, `<skipped>` = 26; no report with failures/errors) | spotless made no change (`git status` clean) |
| **E2E-LEGAL** legal-lifecycle | `E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-lifecycle` | 20:17 → 20:20 (3.2m) | 105 passed | **105 passed** | = |
| **E2E-LEGAL** legal-depth-ii | `… pnpm test:e2e:legal-depth-ii` | 20:20 → 20:22 (2.0m) | 40 passed, 4 skipped | **40 passed, 4 skipped** | = |
| **E2E-DEFAULT** | `… pnpm test:e2e` (same stack) | 20:22 → 20:24 (1.7m) | 157 passed, 1 failed, 7 did not run, 14 skipped | **165 passed, 14 skipped, 0 failed** | +8 passed: INV-02 *DRAFT → APPROVED* passed ([`default-inv02.txt`](default-inv02.txt)), so its serial describe's 7 "did not run" ran — 157 + 1 + 7 = 165 |
| **FE-GATE** | `cd frontend && rm -rf .next && pnpm lint && pnpm typecheck && pnpm format:check && pnpm test && pnpm build` | 20:24 → 20:28 | 457 files, 3763 passed / 2 skipped | **exit 0 — 477 files, 4347 passed / 2 skipped (4349)** | +20 files, +584 tests (the phase's new vitest suites) |
| **PORTAL-GATE** | `cd portal && pnpm lint && pnpm typecheck && pnpm format:check && pnpm test && pnpm build` | 20:28 → 20:28 | 51 files, 284 passed | **exit 0 — 51 files, 284 passed** | = (portal only received the token retune) |

Flaky / retried tests across the three Playwright runs: **0** (`grep -ciE "flaky|retry #"` = 0 on each log).

The baseline E2E-DEFAULT failure (INV-02 *DRAFT → APPROVED*, `e2e/tests/invoices/invoice-lifecycle.spec.ts:88`) was recorded in 637.1 as intermittent, cause unconfirmed, and passing on the next fresh stack. It passed here too (2.5 s). This exit run shows it is not a Phase 90 regression. It does not show the intermittency is gone, so it stays an open baseline observation, not a fix.

## Fresh stack (644.1)

- Sequence (in the worktree, any time of day): `bash compose/scripts/e2e-down.sh` (exit 0, volumes wiped) → `SHELL=/bin/bash docker builder prune -f` (exit 0) → `bash compose/scripts/e2e-up.sh` (exit 0, 20:15:37 → 20:17:27Z, "Building from source at: …/worktree-epic-644A").
- Containers: all six `e2e-*` containers created 22:16:53–54 SAST (= 20:16:53Z) by that `e2e-up`.
- Images: `docteams-e2e-frontend` image built in this run (created 20:16:53Z). `docteams-e2e-backend` resolved entirely from layer cache, including `COPY src src` (image created 04:39Z). The cache is content-addressed, and `builder prune` does not evict image-store layers, so a CACHED `COPY src src` means the backend build context matched an earlier build byte for byte. That is consistent with `git log -- backend/`: the last backend commit on `main` is `5864da54d` (643B #1776), whose head was built for 643B's own E2E.
- After the suites: `git checkout -- frontend/test-results documentation/screenshots`. The run also left 26 untracked `documentation/screenshots/legal-vertical/*.png`, removed with `git clean -f documentation/screenshots`. The tree is clean apart from this evidence folder (`git status --short` → `?? qa_cycle/evidence/phase90-ui/suites/`).

**Specs amended by a phase slice:** none. `git diff --name-only f7b7dbdf1..2773ec9b6 -- frontend/e2e` is empty, so no spec, helper or Playwright config changed between the 637.1 baseline and the exit head (matches 644.1's "expected: none").

## Verify markers (644.2)

See [`markers.md`](markers.md): `scripts/check-verify-markers.sh --committed-only` over #1758–#1777 → **20 / 20 PASS**. Backend markers are present for P1, 643A and 643B. Every UI slice has a frontend marker, and portal markers exist for 637A and 639A (plus 640A and 640C).

## Files

| File | What |
|---|---|
| `run-gates.sh` | the runner, verbatim |
| `summary.txt` | runner timeline and counts |
| `be-verify-tail.txt` | Maven totals and `BUILD SUCCESS` line |
| `legal-lifecycle-tail.txt`, `legal-depth-ii-tail.txt`, `default-tail.txt` | Playwright result tails |
| `default-inv02.txt` | every INV-02 line from the E2E-DEFAULT log (all ✓) |
| `fe-test-tail.txt`, `portal-test-tail.txt` | vitest totals |
| `markers.md` | marker audit (644.2) |

Full logs (not committed; ~MBs) were in `/tmp/644A/run1/` on the run host.
