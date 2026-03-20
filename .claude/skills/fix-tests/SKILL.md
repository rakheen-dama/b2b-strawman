---
name: fix-tests
description: Fix broken regression tests after codebase changes. Analyzes failures, determines root cause (broken test vs broken code), and applies targeted fixes. Usage - /fix-tests [spec-file-or-domain]
---

# Fix Broken Tests

Analyze and fix failing regression tests after codebase changes. Determines whether failures are broken tests (wrong selectors/stale assumptions) or broken features (actual regressions), then applies the appropriate fix.

## Arguments

- **No args**: Find and fix all failing tests
- `<spec-file>`: Fix a specific spec file (e.g., `/fix-tests invoices/invoice-lifecycle.spec.ts`)
- `<domain>`: Fix all specs in a domain (e.g., `/fix-tests customers`)

## Step 0 — Identify Failures

Run the relevant tests to get the current failure list:

```bash
cd frontend

# If no args: run all tests
NODE_OPTIONS="" PLAYWRIGHT_BASE_URL=http://localhost:3001 \
  /opt/homebrew/bin/pnpm exec playwright test \
  --reporter=json --config=e2e/playwright.config.ts 2>/dev/null \
  | jq -r '.suites[].suites[]?.specs[]? | select(.tests[0].results[0].status == "failed" or .tests[0].results[0].status == "timedOut") | .file + " :: " + .title + " :: " + (.tests[0].results[0].errors[0].message // "no message" | split("\n")[0])' 2>/dev/null

# If specific file/domain: run just those
NODE_OPTIONS="" PLAYWRIGHT_BASE_URL=http://localhost:3001 \
  /opt/homebrew/bin/pnpm exec playwright test e2e/tests/{ARG} \
  --reporter=json --config=e2e/playwright.config.ts 2>/dev/null \
  | jq -r '...'
```

Also check API tests if relevant:
```bash
bash scripts/regression-test.sh 2>&1 | grep FAIL
```

## Step 1 — Classify Each Failure

For each failing test, classify it by reading the test file and the component it tests:

### Category A: Broken Test (selector/assumption drift)
**Symptoms:**
- `toBeVisible` failed — element exists but selector doesn't match
- `toContainText` failed — text changed (e.g., "Customers" → "Clients" from terminology)
- `toHaveURL` failed — route changed
- `getByRole` finds 0 or multiple elements — component structure changed
- API returns different status code — endpoint contract changed

**Fix approach:** Update the test to match the current codebase.

### Category B: Broken Feature (actual regression)
**Symptoms:**
- Page shows "Something went wrong" (500 error)
- API returns 500/502
- Feature that was working now returns unexpected data
- Console shows TypeError or similar runtime error

**Fix approach:** Fix the code, not the test.

### Category C: Pre-existing / Known
**Known baselines:**
- `lifecycle.spec.ts` — depends on original seed data, drifts after QA cycles
- `lifecycle-interactive.spec.ts` — same
- `lifecycle-portal.spec.ts` — same

**Fix approach:** Skip or ignore. These are not regressions.

### Category D: Feature Not Built
**Symptoms:**
- Test is already `test.skip()` but someone unskipped it
- Page returns 404

**Fix approach:** Re-add `test.skip(true, 'Feature not implemented')`.

## Step 2 — Fix Category A Failures (Broken Tests)

For each broken test, dispatch a subagent:

```
You are fixing a broken test after codebase changes.

## Failing Test
- File: {spec_file}
- Test: {test_name}
- Error: {error_message}

## Instructions
1. Read the test file to understand what it's asserting
2. Read the ACTUAL component/page it tests to find the correct selectors:
   - For page tests: read `frontend/app/(app)/org/[slug]/{path}/page.tsx`
   - For component tests: check `frontend/components/{domain}/`
3. Determine what changed:
   - Button text changed? → update `getByRole('button', { name: /.../ })`
   - Heading changed? → update heading selector
   - Dialog structure changed? → update dialog locators
   - API response shape changed? → update assertions
4. Fix the test with the MINIMUM change needed
5. Verify by running the single test:
   ```bash
   NODE_OPTIONS="" PLAYWRIGHT_BASE_URL=http://localhost:3001 \
     /opt/homebrew/bin/pnpm exec playwright test {spec_file} \
     --reporter=list --config=e2e/playwright.config.ts
   ```
6. Do NOT change the component/page code — only fix the test

## Selector Best Practices
- Prefer `getByRole` with regex names: `getByRole('button', { name: /Save/i })`
- Use `page.locator('h1').first()` instead of `getByRole('heading', { name: 'Exact Match' })`
- Scope within dialogs: `const dialog = page.getByRole('dialog'); dialog.getByLabel('Name')`
- For Shadcn selects: `page.locator('[data-slot="select-trigger"]')`
- For Shadcn command/combobox: `page.locator('[cmdk-item]')`
- For terminology flexibility: `/New (Customer|Client)/i`

## Environment
- Working directory: /Users/rakheendama/Projects/2026/b2b-strawman/frontend
- PLAYWRIGHT_BASE_URL=http://localhost:3001
- pnpm: /opt/homebrew/bin/pnpm
- NODE_OPTIONS="" required
```

## Step 3 — Fix Category B Failures (Broken Code)

For each broken feature, dispatch a subagent with `isolation: "worktree"`:

```
You are fixing a regression bug found by a failing test.

## Failing Test
- File: {spec_file}
- Test: {test_name}
- Error: {error_message}

## Instructions
1. Read the test to understand the expected behavior
2. Navigate the actual page/endpoint to reproduce the issue
3. Read the relevant source code to find the bug
4. Fix the code (backend or frontend)
5. Build and verify:
   - Frontend: NODE_OPTIONS="" /opt/homebrew/bin/pnpm run build
   - Backend: ./mvnw compile test-compile -q
6. Run the failing test to confirm it passes
7. Run surrounding tests to confirm no cascading breakage
8. Commit: "fix: {description} (caught by regression test)"
9. Create PR if in worktree

## Environment
- Read frontend/CLAUDE.md or backend/CLAUDE.md before making changes
```

## Step 4 — Update API Regression Script

If API test failures exist in `scripts/regression-test.sh`:

1. Read the failing section
2. Check the actual API endpoint via curl
3. Fix the endpoint path, payload, or assertion
4. Re-run: `bash scripts/regression-test.sh --only {section} 2>&1`

Common API test fixes:
- Endpoint path changed → update the `api_get`/`api_post` call
- Request body changed → update the JSON payload
- Response field renamed → update the `jq` filter
- Status code changed → update `assert_http` expected code

## Step 5 — Verify and Commit

After all fixes:

1. Run the full suite:
```bash
bash scripts/run-regression-test.sh 2>&1
```

2. Confirm:
   - API: 0 FAIL
   - Playwright: 0 new failures (pre-existing lifecycle failures are OK)
   - No tests were deleted or blindly skipped

3. Commit all test fixes in one commit:
```bash
git add scripts/regression-test.sh frontend/e2e/tests/
git commit -m "test: update regression tests for codebase changes

Updated selectors/assertions to match current UI after [describe changes].
N tests fixed, M tests remain skipped (features not yet built)."
```

## Step 6 — Report

Present:
- How many tests were broken and why
- Category breakdown (A/B/C/D)
- What was fixed
- Any tests that couldn't be fixed (and why)
- Final pass/fail/skip counts

## Guard Rails

- **Never delete a test** to make the suite pass — fix or skip with reason
- **Never change assertions to match wrong behavior** — if the page shows an error, fix the page
- **Preserve test intent** — if a test checks "member cannot access X", keep that assertion even if the selector changes
- **One commit per category** — don't mix test fixes with code fixes
- **Run the full suite after fixes** — catch cascading breakage before committing
- When fixing selectors, prefer **resilient patterns**:
  - Regex over exact match: `/Save/i` not `'Save Changes'`
  - Role-based over CSS: `getByRole('button')` not `.btn-primary`
  - Scoped over global: `dialog.getByLabel()` not `page.getByLabel()`

## Proactive Mode

This skill should be invoked AFTER any of these events:
- A feature branch is merged that changes UI components
- A backend API contract changes (endpoints, request/response shapes)
- Terminology/branding updates (e.g., "Customers" → "Clients")
- Shadcn component upgrades or design system changes
- Seed data is modified
- New features are built (to unskip previously skipped tests)

When invoked proactively, focus on the specific domain affected by the change rather than running the full suite.
