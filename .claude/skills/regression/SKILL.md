---
name: regression
description: Run the regression test suite (API + Playwright) against the E2E stack or Keycloak dev stack. Reports results, diagnoses failures, and optionally fixes them. Usage - /regression [--api|--ui] [--kc] [--fix]
---

# Regression Test Runner

Run the full regression test suite against the live E2E stack (mock auth) or Keycloak dev stack. Reports pass/fail/skip counts, diagnoses any failures, and optionally fixes them.

## Arguments

- **No args**: Run both API and Playwright tests against the E2E mock-auth stack
- `--api`: Run only the shell API regression tests
- `--ui`: Run only the Playwright browser tests
- `--kc`: Run against the **Keycloak dev stack** (ports 3000/8080/8443/8180) instead of the E2E mock-auth stack
- `--fix`: After running, automatically fix any new failures (dispatches fix agent)

## Stack Reference

| Aspect | E2E Mock-Auth (default) | Keycloak Dev (`--kc`) |
|--------|------------------------|----------------------|
| Frontend | http://localhost:3001 (Docker) | http://localhost:3000 (local pnpm dev) |
| Backend | http://localhost:8081 (Docker) | http://localhost:8080 (local mvnw) |
| Auth | Mock IDP :8090 | Keycloak :8180 via Gateway :8443 |
| Start cmd | `bash compose/scripts/e2e-up.sh` | `bash compose/scripts/dev-up.sh` + `bash compose/scripts/svc.sh start all` |
| Stop cmd | `bash compose/scripts/e2e-down.sh` | `bash compose/scripts/svc.sh stop all` + `bash compose/scripts/dev-down.sh` |
| Seed data | Docker seed container (automatic) | `keycloak-bootstrap.sh` (platform admin only); orgs via UI |
| Playwright env | `PLAYWRIGHT_BASE_URL=http://localhost:3001` | `E2E_AUTH_MODE=keycloak PLAYWRIGHT_BASE_URL=http://localhost:3000` |
| Test scope | `e2e/tests/*` (excludes `keycloak/`) | `e2e/tests/keycloak/*` + standard tests |
| Auth fixture | `e2e/fixtures/auth.ts` — `loginAs(page, 'alice')` | `e2e/fixtures/keycloak-auth.ts` — `loginAs(page, email, password)` |
| API tokens | Mock IDP `/token` endpoint | Not available — API shell tests skip in KC mode |

## Step 0 — Pre-flight Health Check

### E2E Mock-Auth Stack (default)

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

### Keycloak Dev Stack (`--kc`)

```bash
curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1 && echo "Backend OK" || echo "Backend DOWN"
curl -sf http://localhost:3000 > /dev/null 2>&1 && echo "Frontend OK" || echo "Frontend DOWN"
curl -sf http://localhost:8443/actuator/health > /dev/null 2>&1 && echo "Gateway OK" || echo "Gateway DOWN"
curl -sf http://localhost:8180/realms/docteams > /dev/null 2>&1 && echo "Keycloak OK" || echo "Keycloak DOWN"
```

If any service is down:
1. Check Docker infra: `docker compose -f compose/docker-compose.yml ps`
2. Check local services: `bash compose/scripts/svc.sh status`
3. Start infra: `bash compose/scripts/dev-up.sh`
4. Start services: `bash compose/scripts/svc.sh start all`
5. If Keycloak just started, run bootstrap: `bash compose/scripts/keycloak-bootstrap.sh`
6. If backend/gateway need restart after Java changes: `bash compose/scripts/svc.sh restart backend`

Only proceed when all required services are healthy.

## Step 1 — Run API Regression Tests

Skip if `--ui` flag was passed.
**Skip entirely if `--kc` flag was passed** — API shell tests use Mock IDP for tokens and are not compatible with the Keycloak stack.

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

### E2E Mock-Auth Stack (default)

```bash
cd frontend
NODE_OPTIONS="" PLAYWRIGHT_BASE_URL=http://localhost:3001 \
  /opt/homebrew/bin/pnpm exec playwright test \
  --reporter=list --config=e2e/playwright.config.ts 2>&1
```

### Keycloak Dev Stack (`--kc`)

Run Keycloak-specific tests first, then optionally run standard tests in KC mode:

```bash
cd frontend

# 1. Keycloak-specific tests (onboarding, member-invite-rbac)
NODE_OPTIONS="" E2E_AUTH_MODE=keycloak PLAYWRIGHT_BASE_URL=http://localhost:3000 \
  /opt/homebrew/bin/pnpm exec playwright test e2e/tests/keycloak/ \
  --reporter=list --config=e2e/playwright.config.ts 2>&1

# 2. Standard Playwright tests in KC mode (uses Keycloak OIDC login flow)
NODE_OPTIONS="" E2E_AUTH_MODE=keycloak PLAYWRIGHT_BASE_URL=http://localhost:3000 \
  /opt/homebrew/bin/pnpm exec playwright test \
  --reporter=list --config=e2e/playwright.config.ts 2>&1
```

**NOTE on KC mode standard tests:** Standard tests use `loginAs(page, 'alice')` from `e2e/fixtures/auth.ts` (mock auth). In KC mode, these will fail at login unless the test imports from `keycloak-auth.ts`. Only `e2e/tests/keycloak/*` tests are designed for KC auth. Running standard tests in KC mode is useful for smoke-checking pages but expect auth-related failures.

**Parse the output:**
- Look for the summary line: `N passed`, `N failed`, `N skipped`
- If failed > 0, capture the test names and error messages
- Group failures by spec file

**Known baselines:**
- `lifecycle.spec.ts`, `lifecycle-interactive.spec.ts`, `lifecycle-portal.spec.ts` may have pre-existing failures from seed data drift — these are NOT new regressions
- `72 skipped` tests are for features not yet built — this is expected
- In `--kc` mode: standard tests that use mock auth fixture will fail at login — this is expected, not a regression

### Keycloak-Specific Test Details

| Test File | What It Tests |
|-----------|--------------|
| `keycloak/onboarding.spec.ts` | Full onboarding flow: access request → OTP → admin approval → KC registration → first login |
| `keycloak/member-invite-rbac.spec.ts` | Member invite via Teams page → KC invite link → registration → RBAC verification |

**Prerequisites for KC tests:**
- Platform admin exists (run `keycloak-bootstrap.sh` once)
- Mailpit running at :8025 (for OTP and invite emails)
- Gateway running at :8443 (handles OAuth2 BFF flow)
- No pre-existing orgs needed — onboarding test creates its own

## Step 3 — Report Results

Present a summary table:

```
| Layer      | Passed | Failed | Skipped |
|------------|--------|--------|---------|
| API        | N      | N      | N       |
| Playwright | N      | N      | N       |
| KC Tests   | N      | N      | N       |
| **Total**  | **N**  | **N**  | **N**   |
```

For any failures:
- List each failing test with its error message (1-2 lines)
- Categorize: **New regression** vs **Pre-existing** vs **Flaky** vs **Auth mismatch** (KC mode)
- A failure is a "new regression" if the test was passing in the previous run

## Step 4 — Fix (if `--fix` flag)

If `--fix` was passed and there are new failures:

1. For each new failure, dispatch a **Dev subagent** to fix it:

```
You are fixing a failing regression test.

Test: {test_name}
File: {spec_file}
Error: {error_message}
Stack: {e2e | keycloak}

Read the test file and the component/page it tests.
Determine if the failure is:
a) A broken test (wrong selector, stale assumption) → fix the test
b) A broken feature (actual regression) → fix the code

For (a): Update the test selector/assertion to match the current UI
For (b): Fix the code, then verify the test passes

## Environment (varies by stack)

### E2E Mock-Auth
Run the single test to verify:
NODE_OPTIONS="" PLAYWRIGHT_BASE_URL=http://localhost:3001 /opt/homebrew/bin/pnpm exec playwright test {spec_file} --reporter=list --config=e2e/playwright.config.ts

### Keycloak Dev
Run the single test to verify:
NODE_OPTIONS="" E2E_AUTH_MODE=keycloak PLAYWRIGHT_BASE_URL=http://localhost:3000 /opt/homebrew/bin/pnpm exec playwright test {spec_file} --reporter=list --config=e2e/playwright.config.ts

Commit with: "fix(regression): {description}"
```

2. After all fixes, re-run the full suite to confirm no cascading breakage
3. Report the final results

## Step 5 — Summary

Report:
- Total tests: API + Playwright (+ KC tests if `--kc`)
- Pass rate (excluding skips)
- Any remaining failures with recommended action
- If `--fix` was used: list PRs/commits created

## Guard Rails

- **Never skip failing tests** to make the suite "pass" — fix the test or fix the code
- **Never modify seed data** to make tests pass — tests should be resilient to seed state
- **Pre-existing lifecycle test failures** are known — don't count them as new regressions
- **Skipped tests** for unbuilt features are expected — don't try to unskip them
- If the E2E stack needs a rebuild, do it before running tests (don't run against stale containers)
- **KC mode auth mismatches** — standard tests failing at login in KC mode are NOT regressions
- **Keycloak session state** — KC tests run with 1 worker (serial) to avoid session conflicts

## Quick Reference

```bash
# ── E2E Mock-Auth Stack (default) ──────────────────
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

# ── Keycloak Dev Stack ─────────────────────────────
# KC-specific tests only (onboarding, invite)
NODE_OPTIONS="" E2E_AUTH_MODE=keycloak PLAYWRIGHT_BASE_URL=http://localhost:3000 \
  /opt/homebrew/bin/pnpm exec playwright test e2e/tests/keycloak/ \
  --reporter=list --config=e2e/playwright.config.ts

# All Playwright tests in KC mode (expect auth mismatches on standard tests)
NODE_OPTIONS="" E2E_AUTH_MODE=keycloak PLAYWRIGHT_BASE_URL=http://localhost:3000 \
  /opt/homebrew/bin/pnpm exec playwright test \
  --reporter=list --config=e2e/playwright.config.ts

# Service management
bash compose/scripts/svc.sh status              # Health check all
bash compose/scripts/svc.sh restart backend      # After Java changes
bash compose/scripts/svc.sh logs backend         # Last 50 lines
```