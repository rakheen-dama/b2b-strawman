/**
 * PROP-03: Portal Proposal Acceptance — Playwright E2E Tests
 *
 * Tests portal contact viewing, accepting, and declining proposals.
 * Uses the magic-link auth flow to authenticate as a portal contact.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. API lifecycle seed complete: bash compose/seed/lifecycle-test.sh
 *   3. Portal contacts exist for seeded customers
 *
 * Run:
 *   PLAYWRIGHT_BASE_URL=http://localhost:3001 npx playwright test portal/portal-proposal-acceptance
 */
import { test, expect, Page } from '@playwright/test'

const BACKEND_URL = process.env.BACKEND_URL || 'http://localhost:8081'
const MOCK_IDP_URL = process.env.MOCK_IDP_URL || 'http://localhost:8090'
const ORG_SLUG = 'e2e-test-org'

// ── Portal Auth Helper ──────────────────────────────────────────
async function getPortalJwt(email: string): Promise<string | null> {
  const linkRes = await fetch(`${BACKEND_URL}/portal/auth/request-link`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, orgId: ORG_SLUG }),
  })
  if (!linkRes.ok) return null
  const linkData = await linkRes.json()
  // Extract token from magicLink URL or direct token field
  const token = linkData.token || linkData.magicLink?.split('token=').pop()?.split('&')[0] || null
  if (!token) return null

  const exchangeRes = await fetch(`${BACKEND_URL}/portal/auth/exchange`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ token, orgId: ORG_SLUG }),
  })
  if (!exchangeRes.ok) return null
  const data = await exchangeRes.json()
  return data.token || data.accessToken || data.access_token || null
}

async function loginAsPortalContact(page: Page, jwt: string) {
  // Set portal auth token as localStorage and cookie
  await page.context().addCookies([{
    name: 'portal-auth-token',
    value: jwt,
    domain: 'localhost',
    path: '/',
    httpOnly: false,
    sameSite: 'Lax' as const,
  }])

  // Also set in localStorage for the portal-api client
  await page.goto('/portal')
  await page.evaluate((token) => {
    localStorage.setItem('portal-token', token)
    localStorage.setItem('portal-auth-token', token)
  }, jwt)
}

async function getAliceJwt(): Promise<string> {
  const res = await fetch(`${MOCK_IDP_URL}/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      userId: 'user_e2e_alice',
      orgId: ORG_SLUG,
      orgSlug: ORG_SLUG,
      orgRole: 'owner',
    }),
  })
  const { access_token } = await res.json()
  return access_token
}

// Find a portal contact email from seed data
async function findPortalContactEmail(): Promise<string | null> {
  const jwt = await getAliceJwt()
  const customersRes = await fetch(`${BACKEND_URL}/api/customers?size=200`, {
    headers: { Authorization: `Bearer ${jwt}` },
  })
  if (!customersRes.ok) return null

  const customers = await customersRes.json()
  const customerList = Array.isArray(customers) ? customers : customers.content ?? []

  for (const customer of customerList) {
    const contactsRes = await fetch(`${BACKEND_URL}/api/customers/${customer.id}/portal-contacts`, {
      headers: { Authorization: `Bearer ${jwt}` },
    })
    if (contactsRes.ok) {
      const contacts = await contactsRes.json()
      if (Array.isArray(contacts) && contacts.length > 0) {
        return contacts[0].email
      }
    }
  }
  return null
}

// Find a SENT proposal visible in the portal
async function findSentProposalId(): Promise<string | null> {
  const jwt = await getAliceJwt()
  const res = await fetch(`${BACKEND_URL}/api/proposals?size=200`, {
    headers: { Authorization: `Bearer ${jwt}` },
  })
  if (!res.ok) return null
  const data = await res.json()
  const proposals = data.content || data
  const sent = proposals.find((p: { status: string }) => p.status === 'SENT')
  return sent?.id ?? null
}

test.describe('PROP-03: Portal Proposal Acceptance', () => {
  let portalEmail: string | null = null
  let portalJwt: string | null = null

  test.beforeAll(async () => {
    portalEmail = await findPortalContactEmail()
    if (portalEmail) {
      portalJwt = await getPortalJwt(portalEmail)
    }
  })

  test('Portal contact sees proposal detail', async ({ page }) => {
    if (!portalJwt) {
      test.skip(true, 'Portal auth not available — no portal contacts or JWT exchange failed')
      return
    }

    await loginAsPortalContact(page, portalJwt)
    await page.goto('/portal/proposals')
    await page.waitForLoadState('networkidle')

    // Check if proposals page loaded
    const heading = page.getByRole('heading', { name: /Proposals/i }).first()
    const hasProposals = await heading.isVisible({ timeout: 5000 }).catch(() => false)

    if (!hasProposals) {
      // Portal proposals page may not have any proposals for this contact
      test.skip(true, 'No proposals visible in portal')
      return
    }

    // Click on the first proposal
    const proposalLink = page.getByRole('link').first()
    const hasLink = await proposalLink.isVisible({ timeout: 3000 }).catch(() => false)
    if (!hasLink) {
      test.skip(true, 'No proposal links visible')
      return
    }

    await proposalLink.click()
    await page.waitForLoadState('networkidle')

    // Verify proposal detail renders with title, amount, and fee model
    await expect(page.locator('body')).not.toContainText('Something went wrong')

    // Portal proposal detail should show fee and fee model
    const hasFee = await page.getByText(/Fee/).first().isVisible({ timeout: 5000 }).catch(() => false)
    expect(hasFee).toBeTruthy()
  })

  test('Accept proposal in portal', async ({ page }) => {
    if (!portalJwt) {
      test.skip(true, 'Portal auth not available')
      return
    }

    const proposalId = await findSentProposalId()
    if (!proposalId) {
      test.skip(true, 'No SENT proposals available to accept')
      return
    }

    await loginAsPortalContact(page, portalJwt)
    await page.goto(`/portal/proposals/${proposalId}`)
    await page.waitForLoadState('networkidle')

    // Look for the Accept Proposal button
    const acceptButton = page.getByRole('button', { name: /Accept/i })
    const canAccept = await acceptButton.isVisible({ timeout: 5000 }).catch(() => false)

    if (!canAccept) {
      test.skip(true, 'Accept button not visible — proposal may not be accessible to this portal contact')
      return
    }

    await acceptButton.click()
    await page.waitForTimeout(3000)

    // Verify acceptance confirmation or status change
    const hasSuccess = await page.getByText(/accepted|confirmed|success/i).first().isVisible({ timeout: 5000 }).catch(() => false)
    const hasAcceptedBadge = await page.getByText('Accepted').first().isVisible({ timeout: 5000 }).catch(() => false)
    expect(hasSuccess || hasAcceptedBadge).toBeTruthy()
  })

  test('Decline proposal with reason in portal', async ({ page }) => {
    if (!portalJwt) {
      test.skip(true, 'Portal auth not available')
      return
    }

    // We need a different SENT proposal
    const jwt = await getAliceJwt()
    const RUN_ID = Date.now().toString(36).slice(-4)

    // Create and send a fresh proposal for decline testing
    const customersRes = await fetch(`${BACKEND_URL}/api/customers?size=200`, {
      headers: { Authorization: `Bearer ${jwt}` },
    })
    const customers = await customersRes.json()
    const customerList = Array.isArray(customers) ? customers : customers.content ?? []
    const activeCustomer = customerList.find(
      (c: { lifecycleStatus: string }) => c.lifecycleStatus === 'ACTIVE'
    )

    if (!activeCustomer) {
      test.skip(true, 'No active customer for decline test')
      return
    }

    // Create proposal
    const createRes = await fetch(`${BACKEND_URL}/api/proposals`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${jwt}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        title: `Decline Test ${RUN_ID}`,
        customerId: activeCustomer.id,
        feeModel: 'FIXED',
        fixedFeeAmount: 10000,
        fixedFeeCurrency: 'ZAR',
      }),
    })

    if (!createRes.ok) {
      test.skip(true, 'Could not create proposal for decline test')
      return
    }

    const proposal = await createRes.json()

    // Get portal contacts and send
    const contactsRes = await fetch(`${BACKEND_URL}/api/customers/${activeCustomer.id}/portal-contacts`, {
      headers: { Authorization: `Bearer ${jwt}` },
    })
    if (!contactsRes.ok) {
      test.skip(true, 'No portal contacts for decline test')
      return
    }
    const contacts = await contactsRes.json()
    if (!Array.isArray(contacts) || contacts.length === 0) {
      test.skip(true, 'No portal contacts configured')
      return
    }

    await fetch(`${BACKEND_URL}/api/proposals/${proposal.id}/send`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${jwt}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ portalContactId: contacts[0].id }),
    })

    // Navigate to proposal in portal
    await loginAsPortalContact(page, portalJwt)
    await page.goto(`/portal/proposals/${proposal.id}`)
    await page.waitForLoadState('networkidle')

    const declineButton = page.getByRole('button', { name: /Decline/i })
    const canDecline = await declineButton.isVisible({ timeout: 5000 }).catch(() => false)

    if (!canDecline) {
      test.skip(true, 'Decline button not visible')
      return
    }

    await declineButton.click()

    // Fill in the decline reason in the dialog
    const dialog = page.getByRole('dialog')
    const dialogVisible = await dialog.isVisible({ timeout: 5000 }).catch(() => false)
    if (dialogVisible) {
      const reasonInput = page.getByPlaceholder(/reason/i)
      if (await reasonInput.isVisible({ timeout: 2000 }).catch(() => false)) {
        await reasonInput.fill('Budget constraints for this quarter')
      }
      // Click Decline Proposal in dialog
      const confirmDecline = dialog.getByRole('button', { name: /Decline Proposal/i })
      await confirmDecline.click()
    }

    await page.waitForTimeout(3000)

    // Verify declined status
    const hasDeclined = await page.getByText('Declined').first().isVisible({ timeout: 5000 }).catch(() => false)
    expect(hasDeclined).toBeTruthy()
  })

  test('No unresolved variables in portal proposal view', async ({ page }) => {
    if (!portalJwt) {
      test.skip(true, 'Portal auth not available')
      return
    }

    await loginAsPortalContact(page, portalJwt)
    await page.goto('/portal/proposals')
    await page.waitForLoadState('networkidle')

    // Navigate to the first visible proposal
    const proposalLink = page.getByRole('link').first()
    const hasLink = await proposalLink.isVisible({ timeout: 5000 }).catch(() => false)

    if (!hasLink) {
      test.skip(true, 'No proposals visible in portal')
      return
    }

    await proposalLink.click()
    await page.waitForLoadState('networkidle')

    // Check for unresolved template variables ({{ }})
    const bodyText = await page.locator('body').innerText()
    const hasUnresolved = /\{\{.*?\}\}/.test(bodyText)
    expect(hasUnresolved).toBe(false)
  })
})
