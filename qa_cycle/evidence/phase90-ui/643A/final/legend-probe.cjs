// 643A final-OBS (read-only): D3 — chart legend items, direct end labels and plot right edge at 1024/1440 x light/dark.
const { chromium } = require("/Users/rakheendama/Projects/2026/worktree-epic-643A/frontend/node_modules/@playwright/test");
const BASE = "http://localhost:3001";
(async () => {
  const browser = await chromium.launch();
  const res = {}, msgs = [];
  for (const theme of ["light", "dark"]) {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, colorScheme: theme });
    await ctx.addInitScript((t) => { try { localStorage.setItem("kazi-theme", t); } catch {} }, theme);
    const page = await ctx.newPage();
    page.on("console", (m) => { if (["error", "warning"].includes(m.type())) msgs.push(`${theme} ${m.type()} ${m.text().slice(0, 200)}`); });
    await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
    await page.getByRole("button", { name: "Sign In" }).click();
    await page.waitForURL(/\/dashboard/, { timeout: 60000 });
    await page.locator("h1").first().waitFor({ timeout: 60000 }); await page.waitForTimeout(1000);
    for (const [w, h] of [[1024, 768], [1440, 900]]) {
      await page.setViewportSize({ width: w, height: h });
      await page.goto(`${BASE}/org/e2e-test-org/trust-accounting`, { waitUntil: "load" });
      await page.locator('[data-testid="trust-balance-trend"][data-state="ready"]').waitFor({ timeout: 60000 }); await page.waitForTimeout(3000);
      res[`${w}-${theme}`] = await page.evaluate(() => {
        const c = document.querySelector('[data-testid="trust-balance-trend"]'); const svg = c.querySelector("svg.recharts-surface");
        const B = (e) => { const r = e.getBoundingClientRect(); return [Math.round(r.left), Math.round(r.right), Math.round(r.top), Math.round(r.bottom)]; };
        const legendWrap = c.querySelector(".recharts-legend-wrapper");
        const legendItems = legendWrap ? [...legendWrap.querySelectorAll("div > div")].map((d) => d.textContent.trim()).filter(Boolean) : [];
        const svgTexts = [...svg.querySelectorAll("text")].map((t) => t.textContent.trim());
        const endLabels = svgTexts.filter((t) => t === "Cashbook" || t === "Client ledgers");
        const curves = [...svg.querySelectorAll("path.recharts-curve")].map((p) => { const b = p.getBBox(); return { stroke: getComputedStyle(p).stroke, dash: p.getAttribute("stroke-dasharray"), right: Math.round(b.x + b.width) }; });
        return { html: document.documentElement.className, legendText: legendWrap ? legendWrap.innerText.replace(/\n/g, " | ") : null, legendItems, endLabels, svgWidth: Math.round(svg.getBoundingClientRect().width), curvesRightMax: Math.max(...curves.map((c) => c.right)), curveCount: curves.length, xTickColor: (() => { const t = svg.querySelector(".recharts-cartesian-axis-tick-value, .recharts-cartesian-axis-tick text"); return t ? getComputedStyle(t).fill : null; })() };
      });
    }
    await ctx.close();
  }
  console.log(JSON.stringify({ res, console: msgs }));
  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
