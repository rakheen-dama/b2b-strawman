// 664A OBS-after step 5 (NEW): contact sheets from 03-verify.cjs's raw tiles
// (/tmp/664A-after-verify-raw/tiles.json) with the shared 661B sheet() helper. 1:1 tiles unless noted.
const path = require("path");
const fs = require("fs");
const C = require("../../../661B/walk/scripts/common.cjs");

const OUT = path.resolve(__dirname, "..");
const RAW = "/tmp/664A-after-verify-raw";
const T = JSON.parse(fs.readFileSync(path.join(RAW, "tiles.json"), "utf8"));
const t = (k) => T[k] || [`${k} (not shot)`, null];

(async () => {
  const browser = await C.chromium.launch();
  const written = [];
  const sheet = async (name, title, tiles, cols, tileW) => {
    await C.sheet(browser, path.join(OUT, name), title, tiles, cols, tileW);
    written.push(name);
  };
  const fams = ["matters", "documents", "fees", "connections", "firm"];

  // K-02: two stacked rows (2× scale) light / dark, then the click-through per persona.
  await sheet("sheet-k02-two-rows-390.png", "664A after — 390×844 Alice /settings/firm, top 300 px at 2×: family bar (nav, border-b) above the Firm tab strip (border-b); cols light / dark", [t("k02__alice-firm"), t("k02__alice-firm-dark")], 2, 780);
  await sheet(
    "sheet-k02-family-bar-390.png",
    "664A after — K-02 family switching through the bar at 390×844 (top 300 px). Rows Alice / Carol: load Firm, then click Matters, Documents, Fees & Trust, Connections, Firm; last row Alice dark Firm + Connections deep link",
    [...["alice", "carol"].flatMap((who) => [t(`k02__${who}-firm`), ...fams.map((f) => t(`k02__${who}-click-${f}`))]), t("k02__alice-firm-dark"), t("k02__alice-connections-dark")],
    6,
  );

  // Tab strip at 1024 (Alice): Firm / Matters / Fees, light + dark, at start and scrolled to the end.
  const strip = [];
  for (const f of ["firm", "matters", "fees"]) for (const th of ["light", "dark"]) strip.push(t(`strip__${f}-${th}-start`), t(`strip__${f}-${th}-end`));
  await sheet("sheet-strip-1024.png", "664A after — K-101 family tab strip at 1024, Alice (element shots incl. border-b): rows Firm light/dark, Matters light/dark, Fees & Trust light/dark; cols at start | scrolled to end", strip, 2);

  // K-20: labels + pixel diff of the default-state bars vs before.
  await sheet(
    "sheet-k20-grouped-tab-bar.png",
    "664A after — K-20 GroupedTabBar, Alice 1440 light: active sub-tab labelled like its group shows the group label once; then the after-vs-before pixel diff of the default (Overview) bars (0 px differ)",
    [t("k20__matter-details"), t("k20__matter-activity"), t("k20__matter-time"), t("k20__client-details"), t("k20__client-matters"), t("diff__gtb-matter-bar"), t("diff__gtb-client-bar")],
    1,
  );

  // K-03 + K-100: rail tokens/sticky 1440 light/dark, rail at 1024, Connections cards at 1024.
  await sheet(
    "sheet-rail-k03-k100.png",
    "664A after — K-03 rail at 1440 scrollY 600 light / dark; K-100 rail 192 px at 1024 (Matters › Request templates, Fees › Trust accounting); Connections › Integrations and › AI at 1024 (viewport)",
    [t("k03__1440-light"), t("k03__1440-dark"), t("k100__rail-matters-1024"), t("k100__rail-fees-1024"), t("k100__connections-xero-1024"), t("k100__connections-ai-1024")],
    2,
  );
  await browser.close();
  console.log("sheets", written.length, written.join(" "));
})();
