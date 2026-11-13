// 643C OBS: Matter table right-edge clipping as rendered (no DOM mutation), 1024/1440 x light/dark.
const fs = require("fs"), path = require("path");
const { chromium, PROF, OUT, session, open } = require("./common.cjs");
(async () => {
  const browser = await chromium.launch(); const out = {};
  for (const theme of ["light", "dark"]) for (const w of [1024, 1440]) {
    const { ctx, page } = await session(browser, { theme, viewport: { width: w, height: 900 } });
    await open(page, PROF, '[data-slot="card"] table');
    out[`${w}-${theme}`] = await page.evaluate(() => {
      const t = [...document.querySelectorAll('[data-slot="card-title"]')].find((e) => e.textContent.trim() === "Matter Profitability").closest('[data-slot="card"]').querySelector("table");
      const sc = t.closest('[data-slot="table-container"]'); const sr = sc.getBoundingClientRect().right;
      const cols = [...t.querySelectorAll("thead th")].map((th) => { const b = th.getBoundingClientRect(); return { col: th.textContent.trim(), w: Math.round(b.width), visiblePx: Math.max(0, Math.round(Math.min(b.right, sr) - b.left)), fullyVisible: b.right <= sr + 0.5 }; });
      const firstMargin = t.querySelector("tbody tr td:nth-child(7) span"); const mb = firstMargin.getBoundingClientRect();
      return { htmlClass: document.documentElement.className, scrollWidth: sc.scrollWidth, clientWidth: sc.clientWidth, cols, firstMarginValue: { text: firstMargin.textContent.trim(), font: getComputedStyle(firstMargin).fontFamily.slice(0, 50), visiblePx: Math.max(0, Math.round(Math.min(mb.right, sr) - mb.left)), widthPx: Math.round(mb.width) }, scrollbarGutter: sc.offsetHeight - sc.clientHeight };
    });
    if (w === 1440) { const card = page.locator('[data-slot="card"]').filter({ has: page.locator('[data-slot="card-title"]', { hasText: /^Matter Profitability$/ }) }).last(); await card.screenshot({ path: path.join(OUT, `matter-card-1440-${theme}.png`) }); }
    await ctx.close();
  }
  fs.writeFileSync(path.join(OUT, "matter-clip-raw.json"), JSON.stringify(out)); await browser.close(); console.log(JSON.stringify(out));
})();
