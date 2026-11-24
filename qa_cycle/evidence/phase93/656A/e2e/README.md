# 656A E2E-LEGAL (PR #1852 @ `580ae0388`)

All runs from `worktree-epic-656A` (branch `phase93/56A-matters-go-live`) on nvm node **v20.11.1** (first line of each log), `PLAYWRIGHT_BASE_URL=http://localhost:3001`, `NODE_OPTIONS=""`, pnpm by absolute path. Fresh stack: `e2e-down.sh && e2e-up.sh` built from the worktree at `580ae03883055093a8a8685635946df7a1d2d328` (`stack1-head.txt`, `stack1-down.log`, `stack1-up.log`). Containers created 2026-11-24T04:02:32Z (`stack1-containers.txt`). The built frontend bundle contains the 656A card title "Go-live checklist" (`/app/frontend/.next/server/chunks/ssr/frontend_0imcgad._.js`, `stack1-frontend-656a-string.txt`), so the image is the PR code. Tenant `tenant_7d218705360b` at Flyway **V35** `phase93 go live checklist` (`stack1-flyway.txt`). No Maven verify ran at the same time (`pgrep -fl surefire` empty); one stack only.

| Head | Suite | Result | Baseline | Log |
|---|---|---|---|---|
| `580ae0388` | `test:e2e:legal-lifecycle` (04:03:29Z) | **105 passed** (3.2 m) | 105 passed | `lifecycle-580ae03.log` (force-added) |
| `580ae0388` | `test:e2e:legal-depth-ii` (after lifecycle, same stack, 04:06:48Z) | **40 passed, 4 skipped** (1.9 m) | 40 passed, 4 skipped | `depth-580ae03.log` (force-added) |

Notes
- Both suites equal the baseline on the first run: 0 failed, 0 flaky, no retries. Nothing failed, so no re-run was needed.
- The 4 skips are the opt-in `null-prefetch-probe.spec.ts` probes, as in the baseline.
- The lifecycle run wrote 25 untracked `documentation/screenshots/legal-vertical/*.png`; they were deleted with `git clean -f documentation/screenshots/legal-vertical/` (tracked files untouched). No tracked Playwright config was dirtied (`git status` showed only this evidence folder).
- The stack was then torn down and rebuilt fresh for OBS (`../obs/`).
