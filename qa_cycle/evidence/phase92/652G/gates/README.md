# 652G — gates (orchestrator)

Slice head: `58bc3b7919f2` (PR #1819). Merged as `fa616a66a`.

| Gate | Result |
|---|---|
| Reproduce | RED on main `1621e4cfe`. `zeroAmountBalanceRows_areNotImportedAsLines` failed because the parser kept `Opening Balance` 0.00 as line 0. See [`../repro-red.txt`](../repro-red.txt). The test was committed alone as `cde56a5a7` before the fix (`58bc3b791`). |
| Tier 2 (builder) | 9 classes, 170 testcases, 0 failures. |
| BE-VERIFY | `verify.sh` exit 0 at `58bc3b791`. **7 405 testcases, 0 failures, 0 errors, 26 skipped** (counted from `<testcase>`), 27:45 min. That is +16 over 652D's 7 389. |
| CI Backend | pass, 32m22s. |
| E2E-LEGAL (stage-9 checkpoint) | Each suite ran on its own fresh stack (`e2e-down` + `e2e-up` from the slice worktree), with `PLAYWRIGHT_BASE_URL=http://localhost:3001`. legal-lifecycle **105 passed**; legal-depth-ii **40 passed, 4 skipped**. Both are at the post-651C baseline. Summaries: `e2e-*-summary.txt`; stack head: `e2e-head.txt`. |
| OBS | Not required for 652G (task row: "observed in 652E step 9"). |
| Review | Agent A APPROVE @58bc3b791 (0/0/0, 2 notes). Agent B APPROVE @58bc3b791 (0/0/0, 1 nit). CodeRabbit REVIEWED, no actionable comments. 0 fix cycles. |

The first `e2e-up` attempt exited with 127 because the runner's PATH lacked the Docker CLI. That was an environment problem with the orchestrator script, not the stack. The re-run results are in the table above.
