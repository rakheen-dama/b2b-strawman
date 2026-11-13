// 643A before-OBS probe: mock-login as Alice, read trust-position + dashboard + home widget (read-only)
const { chromium } = require("/Users/rakheendama/Projects/2026/worktree-epic-643A/frontend/node_modules/@playwright/test");
const BASE = "http://localhost:3001", API = "http://localhost:8081", SLUG = "e2e-test-org";
(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, colorScheme: "light" });
  const page = await ctx.newPage();
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.waitForURL(/\/dashboard/, { timeout: 60000 });
  const token = (await ctx.cookies()).find((c) => c.name === "mock-auth-token").value;
  const get = async (p) => { const r = await ctx.request.get(API + p, { headers: { Authorization: `Bearer ${token}` } }); let b = null; try { b = await r.json(); } catch {} return { status: r.status(), body: b }; };
  const tp = await get("/api/dashboard/trust-position");
  console.log("trust-position", JSON.stringify(tp));
  const acct = tp.body && tp.body.trustAccountId;
  console.log("cashbook", JSON.stringify(await get(`/api/trust-accounts/${acct}/cashbook-balance`)));
  console.log("recons", JSON.stringify(await get(`/api/trust-accounts/${acct}/reconciliations`)));
  console.log("trend", JSON.stringify(await get("/api/dashboard/trust-balance-trend")));
  await page.goto(`${BASE}/org/${SLUG}/trust-accounting`, { waitUntil: "load" });
  await page.locator("h1").first().waitFor();
  console.log("cards", await page.locator('[data-testid="summary-cards"]').innerText());
  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
