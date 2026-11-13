// 643C re-OBS @ 8dbb9a408: Matter table right-edge clipping as rendered (no DOM mutation), 1024/1440 x light/dark,
// plus money-cell font-family (Matter Revenue/Cost/Margin vs Client Profitability money cells).
const fs = require("fs"), path = require("path");
const { chromium, PROF, OUT, session, open } = require("./common.cjs");
(async () => {
  const browser = await chromium.launch(); const out = {};
  for (const theme of ["light", "dark"]) for (const w of [1024, 1440]) {
    const { ctx, page } = await session(browser, { theme, viewport: { width: w, height: 900 } });
    await open(page, PROF, '[data-slot="card"] table');
    out[`${w}-${theme}`] = await page.evaluate(() => {
      const card = (title) => [...document.querySelectorAll('[data-slot="card-title"]')].find((e) => e.textContent.trim() === title).closest('[data-slot="card"]');
      const t = card("Matter Profitability").querySelector("table");
      const sc = t.closest('[data-slot="table-container"]'); const scb = sc.getBoundingClientRect(); const sr = scb.right;
      const cols = [...t.querySelectorAll("thead th")].map((th) => { const b = th.getBoundingClientRect(); return { col: th.textContent.trim(), w: Math.round(b.width), right: Math.round(b.right), visiblePx: Math.max(0, Math.round(Math.min(b.right, sr) - b.left)), fullyVisible: b.right <= sr + 0.5 }; });
      // every Margin value span (col 7): text box vs container right edge
      const marginSpans = [...t.querySelectorAll("tbody tr td:nth-child(7) span")];
      const marginValues = marginSpans.map((s) => { const b = s.getBoundingClientRect(); return { text: s.textContent.trim(), right: Math.round(b.right), clipped: b.right > sr + 0.5 }; });
      const firstMargin = marginSpans[0]; const mb = firstMargin.getBoundingClientRect();
      const fontOf = (e) => ({ cls: e.className, font: getComputedStyle(e).fontFamily.slice(0, 70), size: getComputedStyle(e).fontSize, numeric: getComputedStyle(e).fontVariantNumeric });
      const r1 = t.querySelector("tbody tr");
      const matterFonts = { Revenue: fontOf(r1.querySelector("td:nth-child(5) span")), Cost: fontOf(r1.querySelector("td:nth-child(6) span")), Margin: fontOf(r1.querySelector("td:nth-child(7) span")) };
      const monoAnywhere = [...t.querySelectorAll("tbody td, tbody td *")].filter((e) => /mono/i.test(getComputedStyle(e).fontFamily.split(",")[0])).length;
      const ct = card("Client Profitability").querySelector("table");
      const clientMoney = ct ? [...ct.querySelectorAll("tbody tr:first-child td")].filter((td) => /^-?R\s?/.test(td.textContent.trim())).map((td) => { const leaf = td.querySelector("span") || td; return { text: td.textContent.trim(), font: getComputedStyle(leaf).fontFamily.slice(0, 70), size: getComputedStyle(leaf).fontSize }; }) : null;
      const tb = t.getBoundingClientRect();
      return { htmlClass: document.documentElement.className, tableWidth: Math.round(tb.width), tableRight: Math.round(tb.right), containerRight: Math.round(sr), scrollWidth: sc.scrollWidth, clientWidth: sc.clientWidth, overflowPx: sc.scrollWidth - sc.clientWidth, mainWidth: Math.round(document.querySelector("#main-content").getBoundingClientRect().width), cols, marginColRight: cols[6].right, marginColFullyVisible: cols[6].fullyVisible, marginValuesClipped: marginValues.filter((m) => m.clipped).length, marginValues, firstMarginValue: { text: firstMargin.textContent.trim(), visiblePx: Math.max(0, Math.round(Math.min(mb.right, sr) - mb.left)), widthPx: Math.round(mb.width) }, matterFonts, monoElementsInMatterBody: monoAnywhere, clientMoney, scrollbarGutter: sc.offsetHeight - sc.clientHeight };
    });
    if (w === 1440) { const c = page.locator('[data-slot="card"]').filter({ has: page.locator('[data-slot="card-title"]', { hasText: /^Matter Profitability$/ }) }).last(); await c.screenshot({ path: path.join(OUT, `matter-card-1440-${theme}.png`) }); }
    await ctx.close();
  }
  fs.writeFileSync(path.join(OUT, "matter-clip-raw.json"), JSON.stringify(out)); await browser.close(); console.log(JSON.stringify(out));
})();
