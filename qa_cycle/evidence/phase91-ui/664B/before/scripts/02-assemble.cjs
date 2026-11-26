// 664B step 2 (PHASE=before|after): assemble the raw tiles of 01-capture.cjs (/tmp/664B-$PHASE-raw/
// tiles.json) into contact sheets in ../../$PHASE/ with the shared 661B sheet() helper
// (EVIDENCE-BUDGET: one sheet per surface × persona). Raw tiles are not modified.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const T = Object.fromEntries(JSON.parse(fs.readFileSync(path.join(L.RAW, "tiles.json"), "utf8")).map(([k, f, c]) => [k, { f, c }]));
const tile = (key) => [T[key] ? T[key].c : `${key} (not shot)`, T[key] ? T[key].f : null];
const VPS = ["1024-light", "1024-dark", "1440-light", "1440-dark"];
const P = L.PHASE;

(async () => {
  const browser = await L.chromium.launch();
  const written = [];
  const sheet = async (name, title, tiles, cols, tileW) => {
    await L.sheet(browser, path.join(L.OUT, name), title, tiles, cols, tileW);
    written.push(name);
  };
  await sheet("sheet-other-creditors-alice.png", `664B ${P} — /trust-accounting/other-creditors, Alice (owner), full page, no params: cols 1024×768 light / dark, 1440×900 light / dark (scaled)`, VPS.map((v) => tile(`oc__alice-${v}`)), 4, 520);
  const pg = [];
  for (const k of ["p0", "p1", "p2"]) for (const s of ["fund", "items"]) pg.push(tile(`ocpager__${s}-${k}`));
  await sheet("sheet-other-creditors-pagers.png", `664B ${P} — other-creditors pagers (Alice 1440×900 light, element crops): rows no params / ?page=1&itemsPage=1 / ?page=2&itemsPage=2; cols Fund ledger / Unidentified deposits`, pg, 2);
  await sheet("sheet-matters-alice.png", `664B ${P} — /matters, Alice (owner): rows 1024×768 / 1440×900, cols light / dark; then 1024 light thead at rest / hovering an inactive sortable header, and the page after clicking it`, [...VPS.map((v) => tile(`mat__alice-${v}`)), tile("mat__alice-1024-light-thead-rest"), tile("mat__alice-1024-light-thead-hover"), tile("mat__alice-1024-light-sorted")], 2);
  await sheet("sheet-matters-carol.png", `664B ${P} — /matters, Carol (member): rows 1024×768 / 1440×900, cols light / dark`, VPS.map((v) => tile(`mat__carol-${v}`)), 2);
  await sheet("sheet-k11-clients-saved-view.png", `664B ${P} — K-11 /clients, Alice: row 1 "All clients" (default), row 2 saved view "664B K-11 view" (explicit columns); cols 1024 light / dark, 1440 light / dark (scaled)`, [...VPS.map((v) => tile(`k11__default-${v}`)), ...VPS.map((v) => tile(`k11__saved-${v}`))], 4, 720);
  const k = [];
  for (const r of ["matters", "clients"]) for (const vp of ["1024x768", "1024x900", "1440x900"]) for (const t of ["light", "dark"]) k.push(tile(`k47__${r}-${vp}-${t}`));
  await sheet("sheet-k47-save-view-dialog.png", `664B ${P} — K-47 Save View step 1 "Configure Filters", Alice: row 1 /matters, row 2 /clients; cols 1024×768 L/D, 1024×900 L/D, 1440×900 L/D (scaled)`, k, 6, 480);
  await sheet("sheet-spotcheck-tables.png", `664B ${P} — spot-check, Alice 1024×768 light: /clients (All clients, viewport) | /reports/profitability (full page)`, [tile("spot__clients"), tile("spot__profitability")], 2);
  await browser.close();
  console.log("wrote", written.join(", "));
})();
