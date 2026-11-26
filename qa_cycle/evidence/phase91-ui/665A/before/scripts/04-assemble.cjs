// 665A OBS step 4: merge the raw run files in $RAW (persona-*.json from 01, roles-*.json from 02,
// saves.json from 03) into ../measurements.json, ../sel-compat.json, ../console.json, ../saves.json
// (single-line JSON) and build the contact sheets with the shared 661B sheet() helper.
// EVIDENCE-BUDGET: one sheet per tab × persona (cols 1024 light | 1024 dark | 1440 light | 1440 dark;
// row 1 viewport, row 2 full page), the two custom-role personas share one sheet per tab, plus
// sheet-roles-ui.png (custom-role creation + assignment) and sheet-saves.png (save-model crops + FICA).
// Usage: PHASE=before STACK="<provenance>" node 04-assemble.cjs
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const STACK = process.env.STACK || "(STACK not given)";
const rd = (f) => (fs.existsSync(path.join(L.RAW, f)) ? JSON.parse(fs.readFileSync(path.join(L.RAW, f), "utf8")) : null);
const PERSONAS = [
  ["alice", "Alice (owner)"],
  ["carol", "Carol (member)"],
  ["carol-cr-holder", "Carol with custom role '665A OBS Oversight' (TEAM_OVERSIGHT)"],
  ["carol-cr-lacker", "Carol with custom role '665A OBS No Oversight' (PROJECT_MANAGEMENT, no TEAM_OVERSIGHT)"],
];
const VARIANTS = ["1024-light", "1024-dark", "1440-light", "1440-dark"];

(async () => {
  const P = {};
  for (const [k] of PERSONAS) P[k] = rd(`persona-${k}.json`);
  const roleFiles = fs.readdirSync(L.RAW).filter((f) => /^roles-.*\.json$/.test(f)).sort();
  const roles = Object.fromEntries(roleFiles.map((f) => [f.replace(/^roles-|\.json$/g, ""), rd(f)]));
  const saves = rd("saves.json");
  const now = new Date().toISOString();

  const meas = { capturedAt: now, stack: STACK, phase: L.PHASE, personas: {}, customRoles: {}, kinks: saves ? saves.kinks : null };
  const sel = { capturedAt: now, stack: STACK, note: "counts per selector, 1024×768 light, each 665A tab; legacyRoutes = rbac-capabilities.spec routes (+ the old per-page routes)", personas: {} };
  const cons = { capturedAt: now, stack: STACK, totals: { pagesOpened: 0, errors: 0, warnings: 0, hydration: 0 }, byRun: {} };
  for (const [k] of PERSONAS) {
    const r = P[k];
    if (!r) continue;
    meas.personas[k] = { capturedAt: r.capturedAt, caps: r.caps, tabs: r.tabs, variants: r.variants, capabilityReference: r.capabilityReference };
    sel.personas[k] = { tabs: r.selCompat, legacyRoutes: r.legacyRoutes };
    cons.byRun[`01-capture ${k}`] = { pagesOpened: r.console.pagesOpened, errors: r.console.errors, warnings: r.console.warnings, hydration: r.console.hydration, messages: r.console.messages };
    cons.totals.pagesOpened += r.console.pagesOpened;
    cons.totals.errors += r.console.errors;
    cons.totals.warnings += r.console.warnings;
    cons.totals.hydration += r.console.hydration;
  }
  for (const [k, r] of Object.entries(roles)) {
    meas.customRoles[k] = { at: r.at, cmd: r.cmd, arg: r.arg, steps: r.steps };
    cons.byRun[`02-roles ${k}`] = { messages: r.console };
  }
  if (saves) cons.byRun["03-saves"] = { messages: saves.console };
  const allMsgs = Object.values(cons.byRun).flatMap((x) => x.messages || []);
  cons.totals.allMessages = allMsgs.length;
  cons.totals.allErrors = allMsgs.filter((m) => m.type === "error" || m.type === "pageerror").length;
  fs.writeFileSync(path.join(L.OUT, "measurements.json"), JSON.stringify(meas) + "\n");
  fs.writeFileSync(path.join(L.OUT, "sel-compat.json"), JSON.stringify(sel) + "\n");
  fs.writeFileSync(path.join(L.OUT, "console.json"), JSON.stringify(cons) + "\n");
  if (saves) {
    const { tiles, console: c, ...rest } = saves;
    fs.writeFileSync(path.join(L.OUT, "saves.json"), JSON.stringify({ stack: STACK, ...rest }) + "\n");
  }

  // ---------- sheets ----------
  const T = {};
  for (const r of [...Object.values(P), ...Object.values(roles), saves].filter(Boolean)) for (const [k, f, c] of r.tiles || []) T[k] = { f, c };
  const tile = (key) => [T[key] ? T[key].c : `${key} (not shot)`, T[key] ? T[key].f : null];
  const browser = await L.chromium.launch();
  const written = [];
  const sheet = async (name, title, tiles, cols, tileW) => {
    await L.sheet(browser, path.join(L.OUT, name), title, tiles, cols, tileW);
    written.push(name);
  };
  const ph = L.PHASE;
  for (const tab of L.TABS) {
    for (const [k, label] of PERSONAS.slice(0, 2)) {
      if (!P[k]) continue;
      const t = [...VARIANTS.map((v) => tile(`tab__${tab}-${k}-${v}-vp`)), ...VARIANTS.map((v) => tile(`tab__${tab}-${k}-${v}-full`))];
      if (tab === "roles" && T[`tab__roles-${k}-1440-light-capref`]) t.push(tile(`tab__roles-${k}-1440-light-capref`));
      await sheet(`sheet-${tab}-${k}.png`, `665A ${ph} — Settings › Firm ?tab=${tab}, ${label}: cols 1024 light | 1024 dark | 1440 light | 1440 dark; row 1 viewport, row 2 full page`, t, 4, 520);
    }
    if (P["carol-cr-holder"] || P["carol-cr-lacker"]) {
      const t = [];
      for (const k of ["carol-cr-holder", "carol-cr-lacker"]) for (const kind of ["vp", "full"]) for (const v of VARIANTS) t.push(tile(`tab__${tab}-${k}-${v}-${kind}`));
      await sheet(`sheet-${tab}-custom-role.png`, `665A ${ph} — Settings › Firm ?tab=${tab}, Carol holding a custom role: rows 1–2 '665A OBS Oversight' (TEAM_OVERSIGHT) viewport / full, rows 3–4 '665A OBS No Oversight' (no TEAM_OVERSIGHT) viewport / full; cols 1024 light | 1024 dark | 1440 light | 1440 dark`, t, 4, 520);
    }
  }
  const rk = Object.keys(T).filter((k) => /^(roles__|team__)/.test(k));
  if (rk.length) await sheet("sheet-roles-ui.png", `665A ${ph} — custom-role persona through the UI (Alice): Create Role dialog at 1280×720 and 1440×900 (K-102), Roles tab after creation, Team › Carol's member panel per assignment (custom role → custom role → restored to Member)`, rk.map(tile), 3, 640);
  const sk = Object.keys(T).filter((k) => /^(save__|fica__)/.test(k));
  if (sk.length) await sheet("sheet-saves.png", `665A ${ph} — one save per save model (Alice 1440 light, element crops taken when the message appeared) + Phase 93 FICA review-interval fieldset light / dark`, sk.map(tile), 2);
  await browser.close();
  console.log("sheets", written.length, written.join(" "));
  console.log("console totals", JSON.stringify(cons.totals));
})();
