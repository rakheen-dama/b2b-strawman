# 664B — E2E gates (orchestrator, 2026-11-26)

Run by the orchestrator with `run-e2e-664B.sh` (committed as executed) from `worktree-epic-664B` at `1e5a0b548`. The product code is `9ef3ae9c9`; later commits are evidence only. Node is nvm 20, pnpm is called by absolute path, flags are passed without `--`, and no Maven verify overlapped.

The script used two fresh stacks. Each one was set up with `e2e-down.sh`, then `docker builder prune -f`, then `e2e-up.sh`, with every image rebuilt from the worktree.

| Stack | Suite | Exit | Result | 661B baseline |
|---|---|---|---|---|
| 1 | `test:e2e:legal-lifecycle` | 0 | 105 passed (3.0m) | 105 |
| 1 | `test:e2e:legal-depth-ii` | 0 | 40 passed, 4 skipped (1.8m) | 40 + 4 skipped |
| 1 | `test:e2e:projects` (E2E-AREA, header change) | 0 | 27 passed | n/a |
| 1 | `test:e2e:customers` (E2E-AREA, header change) | 0 | 18 passed, 2 skipped | n/a |
| 2 | `test:e2e:exit-month` (portal dev on :3002, `PORTAL_BASE_URL`) | 0 | 16 passed (1.3m) | E2E-EXIT-MONTH |

- `trust-balanced-month.spec.ts`, the exit-month suite, covers other-creditors (`other-creditors-page`), the first consumer of the server-paged mode.
- The status file is `/tmp/e2e-664B.status`; the logs are `/tmp/e2e-664B-test-e2e-*.log`, not committed.
- The legal configs' untracked screenshot and test-result files were removed afterwards with `git clean -n` followed by `-f`, limited to the untracked files.

## SEL-COMPAT (browser level)

`before/sel-compat.json` and `after-fix/sel-compat.json` have the same 64 data keys. The only differences are `capturedAt`, `phase` and `stack`. This covers:
- the `other-creditors-page`, `no-account-state` and `fund-ledger-in-debit` testids;
- row testids;
- the pager texts and the names "Previous" / "Next";
- `aria-sort` on the matters and clients headers.

No e2e spec was edited.
