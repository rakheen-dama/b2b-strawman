# Backend Test-Speed Campaign — 2026-07-12

Plan: `docs/superpowers/plans/2026-07-12-backend-test-speed.md`
Worktree: `../b2b-strawman-test-speed` (branch `perf/backend-test-speed-2026-07`)
Mode: orchestrator + scouts (parallel, read-only) + consensus reviewers + sequential builders.
(Previous simplification-roadmap tracker was fully complete — see memory `project_simplification_roadmap_2026-06`.)

- [x] 0. Baseline GREEN: 6,069 tests, wall 3587s raw → ~1621s sleep-corrected (pmset-proven: two ~16-min system sleeps absorbed by 2 testcases) → `hot-classes.md`. No new monster classes; June distribution holds. NEW SCOPE for B3: verify wrapper must use `caffeinate -is`.
- [x] 1a. Scout A — 6 candidates found → `tasks/test-speed-2026-07/candidates.md`
- [x] 1b. Scout B — 2 new fixes (preflight scripts + GreenMail dynamic port); surefire levers already in pom since Feb; Hikari NO-GO → `flakiness-fixes.md`
- [x] 2. Consensus gate: 7/7 items 2/2 APPROVE (with binding implementation notes) → `consensus.md`
- [ ] 3. Implement (sequential builders, shared worktree): B1 items 1/2/6 DONE (commits b1a47f7/c4cdc9b/b3177d7, all green) → B2 items 3/4/5 DONE (c8980e34a/095ad882f/d7f46a5c2, combined 40 tests green) → B3 item 7 + preflight/caffeinate scripts + docs (RUNNING)
- [x] 4. Full verify in worktree GREEN: 6,070 tests 0F/0E, wall 1577s caffeinated-idle (vs ~1621s corrected baseline). +1 test = exact planned delta; converted classes sub-second; 0 GreenMail bind errors; no new distinct contexts (missCount 43 vs 40 = order-dependent rebuilds).
- [x] 5. PR #1547 MERGED (squash 1dbd038a4). Review round: both agents REQUEST_CHANGES → fix commit 4970dc17a → both APPROVE; CodeRabbit 3 Major (2 fixed, 1 declined with accepted rationale); CI Backend/CodeRabbit/qodana green on both commits; full verify re-run green on fix commit.

## Review

**Outcome.** Six consensus-gated integration→unit conversions + three flakiness fixes merged as PR #1547. Full verify: 6,070 tests 0F/0E, wall ~26 min (1565s) — statistically unchanged vs the ~27 min sleep-corrected baseline, as scoped: the suite is integration-dominated and the June structural verdicts stand. Real wins: converted classes sub-second (3 tenant provisions eliminated), heap/OOM headroom, and the leftover-process flake class closed (GreenMail dynamic port with bind-retry, preflight zombie sweep scoped to b2b-strawman, caffeinate wrapper).

**Notable discovery.** The baseline's apparent 60-min wall was two macOS system sleeps (989s+977s, pmset-matched to the second against two stalled test spots). Unattended verifies absorbing sleep likely fed the "tests keep getting slower" perception. `backend/scripts/verify.sh` (caffeinate) prevents recurrence.

**Process.** 2-reviewer consensus gate earned its cost twice: reviewers found the OrgSettings protected-ctor, heykazi-prefix tautology trap, missing junit-platform-launcher dep, and ephemeral-port risk BEFORE build; the post-build review round found the TOCTOU race + session-abort blast radius (3 reviewers converged independently on the TOCTOU). One builder died mid-stream (API error) and was resumed cleanly from a clean worktree.

**Pre-existing anomalies triaged in PR body (not fixed, out of scope):** 65 caught `org_settings does not exist` TenantScopedRunner errors over stub shard tenants (identical on main); Surefire 60s self-kill after System.exit (the pom cap working; non-daemon-thread root cause still open).
