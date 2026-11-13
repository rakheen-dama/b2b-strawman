// 643A before-OBS (read-only): after the wizard runs (2 DRAFT reconciliations now exist), observe the
// trust dashboard, Home trust widget, reconciliation list, trust-position JSON and trend 404.
const { chromium } = require("/Users/rakheendama/Projects/2026/worktree-epic-643A/frontend/node_modules/@playwright/test");
const fs = require("fs"), path = require("path");
const BASE = "http://localhost:3001", API = "http://localhost:8081", SLUG = "e2e-test-org", OUT = __dirname;
const lines = (t) => t.split("\n").map((s) => s.trim()).filter(Boolean);
(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, colorScheme: "light" });
  const page = await ctx.newPage();
  const consoleMsgs = [];
  page.on("console", (m) => { if (["error", "warning"].includes(m.type())) consoleMsgs.push({ url: page.url(), type: m.type(), text: m.text().slice(0, 300) }); });
  page.on("pageerror", (e) => consoleMsgs.push({ url: page.url(), type: "pageerror", text: String(e).slice(0, 300) }));
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.waitForURL(/\/dashboard/, { timeout: 60000 });
  const token = (await ctx.cookies()).find((c) => c.name === "mock-auth-token").value;
  const get = async (p) => { const r = await ctx.request.get(API + p, { headers: { Authorization: `Bearer ${token}` } }); let b = null; try { b = await r.json(); } catch {} return { status: r.status(), body: b }; };
  const out = {};
  out.trustPosition = await get("/api/dashboard/trust-position");
  out.trend = await get("/api/dashboard/trust-balance-trend");
  out.reconciliationsApi = (await get(`/api/trust-accounts/${out.trustPosition.body.trustAccountId}/reconciliations`)).body;
  for (const [w, h] of [[1440, 900], [1024, 768]]) {
    const tag = `${w}`;
    await page.setViewportSize({ width: w, height: h });
    await page.goto(`${BASE}/org/${SLUG}/trust-accounting`, { waitUntil: "load" });
    await page.locator("h1", { hasText: "Trust Accounting" }).waitFor({ timeout: 60000 });
    await page.locator('[data-testid="summary-cards"]').waitFor();
    if (w === 1440) {
      out.cards = await page.locator('[data-testid="summary-cards"] > *').evaluateAll((els) => els.map((el) => el.innerText.split("\n").map((s) => s.trim()).filter(Boolean)));
      const tbl = page.locator('[data-testid="transactions-table"]');
      out.transactionsTable = { testid: "transactions-table", columns: await tbl.locator("thead th").allInnerTexts(), rowCount: await tbl.locator("tbody tr").count() };
      out.alerts = await page.locator('[data-testid="alerts-section"]').innerText().then(lines).catch(() => null);
    }
    await page.screenshot({ path: path.join(OUT, `trust-dashboard-post-${tag}.png`), fullPage: true });
    await page.goto(`${BASE}/org/${SLUG}/dashboard`, { waitUntil: "load" });
    const wdg = page.locator('[data-testid="trust-position-widget"]');
    await wdg.waitFor({ timeout: 60000 });
    if (w === 1440) out.homeWidget = lines(await wdg.innerText());
    await page.screenshot({ path: path.join(OUT, `home-post-${tag}.png`), fullPage: true });
    await wdg.screenshot({ path: path.join(OUT, `home-trust-widget-post-${tag}.png`) });
  }
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto(`${BASE}/org/${SLUG}/trust-accounting/reconciliation`, { waitUntil: "load" });
  await page.locator('[data-testid="new-reconciliation-btn"]').waitFor({ timeout: 60000 });
  out.reconListRows = (await page.locator('[data-testid^="reconciliation-row-"]').allInnerTexts()).map((t) => t.replace(/\s+/g, " ").trim());
  await page.screenshot({ path: path.join(OUT, "recon-list-post-1440.png"), fullPage: true });
  out.console = consoleMsgs;
  fs.writeFileSync(path.join(OUT, "observe-raw.json"), JSON.stringify(out));
  console.log(JSON.stringify(out, null, 1));
  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
