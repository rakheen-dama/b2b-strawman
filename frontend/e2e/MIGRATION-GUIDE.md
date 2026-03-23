# E2E Test Migration Guide — Mock Auth to Keycloak Stack

## Overview

Phase 54 introduces the Keycloak dev stack as the primary E2E test target. This guide covers
the mechanical changes needed to migrate existing tests from the mock-auth stack.

**Reference migration:** `frontend/e2e/tests/keycloak/existing-migration.spec.ts`
(adapted from `frontend/e2e/tests/smoke.spec.ts`)

---

## Step-by-Step Migration

### 1. Update auth fixture import

**Before:**
```typescript
import { loginAs } from '../fixtures/auth'
```

**After:**
```typescript
import { loginAs } from '../../fixtures/keycloak-auth'
// adjust relative path depth as needed
```

### 2. Update loginAs calls

**Before:**
```typescript
await loginAs(page, 'alice')
await loginAs(page, 'bob')
await loginAs(page, 'carol')
```

**After:**
```typescript
await loginAs(page, 'alice@example.com', 'password')
await loginAs(page, 'bob@example.com',   'password')
await loginAs(page, 'carol@example.com', 'password')
```

### 3. Replace mock-login navigation

**Before:**
```typescript
await page.goto('/mock-login')
await page.getByRole('button', { name: 'Sign In' }).click()
```

**After:**
```typescript
await loginAs(page, 'alice@example.com', 'password')
```

### 4. Update org slug

**Before:** `e2e-test-org`
**After:** `acme-corp`

Example:
```typescript
// Before
await page.goto('/org/e2e-test-org/projects')

// After
await page.goto('/org/acme-corp/projects')
```

### 5. Update hardcoded port references (if any)

| Old (mock-auth) | New (Keycloak) |
|-----------------|----------------|
| `localhost:3001` | `localhost:3000` |
| `localhost:8081` | `localhost:8080` |
| `localhost:8026` (Mailpit) | `localhost:8025` |

### 6. Wrap in `test.describe.serial(...)` if tests share state

Keycloak tests run serially (`workers: 1` in config). For tests that depend on shared
state (e.g., user creates data in test 1, reads it in test 2), wrap in:

```typescript
test.describe.serial('My test suite', () => {
  // ...
})
```

### 7. Run the migrated test

```bash
# Start the stack (if not already running)
bash compose/scripts/dev-e2e-up.sh

# Seed the acme-corp tenant (for migrated tests)
bash compose/scripts/dev-seed-tenant.sh

# Run your migrated test
cd frontend && npx playwright test e2e/tests/keycloak/your-migrated.spec.ts
```

---

## Example Diff

```diff
-import { loginAs } from '../fixtures/auth'
+import { loginAs } from '../../fixtures/keycloak-auth'

+const ORG_SLUG = 'acme-corp'

-test('owner can create a project', async ({ page }) => {
-  const name = `E2E Test ${Date.now()}`
-  await loginAs(page, 'alice')
-  await page.goto('/org/e2e-test-org/projects')
+test('owner can create a project', async ({ page }) => {
+  const name = `E2E Test ${Date.now()}`
+  await loginAs(page, 'alice@example.com', 'password')
+  await page.goto(`/org/${ORG_SLUG}/projects`)
   await page.getByRole('button', { name: 'New Project' }).click()
   await page.getByLabel('Name').fill(name)
   await page.getByRole('button', { name: 'Create Project' }).click()
   await expect(page.getByText(name)).toBeVisible()
 })
```

---

## Remaining Files (50+ to migrate)

The following test directories contain mock-auth tests awaiting migration. Migrate in
dependency order — navigation tests before feature tests that depend on navigation.

- `frontend/e2e/tests/auth/`
- `frontend/e2e/tests/automations/`
- `frontend/e2e/tests/compliance/`
- `frontend/e2e/tests/customers/`
- `frontend/e2e/tests/documents/`
- `frontend/e2e/tests/finance/`
- `frontend/e2e/tests/information-requests/`
- `frontend/e2e/tests/invoices/`
- `frontend/e2e/tests/navigation/`
- `frontend/e2e/tests/notifications/`
- `frontend/e2e/tests/portal/`
- `frontend/e2e/tests/projects/`
- `frontend/e2e/tests/proposals/`
- `frontend/e2e/tests/resources/`
- `frontend/e2e/tests/retainers/`
- `frontend/e2e/tests/schedules/`
- `frontend/e2e/tests/settings/`
- `frontend/e2e/tests/verticals/`
- `frontend/e2e/tests/lifecycle.spec.ts`
- `frontend/e2e/tests/lifecycle-portal.spec.ts`
- `frontend/e2e/tests/lifecycle-interactive.spec.ts`

Target directory for migrated tests: `frontend/e2e/tests/keycloak/`

---

## Seed Data Reference

`dev-seed-tenant.sh` provides:
- Organization: `acme-corp` (Acme Corp)
- `alice@example.com` / `password` — owner
- `bob@example.com` / `password` — member
- `carol@example.com` / `password` — member
- `padmin@docteams.local` / `password` — platform admin (not a tenant member)
