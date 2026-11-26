// 663C OBS-after step 4 (copy of before/scripts/04-assemble.cjs; changed: this line, RAW dir, "before" → "after" in sheet titles, K-63/K-64 titles, + the 05-capture-extra sheets at the end): assemble the raw tiles of 03-capture.cjs (.tmp/663C-after-raw/tiles.json)
// into contact sheets in ../ with the shared 661B sheet() helper. Crops are done in a blank Chromium
// page (canvas) and passed as data URLs; the raw tiles are not modified.
const path = require("path");
const fs = require("fs");
const C = require("../../../661B/walk/scripts/common.cjs");

const OUT = path.resolve(__dirname, "..");
const RAW = path.join(C.W, ".tmp/663C-after-raw");
const T = Object.fromEntries(JSON.parse(fs.readFileSync(path.join(RAW, "tiles.json"), "utf8")).map(([k, f, c]) => [k, { f: path.join(C.W, f), c }]));

(async () => {
  const browser = await C.chromium.launch();
  const cp = await browser.newPage();
  await cp.setContent("<html><body></body></html>");
  const crop = async (key, [x, y, w, h]) => {
    if (!T[key]) return null;
    return cp.evaluate(
      async ({ src, x, y, w, h }) => {
        const i = await new Promise((ok, ko) => { const im = new Image(); im.onload = () => ok(im); im.onerror = ko; im.src = src; });
        const c = document.createElement("canvas");
        c.width = Math.min(w, i.width - x);
        c.height = Math.min(h, i.height - y);
        c.getContext("2d").drawImage(i, x, y, c.width, c.height, 0, 0, c.width, c.height);
        return c.toDataURL("image/png");
      },
      { src: "data:image/png;base64," + fs.readFileSync(T[key].f).toString("base64"), x, y, w, h },
    );
  };
  const tile = (key, cap) => [T[key] ? cap || T[key].c : `${key} (not shot)`, T[key] ? T[key].f : null];
  const ctile = async (key, box, cap) => [T[key] ? cap || T[key].c : `${key} (not shot)`, await crop(key, box)];
  const out = (n) => path.join(OUT, n);

  // K-05: 1440 tiles cropped to the rail + 160 px of page (x 0..420), 390 tiles 1:1.
  const k05 = [];
  for (const who of ["alice", "carol"]) {
    k05.push(await ctile(`k05__${who}-1440-default`, [0, 0, 420, 900]));
    k05.push(await ctile(`k05__${who}-1440-allopen`, [0, 0, 420, 900]));
    k05.push(tile(`k05__${who}-390-sheet`));
    k05.push(tile(`k05__${who}-390-sheet-bottom`));
  }
  await C.sheet(browser, out("sheet-k05-sidebar-density.png"), "K-05 after — sidebar density, light: rows Alice / Carol; 1440×900 (rail crop) default + all open; 390×844 Sheet top + bottom", k05, 4);

  // K-07: rows Alice light / Alice dark / Carol light; tiles 1440×900 shown at 300 px.
  const k07 = [];
  for (const r of ["alice-light", "alice-dark", "carol-light"])
    for (const t of ["header-click-open", "header-click-closed", "header-keyboard-enter-closed", "pill-click-open", "pill-click-closed", "cmdk-from-notifications-open", "cmdk-from-notifications-closed"]) k07.push(tile(`k07__${r}-${t}`));
  await C.sheet(browser, out("sheet-k07-palette-focus.png"), "K-07 after — command palette open / after Escape (activeElement in caption): rows Alice light, Alice dark, Carol light", k07, 7, 300);

  // K-08: Alice widget element shots, 1440 / 1024 × light / dark (Carol: widget absent, no tile).
  await C.sheet(browser, out("sheet-k08-sensitive-events.png"), "K-08 after — Home sensitive-events widget, Alice (owner), normal read (the failing read could not be forced; see README)", ["1440-light", "1440-dark", "1024-light", "1024-dark"].map((v) => tile(`k08__alice-${v}`)), 2);

  // K-24: DPR-2 crops around the open menu / listbox, keyboard focus.
  await C.sheet(browser, out("sheet-k24-focused-item-icons.png"), "K-24 after — keyboard-focused menu / select items, Alice 1440 (DPR 2): rows light / dark", ["light", "dark"].flatMap((t) => ["menu-first", "menu-second", "select-first", "select-second"].map((k) => tile(`k24__alice-${t}-${k}`))), 4, 420);

  // K-26: header strip 1024×96; rows persona × page × theme; cols rail expanded / collapsed.
  const k26 = [];
  for (const [who, pk] of [["alice", "fee-note"], ["alice", "trust-transactions"], ["carol", "trust-transactions"], ["carol", "matter"]])
    for (const theme of ["light", "dark"]) for (const rail of ["expanded", "collapsed"]) k26.push(tile(`k26__${who}-${pk}-${rail}-${theme}`));
  await C.sheet(browser, out("sheet-k26-breadcrumbs-1024.png"), "K-26 after — 1024×768 header strip (top 96 px): cols rail expanded / collapsed; rows persona × page × theme", k26, 2);

  // K-62 / K-63 / K-64: 1440×900 light at 480 px.
  await C.sheet(browser, out("sheet-k62-member-clients.png"), "K-62 after — Carol (member) /clients and the FICA review view; Alice control", ["k6x__carol-clients", "k6x__carol-clients-fica-review-due", "k6x__alice-clients-fica-review-due"].map((k) => tile(k)), 3, 480);
  await C.sheet(browser, out("sheet-k63-member-404s.png"), "K-63 after — Carol (member) on /invoices + 4 deep links (HTTP 404; state in each caption)", ["invoices", "invoices-collections", "invoices-unbilled", "compliance", "invoice-detail"].map((k) => tile(`k6x__carol-${k}`)), 3, 480);
  await C.sheet(browser, out("sheet-k64-member-report-runner.png"), "K-64 after — Carol (member) on /reports/invoice-aging and /reports; Alice control", ["k6x__carol-report-runner", "k6x__carol-reports-index", "k6x__alice-report-runner"].map((k) => tile(k)), 3, 480);
  // ---- after-only: the 05-capture-extra.cjs tiles (.tmp/663C-after-raw/tiles-extra.json) ----
  const TX = Object.fromEntries(JSON.parse(fs.readFileSync(path.join(RAW, "tiles-extra.json"), "utf8")).map(([k, f, c]) => [k, { f: path.join(C.W, f), c }]));
  const xt = (key) => [TX[key] ? TX[key].c : `${key} (not shot)`, TX[key] ? TX[key].f : null];
  await C.sheet(browser, out("sheet-k05-dark-1440.png"), "K-05 after — 1440×900 dark, default groups (rail crop): Alice / Carol", ["alice", "carol"].map((w) => xt(`k05x__${w}-1440-dark-default`)), 2);
  await C.sheet(browser, out("sheet-k07-keyboard-da13.png"), "K-07 after — keyboard only (Tab to the trigger, Enter, Escape) and DA-13 (palette → page, Enter): rows Alice light / Carol light", ["alice", "carol"].flatMap((w) => ["header-tab-closed", "pill-tab-closed", "header-navigate", "pill-navigate"].map((k) => xt(`k07x__${w}-light-${k}`))), 2, 700);
  await C.sheet(browser, out("sheet-k26-long-chain-1024.png"), "K-26 after — 1024×768 /settings/documents/templates/[id]/edit (5 crumbs), Alice: cols rail expanded / collapsed; rows light / dark", ["light", "dark"].flatMap((t) => ["expanded", "collapsed"].map((r) => xt(`k26x__alice-template-edit-${r}-${t}`))), 2);
  await C.sheet(browser, out("sheet-k62-k63-extras.png"), "K-62/K-63 after — Carol dark /clients and /invoices; Alice real 404s (matter nearer not-found; fee note → org not-found); Carol \"Back to Home\"", ["carol-dark-clients", "carol-dark-invoices", "alice-light-matter-404", "alice-light-invoice-404", "carol-back-to-home"].map((k) => xt(`k6xx__${k}`)), 3, 480);
  await browser.close();
  console.log("sheets written");
})();
