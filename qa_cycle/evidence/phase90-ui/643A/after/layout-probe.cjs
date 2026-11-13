// 643A after-OBS: measure StatCard value/icon overlap + value overflow, and which transactions-table
// columns are inside the visible scroller box, at 768/1024/1280/1440 (light, Alice).
const { chromium } = require("/Users/rakheendama/Projects/2026/worktree-epic-643A/frontend/node_modules/@playwright/test");
const path = require("path");
const BASE = "http://localhost:3001", OUT = __dirname;
(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, colorScheme: "light" });
  const page = await ctx.newPage();
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.waitForURL(/\/dashboard/, { timeout: 60000 });
  await page.locator("h1").first().waitFor({ timeout: 60000 }); await page.waitForTimeout(1000);
  const res = {};
  for (const [w, h] of [[768, 1024], [1024, 768], [1280, 800], [1440, 900]]) {
    await page.setViewportSize({ width: w, height: h });
    await page.goto(`${BASE}/org/e2e-test-org/trust-accounting`, { waitUntil: "load" });
    await page.locator('[data-testid="summary-cards"]').waitFor({ timeout: 60000 }); await page.waitForTimeout(800);
    res[w] = await page.evaluate(() => {
      const R = (e) => { const r = e.getBoundingClientRect(); return { l: Math.round(r.left), r: Math.round(r.right), t: Math.round(r.top), b: Math.round(r.bottom) }; };
      const hit = (a, b) => a.l < b.r && b.l < a.r && a.t < b.b && b.t < a.b;
      const cards = [...document.querySelectorAll('[data-testid="summary-cards"] > *')].map((card) => {
        const label = card.querySelector('[data-slot="stat-card-label"]') || card.querySelector("p,span,div");
        const val = card.querySelector('[data-slot="stat-card-value"]');
        const icon = card.querySelector('[data-slot="stat-card-icon"]');
        const cardBox = R(card);
        const valText = val ? val.innerText.trim() : null;
        // measure the value's text extent with a Range (catches text that paints beyond its box)
        let textBox = null; if (val) { const rg = document.createRange(); rg.selectNodeContents(val); textBox = R(rg); }
        const iconBox = icon ? R(icon) : null;
        return { label: card.innerText.split("\n")[0], value: valText, valueTextRight: textBox && textBox.r, cardRight: cardBox.r - 1, valueOverflowsCard: textBox ? textBox.r > cardBox.r - 1 : null, valueHitsIcon: textBox && iconBox ? hit(textBox, iconBox) : null, valueScroll: val ? [val.scrollWidth, val.clientWidth] : null };
      });
      const t = document.querySelector('[data-testid="transactions-table"]'); const sc = t.parentElement; const sb = R(sc);
      const cols = [...t.querySelectorAll("thead th")].map((th) => { const b = R(th); return { col: th.innerText.trim(), fullyVisible: b.l >= sb.l - 1 && b.r <= sb.r + 1 }; });
      return { cards, transactions: { scrollerW: sc.clientWidth, tableW: t.scrollWidth, cols } };
    });
    if (w === 1024 || w === 768 || w === 1280) await page.locator('[data-testid="summary-cards"]').screenshot({ path: path.join(OUT, `summary-cards-${w}-light.png`) });
  }
  console.log(JSON.stringify(res));
  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
