# 657C E2E-LEGAL + exit-month (PR #1854 @ `f22e316a5`)

All runs from `worktree-epic-657C` (branch `epic-657C/trust-sign-off-and-commit`, head `f22e316a5…` — `stack{1,2}-head.txt`) on nvm node **v20.11.1** (first line of each log), `PLAYWRIGHT_BASE_URL=http://localhost:3001`, `NODE_OPTIONS=""`, pnpm by absolute path (`/opt/homebrew/bin/pnpm`), flags without `--`. Each stack fresh (`e2e-down.sh && e2e-up.sh` from the worktree). No Maven verify ran (`pgrep -fl surefire` empty before each suite). 2026-11-24.

| Stack | Suite | Command | Result | Baseline (657B) | Log |
|---|---|---|---|---|---|
| 1 (containers 10:32:10Z) | legal-lifecycle (start 10:34:25Z) | `pnpm test:e2e:legal-lifecycle` | **105 passed** (3.2 m) | 105 passed | `lifecycle-f22e316.log` |
| 1 (same stack, after lifecycle) | legal-depth-ii (start 10:38:14Z) | `pnpm test:e2e:legal-depth-ii` | **40 passed, 4 skipped** (2.0 m) | 40 passed, 4 skipped | `depth-f22e316.log` |
| 2 (containers 10:40:26Z) | exit-month (start 10:41:49Z) | `PORTAL_BASE_URL=http://localhost:3002 pnpm test:e2e:exit-month`, portal `cd portal && NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081 pnpm dev` (`portal-dev.log`) | **16 passed** (1.4 m) | 16 passed | `exitmonth-f22e316.log` |

0 failed, 0 flaky on every suite; first run each, no re-runs. The 4 depth-ii skips are the opt-in `null-prefetch-probe.spec.ts` probes, as in the baseline.

Provenance
- Stack 1: the frontend bundle holds the 657C testid `take-on-sign-off-widget` (dashboard client-reference manifest + two SSR chunks) and `/app/app.jar` holds `takeon/TakeOnReportService.class` and `trustaccounting/takeon/TrustOpeningPositionService.class`, built 10:31 (`stack1-657c-provenance.txt`).
- Tenant `tenant_7d218705360b` at Flyway **V36** `phase93 take on reports` on stacks 1, 2 and 3 (`stack{1,2,3}-flyway.txt`). Containers: `stack{1,2,3}-containers.txt`; up/down logs `stack{1,2,3}-*.log`.
- Stack 2 was then used for OBS run G, stack 3 (fresh, containers 10:48:53Z) for OBS run P (`../obs/README.md`). **Stack 3 is left running.**

Tree hygiene
- The lifecycle run wrote 25 untracked `documentation/screenshots/legal-vertical/*.png`; removed with `git clean -f documentation/screenshots/legal-vertical/`. No tracked file (Playwright configs included) was modified — `git status` showed only this evidence folder before the commit.
- The portal dev server on :3002 (started for exit-month, reused for OBS check 6) was stopped at the end.
