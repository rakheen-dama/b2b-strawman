# 647C gates — round 3: depth-ii re-run on the final head (PR #1795 @ df8550113, 2026-11-17 SAST)

Why: round 2 (`../gates-r2/`) depth-ii run 1 was 32 passed / 1 failed / 4 skipped / 7 did not run. The failure was
N-1, `day-30-45-soa-writeoff.spec.ts:227`: the matter Time tab showed a stale "No time tracked yet" after a 2h log,
although the write had landed. A targeted re-run was green, but that is not a clean full-suite run, so the orchestrator
re-ran the full suite on a fresh stack at the final head.

- **Stack #3:** `e2e-down.sh` then `e2e-up.sh` from the worktree (`stack3-build.log`: "Building from source at:
  …/worktree-epic-647C", rc 0). The tenant was fresh, with 0 trust txns and 0 statements, and `:3001` returned 200.
  The frontend `.next` contains the F-1 fix (`shrink whitespace-normal` in 2 chunks) and `fund-ledger-table`. See
  `stack3-proof.txt`; HEAD is df85501137f690a42d680e1867605d0332e933ef.
- **No Maven JVM was running** (`pgrep -fl surefire` was empty).
- **E2E-LEGAL depth-ii:** **40 passed / 4 skipped / 0 failed in 2.0 min**, rc 0 (`depth-ii.log`, `depth-ii-rc.txt`).
  The 4 skips are the baseline ones (`day-30-45-soa-writeoff.spec.ts:361` fixme and `null-prefetch-probe.spec.ts:74`
  ×3). `day-30-45-soa-writeoff.spec.ts:227` passed.
- **Cleanup:** the stack was torn down (`stack3-down.log`). The suite screenshots were copied to
  `depth-ii-artifacts/` and `documentation/screenshots/legal-vertical` was restored.

N-1 triage: it is intermittent and not in the PR's code path. The only change between round 1 (depth-ii 40/4 green at
6e485f412) and df8550113 is `FundLedgerTable.tsx`, which the matter Time tab never renders. The symptom (a
server-action write landed but the revalidated view stayed stale for more than 15 s) matches the known next@16.2.6
lost-React-ping stall, but that is unverified. It is reported for the orchestrator's backlog and not fixed in this
slice (scope discipline).
