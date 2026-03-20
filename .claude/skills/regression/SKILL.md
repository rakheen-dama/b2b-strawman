---
name: regression
description: Run the regression test suite (API + Playwright) against the E2E stack. Reports results, diagnoses failures, and optionally fixes them. Usage - /regression [--api|--ui] [--fix]
---

# Regression Test Runner

Run the full regression test suite against the live E2E stack. Reports pass/fail/skip counts, diagnoses any failures, and optionally fixes them.

## Arguments

- **No args**: Run both API and Playwright tests
- `--api`: Run only the shell API regression tests
- `--ui`: Run only the Playwright browser tests
- `--fix`: After running, automatically fix any new failures (dispatches fix agent)

## Prerequisites

The E2E stack must be running. If not, start it:

```bash
bash compose/scripts/e2e-up.sh
```

## Step 0 — Pre-flight Health Check

```bash
curl -sf http://localhost:8081/actuator/health > /dev/null 2>&1 && echo "Backend OK" || echo "Backend DOWN"
curl -sf http://localhost:3001 > /dev/null 2>&1 && echo "Frontend OK" || echo "Frontend DOWN"
curl -sf http://localhost:8090/.well-known/jwks.json > /dev/null 2>&1 && echo "Mock IDP OK" || echo "Mock IDP DOWN"
```

If any service is down:
1. Check `docker compose -f compose/docker-compose.e2e.yml ps`
2. Restart failed services: `docker compose -f compose/docker-compose.e2e.yml up -d <service>`
3. If backend exited (code 137), it was OOM-killed — just restart it
4. If frontend needs rebuild after code changes: `SHELL=/bin/bash bash compose/scripts/e2e-rebuild.sh frontend`

Only proceed when all 3 are healthy.

## Step 1 — Run API Regression Tests

Skip if `--ui` flag was passed.

```bash
bash scripts/regression-test.sh 2>&1
```

**Parse the output:**
- Look for the summary box at the end: `PASS: N  FAIL: N  SKIP: N`
- If FAIL > 0, capture the failing assertion labels
- If all PASS, report success

**API test sections** (can be run individually with `--only`):
- `rbac` — Role-based access control
- `customer_lifecycle` — Customer state machine + guards
- `invoice_lifecycle` — Invoice DRAFT→APPROVED→SENT→PAID→VOID + guards
- `invoice_math` — Line item arithmetic, tax, rounding
- `task_lifecycle` — Task OPEN→IN_PROGRESS→DONE + reopen/cancel
- `portal_isolation` — Cross-customer data isolation
- `audit_integrity` — Audit event immutability triggers

## Step 2 — Run Playwright Regression Tests

Skip if `--api` flag was passed.

```bash
cd frontend
NODE_OPTIONS="" PLAYWRIGHT_BASE_URL=http://localhost:3001 \
  /opt/homebrew/bin/pnpm exec playwright test \
  --reporter=list --config=e2e/playwright.config.ts 2>&1
```

**Parse the output:**
- Look for the summary line: `N passed`, `N failed`, `N skipped`
- If failed > 0, capture the test names and error messages
- Group failures by spec file

**Known baselines:**
- `lifecycle.spec.ts`, `lifecycle-interactive.spec.ts`, `lifecycle-portal.spec.ts` may have pre-existing failures from seed data drift — these are NOT new regressions
- `72 skipped` tests are for features not yet built — this is expected

## Step 3 — Report Results

Present a summary table:

```
| Layer      | Passed | Failed | Skipped |
|------------|--------|--------|---------|
| API        | N      | N      | N       |
| Playwright | N      | N      | N       |
| **Total**  | **N**  | **N**  | **N**   |
```

For any failures:
- List each failing test with its error message (1-2 lines)
- Categorize: **New regression** vs **Pre-existing** vs **Flaky**
- A failure is a "new regression" if the test was passing in the previous run

## Step 4 — Fix (if `--fix` flag)

If `--fix` was passed and there are new failures:

1. For each new failure, dispatch a **Dev subagent** to fix it:

```
You are fixing a failing regression test.

Test: {test_name}
File: {spec_file}
Error: {error_message}

Read the test file and the component/page it tests.
Determine if the failure is:
a) A broken test (wrong selector, stale assumption) → fix the test
b) A broken feature (actual regression) → fix the code

For (a): Update the test selector/assertion to match the current UI
For (b): Fix the code, then verify the test passes

Run the single test to verify: NODE_OPTIONS="" PLAYWRIGHT_BASE_URL=http://localhost:3001 /opt/homebrew/bin/pnpm exec playwright test {spec_file} --reporter=list --config=e2e/playwright.config.ts

Commit with: "fix(regression): {description}"
```

2. After all fixes, re-run the full suite to confirm no cascading breakage
3. Report the final results

## Step 5 — Summary

Report:
- Total tests: API + Playwright
- Pass rate (excluding skips)
- Any remaining failures with recommended action
- If `--fix` was used: list PRs/commits created

## Guard Rails

- **Never skip failing tests** to make the suite "pass" — fix the test or fix the code
- **Never modify seed data** to make tests pass — tests should be resilient to seed state
- **Pre-existing lifecycle test failures** are known — don't count them as new regressions
- **Skipped tests** for unbuilt features are expected — don't try to unskip them
- If the E2E stack needs a rebuild, do it before running tests (don't run against stale containers)

## Quick Reference

```bash
# Full suite
bash scripts/run-regression-test.sh

# API only (fast, ~30s)
bash scripts/run-regression-test.sh --api

# Playwright only (~2.5 min)
bash scripts/run-regression-test.sh --ui

# Single API section
bash scripts/regression-test.sh --only invoice_math

# Single Playwright domain
NODE_OPTIONS="" PLAYWRIGHT_BASE_URL=http://localhost:3001 \
  /opt/homebrew/bin/pnpm exec playwright test e2e/tests/invoices/ \
  --reporter=list --config=e2e/playwright.config.ts
```
