# Phase 93 baseline — task 653.1 (recorded on `main` @ `cb803686c`, 2026-11-23)

The numbers later slices and the 660A capstone compare against. `main` @ `cb803686c` has the same backend and frontend tree as `f4923eb29` (PR #1845); `cb803686c` changes docs only.

| Gate | Baseline | Source |
|---|---|---|
| BE-VERIFY `<testcase>` total | **7576**, 0 failures / 0 errors | `.claude/markers/verify-backend.json` @ `fe1ab1a74` (PR #1845 backend head; tree identical to main) |
| BE-VERIFY skips | 26 (the 653A run at `9bddc3f40` counts 26 `<skipped>`; the PR #1845 marker does not record skips) | 653A full verify |
| FE-GATE vitest | 487 files / **4654 passed** / 2 skipped | `verify-frontend.json` @ `cb414488b` (PR #1845) |
| PORTAL-GATE vitest | 51 files / **292 passed** | `verify-portal.json` @ `9063aceda` (PR #1841, last portal change) |
| E2E `test:e2e:legal-lifecycle` (fresh stack, main) | **105 passed** (3.9 min) | run 2026-11-23 11:17–11:21 SAST from worktree `wt-653-baseline` @ `cb803686c` |
| E2E `test:e2e:legal-depth-ii` (main) | **not a valid baseline**: 10 passed, then the mock IDP stopped answering (`TypeError: fetch failed` at `e2e/fixtures/auth.ts:34`) → 4 failed, 4 skipped, 26 did not run. The stack was later taken down externally at 11:34 SAST. Reference instead: the 653A PR run on a fresh stack — **40 passed, 4 skipped**. | `/tmp/653base/depth.log` |
| E2E `test:e2e:exit-month` (main) | **not captured**: same mock-IDP outage (`fetch failed`). Must run **alone on a fresh stack** (the spec's step 1 refuses a stack where the seeded trust account already has transactions). To be re-taken before the first trust slice (657A). | `/tmp/653base/exitmonth.log` |
| Audit event-type registry | 83 (`AuditEventTypeRegistryTest`); 653A takes it to 89 | scout count at `cb803686c` |
| Next free migration | tenant **V32** (V31 = `phase92_trust_idempotency_keys`), global **V8** | `ls backend/src/main/resources/db/migration/{tenant,global}` |

Commands:
`bash compose/scripts/e2e-down.sh && bash compose/scripts/e2e-up.sh`, then from `frontend/` with `PLAYWRIGHT_BASE_URL=http://localhost:3001 NODE_OPTIONS=""`: `pnpm test:e2e:legal-lifecycle --reporter=list`, `pnpm test:e2e:legal-depth-ii --reporter=list`, `pnpm test:e2e:exit-month --reporter=list`.
