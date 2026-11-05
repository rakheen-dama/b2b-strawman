# 636A — legal-lifecycle (E2E-LEGAL), Phase 89 exit gate

**Status:** PASS

| Field | Value |
|---|---|
| Command | `cd frontend && NODE_OPTIONS= E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 BACKEND_URL=http://localhost:8081 MAILPIT_URL=http://localhost:8026 PLAYWRIGHT_JSON_OUTPUT_FILE=/tmp/636A/G1-lifecycle.json pnpm test:e2e:legal-lifecycle --reporter=list,json` |
| Stack | G1. Fresh (`e2e-down.sh && docker builder prune -f && e2e-up.sh`) from `$WT` @ `1034b6af8` (= origin/main). Containers created 2026-11-05T18:06:54Z. Backend image `8111f5608eda`, frontend `d026d5fd096c`. Tenant `tenant_7d218705360b`. Tenant Flyway 11, global 3. Seed `E2E Boot-Seed Complete!`. Pristine `projects` = `Website Redesign\|ACTIVE` |
| Baselines | Captured on stack C1 (`--update-snapshots=all`, same sha, same suite order): 26 frames, 0 missing vs the spec `captureScreenshot` names. Not committed |
| Order on stack | lifecycle → depth-ii → default (this suite ran first) |
| Start / end (SAST) | 20:07:45 / 20:10:40 (2.9 min). Outside 00:00–02:00 ✓ |
| Result (JSON stats) | expected 105 / unexpected 0 / skipped 0 / flaky 0 (duration 174 s) |
| Result (list summary) | `105 passed (2.9m)` |
| Exit code | 0 |
| Snapshot writes | 0 "writing actual" / "snapshot doesn't exist" lines, so every frame was compared against the C1 baseline |

## Baseline comparison

| | passed | failed | skipped | did not run |
|---|---|---|---|---|
| Plan floor | ≥ 102 | 0 | 2 | — |
| Current main (634E run 6 / 634D H / 634C D) | 105 | 0 | 0 | 0 |
| **This run (G1)** | **105** | **0** | **0** | **0** |

No test's outcome differs from current main: all 105 passed in both. No per-test list exists for the current-main runs, so this comparison rests on equal totals with 0 failed and 0 skipped on both sides.

Per-file counts (from `raw/G1-lifecycle.tsv`):

| File | Tests |
|---|---|
| day-00-firm-setup | 16 |
| day-01-03-onboarding | 17 |
| day-07-14-work-trust | 16 |
| day-30-billing | 9 |
| day-45-recon-prescription | 5 |
| day-60-interest | 10 |
| day-75-complex | 9 |
| day-90-review | 19 |
| matter-team | 4 |

## Named specs

| Spec | Result |
|---|---|
| `day-60-interest.spec.ts:97` "Alice: Interest run — create for the last 30 days and calculate allocations at the 6.5% LPFF rate" (contains the plan's `:160` `run-status` = Draft assertion) | ✓ |
| `day-60-interest.spec.ts:168` "Bob: Review & approve Alice's interest run as the second member" | ✓ |
| `day-60-interest.spec.ts:189` "Alice: Post the approved interest run to the client ledgers" | ✓ |
| `day-90-review.spec.ts:118` "Alice: Generate Section 35 Data Pack report" | ✓ |

## Skips

None. The known-skip list for this suite is empty.

## Failures

None. The known candidates did not recur: the `day-30-billing` `day-30-fee-note-tariff-completed` / `day-30-fee-note-list-mixed` pixel diffs, and the `day-00-dashboard-legal-nav-active` diff on a polluted capture stack. The baselines were captured on a pristine C1 with lifecycle first, and the G1 frames matched within the threshold.

## Capture run (C1, unscored)

- Result: `105 passed (2.9m)` (JSON: expected 105 / unexpected 0 / skipped 0), rc 0, 20:00:06–20:03:04 SAST.
- 26 "writing actual" lines and 26 PNGs in `frontend/e2e/screenshots/legal-lifecycle/`. These match the 26 non-curated `captureScreenshot` names in the specs exactly.
- No non-snapshot failure occurred.
- Visual sanity check: `day-00-dashboard-legal-nav-active.png` shows an authenticated Home dashboard (Alice Owner, E2E Test Organization), not `/login`.

Summary: `raw/C1-lifecycle.summary.txt`.

## Raw output

`raw/G1-lifecycle.txt`, `raw/G1-lifecycle.tsv`, `raw/G1-lifecycle.json`

## Tree hygiene

See `README.md` § Tree hygiene. After cleanup, `git status --short` showed only `?? qa_cycle/evidence/phase89-rewalk/suites/`.
