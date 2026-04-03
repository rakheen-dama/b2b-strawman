# Keycloak E2E Test Suite Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Playwright E2E test infrastructure and tests that run against the Keycloak dev stack (services running locally), validating the full onboarding lifecycle through real Keycloak authentication.

**Architecture:** Dual-mode Playwright setup — existing mock IDP fixtures stay untouched, new `keycloak-auth.ts` fixture handles OIDC login via browser. Mailpit REST API helper enables email-driven flows (OTP extraction, invite link extraction). Tests run serially against local services (frontend:3000, backend:8080, gateway:8443, keycloak:8180).

**Tech Stack:** Playwright, TypeScript, Mailpit REST API, Keycloak 26.x (Keycloakify theme), Next.js 16, Spring Boot 4

**Spec:** `requirements/claude-code-prompt-phase54.md`

---

## Scope Decisions

| Spec Section | In Scope? | Rationale |
|---|---|---|
| S1: Test harness (config, fixtures, helpers) | Yes | Foundation — all other tests depend on this |
| S2: Access request & org provisioning | Yes | Core onboarding flow |
| S3: Accounting-ZA pack verification | **Deferred** | Provisioning gaps (rate cards, tax defaults, currency) must be resolved first (spec S6.1-S6.3). Pack verification tests are diagnostic — write them after the provisioning pipeline is complete. |
| S4: Member invite & RBAC | Yes | Validates multi-user flows through Keycloak |
| S5: Existing test migration | **Deferred** | Migration is mechanical (swap fixtures, update ports). Better done after the Keycloak tests prove the harness works end-to-end. |
| S1.4: `dev-e2e-up.sh` / `dev-e2e-down.sh` | **Not needed** | User runs services locally in terminal sessions (backend, frontend, gateway, portal). Only Docker infra uses `dev-up.sh`. No containerised full-stack needed. |

## File Map

### New Files

| File | Responsibility |
|------|---------------|
| `frontend/e2e/fixtures/keycloak-auth.ts` | Keycloak login/registration via Playwright browser |
| `frontend/e2e/fixtures/keycloak-selectors.ts` | Keycloak page form selectors (single source of truth) |
| `frontend/e2e/helpers/mailpit.ts` | Mailpit REST API client (wait for email, extract OTP/links) |
| `frontend/e2e/helpers/e2e-state.ts` | Cross-spec state file (write/read `/tmp/e2e-keycloak-state.json`) |
| `frontend/e2e/tests/keycloak/onboarding.spec.ts` | Access request → approval → registration → first login |
| `frontend/e2e/tests/keycloak/member-invite-rbac.spec.ts` | Invite members → register → verify RBAC |

### Modified Files

| File | Change |
|------|--------|
| `frontend/e2e/playwright.config.ts` | Add keycloak project with longer timeouts, serial workers |
| `compose/scripts/check-playwright-port.sh` | Allow port 3000 when `E2E_AUTH_MODE=keycloak` |
| `.claude/skills/qa-cycle-kc/SKILL.md` | Update with local-services setup (not Docker `--all`) |

---

## Chunk 1: Fixtures & Helpers

### Task 1: Keycloak Form Selectors Constants

**Files:**
- Create: `frontend/e2e/fixtures/keycloak-selectors.ts`

- [ ] **Step 1: Create the selectors file**

This file is the single source of truth for Keycloak page form elements. If the Keycloakify theme changes selectors, only this file needs updating.

```typescript
// frontend/e2e/fixtures/keycloak-selectors.ts

/**
 * Keycloak login/registration page selectors.
 * Based on the docteams Keycloakify theme (compose/keycloak/theme/).
 * If theme changes, update selectors here — all fixtures reference this file.
 */
export const KC_LOGIN = {
  username: '#username',
  password: '#password',
  submit: 'input[name="login"], button[type="submit"]',
  error: '[role="alert"]',
} as const

export const KC_REGISTER = {
  firstName: '#firstName',
  lastName: '#lastName',
  email: '#email',
  password: '#password',
  passwordConfirm: '#password-confirm',
  submit: 'button[type="submit"], input[type="submit"]',
} as const
```

- [ ] **Step 2: Commit**

```bash
git add frontend/e2e/fixtures/keycloak-selectors.ts
git commit -m "feat(e2e): add Keycloak form selectors constants"
```

---

### Task 2: Mailpit API Helper

**Files:**
- Create: `frontend/e2e/helpers/mailpit.ts`

Mailpit REST API docs: `GET /api/v1/messages` returns a list. `GET /api/v1/message/{id}` returns full message. `DELETE /api/v1/messages` clears all.

- [ ] **Step 1: Create the mailpit helper**

```typescript
// frontend/e2e/helpers/mailpit.ts

const MAILPIT_URL = process.env.MAILPIT_URL || 'http://localhost:8025'

interface MailpitMessage {
  ID: string
  From: { Name: string; Address: string }
  To: { Name: string; Address: string }[]
  Subject: string
  Snippet: string
  Created: string
}

interface MailpitMessageDetail {
  ID: string
  From: { Name: string; Address: string }
  To: { Name: string; Address: string }[]
  Subject: string
  Text: string
  HTML: string
  Created: string
}

interface MailpitSearchResponse {
  total: number
  messages: MailpitMessage[]
}

/**
 * Clear all messages in Mailpit. Call in test setup.
 */
export async function clearMailbox(): Promise<void> {
  await fetch(`${MAILPIT_URL}/api/v1/messages`, { method: 'DELETE' })
}

/**
 * Search for emails matching a query. Uses Mailpit's search syntax.
 * @see https://mailpit.axllent.org/docs/api-v1/view.html
 */
async function searchMessages(query: string): Promise<MailpitMessage[]> {
  const res = await fetch(
    `${MAILPIT_URL}/api/v1/search?query=${encodeURIComponent(query)}`
  )
  if (!res.ok) {
    throw new Error(`Mailpit search failed: ${res.status}`)
  }
  const data: MailpitSearchResponse = await res.json()
  return data.messages ?? []
}

/**
 * Get the full message detail (including HTML/Text body) by ID.
 */
async function getMessageDetail(id: string): Promise<MailpitMessageDetail> {
  const res = await fetch(`${MAILPIT_URL}/api/v1/message/${id}`)
  if (!res.ok) {
    throw new Error(`Mailpit get message failed: ${res.status}`)
  }
  return res.json()
}

/**
 * Wait for an email to arrive for a specific recipient.
 * Polls Mailpit until a matching email is found or timeout is reached.
 *
 * @param recipient - Email address to search for (e.g., "alice@example.com")
 * @param opts.subject - Optional substring to match in subject
 * @param opts.timeout - Max wait time in ms (default: 30000)
 * @param opts.interval - Poll interval in ms (default: 2000)
 * @returns The full message detail
 */
export async function waitForEmail(
  recipient: string,
  opts: { subject?: string; timeout?: number; interval?: number } = {}
): Promise<MailpitMessageDetail> {
  const { subject, timeout = 30_000, interval = 2_000 } = opts
  const deadline = Date.now() + timeout

  while (Date.now() < deadline) {
    const query = `to:${recipient}`
    const messages = await searchMessages(query)

    const matches = subject
      ? messages.filter((m) => m.Subject.toLowerCase().includes(subject.toLowerCase()))
      : messages

    if (matches.length > 0) {
      // Return the most recent matching message
      const latest = matches.sort(
        (a, b) => new Date(b.Created).getTime() - new Date(a.Created).getTime()
      )[0]
      return getMessageDetail(latest.ID)
    }

    await new Promise((r) => setTimeout(r, interval))
  }

  throw new Error(
    `Timed out waiting for email to ${recipient}${subject ? ` with subject "${subject}"` : ''} (${timeout}ms)`
  )
}

/**
 * Extract a 6-digit OTP code from an email body.
 */
export function extractOtp(email: MailpitMessageDetail): string {
  // Try HTML body first, fall back to text
  const body = email.HTML || email.Text
  const match = body.match(/\b(\d{6})\b/)
  if (!match) {
    throw new Error(`No 6-digit OTP found in email "${email.Subject}"`)
  }
  return match[1]
}

/**
 * Extract an invitation/registration link from a Keycloak email.
 * Keycloak invite emails contain an "Accept Invitation" link.
 */
export function extractInviteLink(email: MailpitMessageDetail): string {
  const body = email.HTML || email.Text
  // Keycloak invitation links contain /realms/ and action-token or similar
  // The org-invite.ftl template wraps the link in an <a href="..."> tag
  const hrefMatch = body.match(/href="(https?:\/\/[^"]*(?:action-token|registration|orgs\/invite)[^"]*)"/i)
  if (hrefMatch) {
    return hrefMatch[1]
  }
  // Fallback: look for any Keycloak realm link
  const realmMatch = body.match(/(https?:\/\/[^\s"<]+realms\/[^\s"<]+)/i)
  if (realmMatch) {
    return realmMatch[1]
  }
  throw new Error(`No invitation link found in email "${email.Subject}"`)
}

/**
 * Get all emails for a recipient.
 */
export async function getEmails(recipient: string): Promise<MailpitMessage[]> {
  return searchMessages(`to:${recipient}`)
}
```

- [ ] **Step 2: Verify Mailpit API is accessible**

Before committing, manually verify the API format works. Start the dev stack infra and test:

```bash
# Verify Mailpit API responds
curl -sf http://localhost:8025/api/v1/messages | jq '.total'
```

Expected: `0` (or a number — confirms API is reachable)

- [ ] **Step 3: Commit**

```bash
git add frontend/e2e/helpers/mailpit.ts
git commit -m "feat(e2e): add Mailpit REST API helper for email-driven tests"
```

---

### Task 2b: Cross-Spec State Helper

**Files:**
- Create: `frontend/e2e/helpers/e2e-state.ts`

The onboarding test creates an org with a dynamic RUN_ID. The member-invite test needs to know the owner email and org slug. This helper writes/reads a JSON state file to pass context between spec files.

- [ ] **Step 1: Create the state helper**

```typescript
// frontend/e2e/helpers/e2e-state.ts

import { readFileSync, writeFileSync, existsSync } from 'fs'

const STATE_FILE = '/tmp/e2e-keycloak-state.json'

export interface E2eState {
  runId: string
  ownerEmail: string
  ownerPassword: string
  orgSlug: string
}

export function saveState(state: E2eState): void {
  writeFileSync(STATE_FILE, JSON.stringify(state, null, 2))
}

export function loadState(): E2eState {
  if (!existsSync(STATE_FILE)) {
    throw new Error(
      `E2E state file not found at ${STATE_FILE}. Run onboarding.spec.ts first.`
    )
  }
  return JSON.parse(readFileSync(STATE_FILE, 'utf-8'))
}

export function hasState(): boolean {
  return existsSync(STATE_FILE)
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/e2e/helpers/e2e-state.ts
git commit -m "feat(e2e): add cross-spec state helper for Keycloak test chain"
```

---

### Task 3: Keycloak Auth Fixture

**Files:**
- Create: `frontend/e2e/fixtures/keycloak-auth.ts`

- [ ] **Step 1: Create the Keycloak auth fixture**

```typescript
// frontend/e2e/fixtures/keycloak-auth.ts

import type { Page } from '@playwright/test'
import { KC_LOGIN, KC_REGISTER } from './keycloak-selectors'

const GATEWAY_URL = process.env.GATEWAY_URL || 'http://localhost:8443'

/**
 * Log in to the app via Keycloak OIDC flow.
 * Navigates to a protected route, gets redirected to Keycloak login page,
 * fills credentials, submits, and waits for redirect back to the app.
 *
 * @param page - Playwright page
 * @param email - Keycloak username (email)
 * @param password - Keycloak password
 */
export async function loginAs(
  page: Page,
  email: string,
  password: string
): Promise<void> {
  // Navigate to a protected route — middleware redirects to gateway OAuth2 endpoint
  await page.goto('/dashboard')

  // Wait for Keycloak login page to load (URL contains /realms/)
  await page.waitForURL(/\/realms\//, { timeout: 15_000 })

  // Fill login form
  await page.locator(KC_LOGIN.username).fill(email)
  await page.locator(KC_LOGIN.password).fill(password)
  await page.locator(KC_LOGIN.submit).first().click()

  // Wait for redirect back to the app (URL should no longer contain /realms/)
  await page.waitForURL(
    (url) => !url.pathname.includes('/realms/'),
    { timeout: 15_000 }
  )
}

/**
 * Log in as the platform admin (pre-created by keycloak-bootstrap.sh).
 */
export async function loginAsPlatformAdmin(page: Page): Promise<void> {
  await loginAs(page, 'padmin@docteams.local', 'password')
}

/**
 * Follow a Keycloak invitation link and complete user registration.
 * The invite link lands on a Keycloak registration page where the user
 * fills in their name and password.
 *
 * @param page - Playwright page
 * @param inviteLink - Full URL from the Keycloak invitation email
 * @param firstName - First name to register
 * @param lastName - Last name to register
 * @param password - Password to set
 */
export async function registerFromInvite(
  page: Page,
  inviteLink: string,
  firstName: string,
  lastName: string,
  password: string
): Promise<void> {
  // Navigate to the invite link
  await page.goto(inviteLink)

  // Wait for the registration form to appear
  await page.waitForSelector(KC_REGISTER.firstName, { timeout: 15_000 })

  // Fill registration form
  await page.locator(KC_REGISTER.firstName).fill(firstName)
  await page.locator(KC_REGISTER.lastName).fill(lastName)
  // Email may be pre-filled from the invite — skip if readonly
  const emailField = page.locator(KC_REGISTER.email)
  if (await emailField.isEditable().catch(() => false)) {
    // Only fill if editable (some invite flows pre-fill and lock email)
  }
  await page.locator(KC_REGISTER.password).fill(password)
  await page.locator(KC_REGISTER.passwordConfirm).fill(password)
  await page.locator(KC_REGISTER.submit).first().click()

  // Wait for redirect back to the app after registration
  await page.waitForURL(
    (url) => !url.pathname.includes('/realms/'),
    { timeout: 20_000 }
  )
}

/**
 * Log out the current user.
 * Clears cookies and navigates to the gateway logout endpoint.
 */
export async function logout(page: Page): Promise<void> {
  await page.context().clearCookies()
  await page.goto('/')
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/e2e/fixtures/keycloak-auth.ts
git commit -m "feat(e2e): add Keycloak OIDC auth fixtures (login, register, logout)"
```

---

### Task 4: Update Playwright Config for Dual-Mode

**Files:**
- Modify: `frontend/e2e/playwright.config.ts`

- [ ] **Step 1: Read current config**

```bash
cat frontend/e2e/playwright.config.ts
```

- [ ] **Step 2: Update config with a keycloak project**

Add a `keycloak` project alongside the existing `chromium` project. The env var `E2E_AUTH_MODE` selects which set of tests to run.

```typescript
// frontend/e2e/playwright.config.ts

import { defineConfig, devices } from '@playwright/test'

const authMode = process.env.E2E_AUTH_MODE || 'mock'

export default defineConfig({
  testDir: './tests',
  globalTimeout: 600_000,          // 10 min (onboarding is slow)
  timeout: authMode === 'keycloak' ? 60_000 : 30_000,
  retries: process.env.CI ? 1 : 0,
  workers: authMode === 'keycloak' ? 1 : undefined, // serial for keycloak (shared state)
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:3000',
    screenshot: 'only-on-failure',
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
      testIgnore: authMode === 'keycloak' ? undefined : ['**/keycloak/**'],
    },
  ],
})
```

- [ ] **Step 3: Verify config loads**

```bash
cd frontend && npx playwright test --list --config e2e/playwright.config.ts 2>&1 | head -20
```

Expected: Lists test files without errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/e2e/playwright.config.ts
git commit -m "feat(e2e): update Playwright config for dual-mode (mock/keycloak)"
```

---

### Task 5: Update Port-Blocking Hook

**Files:**
- Modify: `compose/scripts/check-playwright-port.sh`

- [ ] **Step 1: Read current hook script**

```bash
cat compose/scripts/check-playwright-port.sh
```

- [ ] **Step 2: Add E2E_AUTH_MODE check**

The hook should allow port 3000 when `E2E_AUTH_MODE=keycloak`. The Clerk CAPTCHA issue only applies in Clerk mode — Keycloak has no CAPTCHA.

Update the port 3000 check block (around line 21) to:

```bash
# Check if navigating to localhost:3000
if echo "$URL" | grep -qE '(localhost|127\.0\.0\.1):3000'; then
  # In Keycloak mode, port 3000 is the correct target (no CAPTCHA)
  if [[ "${E2E_AUTH_MODE:-}" == "keycloak" ]]; then
    # Verify frontend is running
    if ! curl -sf --max-time 2 http://localhost:3000/ > /dev/null 2>&1; then
      echo "Frontend is not running on port 3000."
      echo ""
      echo "Start it with:  cd frontend && NEXT_PUBLIC_AUTH_MODE=keycloak pnpm dev"
      exit 2
    fi
    exit 0
  fi

  echo "BLOCKED: Port 3000 uses Clerk authentication with CAPTCHA — agents cannot authenticate there."
  echo ""
  echo "Use the E2E mock-auth stack on port 3001 instead:"
  echo "  1. Start the stack:  bash compose/scripts/e2e-up.sh"
  echo "  2. Navigate to:      http://localhost:3001/mock-login"
  echo "  3. Click 'Sign In' to authenticate as Alice (owner)"
  echo ""
  echo "Replace 'localhost:3000' with 'localhost:3001' in your URL."
  echo ""
  echo "Or set E2E_AUTH_MODE=keycloak to use the Keycloak dev stack on port 3000."
  exit 2
fi
```

- [ ] **Step 3: Verify hook allows keycloak mode**

```bash
echo '{"tool_input":{"url":"http://localhost:3000/dashboard"}}' | E2E_AUTH_MODE=keycloak bash compose/scripts/check-playwright-port.sh
echo "Exit code: $?"
```

Expected: Exit code 0 (allowed) — or exit 2 with "Frontend is not running" if frontend isn't started.

- [ ] **Step 4: Verify hook still blocks default mode**

```bash
echo '{"tool_input":{"url":"http://localhost:3000/dashboard"}}' | bash compose/scripts/check-playwright-port.sh
echo "Exit code: $?"
```

Expected: Exit code 2 (blocked) with the Clerk CAPTCHA message.

- [ ] **Step 5: Commit**

```bash
git add compose/scripts/check-playwright-port.sh
git commit -m "feat(e2e): allow port 3000 navigation when E2E_AUTH_MODE=keycloak"
```

---

## Chunk 2: Onboarding E2E Test

### Task 6: Access Request → Approval → Registration Test

**Files:**
- Create: `frontend/e2e/tests/keycloak/onboarding.spec.ts`

This is the most important test — it validates the full onboarding flow that every org goes through. It exercises: public access request form, OTP email verification, platform admin approval, Keycloak invitation, user registration, and JIT member sync.

**Prerequisite state:** Dev stack running locally with `keycloak-bootstrap.sh` already run (platform admin exists).

- [ ] **Step 1: Create the test file**

```typescript
// frontend/e2e/tests/keycloak/onboarding.spec.ts

import { test, expect } from '@playwright/test'
import { loginAs, loginAsPlatformAdmin, registerFromInvite, logout } from '../../fixtures/keycloak-auth'
import { clearMailbox, waitForEmail, extractOtp, extractInviteLink } from '../../helpers/mailpit'
import { saveState } from '../../helpers/e2e-state'

// Unique run ID to avoid collisions between test runs
const RUN_ID = Date.now().toString(36).slice(-5)
const OWNER_EMAIL = `owner-${RUN_ID}@thornton-za.e2e-test.local`
const ORG_NAME = `Thornton ${RUN_ID}`

test.describe.serial('Keycloak Onboarding: Access Request → Approval → Registration', () => {
  test.beforeAll(async () => {
    await clearMailbox()
  })

  test('1. Submit access request and verify OTP', async ({ page }) => {
    // Combined into one test to keep the same page session —
    // splitting would require re-submitting the form (fresh page per test),
    // which risks creating duplicate AccessRequest rows.

    await page.goto('/request-access')

    // Fill the access request form
    await page.getByTestId('email-input').fill(OWNER_EMAIL)
    await page.getByTestId('full-name-input').fill('Thandi Thornton')
    await page.getByTestId('org-name-input').fill(ORG_NAME)

    // Select country — Shadcn Select renders via Radix portal
    await page.getByTestId('country-select').click()
    await page.getByRole('option', { name: 'South Africa' }).click()

    // Select industry
    await page.getByTestId('industry-select').click()
    await page.getByRole('option', { name: 'Accounting' }).click()

    // Submit
    await page.getByTestId('submit-request-btn').click()

    // Should advance to OTP step
    await expect(page.getByTestId('otp-input')).toBeVisible({ timeout: 10_000 })

    // Wait for OTP email to arrive in Mailpit
    const email = await waitForEmail(OWNER_EMAIL, { timeout: 15_000 })
    const otp = extractOtp(email)
    expect(otp).toMatch(/^\d{6}$/)

    // Enter OTP on the same page
    await page.getByTestId('otp-input').fill(otp)
    await page.getByTestId('verify-otp-btn').click()

    // Should show success message
    await expect(page.getByTestId('success-message')).toBeVisible({ timeout: 10_000 })
  })

  test('2. Platform admin approves request', async ({ page }) => {
    // Login as platform admin
    await loginAsPlatformAdmin(page)

    // Navigate to access requests
    await page.goto('/platform-admin/access-requests')
    await page.waitForLoadState('networkidle')

    // Verify org appears in the pending list
    await expect(page.getByText(ORG_NAME)).toBeVisible({ timeout: 10_000 })

    // Click Approve — use data-testid for precision
    const row = page.getByTestId(`request-row-${ORG_NAME}`)
    // Fallback if data-testid doesn't match: page.locator('tr', { hasText: ORG_NAME })
    await row.getByTestId('approve-btn').click()

    // Confirm in the AlertDialog
    await expect(page.getByRole('alertdialog')).toBeVisible({ timeout: 5_000 })
    await page.getByTestId('confirm-approve-btn').click()

    // Wait for dialog to close and status to update
    await expect(page.getByRole('alertdialog')).not.toBeVisible({ timeout: 15_000 })

    // Verify status changed to Approved
    await expect(page.getByText(/approved/i)).toBeVisible({ timeout: 10_000 })
  })

  test('3. Owner receives invitation and registers', async ({ page }) => {
    // Wait for Keycloak invitation email
    const email = await waitForEmail(OWNER_EMAIL, {
      subject: 'invitation',
      timeout: 30_000,
    })
    const inviteLink = extractInviteLink(email)
    expect(inviteLink).toBeTruthy()

    // Complete registration via Keycloak
    await registerFromInvite(page, inviteLink, 'Thandi', 'Thornton', 'SecureP@ss1')

    // Should be redirected to the app after registration
    await page.waitForLoadState('networkidle')

    // Verify we land somewhere authenticated
    await expect(page.locator('body')).not.toContainText('Sign in')
  })

  test('4. Owner can login and sees dashboard', async ({ page }) => {
    // Login as the newly registered owner
    await loginAs(page, OWNER_EMAIL, 'SecureP@ss1')

    // Should land on org dashboard
    await page.waitForLoadState('networkidle')

    // Verify authenticated
    await expect(page.locator('body')).not.toContainText('Sign in')
    // Verify we're on an org-scoped page
    await expect(page).toHaveURL(/\/org\//)

    // Extract org slug and save state for downstream tests
    const url = page.url()
    const slugMatch = url.match(/\/org\/([^/]+)/)
    const orgSlug = slugMatch?.[1] ?? ''
    expect(orgSlug).toBeTruthy()

    saveState({
      runId: RUN_ID,
      ownerEmail: OWNER_EMAIL,
      ownerPassword: 'SecureP@ss1',
      orgSlug,
    })
  })
})
```

**IMPORTANT NOTES for the implementing agent:**
- The access request form uses `data-testid` attributes. Read `frontend/components/access-request/request-access-form.tsx` to verify the exact test IDs match.
- The Select components for country/industry may use Shadcn's `<Select>` which renders differently from native `<select>`. The implementing agent MUST check the actual rendered DOM via Playwright snapshot to confirm the correct way to select options.
- The OTP verification flow may reuse the same page session or require re-navigation. Test step 2 handles this by re-filling the form. If the access request form preserves state across pages (e.g., via URL params or session storage), this step may need adjustment.
- After approval, the Keycloak invitation email may take several seconds. The 30s timeout should be sufficient.

- [ ] **Step 2: Manually verify prerequisite state**

Before running this test, ensure:
1. Dev stack infra running: `bash compose/scripts/dev-up.sh`
2. Backend running: `SPRING_PROFILES_ACTIVE=local,keycloak ./mvnw spring-boot:run` (in `backend/`)
3. Gateway running: `./mvnw spring-boot:run` (in `gateway/`)
4. Frontend running: `NEXT_PUBLIC_AUTH_MODE=keycloak pnpm dev` (in `frontend/`)
5. Keycloak bootstrapped: `bash compose/scripts/keycloak-bootstrap.sh`
6. Mailpit accessible: `curl -sf http://localhost:8025/api/v1/messages | jq '.total'`

- [ ] **Step 3: Run the test**

```bash
cd frontend && E2E_AUTH_MODE=keycloak npx playwright test e2e/tests/keycloak/onboarding.spec.ts --config e2e/playwright.config.ts --reporter=list
```

Expected: All 5 tests pass. If any fail, debug by checking:
- Mailpit emails: `curl http://localhost:8025/api/v1/messages | jq`
- Keycloak admin console: `http://localhost:8180/admin/master/console/`
- Backend logs: check for provisioning errors

- [ ] **Step 4: Fix any selector mismatches**

The most likely failures are:
- Keycloak form selectors don't match the Keycloakify theme → update `keycloak-selectors.ts`
- Select components need different interaction pattern → update test to use Playwright's snapshot to identify correct selectors
- Invite link regex doesn't match Keycloak's actual URL format → update `extractInviteLink()` regex

- [ ] **Step 5: Commit**

```bash
git add frontend/e2e/tests/keycloak/onboarding.spec.ts
git commit -m "feat(e2e): add Keycloak onboarding test (access request → approval → registration)"
```

---

## Chunk 3: Member Invite & RBAC Test

### Task 7: Member Invite → Registration → RBAC Test

**Files:**
- Create: `frontend/e2e/tests/keycloak/member-invite-rbac.spec.ts`

**Prerequisite:** Onboarding test (Task 6) must have run successfully first. This test expects an org to exist with an owner who can login.

This test covers:
1. Owner upgrades plan to allow more members
2. Owner invites an admin and a member
3. Invited users register via Keycloak invitation emails
4. RBAC verification for each role

- [ ] **Step 1: Create the test file**

```typescript
// frontend/e2e/tests/keycloak/member-invite-rbac.spec.ts

import { test, expect } from '@playwright/test'
import { loginAs, registerFromInvite, logout } from '../../fixtures/keycloak-auth'
import { clearMailbox, waitForEmail, extractInviteLink } from '../../helpers/mailpit'
import { loadState, hasState, type E2eState } from '../../helpers/e2e-state'

// Load state from onboarding test (written to /tmp/e2e-keycloak-state.json)
let state: E2eState
let ADMIN_EMAIL: string
let MEMBER_EMAIL: string

test.describe.serial('Keycloak Member Invite & RBAC', () => {
  test.skip(!hasState(), 'Requires onboarding.spec.ts to run first (state file missing)')

  test.beforeAll(() => {
    state = loadState()
    ADMIN_EMAIL = `bob-${state.runId}@thornton-za.e2e-test.local`
    MEMBER_EMAIL = `carol-${state.runId}@thornton-za.e2e-test.local`
  })

  test('1. Owner logs in and upgrades plan', async ({ page }) => {
    await loginAs(page, state.ownerEmail, state.ownerPassword)
    await page.waitForURL(/\/org\//)

    await page.goto(`/org/${state.orgSlug}/settings/billing`)
    await page.waitForLoadState('networkidle')

    // Click "Upgrade to Pro"
    await page.getByRole('button', { name: /upgrade to pro/i }).click()

    // Confirm in dialog
    await expect(page.getByRole('alertdialog').or(page.getByRole('dialog'))).toBeVisible({ timeout: 5_000 })
    await page.getByRole('button', { name: /confirm/i }).click()

    // Wait for upgrade to complete
    await page.waitForTimeout(3000)

    // Verify plan shows Pro
    await expect(page.getByText(/professional|pro/i)).toBeVisible({ timeout: 5_000 })
  })

  test('2. Owner invites admin (Bob)', async ({ page }) => {
    await clearMailbox()
    await loginAs(page, state.ownerEmail, state.ownerPassword)
    await page.waitForURL(/\/org\//)

    await page.goto(`/org/${state.orgSlug}/team`)
    await page.waitForLoadState('networkidle')

    // Click invite button
    await page.getByRole('button', { name: /invite/i }).click()

    // Fill invite form
    await page.getByTestId('invite-email-input').fill(ADMIN_EMAIL)

    // Select Admin role
    await page.getByTestId('role-select').click()
    await page.getByRole('option', { name: /admin/i }).click()

    // Submit
    await page.getByTestId('invite-member-btn').click()

    // Wait for invitation to process
    await page.waitForTimeout(2000)
  })

  test('3. Bob registers from invitation email', async ({ page }) => {
    const email = await waitForEmail(ADMIN_EMAIL, {
      subject: 'invitation',
      timeout: 30_000,
    })
    const inviteLink = extractInviteLink(email)
    expect(inviteLink).toBeTruthy()

    await registerFromInvite(page, inviteLink, 'Bob', 'Ndlovu', 'SecureP@ss2')
    await page.waitForLoadState('networkidle')
    await expect(page.locator('body')).not.toContainText('Sign in')
  })

  test('4. Owner invites member (Carol)', async ({ page }) => {
    await clearMailbox()
    await loginAs(page, state.ownerEmail, state.ownerPassword)
    await page.waitForURL(/\/org\//)

    await page.goto(`/org/${state.orgSlug}/team`)
    await page.waitForLoadState('networkidle')

    await page.getByRole('button', { name: /invite/i }).click()
    await page.getByTestId('invite-email-input').fill(MEMBER_EMAIL)
    await page.getByTestId('role-select').click()
    await page.getByRole('option', { name: /member/i }).click()
    await page.getByTestId('invite-member-btn').click()
    await page.waitForTimeout(2000)
  })

  test('5. Carol registers from invitation email', async ({ page }) => {
    const email = await waitForEmail(MEMBER_EMAIL, {
      subject: 'invitation',
      timeout: 30_000,
    })
    const inviteLink = extractInviteLink(email)
    await registerFromInvite(page, inviteLink, 'Carol', 'Mokoena', 'SecureP@ss3')
    await page.waitForLoadState('networkidle')
    await expect(page.locator('body')).not.toContainText('Sign in')
  })

  test('6. RBAC: Admin (Bob) can access settings', async ({ page }) => {
    await loginAs(page, ADMIN_EMAIL, 'SecureP@ss2')
    await page.waitForURL(/\/org\//)

    // Admin should be able to access settings
    await page.goto(`/org/${state.orgSlug}/settings`)
    await expect(page.locator('body')).not.toContainText('Something went wrong')
    await expect(page.locator('body')).not.toContainText(/don.t have access/i)

    // Admin should be able to access team page
    await page.goto(`/org/${state.orgSlug}/team`)
    await expect(page.locator('body')).not.toContainText('Something went wrong')
  })

  test('7. RBAC: Member (Carol) cannot access financial settings', async ({ page }) => {
    await loginAs(page, MEMBER_EMAIL, 'SecureP@ss3')
    await page.waitForURL(/\/org\//)

    // Member should NOT be able to access rates
    await page.goto(`/org/${state.orgSlug}/settings/rates`)
    await expect(page.locator('body')).toContainText(/don.t have access|permission/i)

    // Member should NOT be able to access profitability
    await page.goto(`/org/${state.orgSlug}/profitability`)
    await expect(page.locator('body')).toContainText(/don.t have access|permission/i)
  })
})
```

**IMPORTANT NOTES for the implementing agent:**
- This test reads state from `/tmp/e2e-keycloak-state.json` (written by `onboarding.spec.ts`). Run onboarding first.
- The invite form selectors (`data-testid="invite-email-input"`, etc.) come from research. Read `frontend/components/team/invite-member-form.tsx` to verify they match.
- The UpgradeButton triggers `upgradeToPro(slug)` via `UpgradeConfirmDialog`. Read `frontend/components/billing/upgrade-confirm-dialog.tsx` to verify button text.
- The test assumes PRO plan allows at least 4 members (owner + 2 invites). Verify plan limits in backend.

- [ ] **Step 2: Run the test**

Run onboarding first, then this test (it reads the state file automatically):

```bash
cd frontend && E2E_AUTH_MODE=keycloak npx playwright test keycloak/onboarding keycloak/member-invite-rbac --config e2e/playwright.config.ts --reporter=list
```

- [ ] **Step 3: Fix any issues and commit**

```bash
git add frontend/e2e/tests/keycloak/member-invite-rbac.spec.ts
git commit -m "feat(e2e): add member invite and RBAC verification test (Keycloak)"
```

---

## Chunk 4: Skill Update & Cleanup

### Task 8: Update qa-cycle-kc Skill

**Files:**
- Modify: `.claude/skills/qa-cycle-kc/SKILL.md`

- [ ] **Step 1: Read current skill**

```bash
head -30 .claude/skills/qa-cycle-kc/SKILL.md
```

- [ ] **Step 2: Update skill with local-services setup**

Replace the "Environment" section to reflect local services (not Docker `--all`). Key changes:
- Services run locally in terminal sessions, not Docker containers
- Infra Agent starts infra only via `dev-up.sh` (without `--all`)
- QA Agent auth instructions reference the `loginAs()` pattern from `keycloak-auth.ts`
- Remove `/etc/hosts` prerequisite (gateway runs locally, uses `localhost:8180`)

The updated environment table should be:

```markdown
| Service | How to Start | URL |
|---------|-------------|-----|
| Infra (Postgres, LocalStack, Mailpit, Keycloak) | `bash compose/scripts/dev-up.sh` | various |
| Backend | `SPRING_PROFILES_ACTIVE=local,keycloak ./mvnw spring-boot:run` (in backend/) | http://localhost:8080 |
| Frontend | `NEXT_PUBLIC_AUTH_MODE=keycloak pnpm dev` (in frontend/) | http://localhost:3000 |
| Gateway | `./mvnw spring-boot:run` (in gateway/) | http://localhost:8443 |
| Portal | `pnpm dev` (in portal/) | http://localhost:3002 |
| Keycloak Bootstrap | `bash compose/scripts/keycloak-bootstrap.sh` (run once) | |
```

- [ ] **Step 3: Commit**

```bash
git add .claude/skills/qa-cycle-kc/SKILL.md
git commit -m "fix(skill): update qa-cycle-kc with local-services setup"
```

---

## Execution Order

Tasks must run in this order (dependencies flow downward):

```
Task 1 (selectors) ──┐
                      ├──→ Task 3 (auth fixture)
Task 2 (mailpit)  ───┤
Task 2b (state)   ───┤
                      ├──→ Task 6 (onboarding test) ──→ Task 7 (member invite test)
Task 4 (config)   ───┘
Task 5 (hook)  ───────┘

Task 8 (skill update) ← independent, can run anytime
```

**Parallel opportunities:**
- Tasks 1, 2, 2b, 4, 5 are independent of each other
- Task 8 is independent of all others
- Task 3 depends on Task 1
- Task 6 depends on Tasks 1, 2, 2b, 3, 4
- Task 7 depends on Task 6 (reads state file written by onboarding test)

---

## Test Run Commands

```bash
# Run just the Keycloak onboarding test
cd frontend && E2E_AUTH_MODE=keycloak npx playwright test keycloak/onboarding --config e2e/playwright.config.ts --reporter=list

# Run all Keycloak tests
cd frontend && E2E_AUTH_MODE=keycloak npx playwright test keycloak/ --config e2e/playwright.config.ts --reporter=list

# Run existing mock-auth tests (unchanged)
cd frontend && PLAYWRIGHT_BASE_URL=http://localhost:3001 npx playwright test smoke --config e2e/playwright.config.ts

# Debug a failing test with headed browser
cd frontend && E2E_AUTH_MODE=keycloak npx playwright test keycloak/onboarding --config e2e/playwright.config.ts --headed --reporter=list
```

## Known Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Keycloak form selectors differ from research | All selectors in one file (`keycloak-selectors.ts`). If tests fail on login, take a Playwright screenshot/snapshot and update selectors. |
| Mailpit API format differs from expected | Task 2 includes a manual verification step. If API differs, check `http://localhost:8025/api/v1/` for correct endpoints. |
| Invite email takes too long | 30s timeout in `waitForEmail`. Can be increased if Keycloak SMTP is slow. |
| Access request form Select components render differently | Use Playwright snapshot to identify actual DOM structure for Select components. Radix Select renders items in a portal with `role="option"`. |
| Plan upgrade dialog button text mismatch | Read `frontend/components/billing/upgrade-confirm-dialog.tsx` to verify button text. |
| `data-testid` attributes on request row don't match | The approve table may use different testid patterns. Fallback: `page.locator('tr', { hasText: ORG_NAME })`. |
| Keycloak invite email subject doesn't contain "invitation" | Adjust `waitForEmail` subject filter. Check actual email subject in Mailpit UI first. |

## Deferred Work (Follow-on)

| Item | Why Deferred |
|------|-------------|
| **Section 3: Accounting-ZA pack verification** (`accounting-za-packs.spec.ts`) | Provisioning pipeline gaps (spec S6.1-6.3) must be resolved first. Rate card, tax, and currency auto-seeding may not work yet. Write diagnostic tests after fixing provisioning. |
| **Section 5: Existing test migration** | Mechanical work (swap fixtures, update ports). Better done after Keycloak harness is proven. The mock IDP stack remains functional. |
| **`dev-e2e-up.sh` / `dev-e2e-down.sh`** | User runs services locally in terminal sessions. Docker full-stack scripts not needed for current workflow. |
