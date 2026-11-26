// 665C OBS step 6: why Templates and Checklists overflow the document at 1024×768 (01-capture measured
// scrollWidth 1071 / 1141 vs 1024) while their tables sit in `overflow-x-auto` scrollers.
// Read-only, Alice, 1024×768 light. For each tab: document scrollWidth; every position:absolute
// element whose right edge is past the viewport (the action buttons' `sr-only` names); the scroller's
// `position`; then — in this page only, nothing saved — hide the table's `.sr-only` spans and
// re-measure. Plus a viewport crop of the clipped right edge for the defect PNG.
// Writes $RAW/overflow-probe.json + tiles; 05-assemble merges them.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const out = { at: new Date().toISOString(), tabs: {} };
const tiles = [];
(async () => {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  for (const theme of ["light"]) {
    const ctx = await L.newCtx(browser, S, { width: 1024, height: 768, theme, extra: L.EXTRA });
    const p = await ctx.newPage();
    for (const t of ["templates", "checklists", "request-templates", "tags"]) {
      await p.goto(`${L.BASE}/settings/matters?tab=${t}`, { waitUntil: "load" });
      await p.waitForLoadState("networkidle").catch(() => {});
      await p.waitForTimeout(400);
      if (t === "templates" || t === "checklists") {
        const f = path.join(L.RAW, `defect__overflow-${t}-1024.png`);
        await p.evaluate(() => window.scrollTo(0, 0));
        await p.screenshot({ path: f, clip: { x: 480, y: 0, width: 544, height: 768 } });
        tiles.push([`defect__overflow-${t}-1024`, f, `Alice 1024×768 light — ?tab=${t}: right half of the viewport; the table's Actions column is cut at the canvas edge and the document scrolls sideways`]);
      }
      out.tabs[t] = await p.evaluate(() => {
        const W = document.documentElement.clientWidth;
        const se = document.scrollingElement;
        const before = se.scrollWidth;
        const abs = [...document.querySelectorAll("#main-content *")].filter((e) => getComputedStyle(e).position === "absolute" && e.getBoundingClientRect().right > W + 0.5);
        const table = document.querySelector("#main-content table");
        let sc = table && table.parentElement;
        while (sc && sc !== document.body && !/(auto|scroll)/.test(getComputedStyle(sc).overflowX)) sc = sc.parentElement;
        const r = {
          viewport: W,
          docScrollWidth: before,
          docOverflowPx: before - W,
          absolutePastViewport: abs.length,
          examples: abs.slice(0, 4).map((e) => `${e.tagName.toLowerCase()}.${e.className} right=${Math.round(e.getBoundingClientRect().right)} "${e.textContent.trim().slice(0, 40)}"`),
          scroller: sc && sc !== document.body ? { cls: sc.className.toString().slice(0, 90), position: getComputedStyle(sc).position, clientWidth: sc.clientWidth, scrollWidth: sc.scrollWidth } : null,
        };
        document.querySelectorAll("#main-content table .sr-only").forEach((e) => (e.style.display = "none"));
        r.docScrollWidthWithTableSrOnlyHidden = se.scrollWidth;
        return r;
      });
      console.log(t, JSON.stringify(out.tabs[t]));
    }
    await ctx.close();
  }
  await browser.close();
  out.tiles = tiles;
  fs.writeFileSync(path.join(L.RAW, "overflow-probe.json"), JSON.stringify(out));
})();
