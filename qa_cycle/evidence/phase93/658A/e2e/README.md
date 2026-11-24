# 658A E2E-LEGAL

- **Head:** PR #1857 @ `0d08e820f` (backend image rebuilt at this head with `e2e-rebuild.sh backend`; frontend/portal images built at `170c5eea3` — the 170c5eea3..0d08e820f delta is backend + docs only).
- **Stack:** fresh — `e2e-down.sh && e2e-up.sh` from `worktree-epic-658A` (2026-11-24), replacing a stack left by the PR #1856 worktree.
- **Runner:** from the worktree's `frontend/`, nvm node v20.11.1, `PLAYWRIGHT_BASE_URL=http://localhost:3001`, flags without `--`.

| Suite | Command | Result | Baseline (653.1 / D-237) | Log |
|---|---|---|---|---|
| legal-lifecycle | `pnpm test:e2e:legal-lifecycle` | **105 passed** (3.7m), exit 0 | 105 | `lifecycle-0d08e82.log` |
| legal-depth-ii | `pnpm test:e2e:legal-depth-ii` | **40 passed, 4 skipped** (2.1m), exit 0 | 40 + 4 skipped | `depth-0d08e82.log` |

Tree restored after the runs (untracked Playwright screenshots under `documentation/screenshots/` removed; no tracked file modified).
