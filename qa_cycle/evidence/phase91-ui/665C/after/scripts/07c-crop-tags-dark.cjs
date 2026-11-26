// 665C OBS-after step 7c (after-only, no app traffic): a crop of 07's raw tile
// $RAW/tags-rows-1440-dark.png (Tags with the two UI-created tags, Alice 1440×900 dark) around the table,
// for the dark Delete-icon finding (F-665C-10): the trash icons render slate-400 like Edit, not red.
// Writes ../crop-defect-tags-delete-grey-dark.png.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");
(async () => {
  const src = path.join(L.RAW, "tags-rows-1440-dark.png");
  const browser = await L.chromium.launch();
  const p = await browser.newPage({ viewport: { width: 1440, height: 940 }, deviceScaleFactor: 1 });
  await p.setContent(`<html><body style="margin:0"><img src="data:image/png;base64,${fs.readFileSync(src).toString("base64")}"></body></html>`, { waitUntil: "load" });
  await p.screenshot({ path: path.join(L.OUT, "crop-defect-tags-delete-grey-dark.png"), clip: { x: 544, y: 250, width: 872, height: 188 } });
  await browser.close();
  console.log("ok");
})();
