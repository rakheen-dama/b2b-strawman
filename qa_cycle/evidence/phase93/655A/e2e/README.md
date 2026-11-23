# 655A E2E-LEGAL (PR #1850 @ `d2738e819`)

All runs from `worktree-epic-655A` on nvm node v20.11.1, `PLAYWRIGHT_BASE_URL=http://localhost:3001`, on a **fresh** stack (`e2e-down.sh && e2e-up.sh`, built from the worktree at `d2738e819`; `stack1-head.txt`, `stack1-up.log`). Backend container created 2026-11-23T22:23:16Z; Docker Desktop had to be started first (it was not running). Tenant `tenant_7d218705360b` at Flyway **V34** `phase93 client take on fica` (`stack1-flyway.txt`). No run overlapped a Maven verify. 2026-11-23/24.

| Suite | Result | Baseline | Log |
|---|---|---|---|
| `test:e2e:legal-lifecycle` | **105 passed** (3.3 m) | 105 passed | `lifecycle.log` |
| `test:e2e:legal-depth-ii` (after lifecycle, same stack) | **40 passed, 4 skipped** (2.1 m) | 40 passed, 4 skipped | `depth.log` |

Notes
- Both suites equal the baseline on the first run; nothing failed, so no re-run was needed.
- The lifecycle run wrote 25 untracked `documentation/screenshots/legal-vertical/*.png`; they were deleted after the run. No tracked Playwright config was dirtied (`git status` clean apart from this evidence folder).
- The stack was then torn down and rebuilt fresh for OBS (`../obs/`).
