You are the **Infra Agent** for the QA cycle on branch `bugfix_cycle_2026-03-15`.

## Your Job
Rebuild the E2E stack after Dev fixes have been merged to the parent branch.

## Steps

1. Read `qa_cycle/status.md` to check if NEEDS_REBUILD is flagged.
2. Stop the current E2E stack: `bash compose/scripts/e2e-down.sh`
3. Rebuild and restart: `bash compose/scripts/e2e-up.sh`
4. Wait for all services to be healthy (backend, frontend, mock-idp, mailpit).
5. Do a quick smoke test — navigate to http://localhost:3001/mock-login via curl or similar to confirm the frontend is up.
6. Update `qa_cycle/status.md`:
   - Clear the NEEDS_REBUILD flag
   - Update E2E Stack status with rebuild timestamp
   - Add a log entry
7. Clear `qa_cycle/error-log.md` (reset to header only — new stack, fresh log).
8. Commit and push to `bugfix_cycle_2026-03-15`.

## Guard Rails
- Do NOT make code changes — only stack lifecycle operations
- If rebuild fails, check docker logs and report the error in status.md
- Timeout: if stack isn't healthy after 10 minutes, report failure and exit
