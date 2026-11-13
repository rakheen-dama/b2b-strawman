// 643A after-OBS probe: mock-login (Alice), read trust-position / trend / cashbook / total-balance / reconciliations.
const { chromium } = require("/Users/rakheendama/Projects/2026/worktree-epic-643A/frontend/node_modules/@playwright/test");
const BASE = "http://localhost:3001", API = "http://localhost:8081";
(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.waitForURL(/\/dashboard/, { timeout: 60000 });
  const token = (await ctx.cookies()).find((c) => c.name === "mock-auth-token").value;
  const get = async (p) => { const r = await ctx.request.get(API + p, { headers: { Authorization: `Bearer ${token}` } }); let b = null; try { b = await r.json(); } catch {} return { status: r.status(), body: b }; };
  const tp = await get("/api/dashboard/trust-position");
  const id = tp.body && tp.body.trustAccountId;
  const out = { trustPosition: tp, trend: await get("/api/dashboard/trust-balance-trend"), cashbook: await get(`/api/trust-accounts/${id}/cashbook-balance`), totalBalance: await get(`/api/trust-accounts/${id}/total-balance`), reconciliations: await get(`/api/trust-accounts/${id}/reconciliations`), accounts: await get("/api/trust-accounts") };
  console.log(JSON.stringify(out));
  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
