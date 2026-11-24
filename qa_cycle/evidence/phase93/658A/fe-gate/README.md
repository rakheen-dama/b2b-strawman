# 658A FE-GATE @ 0d08e820f

Command (frontend/, nvm node v20.11.1, pnpm by absolute path): `rm -rf .next && pnpm lint && pnpm typecheck && pnpm format:check && pnpm test && pnpm build`.

| Step | Run 1 (concurrent with the OBS browser run) | Run 2 |
|---|---|---|
| lint | 0 (0 errors) | — |
| typecheck | 0 | — |
| format:check | 0 | — |
| test (full vitest) | **1 failed** / 4959 passed / 2 skipped — `__tests__/trust/trust-balance-trend-chart.test.tsx` `findByRole('figure')` exceeded its default 1000 ms wait (1141 ms) | **506 files, 4960 passed, 2 skipped, exit 0** |
| build | 0 | — |

Triage of the run-1 failure: the file is not touched by PR #1857 and the frontend is unchanged between 170c5eea3 (fixer's full run: 4960 passed) and 0d08e820f. Re-run in isolation: 6/6 passed; full suite re-run with the machine quieter: green. Root cause: a timing-sensitive `findByRole` with the default 1 s timeout under load (load average ≈ 20 during the OBS run). Recorded as F-658A-7 in `../found.md` (pre-existing test fragility, not 658A).
