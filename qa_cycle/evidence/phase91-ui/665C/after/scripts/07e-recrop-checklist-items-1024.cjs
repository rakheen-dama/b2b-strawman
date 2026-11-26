// 665C OBS-after step 7e (after-only, no app traffic): crops of 01-capture's raw tiles of
// checklists/{id} for the PLATFORM "Generic Client Onboarding" (Alice, light) for F-665C-12: the
// Checklist Items DataTable cells no longer wrap, so the table is 1009 px wide; at 1440 the REQUIRED
// badge is cut at the scroller edge ("Requir") and DOCUMENT REQUIRED is off-screen; at 1024 only
// #, NAME and a truncated DESCRIPTION are visible. Before: 814/814 at 1440 and 446/452 at 1024.
// 07d's 1024 clip (y 560) missed the table top; this re-crops 1024 only (y 380) and overwrites
// ../crop-defect-checklist-items-1024-light.png.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");
(async () => {
  const browser = await L.chromium.launch();
  for (const [v, w, clip] of [["1024", 1024, { x: 240, y: 380, width: 784, height: 420 }]]) {
    const src = path.join(L.RAW, `s__checklists-id-pack-alice-${v}-light.png`);
    const p = await browser.newPage({ viewport: { width: w, height: 1300 }, deviceScaleFactor: 1 });
    await p.setContent(`<html><body style="margin:0"><img src="data:image/png;base64,${fs.readFileSync(src).toString("base64")}"></body></html>`, { waitUntil: "load" });
    await p.screenshot({ path: path.join(L.OUT, `crop-defect-checklist-items-${v}-light.png`), clip });
    await p.close();
  }
  await browser.close();
  console.log("ok");
})();
