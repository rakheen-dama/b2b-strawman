# 657A E2E-LEGAL + exit-month (PR #1847 @ `05812c045`)

All runs from `worktree-epic-657A` on nvm node v20.11.1, `PLAYWRIGHT_BASE_URL=http://localhost:3001`, each on a **fresh** stack (`e2e-down.sh && e2e-up.sh`, built from the worktree at `05812c045`). No run overlapped a Maven verify. 2026-11-23.

| Suite | Result | Baseline (653.1 / 653A) | Log |
|---|---|---|---|
| `test:e2e:exit-month` (with portal on :3002 against :8081, `PORTAL_BASE_URL=http://localhost:3002`) | **16 passed** (1.4 m) | not captured on main (baseline README); 653A run failed at step 1 | `exitmonth.log`, `exitmonth-run/psql-output.txt` |
| `test:e2e:legal-lifecycle` run 1 | 90 passed, **2 failed**, 13 did not run | 105 | `lifecycle.log` |
| `test:e2e:legal-lifecycle` run 2 (fresh stack) | **105 passed** (3.6 m) | 105 | `lifecycle-run2.log` |
| `test:e2e:legal-depth-ii` (after lifecycle run 1, same stack) | **40 passed, 4 skipped** (2.2 m) | 40 passed, 4 skipped | `depth.log` |

Notes
- Exit-month first attempt (stack 1) failed step 19 only because no portal was running (`PORTAL_BASE_URL is not set`, ruling D-3 of 652E); re-run on a fresh stack with the portal → 16/16. That stack was then used for OBS (`../obs/`).
- Lifecycle run 1's two failures are in Day 75 time recording (`Prepare Liquidation & Distribution account 6h` not shown; only 1 h on another task) and Day 90 matters list (`Apex Annual Returns …` link not found). 657A changes no time-recording, matters-list or non-trust frontend code (the PR's frontend diff is `trust-accounting/**`, `components/trust/**`, `lib/types/trust.ts` only). They did not reproduce on run 2 (105/105). Recorded, not dismissed: `../found.md` F-657A-1.
- The lifecycle run writes 25 untracked `documentation/screenshots/legal-vertical/*.png`; removed after the run (memory `env_playwright_legal_configs_dirty_tree`).
