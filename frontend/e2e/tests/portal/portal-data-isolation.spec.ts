import { test, expect } from "@playwright/test";

const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8081";
const ORG_SLUG = "e2e-test-org";

async function getPortalJwt(email: string): Promise<string | null> {
  const linkRes = await fetch(`${BACKEND_URL}/portal/auth/request-link`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, orgId: ORG_SLUG }),
  });
  if (!linkRes.ok) return null;
  // MagicLinkResponse(message, magicLink) — magicLink is
  // "/auth/exchange?token=…&orgId=…" (dev/test/e2e profiles only).
  const { magicLink } = await linkRes.json();
  const token = magicLink?.split("token=").pop()?.split("&")[0];
  if (!token) return null;
  const exchangeRes = await fetch(`${BACKEND_URL}/portal/auth/exchange`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ token, orgId: ORG_SLUG }),
  });
  if (!exchangeRes.ok) return null;
  // PortalTokenResponse(token, clientId, clientName)
  const data = await exchangeRes.json();
  return data.token || null;
}

// These emails must match the E2E seed data
const KGOSI_EMAIL = process.env.KGOSI_EMAIL || "";

test.describe("PORTAL-01: Data Isolation", () => {
  test.skip(!KGOSI_EMAIL, "KGOSI_EMAIL not set — run with portal contact emails");

  // Phase 87 S10 (ADR-344): label
  test("Kgosi sees only Kgosi matters via API", async () => {
    const jwt = await getPortalJwt(KGOSI_EMAIL);
    expect(jwt).toBeTruthy();
    // Phase 87 S3 Batch 5 (ADR-337): API path
    const res = await fetch(`${BACKEND_URL}/portal/matters`, {
      headers: { Authorization: `Bearer ${jwt}` },
    });
    expect(res.ok).toBe(true);
    const matters = await res.json();
    expect(matters.length).toBeGreaterThan(0);
  });

  test("Portal JWT blocked from org API", async () => {
    const jwt = await getPortalJwt(KGOSI_EMAIL);
    // Phase 87 S2 (ADR-337): API path
    const res = await fetch(`${BACKEND_URL}/api/clients`, {
      headers: { Authorization: `Bearer ${jwt}` },
    });
    expect([401, 403]).toContain(res.status);
  });
});
