# 655A E2E-LEGAL (PR #1850)

All runs from `worktree-epic-655A` on nvm node v20.11.1, `PLAYWRIGHT_BASE_URL=http://localhost:3001`, on a **fresh** stack (`e2e-down.sh && e2e-up.sh`, built from the worktree at `d2738e819`; `stack1-head.txt`, `stack1-up.log`). Backend container created 2026-11-23T22:23:16Z; Docker Desktop had to be started first (it was not running). Tenant `tenant_7d218705360b` at Flyway **V34** `phase93 client take on fica` (`stack1-flyway.txt`). No run overlapped a Maven verify. 2026-11-23/24.

| Suite | Result | Baseline | Log |
|---|---|---|---|
| `test:e2e:legal-lifecycle` | **105 passed** (3.3 m) | 105 passed | `lifecycle.log` |
| `test:e2e:legal-depth-ii` (after lifecycle, same stack) | **40 passed, 4 skipped** (2.1 m) | 40 passed, 4 skipped | `depth.log` |

Notes
- Both suites equal the baseline on the first run; nothing failed, so no re-run was needed.
- The lifecycle run wrote 25 untracked `documentation/screenshots/legal-vertical/*.png`; they were deleted after the run. No tracked Playwright config was dirtied (`git status` clean apart from this evidence folder).
- The stack was then torn down and rebuilt fresh for OBS (`../obs/`).

## Run 2: PR #1850 @ `493c4fe68` (after the OBS-655A-1/2 fixes)

Fresh stack (`e2e-down.sh && e2e-up.sh`) built from `worktree-epic-655A` at `493c4fe68` (`stack2-head.txt`, `stack2-up.log`, `stack2-down.log`; the run-2 `*.log` files are force-added because `*.log` is gitignored). Backend container created 2026-11-23T22:59:03Z. The built frontend bundle contains the new label "Duplicate — will be skipped", so the image is the fixed code. Tenant `tenant_7d218705360b` at Flyway **V34** `phase93 client take on fica` (`stack2-flyway.txt`). nvm node v20.11.1, `PLAYWRIGHT_BASE_URL=http://localhost:3001`. No Maven verify ran at the same time.

| Head | Suite | Result | Baseline | Log |
|---|---|---|---|---|
| `d2738e819` | `test:e2e:legal-lifecycle` | **105 passed** (3.3 m) | 105 passed | `lifecycle.log` (local only, `*.log` is gitignored) |
| `d2738e819` | `test:e2e:legal-depth-ii` | **40 passed, 4 skipped** (2.1 m) | 40 passed, 4 skipped | `depth.log` (local only) |
| `493c4fe68` | `test:e2e:legal-lifecycle` | **105 passed** (3.1 m) | 105 passed | `lifecycle-493c4fe.log` (force-added) |
| `493c4fe68` | `test:e2e:legal-depth-ii` (after lifecycle, same stack) | **40 passed, 4 skipped** (1.9 m) | 40 passed, 4 skipped | `depth-493c4fe.log` (force-added) |

Notes (run 2)
- Both suites equal the baseline on the first run. Nothing failed, so no re-run was needed.
- The lifecycle run wrote 25 untracked `documentation/screenshots/legal-vertical/*.png`. They were deleted with `git clean -f documentation/screenshots/legal-vertical/` (the 3 tracked files there were not touched). No tracked config was dirtied.
- The stack was then torn down and rebuilt fresh for the OBS re-run (`../obs/run2/`).
