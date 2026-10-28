# 626H — un-fixme day-14 OTP clauses + day-30-45 SoA preview; Epic 626 gate (task 626.18)

PR #1692 (squash `6b15cc40f`), branch `phase88/09H-docs-specs-gate`. The orchestrator ran the gate on 2026-10-28 in `worktree-epic-626H`. The change is E2E specs only, with no product code.

## Reproduction (before any spec edit, fresh stack built from `76dd7971f`)

- Command: `day-05 day-14 day-30-45` (depth-ii config, `--update-snapshots=all`).
- Result: **23 passed / 1 failed / 4 skipped / 1 did not run**. See `logs/before-run.log`.
- **`day-14:297` failed.** 626G (#1691) added clause associations to the OTP, so `GenerateDocumentDialog` now opens on the clause step ("Step 1 of 2"). The preview iframe only exists after "Next: Preview". The failure:
  `Expected substring: "Offer to Purchase"` → `element(s) not found`
- 626G did not run depth-ii, so the break wasn't observed when it merged. This PR fixes it under B-7: one `Next` click, no assertion changed. Scenario 14.7/14.8 (`legal-za-90day-keycloak.md:342-343`) supports the extra click.
- After the edits, on the same stack: **27 passed / 0 failed / 2 skipped**. See `logs/after-run.log`.

## Verdicts

| Gate | Result | Baseline | Verdict | Evidence |
|---|---|---|---|---|
| **BE-VERIFY** on merged head `76dd7971f`. Ran `spotless:apply` then `scripts/verify.sh -q` | exit 0, 16:11; **6159 testcases / 0 failures / 0 errors / 26 skipped**; spotless produced no diff | 626G 6159 | **PASS** | `logs/be-verify-status.txt`, `.claude/markers/verify-backend.json` |
| **FE-GATE** on `ce58e7d22`. Ran `rm -rf .next`, then lint, format:check, vitest, build | lint 0 errors / 92 warnings; format clean; **424 files / 3243 passed / 2 skipped**; build OK | 626F 424/3243/2 | **PASS** | `logs/fe-*-tail.txt`, `.claude/markers/verify-frontend.json` |
| **E2E-LEGAL lifecycle**. Fresh `e2e-down` + `e2e-up` from the worktree at `ce58e7d22`, run with `--update-snapshots=all` | **74 passed / 4 failed / 5 skipped / 14 did not run** | 625G 74/4/5/14 | **= baseline** | `logs/gate-lifecycle.log` |
| **E2E-LEGAL depth-ii**. Same stack, run after lifecycle | **37 passed / 0 failed / 6 skipped** | 625G 35/0/8 | **PASS** (+2: `day-14:335`, `day-30-45:216`) | `logs/gate-depth.log` |
| **CI** on final head `c3279dfb7` | Frontend pass, qodana pass. Backend was skipped by the path filter | — | **PASS** | PR checks |
| **MARKERS** | backend `76dd7971f`, frontend `ce58e7d22`. The final head `c3279dfb7` differs by one E2E comment line | — | **DONE** | `.claude/markers/` |

## Lifecycle failures (all carried; the set did not grow)

- **A1** `day-00-firm-setup.spec.ts:282`
- **A2** `day-30-billing.spec.ts:32`
- **A4** `day-45-recon-prescription.spec.ts:41`
- **A6** `day-90-review.spec.ts:179`. The soft-assert lines are `:204`, `:212` and `:220`, the same three as in 625G.

## Still fixme (out of scope)

- `day-14:139` FICA checklist → ACTIVE. No Phase 88 owner. It is a candidate to probe now that Next is on 16.3.8 (#1651).
- `day-30-45:299` Statements-tab Download. This is an environment limit: the LocalStack presigner host can't be reached from the frontend container.

## Follow-ups

- **Commit stable screenshot baselines suite-wide.** CodeRabbit's Major on `:222` was triaged no-change and then withdrawn. Only `.gitkeep` is tracked, while depth-ii makes 33 `captureScreenshot` calls, and the doc comment at `screenshot.ts:38-40` says baselines are "committed".
- **Same-day SoA S3-key collision.** `StatementService.java:129-132` gives a second statement generated the same day the same key, so its PDF overwrites the earlier one. The `:216` test now saves a second statement on Sipho's matter.

## Stack state after the gate

Fresh stack built from `ce58e7d22` (product = main), then lifecycle and depth-ii suite data.

**Epic 626 Done**: 626A #1685, 626B #1686, 626C #1687, 626D #1688, 626E #1689, 626F #1690, 626G #1691, 626H #1692.
