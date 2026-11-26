// 664A OBS-after (copied from ../../before/scripts/; only the phase/output constants changed) step 2: assemble the raw tiles of 01-capture.cjs (/tmp/664A-after-raw/tiles.json)
// into contact sheets in ../ with the shared 661B sheet() helper (EVIDENCE-BUDGET: one PNG per family ×
// persona tiling 1024/1440 × light/dark, plus 390, forbidden notice, pill strip, rail-sticky and the
// grouped-tab-bar baseline). Crops are done in a blank Chromium page (canvas) and passed as data URLs;
// the raw tiles are not modified.
const path = require("path");
const fs = require("fs");
const C = require("../../../661B/walk/scripts/common.cjs");

const OUT = path.resolve(__dirname, "..");
const RAW = "/tmp/664A-after-raw";
const T = Object.fromEntries(JSON.parse(fs.readFileSync(path.join(RAW, "tiles.json"), "utf8")).map(([k, f, c]) => [k, { f, c }]));
const FAMILIES = [["firm", "Firm"], ["matters", "Matters"], ["documents", "Documents"], ["fees", "Fees & Trust"], ["connections", "Connections"]];

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
  const written = [];
  const sheet = async (name, title, tiles, cols, tileW) => {
    await C.sheet(browser, out(name), title, tiles, cols, tileW);
    written.push(name);
  };

  // Families × persona: 2×2 — row 1024×768 light | dark, row 1440×900 light | dark (viewport shots, 1:1).
  for (const [fam, label] of FAMILIES)
    for (const who of ["alice", "carol"])
      await sheet(
        `sheet-${fam}-${who}.png`,
        `664A after — Settings › ${label} (default tab), ${who === "alice" ? "Alice (owner)" : "Carol (member)"}: rows 1024×768 / 1440×900; cols light / dark`,
        ["1024-light", "1024-dark", "1440-light", "1440-dark"].map((v) => tile(`fam__${fam}-${who}-${v}`)),
        2,
      );

  // 390×844 per persona: the five families light + Firm dark (K-02: no family switch below md).
  for (const who of ["alice", "carol"])
    await sheet(
      `sheet-390-${who}.png`,
      `664A after — 390×844, ${who === "alice" ? "Alice (owner)" : "Carol (member)"}: the five families (light) + Firm (dark). K-02: rail hidden (aside display:none), no family navigation in the a11y tree`,
      [...FAMILIES.map(([fam]) => tile(`m390__${who}-${fam}-light`)), tile(`m390__${who}-firm-dark`)],
      6,
    );

  // Forbidden notice (K-12): Carol 1440, crop x 256..1440, y 0..400 (header, strip, notice, h1).
  const forb = [];
  for (const tab of ["audit-trail", "roles"]) for (const theme of ["light", "dark"]) forb.push(await ctile(`forb__${tab}-${theme}`, [256, 0, 1184, 400]));
  await sheet("sheet-forbidden-notice.png", "664A after — K-12 forbidden-tab notice, Carol (member) 1440×900 (crop x≥256, y<400): rows ?tab=audit-trail (TEAM_OVERSIGHT) / ?tab=roles (adminOnly); cols light / dark", forb, 2);

  // Pill tab strip: Alice 1440 element shots, rows family, cols light / dark.
  const pill = [];
  for (const [fam] of FAMILIES) for (const theme of ["light", "dark"]) pill.push(tile(`pill__${fam}-${theme}`));
  await sheet("sheet-pill-tab-strip.png", "664A after — family pill tab strip (TabsList w-max), Alice 1440×900 element shots: rows Firm / Matters / Documents / Fees & Trust / Connections; cols light / dark", pill, 2);

  // Rail sticky (K-03): Alice light, Firm › General, crop x 256..656 (rail + 136 px of content), full height.
  const sticky = [];
  for (const w of [1024, 1440]) for (const s of ["before", "after"]) sticky.push(await ctile(`sticky__${w}-${s}`, [256, 0, 400, w === 1024 ? 768 : 900]));
  await sheet("sheet-rail-sticky.png", "664A after — K-03 settings rail sticky vs the floating header (top-2, 56 px → bottom 64): Alice light Firm › General, crop x 256..656; 1024 scrollY 0 / 600, 1440 scrollY 0 / 600", sticky, 4);

  // Grouped-tab-bar consumers (pixel baseline for 664A's tab-underline.ts extraction): Alice 1440 light.
  await sheet(
    "sheet-grouped-tab-bar.png",
    "664A after — GroupedTabBar consumers, Alice 1440×900 light: matter detail / client detail (viewport), then the bar element shots (the 2 px ::after underline under the selected tab)",
    [tile("gtb__matter-viewport"), tile("gtb__client-viewport"), tile("gtb__matter-bar"), tile("gtb__client-bar")],
    2,
  );

  await browser.close();
  console.log("sheets", written.length, written.join(" "));
})();
