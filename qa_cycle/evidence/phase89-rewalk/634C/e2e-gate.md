# 634C — E2E gate (E2E-LEGAL + E2E-DEFAULT), PR #1735 head a8d5b86ae

Stack: E2E mock-auth (:3001 / :8081 / :5433), built from the worktree at `a8d5b86ae`. Each run below is on a **fresh** stack: `e2e-down.sh` → `docker builder prune -f` → `e2e-up.sh`. Flyway history ends at V11 and the `notifications.reference_comment_id` column is present (tenant `tenant_7d218705360b`). Runs were 2026-11-05, 14:38–15:12 SAST.

| Run | Suite | Result |
|-----|-------|--------|
| Stack A (14:38) | `e2e/tests/projects/` (playwright.config.ts) | **26 passed**. This includes the new `project-tasks.spec.ts` case 8, "Comment notification deep-links to the comment in the task sheet (G88-M6)". |
| Stack B (14:49), run 1 | `test:e2e:legal-lifecycle` | 79 passed, 26 failed. **All 26** were `A snapshot doesn't exist … writing actual`, because no baselines are committed (only `.gitkeep`), so this run captured them. |
| Stack B, run 1 | `test:e2e:legal-depth-ii` | 16 passed, 4 skipped, 1 did not run, 22 failed. 21 failures were missing baselines. The 22nd was `page.waitForLoadState("networkidle")`, which timed out after 30 s in `day-14-conveyancing.spec.ts:375` (OTP generate). |
| Stack B, run 1 | `test:e2e` (default) | **164 passed, 14 skipped**, exit 0. |
| Stack C (14:57), run 2 | `test:e2e:legal-lifecycle` | 72 passed, 5 failed, 28 did not run. The root failure is in `day-30-billing.spec.ts:90`: `day-30-fee-note-tariff-completed.png` differed by 12161 px (ratio 0.02, limit 0.01) from the run-1 baseline. The functional total assertion just before it (`5 482,63`) passed. The other 4 failures are serial cascades from that one. `apexFeeNote` was never recorded. The day-60 average daily balance was off by R274,20, which is the skipped Moroka R8 500 transfer divided by 31. A later fee-transfer dialog also failed. The screenshot includes the random run suffix in the matter name (`… mw3jmaqs70b9`), a toast and the bell count, so it legitimately varies from run to run. |
| Stack C, run 2 | `test:e2e:legal-depth-ii` | 38 passed, 4 skipped, 1 failed. The failure was a missing baseline, `day-14-otp-acceptance-request-sent.png`, never captured because run 1 timed out at that step. The networkidle timeout did **not** recur. |
| Stack D (15:04), run 3 | `test:e2e:legal-lifecycle` | **105 passed** (2.9 m), exit 0, against the captured baselines. |
| Stack E (15:09), run 3 | `test:e2e:legal-depth-ii` | **39 passed, 4 skipped** (2.0 m), exit 0. The 4 skips are existing `test.skip`/`fixme` in `day-05`, `day-14`, `day-30-45` and `null-prefetch-probe`. |

**Verdict.** The gates are green. Legal-lifecycle passes 105/105 and legal-depth-ii passes 39 with 4 existing skips, each on a fresh stack against captured baselines. Default passes 164 with 14 skipped, and the projects suite passes 26/26 including the new G88-M6 case.

**Not caused by 634C:**
- **Day-30 fee-note screenshot.** It varies between runs because of the random suffix and the toast. It passed in run 3.
- **Day-14 networkidle timeout.** It happened once and did not reproduce.

Neither page touches the code 634C changed. The fee-note editor does not use the notifications, comments, task-sheet or documents panel.

The worktree's `frontend/test-results` and `frontend/e2e/screenshots/*` are scratch output in the worktree, which is removed at merge. Nothing is committed.
