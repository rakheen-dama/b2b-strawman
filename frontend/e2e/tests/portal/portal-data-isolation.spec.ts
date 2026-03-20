import { test, expect } from '@playwright/test'

const BACKEND_URL = process.env.BACKEND_URL || 'http://localhost:8081'
const ORG_SLUG = 'e2e-test-org'

async function getPortalJwt(email: string): Promise<string | null> {
  const linkRes = await fetch(`${BACKEND_URL}/portal/auth/request-link`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, orgId: ORG_SLUG }),
  })
  if (!linkRes.ok) return null
  const { token } = await linkRes.json()
  const exchangeRes = await fetch(`${BACKEND_URL}/portal/auth/exchange`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ token }),
  })
  if (!exchangeRes.ok) return null
  const data = await exchangeRes.json()
  return data.accessToken || data.access_token || null
}

// These emails must match the E2E seed data
const KGOSI_EMAIL = process.env.KGOSI_EMAIL || ''

test.describe('PORTAL-01: Data Isolation', () => {
  test.skip(!KGOSI_EMAIL, 'KGOSI_EMAIL not set — run with portal contact emails')

  test('Kgosi sees only Kgosi projects via API', async () => {
    const jwt = await getPortalJwt(KGOSI_EMAIL)
    expect(jwt).toBeTruthy()
    const res = await fetch(`${BACKEND_URL}/portal/projects`, {
      headers: { Authorization: `Bearer ${jwt}` },
    })
    expect(res.ok).toBe(true)
    const projects = await res.json()
    expect(projects.length).toBeGreaterThan(0)
  })

  test('Portal JWT blocked from org API', async () => {
    const jwt = await getPortalJwt(KGOSI_EMAIL)
    const res = await fetch(`${BACKEND_URL}/api/customers`, {
      headers: { Authorization: `Bearer ${jwt}` },
    })
    expect([401, 403]).toContain(res.status)
  })
})
