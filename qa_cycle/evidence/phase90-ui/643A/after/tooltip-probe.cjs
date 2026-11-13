// 643A after-OBS: does the chart tooltip appear for the partial (to-date) last month? Mouse sweep across
// the right edge of the plot + keyboard (focus the chart, ArrowRight x11) — reads the tooltip each time.
const { chromium } = require("/Users/rakheendama/Projects/2026/worktree-epic-643A/frontend/node_modules/@playwright/test");
const path = require("path");
const BASE = "http://localhost:3001", OUT = __dirname;
const lines = (t) => t.split("\n").map((s) => s.trim()).filter(Boolean);
(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, colorScheme: "light" });
  const page = await ctx.newPage();
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.waitForURL(/\/dashboard/, { timeout: 60000 });
  await page.locator("h1").first().waitFor({ timeout: 60000 }); await page.waitForTimeout(1000);
  await page.goto(`${BASE}/org/e2e-test-org/trust-accounting`, { waitUntil: "load" });
  const chart = page.locator('[data-testid="trust-balance-trend"][data-state="ready"]');
  await chart.waitFor({ timeout: 60000 }); await page.waitForTimeout(3500);
  await chart.scrollIntoViewIfNeeded();
  const surf = chart.locator("svg.recharts-surface").first(); const box = await surf.boundingBox();
  const ticks = await chart.locator("svg.recharts-surface text").evaluateAll((els) => els.filter((e) => /^[A-Z][a-z]{2} \d{4}$/.test(e.textContent.trim())).map((e) => { const r = e.getBoundingClientRect(); return { t: e.textContent.trim(), cx: Math.round(r.x + r.width / 2) }; }));
  const sweep = [];
  for (const dx of [-120, -60, -20, 0, 10, 30, 60]) {
    const x = ticks.at(-1).cx + dx; await page.mouse.move(x, box.y + box.height * 0.4); await page.waitForTimeout(500);
    const tt = await chart.locator(".recharts-tooltip-wrapper").evaluate((e) => ({ vis: getComputedStyle(e).visibility, text: e.innerText })).catch(() => null);
    sweep.push({ x, tooltip: tt && lines(tt.text), visibility: tt && tt.vis });
  }
  await page.mouse.move(ticks.at(-1).cx, box.y + box.height * 0.4); await page.waitForTimeout(500);
  await chart.screenshot({ path: path.join(OUT, "chart-hover-nov-1440-light.png") });
  await page.mouse.move(5, 5);
  await surf.focus(); await page.waitForTimeout(300);
  const kb = [];
  for (let i = 0; i < 11; i++) { await page.keyboard.press("ArrowRight"); await page.waitForTimeout(250); kb.push(lines(await chart.locator(".recharts-tooltip-wrapper").innerText().catch(() => "")).join(" | ")); }
  console.log(JSON.stringify({ ticks, sweep, keyboard: kb, svgBox: box }, null, 1));
  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
