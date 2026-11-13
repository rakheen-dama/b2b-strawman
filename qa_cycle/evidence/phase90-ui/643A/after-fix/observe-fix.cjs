// 643A OBS fix self-observe: worktree `next dev` on :3013 (mock auth) against the e2e backend :8081.
// Measures transactions table scroll, StatCard value fit / icon overlap, trend end-label boxes at 1024 + 1440 light.
const { chromium } = require("/Users/rakheendama/Projects/2026/worktree-epic-643A/frontend/node_modules/@playwright/test");
const path = require("path");
const BASE = process.env.BASE || "http://localhost:3013", OUT = __dirname;
(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, colorScheme: "light", reducedMotion: "reduce" }); // reduced motion: full-page captures resize the viewport, which replays the area animation mid-shot
  const page = await ctx.newPage();
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.waitForURL(/\/dashboard/, { timeout: 120000 });
  await page.locator("h1").first().waitFor({ timeout: 120000 });
  const res = {};
  for (const [w, h] of [[1024, 768], [1440, 900], [390, 844], [768, 1024]]) {
    await page.setViewportSize({ width: w, height: h });
    await page.goto(`${BASE}/org/e2e-test-org/trust-accounting`, { waitUntil: "load" });
    await page.locator("h1").first().waitFor({ timeout: 120000 });
    await page.locator('[data-testid="summary-cards"]').waitFor({ timeout: 120000 });
    await page.locator('[data-testid="trust-balance-trend"] path.recharts-area-curve').first().waitFor({ state: "attached", timeout: 60000 });
    await page.waitForTimeout(1500);
    res[w] = await page.evaluate(() => {
      const R = (e) => { const r = e.getBoundingClientRect(); return { l: Math.round(r.left), r: Math.round(r.right), t: Math.round(r.top), b: Math.round(r.bottom) }; };
      const hit = (a, b) => a.l < b.r && b.l < a.r && a.t < b.b && b.t < a.b;
      const grid = document.querySelector('[data-testid="summary-cards"]');
      const cols = getComputedStyle(grid).gridTemplateColumns.split(" ").length;
      const cards = [...grid.children].map((card) => {
        const val = card.querySelector('[data-slot="stat-card-value"]');
        const lab = card.querySelector('[data-slot="stat-card-label"]');
        const icon = card.querySelector('[data-slot="stat-card-icon"]') || card.querySelector('[aria-hidden="true"].size-9, .size-9');
        let tb = null; if (val) { const rg = document.createRange(); rg.selectNodeContents(val); tb = R(rg); }
        let lb = null; if (lab) { const rg = document.createRange(); rg.selectNodeContents(lab); lb = R(rg); }
        const ib = icon ? R(icon) : null;
        return { label: lab && lab.innerText, value: val && val.innerText.trim(), scroll: val ? [val.scrollWidth, val.clientWidth] : null, valueHitsIcon: !!(tb && ib && hit(tb, ib)), labelHitsIcon: !!(lb && ib && hit(lb, ib)), valueInCard: tb ? tb.r <= R(card).r : null };
      });
      const t = document.querySelector('[data-testid="transactions-table"]'); const sc = t.parentElement; const sb = R(sc);
      const thead = [...t.querySelectorAll("thead th")].map((th) => { const b = R(th); return [th.innerText.trim(), b.l >= sb.l - 1 && b.r <= sb.r + 1]; });
      const chart = document.querySelector('[data-testid="trust-balance-trend"]');
      const labels = [...chart.querySelectorAll("svg.recharts-surface text")].filter((t) => ["Cashbook", "Client ledgers"].includes(t.textContent)).map((t) => ({ text: t.textContent, ...R(t) }));
      const clip = chart.querySelector("svg.recharts-surface clipPath rect");
      const legend = chart.querySelector(".recharts-legend-wrapper");
      return { gridCols: cols, cards, table: { scrollW: sc.scrollWidth, clientW: sc.clientWidth, tableW: t.scrollWidth, cols: thead }, endLabels: labels, plotW: clip && Number(clip.getAttribute("width")), legend: legend && legend.innerText.replace(/\s+/g, " ").trim() };
    });
    if (w === 1024 || w === 1440) {
      await page.screenshot({ path: path.join(OUT, `trust-dashboard-${w}-light.png`), fullPage: true });
      await page.locator('[data-testid="summary-cards"]').screenshot({ path: path.join(OUT, `summary-cards-${w}-light.png`) });
      await page.locator('[data-testid="trust-balance-trend"]').screenshot({ path: path.join(OUT, `chart-${w}-light.png`) });
    }
  }
  require("fs").writeFileSync(path.join(OUT, "observe-fix-out.json"), JSON.stringify(res) + "\n");
  console.log(JSON.stringify(res));
  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
