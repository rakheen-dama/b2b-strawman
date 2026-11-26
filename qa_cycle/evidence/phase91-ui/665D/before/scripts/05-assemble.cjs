// 665D OBS step 5: merge the raw run files in $RAW (persona-*.json from 01, roles-*.json from 02,
// modules.json from 03, saves.json from 04, overflow-probe.json from 06, spec-run.log from the spec
// run) into ../measurements.json, ../sel-compat.json, ../console.json, ../saves.json (single-line
// JSON) and build the contact sheets with the shared 661B sheet() helper.
// EVIDENCE-BUDGET: Alice — one sheet per tab (cols 1024 light | 1024 dark | 1440 light | 1440 dark,
// full page). Carol — one sheet (Rates in 4 variants + the 5 forbidden deep links at 1024 light).
// Each custom role — one sheet. One sheet each for module toggles, saves, role create/assign.
// Separate PNGs only for defects (the 1024 table cuts) and the 1024 tab-strip crop.
// Usage: PHASE=before STACK="<provenance>" SPEC_CMD="<command>" node 05-assemble.cjs
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const STACK = process.env.STACK || "(STACK not given)";
const rd = (f) => (fs.existsSync(path.join(L.RAW, f)) ? JSON.parse(fs.readFileSync(path.join(L.RAW, f), "utf8")) : null);
const PERSONAS = [
  ["alice", "Alice (owner)"],
  ["carol", "Carol (member)"],
  ["carol-cr-holder", `Carol with custom role '${L.NAMES.roleHolder}' (FINANCIAL_VISIBILITY only)`],
  ["carol-cr-lacker", `Carol with custom role '${L.NAMES.roleLacker}' (INVOICING only, no FINANCIAL_VISIBILITY)`],
];
const VARIANTS = ["1024-light", "1024-dark", "1440-light", "1440-dark"];
const VCOLS = "cols 1024 light | 1024 dark | 1440 light | 1440 dark, full page";

(async () => {
  const P = {};
  for (const [k] of PERSONAS) P[k] = rd(`persona-${k}.json`);
  const roleFiles = fs.readdirSync(L.RAW).filter((f) => /^roles-.*\.json$/.test(f)).sort();
  const roles = Object.fromEntries(roleFiles.map((f) => [f.replace(/^roles-|\.json$/g, ""), rd(f)]));
  const mods = rd("modules.json");
  const saves = rd("saves.json");
  const ov = rd("overflow-probe.json");
  const specLog = fs.existsSync(path.join(L.RAW, "spec-run.log")) ? fs.readFileSync(path.join(L.RAW, "spec-run.log"), "utf8") : "";
  const now = new Date().toISOString();

  // ---------- JSON ----------
  const meas = { capturedAt: now, stack: STACK, phase: L.PHASE, note: "per persona: caps, surfaces (full probe at 1440 light), variants (compact probe per 1024/1440 × light/dark); at1024 = the per-tab summary (docHOverflowPx, tables, empty states with outerHTML sha1) per persona; overflowProbe = 06 (columns cut at 1024); modules = 03 (bulk_billing off/on)", at1024: {}, overflowProbe: ov ? ov.tabs : null, modules: mods ? { steps: mods.steps, finalApi: mods.finalApi } : null, personas: {}, customRoles: {} };
  const sel = { capturedAt: now, stack: STACK, note: "counts per selector at 1024×768 light per tab per persona (strict = the spec acts on it, so it must be exactly 1)", personas: {}, specRunBefore: null };
  const cons = { capturedAt: now, stack: STACK, totals: { pagesOpened: 0, errors: 0, warnings: 0, hydration: 0 }, byRun: {} };
  for (const [k] of PERSONAS) {
    const r = P[k];
    if (!r) continue;
    meas.personas[k] = { capturedAt: r.capturedAt, caps: r.caps, surfaces: r.surfaces, variants: r.variants };
    sel.personas[k] = r.selCompat;
    cons.byRun[`01-capture ${k}`] = { pagesOpened: r.console.pagesOpened, errors: r.console.errors, warnings: r.console.warnings, hydration: r.console.hydration, messages: r.console.messages };
    for (const x of ["pagesOpened", "errors", "warnings", "hydration"]) cons.totals[x] += r.console[x];
    for (const [sk, vv] of Object.entries(r.variants)) {
      const m = vv["1024-light"];
      if (!m) continue;
      (meas.at1024[sk] = meas.at1024[sk] || {})[k] = { url: m.url, title: m.title, selectedTab: m.selectedTab, notice: m.notice, tabs: m.tabs.join("|"), rail: m.rail && m.rail.join("|"), h1: m.h1, h1InMain: m.h1Count, mainIdCount: m.mainCount, mainElCount: m.mainElCount, mainWidth: m.mainBox && m.mainBox.width, contentWidth: m.contentBox && m.contentBox.width, tabStrip: m.tabStrip, docHOverflowPx: m.hOverflow, docHOverflowPxDark: vv["1024-dark"] ? vv["1024-dark"].hOverflow : null, tables: m.tables.map((t) => ({ headers: t.headers.join("|"), rows: t.bodyRows, dataSlot: t.dataSlot, visible: t.scroller && t.scroller.clientWidth, scroll: t.scroller && t.scroller.scrollWidth, overflowPx: t.scroller && t.scroller.overflowPx, scrollerPosition: t.scroller && t.scroller.position })), emptyStates: m.emptyStates };
    }
  }
  for (const [k, r] of Object.entries(roles)) {
    meas.customRoles[k] = { at: r.at, cmd: r.cmd, arg: r.arg, steps: r.steps };
    cons.byRun[`02-roles ${k}`] = { messages: r.console };
  }
  if (mods) cons.byRun["03-modules"] = { messages: mods.console };
  if (saves) cons.byRun["04-saves"] = { messages: saves.console };
  const allMsgs = Object.values(cons.byRun).flatMap((x) => x.messages || []);
  cons.totals.allMessages = allMsgs.length;
  cons.totals.allErrors = allMsgs.filter((m) => m.type === "error" || m.type === "pageerror").length;
  cons.totals.allHydration = allMsgs.filter((m) => /hydrat/i.test(m.text)).length;
  const specLines = specLog.split("\n").filter((l) => /✓|✘|passed|failed|skipped|flaky|Running/.test(l)).map((l) => l.trim());
  sel.specRunBefore = { command: process.env.SPEC_CMD || "(SPEC_CMD not given)", lines: specLines };
  fs.writeFileSync(path.join(L.OUT, "measurements.json"), JSON.stringify(meas) + "\n");
  fs.writeFileSync(path.join(L.OUT, "sel-compat.json"), JSON.stringify(sel) + "\n");
  fs.writeFileSync(path.join(L.OUT, "console.json"), JSON.stringify(cons) + "\n");
  if (saves) {
    const { tiles, console: c, ...rest } = saves;
    fs.writeFileSync(path.join(L.OUT, "saves.json"), JSON.stringify({ stack: STACK, ...rest }) + "\n");
  }

  // ---------- sheets ----------
  const T = {};
  for (const r of [...Object.values(P), ...Object.values(roles), mods, saves, ov].filter(Boolean)) for (const [k, f, c] of r.tiles || []) T[k] = { f, c };
  const tile = (key) => [T[key] ? T[key].c : `${key} (not shot)`, T[key] ? T[key].f : null];
  const vt = (sk, k) => VARIANTS.map((v) => tile(`s__${sk}-${k}-${v}`));
  const browser = await L.chromium.launch();
  const written = [];
  const sheet = async (name, title, tiles, cols, tileW) => {
    await L.sheet(browser, path.join(L.OUT, name), title, tiles, cols, tileW);
    written.push(name);
  };
  const copy = (key, name) => { if (T[key]) { fs.copyFileSync(T[key].f, path.join(L.OUT, name)); written.push(name); } };
  const ph = L.PHASE;
  if (P.alice) for (const sk of L.TABS) await sheet(`sheet-${sk}-alice.png`, `665D ${ph} — Settings › Fees & Trust ?tab=${sk} (${L.LABELS[sk]}), Alice (owner): ${VCOLS}`, vt(sk, "alice"), 4, 520);
  if (P.carol) await sheet("sheet-carol.png", `665D ${ph} — Carol (member). Row 1: ?tab=tariffs (Rates; in-page FINANCIAL_VISIBILITY denial) in ${VCOLS}. Rows 2-3 at 1024 light: the forbidden deep links ?tab=tax, time-recording, fee-runs, collections, trust-accounting — each falls back to Rates with the notice`, [...vt("tariffs", "carol"), ...["tax", "time-recording", "fee-runs", "collections", "trust-accounting"].map((s) => tile(`s__${s}-carol-1024-light`))], 4, 520);
  for (const [k, label] of PERSONAS.slice(2)) {
    if (!P[k]) continue;
    await sheet(`sheet-custom-role-${k === "carol-cr-holder" ? "financial" : "no-financial"}.png`, `665D ${ph} — ${label}. Row 1: ?tab=tariffs in ${VCOLS}. Row 2 at 1024 light: ?tab=tax and ?tab=trust-accounting (forbidden → Rates + notice)`, [...vt("tariffs", k), ...["tax", "trust-accounting"].map((s) => tile(`s__${s}-${k}-1024-light`))], 4, 520);
  }
  const mk = Object.keys(T).filter((k) => /^mod__/.test(k));
  if (mk.length) await sheet("sheet-modules.png", `665D ${ph} — module gates (Alice, light): Features before (no trust_accounting switch); Bulk Billing Runs switched OFF; /settings/fees and ?tab=fee-runs at 1440 and 1024 with bulk_billing OFF (no Fee runs tab / rail entry; deep link → Rates + notice); Features after restore (ON)`, mk.map(tile), 2, 720);
  const sk = Object.keys(T).filter((k) => /^save__/.test(k));
  if (sk.length) await sheet("sheet-saves.png", `665D ${ph} — one save per save model (Alice 1440 light): Rates currency re-pick, billing rate add / edit / delete dialogs, Tax Configuration Save Settings, tax rate create / deactivate, Time recording / Fee runs / Collections Save Settings`, sk.map(tile), 2, 720);
  const rk = Object.keys(T).filter((k) => /^team__/.test(k));
  if (rk.length) await sheet("sheet-roles.png", `665D ${ph} — custom roles through the UI (Alice 1440 light): New Role "${L.NAMES.roleHolder}" and "${L.NAMES.roleLacker}", then Team › Carol's member panel: Member → ${L.NAMES.roleHolder} → ${L.NAMES.roleLacker} → Member`, rk.map(tile), 3, 640);
  copy("defect__rates-billing-table-1024", "crop-defect-rates-billing-table-1024.png");
  copy("defect__tax-rates-table-1024", "crop-defect-tax-rates-table-1024.png");
  copy("tabstrip__fees-1024", "crop-tabstrip-fees-1024.png");
  await browser.close();
  console.log("written", written.length, written.join(" "));
  console.log("console totals", JSON.stringify(cons.totals));
  console.log("spec", JSON.stringify(specLines.slice(-2)));
})();
