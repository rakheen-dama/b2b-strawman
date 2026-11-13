// 643A after-OBS: how many [data-testid="trust-position-widget"] does Home render, over time, and are they visible?
const { chromium } = require("/Users/rakheendama/Projects/2026/worktree-epic-643A/frontend/node_modules/@playwright/test");
const BASE = "http://localhost:3001";
(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, colorScheme: "light" });
  const page = await ctx.newPage();
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.waitForURL(/\/dashboard/, { timeout: 60000 });
  const samples = [];
  const sample = async (tag) => samples.push({ tag, url: page.url(), widgets: await page.locator('[data-testid="trust-position-widget"]').evaluateAll((els) => els.map((e) => { const r = e.getBoundingClientRect(); return { x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height), inMain: !!e.closest("#main-content"), parentTestid: e.parentElement && e.parentElement.closest("[data-testid]") ? e.parentElement.closest("[data-testid]").getAttribute("data-testid") : null, hiddenAncestor: !!e.closest("[hidden],[aria-hidden=true]"), firstLine: e.innerText.split("\n")[0] }; })) });
  for (let i = 0; i < 6; i++) { await sample(`login-redirect t+${i}s`); await page.waitForTimeout(1000); }
  await page.goto(`${BASE}/org/e2e-test-org/trust-accounting`, { waitUntil: "load" });
  await page.locator("h1").first().waitFor();
  await page.goto(`${BASE}/org/e2e-test-org/dashboard`, { waitUntil: "load" });
  for (let i = 0; i < 6; i++) { await sample(`direct-goto t+${i}s`); await page.waitForTimeout(1000); }
  await page.goto(`${BASE}/org/e2e-test-org/trust-accounting`, { waitUntil: "load" });
  await page.locator("h1").first().waitFor();
  await page.getByRole("link", { name: "Home" }).first().click();
  await page.waitForURL(/\/dashboard/);
  for (let i = 0; i < 6; i++) { await sample(`client-nav t+${i}s`); await page.waitForTimeout(1000); }
  console.log(JSON.stringify(samples));
  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
