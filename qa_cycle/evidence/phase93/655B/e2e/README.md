# 655B E2E-LEGAL (task 655.17, PR #1851 @ `5eb996c5c`)

All runs from `worktree-epic-655B` (branch `phase93/55B-fica-review-queue`) on nvm node **v20.11.1** (first line of each log), `PLAYWRIGHT_BASE_URL=http://localhost:3001`, `NODE_OPTIONS=""`, pnpm by absolute path. Fresh stack: `e2e-down.sh && e2e-up.sh` built from the worktree at `5eb996c5c0b26a5ae62335b8d73cd773b233f1ba` (`stack-head.txt`, `stack1-down.log`, `stack1-up.log`). Containers created 2026-11-24T01:39:30Z (`stack1-containers.txt`). The built frontend bundle contains the 655B label "Review due (attested)" (`/app/frontend/.next/server/chunks/ssr/…`), so the image is the PR code. Tenant `tenant_7d218705360b` at Flyway **V34** `phase93 client take on fica` (`stack1-flyway.txt`; 655B adds no migration — its three `org_settings.fica_review_months_*` columns come from V34). No Maven verify ran at the same time; one stack only.

| Head | Suite | Result | Baseline | Log |
|---|---|---|---|---|
| `5eb996c5c` | `test:e2e:legal-lifecycle` (01:40:37Z) | **105 passed** (3.3 m) | 105 passed | `lifecycle-5eb996c.log` (force-added) |
| `5eb996c5c` | `test:e2e:legal-depth-ii` (after lifecycle, same stack, 01:44:04Z) | **40 passed, 4 skipped** (2.2 m) | 40 passed, 4 skipped | `depth-5eb996c.log` (force-added) |

Notes
- Both suites equal the baseline on the first run: 0 failed, 0 flaky, no retries. Nothing failed, so no re-run was needed.
- The 4 skips are the opt-in `null-prefetch-probe.spec.ts` probes, as in the baseline.
- The lifecycle run wrote 25 untracked `documentation/screenshots/legal-vertical/*.png`; they were deleted with `git clean -f documentation/screenshots/legal-vertical/` (tracked files untouched). No tracked Playwright config was dirtied (`git status` showed only this evidence folder).
- The stack was then torn down and rebuilt fresh for OBS (`../obs/`).
