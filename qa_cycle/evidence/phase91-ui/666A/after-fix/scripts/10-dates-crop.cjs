// 666A OBS after-fix step 10 (NEW): re-shoot the fee-notes table crop at 1024. 09's element screenshot
// of the 1081 px table container scrolled the page, so the sticky top bar was painted over the table's
// header row (capture artefact, not a product defect). Here the crop is a clip of a single full-page
// capture (no scrolling), so the header row is visible. Read-only (page loads only), Alice.
// Overwrites ../crop-fixed-dates-fee-notes-1024-{light,dark}.png and rebuilds ../sheet-crops-1024.png
// with all six 1024 crops (A1, A2 from 09 unchanged + these two). Writes nothing else.
// Usage: PHASE=after-fix node 10-dates-crop.cjs
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

(async () => {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const caps = [];
  for (const th of ["light", "dark"]) {
    const ctx = await L.newCtx(browser, S, { width: 1024, height: 768, theme: th, extra: L.EXTRA });
    const p = await ctx.newPage();
    await p.goto(`${L.BASE}/invoices`, { waitUntil: "load" });
    await p.waitForLoadState("networkidle").catch(() => {});
    await p.waitForTimeout(700);
    const box = await p.evaluate(() => { const e = document.querySelector('#main-content [data-slot="table-container"]'); const r = e.getBoundingClientRect(); return { x: r.left + scrollX, y: r.top + scrollY, width: r.width, height: r.height, sc: `${e.scrollWidth}/${e.clientWidth}`, scrollY }; });
    const f = path.join(L.OUT, `crop-fixed-dates-fee-notes-1024-${th}.png`);
    await p.screenshot({ path: f, fullPage: true, clip: { x: box.x, y: box.y, width: box.width, height: box.height } });
    caps.push([th, box, f]);
    console.log(th, JSON.stringify(box));
    await ctx.close();
  }
  const O = (n) => path.join(L.OUT, n);
  const tiles = [
    ["crop-fixed-A1-runs-list-1024-light.png — /invoices/billing-runs 1024 light: 710/710; Name 250px 2 lines, Period hidden; row 57", O("crop-fixed-A1-runs-list-1024-light.png")],
    ["crop-fixed-A1-runs-list-1024-dark.png — dark: 718/718; Name 258px 2 lines; row 57", O("crop-fixed-A1-runs-list-1024-dark.png")],
    ["crop-fixed-A2-run-items-1024-light.png — run Items 1024 light: 710/710; Time + Disbursement hidden; Failure Reason 550–710 in view; rows 57/39", O("crop-fixed-A2-run-items-1024-light.png")],
    ["crop-fixed-A2-run-items-1024-dark.png — dark: 718/718; Failure Reason 558–718 in view", O("crop-fixed-A2-run-items-1024-dark.png")],
    ...caps.map(([th, b, f]) => [`${path.basename(f)} — /invoices 1024 ${th}: ${b.sc}; Issue + Due Date visible, Currency hidden (one full-page capture, clipped)`, f]),
  ];
  await L.sheet(browser, O("sheet-crops-1024.png"), "666A OBS after-fix @97d84aeed — table containers at 1024×768 (A1 runs list, A2 run items, fee-notes dates) light | dark", tiles, 2, 720);
  await browser.close();
})();
