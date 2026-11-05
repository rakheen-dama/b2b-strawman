# 634E — Orchestrator E2E gate (PR #1737, head 6e6c9a242)

Every stack was built from the 634E worktree with `e2e-down.sh` → `docker builder prune -f` → `e2e-up.sh`, unless the row says otherwise. Runs used `E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001`. After each run, `git checkout -- frontend/test-results documentation/screenshots` restored the tracked files. Visual baselines are not committed, so the first run of each legal suite only captures them.

| # | Stack | Suite | Result | Notes |
|---|---|---|---|---|
| 1 | fresh | `test:e2e` (default) | 159 passed, 2 failed, 14 skipped | The 2 failures are a pre-existing ordering defect, analysed below. Case 9 (B7) passed. |
| 2 | fresh | `test:e2e:legal-lifecycle` | 79 passed, 26 failed | All 26 were first-run baseline captures ("A snapshot doesn't exist … writing actual"). |
| 3 | fresh | `test:e2e:legal-depth-ii` | 17 passed, 22 failed, 4 skipped | First-run baseline captures: 23 "writing actual" messages. |
| 4 | fresh | closure reproduction (below) | reproduced | `Website Redesign` went ACTIVE → CLOSED |
| 5 | fresh | `test:e2e` (default) | 159 passed, 2 failed, 4 did not run, 14 skipped | The same 2 failures, deterministic on a pristine stack. Case 9 passed. |
| 6 | fresh | `test:e2e:legal-lifecycle` | **105 passed**, exit 0 | |
| 7 | fresh | `test:e2e:legal-depth-ii` | **39 passed, 4 skipped**, exit 0 | The 4 skips are the existing `test.skip`/`fixme`. |
| 8 | stack 7 (has non-seed matters) | `test:e2e` (default) | **165 passed, 14 skipped**, exit 0 | Case 8 (G88-M6) and case 9 (B7) passed; `Website Redesign` stayed ACTIVE. |

## The two default-suite failures: pre-existing, not caused by this PR

These two tests fail:
- `documents/document-generation.spec.ts:118` › Generated documents list
- `information-requests/information-request-crud.spec.ts:70` › View information requests list

Both stop at `helpers/seed.ts:37`: `seed row "Website Redesign" not found via GET /api/matters`.

**Mechanism:**
1. `audit-log/matter-closure-audit-tab.spec.ts:40-61` (`findCloseableMatter`) closes, with override, the first non-CLOSED, non-ARCHIVED matter in `GET /api/matters?size=50`.
2. On a pristine stack the only matter is the seed matter, `Website Redesign`. Closing it breaks the read-only seed-row rule (628F R-5, `helpers/seed.ts:4-6`).
3. `GET /api/matters` with no `status` returns only ACTIVE matters (`ProjectService.java:279-281`). The seed lookups made after that point therefore fail.
4. The closure spec finished second (#2) in run 1. The failures came at #61 and #92.

**Reproduction (run 4):** on a fresh stack, a database read showed `Website Redesign|ACTIVE`. I ran `matter-closure-audit-tab.spec.ts` alone (1 passed) and read the database again: `Website Redesign|CLOSED`. I then ran the two lookup specs: the same 2 failures.

**Why 634D's default run was green:** it ran on stack A after `legal-depth-ii`, which had already created matters, so the closure spec closed one of those. Run 8 recreates that condition here: 165/165.

**Unrelated to this PR:** the PR does not touch the closure spec, `helpers/seed.ts`, the seed, or anything in the backend. The mechanism runs only through backend matter listing and those spec files.

**Triage:** I did not fix it here, because that would be scope expansion (CLAUDE.md §7). Follow-up: `matter-closure-audit-tab.spec.ts` should create and close its own run-unique matter (628F R-5) instead of the first ACTIVE one.
