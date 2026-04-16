import { Page } from "@playwright/test";

const MOCK_IDP_URL = process.env.MOCK_IDP_URL || "http://localhost:8090";

type SeedUser = "alice" | "bob" | "carol";

const USERS: Record<
  SeedUser,
  { userId: string; orgId: string; orgSlug: string; orgRole: string }
> = {
  alice: { userId: "user_e2e_alice", orgId: "org_e2e_test", orgSlug: "e2e-test-org", orgRole: "org:owner" },
  bob: { userId: "user_e2e_bob", orgId: "org_e2e_test", orgSlug: "e2e-test-org", orgRole: "org:admin" },
  carol: { userId: "user_e2e_carol", orgId: "org_e2e_test", orgSlug: "e2e-test-org", orgRole: "org:member" },
};

/**
 * Get a raw API bearer token for the given seed user.
 * Use this for direct backend API calls in tests (not browser-based).
 */
export async function getApiToken(user: SeedUser = "alice"): Promise<string> {
  const res = await fetch(`${MOCK_IDP_URL}/token`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(USERS[user]),
  });

  if (!res.ok) {
    throw new Error(`Failed to get token for ${user}: ${res.status} ${res.statusText}`);
  }

  const { access_token } = await res.json();
  return access_token;
}

export async function loginAs(page: Page, user: SeedUser): Promise<void> {
  const token = await getApiToken(user);

  await page.context().addCookies([
    {
      name: "mock-auth-token",
      value: token,
      domain: "localhost",
      path: "/",
      httpOnly: false,
      sameSite: "Lax",
    },
  ]);
}
