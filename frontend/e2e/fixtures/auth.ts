import { Page } from '@playwright/test'

const MOCK_IDP_URL = process.env.MOCK_IDP_URL || 'http://localhost:8090'

type SeedUser = 'alice' | 'bob' | 'carol'

const USERS: Record<SeedUser, { userId: string; orgId: string; orgRole: string; orgSlug: string }> = {
  alice: { userId: 'user_e2e_alice', orgId: 'org_e2e_test', orgRole: 'owner', orgSlug: 'e2e-test-org' },
  bob:   { userId: 'user_e2e_bob',   orgId: 'org_e2e_test', orgRole: 'admin', orgSlug: 'e2e-test-org' },
  carol: { userId: 'user_e2e_carol', orgId: 'org_e2e_test', orgRole: 'member', orgSlug: 'e2e-test-org' },
}

export async function loginAs(page: Page, user: SeedUser): Promise<void> {
  const res = await fetch(`${MOCK_IDP_URL}/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(USERS[user]),
  })

  if (!res.ok) {
    throw new Error(`Failed to get token for ${user}: ${res.status} ${res.statusText}`)
  }

  const { access_token } = await res.json()

  await page.context().addCookies([{
    name: 'mock-auth-token',
    value: access_token,
    domain: 'localhost',
    path: '/',
  }])
}
