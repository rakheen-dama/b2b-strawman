// 665B OBS step 5: merge the raw run files in $RAW (persona-*.json from 01, roles-*.json from 02,
// audit.json from 03, audit-extra.json from 03b, dataprot.json from 04) into ../measurements.json,
// ../sel-compat.json, ../console.json, ../audit-paging.json, ../saves.json (single-line JSON) and
// build the contact sheets with the shared 661B sheet() helper.
// EVIDENCE-BUDGET: one sheet per tab × persona (cols 1024 light | 1024 dark | 1440 light | 1440 dark,
// full page; the two custom-role personas share one sheet per tab, holder row 1, lacker row 2), one
// sheet for the audit-trail behaviour, one for the data-protection / data-requests interactions, one
// for the Team role assignments; separate PNGs only for measured crops / defects (pending state,
// zero results, out-of-range page, retention "delete" shown as Anonymize).
// Usage: PHASE=before STACK="<provenance>" node 05-assemble.cjs
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
  const audit = rd("audit.json");
  const auditX = rd("audit-extra.json");
  const dp = rd("dataprot.json");
  const now = new Date().toISOString();

  const meas = { capturedAt: now, stack: STACK, phase: L.PHASE, personas: {}, customRoles: {}, overflow1024: {} };
  const sel = { capturedAt: now, stack: STACK, note: "counts per selector (strict = the spec acts on it, so it must be exactly 1). tabs = 1024×768 light per 665B tab per persona; interactive = after the spec's own interaction (03-audit / 04-dataprot, Alice 1440 light)", personas: {}, interactive: { ...(audit ? audit.selCompat : {}), ...(dp ? dp.selCompat : {}) } };
  const cons = { capturedAt: now, stack: STACK, totals: { pagesOpened: 0, errors: 0, warnings: 0, hydration: 0 }, byRun: {} };
  for (const [k] of PERSONAS) {
    const r = P[k];
    if (!r) continue;
    meas.personas[k] = { capturedAt: r.capturedAt, caps: r.caps, tabs: r.tabs, variants: r.variants, legacyRoutes: r.legacyRoutes };
    sel.personas[k] = { tabs: r.selCompat, legacyRoutes: r.legacyRoutes };
    cons.byRun[`01-capture ${k}`] = { pagesOpened: r.console.pagesOpened, errors: r.console.errors, warnings: r.console.warnings, hydration: r.console.hydration, messages: r.console.messages };
    for (const x of ["pagesOpened", "errors", "warnings", "hydration"]) cons.totals[x] += r.console[x];
    for (const tab of L.TABS) for (const v of ["1024-light", "1024-dark"]) {
      const m = r.variants[tab] && r.variants[tab][v];
      if (m && m.tables.length) (meas.overflow1024[`${k} ${tab}`] = meas.overflow1024[`${k} ${tab}`] || {})[v] = m.tables.map((t) => ({ headers: t.headers.join("|"), table: t.tableWidth, visible: t.scroller && t.scroller.clientWidth, scroll: t.scroller && t.scroller.scrollWidth, overflowPx: t.scroller && t.scroller.overflowPx, pageHOverflow: m.hOverflow }));
    }
  }
  for (const [k, r] of Object.entries(roles)) {
    meas.customRoles[k] = { at: r.at, cmd: r.cmd, arg: r.arg, steps: r.steps };
    cons.byRun[`02-roles ${k}`] = { messages: r.console };
  }
  if (audit) cons.byRun["03-audit"] = { messages: audit.console };
  if (auditX) cons.byRun["03b-audit-extra"] = { messages: auditX.console, failedResponses: auditX.failedResponses };
  if (dp) cons.byRun["04-dataprot"] = { messages: dp.console };
  const allMsgs = Object.values(cons.byRun).flatMap((x) => x.messages || []);
  cons.totals.allMessages = allMsgs.length;
  cons.totals.allErrors = allMsgs.filter((m) => m.type === "error" || m.type === "pageerror").length;
  cons.totals.allHydration = allMsgs.filter((m) => /hydrat/i.test(m.text)).length;
  fs.writeFileSync(path.join(L.OUT, "measurements.json"), JSON.stringify(meas) + "\n");
  fs.writeFileSync(path.join(L.OUT, "sel-compat.json"), JSON.stringify(sel) + "\n");
  fs.writeFileSync(path.join(L.OUT, "console.json"), JSON.stringify(cons) + "\n");
  if (audit) {
    const { tiles, console: c, ...rest } = audit;
    const x = auditX ? { failedResponses: auditX.failedResponses, pendingCrop: auditX.pendingCrop } : null;
    fs.writeFileSync(path.join(L.OUT, "audit-paging.json"), JSON.stringify({ stack: STACK, ...rest, extra: x }) + "\n");
  }
  if (dp) {
    const { tiles, console: c, ...rest } = dp;
    fs.writeFileSync(path.join(L.OUT, "saves.json"), JSON.stringify({ stack: STACK, ...rest }) + "\n");
  }

  // ---------- sheets ----------
  const T = {};
  for (const r of [...Object.values(P), ...Object.values(roles), audit, auditX, dp].filter(Boolean)) for (const [k, f, c] of r.tiles || []) T[k] = { f, c };
  const tile = (key) => [T[key] ? T[key].c : `${key} (not shot)`, T[key] ? T[key].f : null];
  const browser = await L.chromium.launch();
  const written = [];
  const sheet = async (name, title, tiles, cols, tileW) => {
    await L.sheet(browser, path.join(L.OUT, name), title, tiles, cols, tileW);
    written.push(name);
  };
  const copy = (key, name) => { if (T[key]) { fs.copyFileSync(T[key].f, path.join(L.OUT, name)); written.push(name); } };
  const ph = L.PHASE;
  for (const tab of L.TABS) {
    for (const [k, label] of PERSONAS.slice(0, 2)) {
      if (!P[k]) continue;
      await sheet(`sheet-${tab}-${k}.png`, `665B ${ph} — Settings › Firm ?tab=${tab}, ${label}: full page, cols 1024 light | 1024 dark | 1440 light | 1440 dark`, VARIANTS.map((v) => tile(`tab__${tab}-${k}-${v}-full`)), 4, 520);
    }
    if (P["carol-cr-holder"] || P["carol-cr-lacker"]) {
      const t = [];
      for (const k of ["carol-cr-holder", "carol-cr-lacker"]) for (const v of VARIANTS) t.push(tile(`tab__${tab}-${k}-${v}-full`));
      await sheet(`sheet-${tab}-custom-role.png`, `665B ${ph} — Settings › Firm ?tab=${tab}, Carol holding a custom role: row 1 '665A OBS Oversight' (TEAM_OVERSIGHT), row 2 '665A OBS No Oversight' (no TEAM_OVERSIGHT); full page, cols 1024 light | 1024 dark | 1440 light | 1440 dark`, t, 4, 520);
    }
  }
  const ak = ["audit__page2", "audit__filtered-page2", "audit__row-expanded", "audit__preset-compliance", "audit__pending-viewport", "audit__out-of-range"].filter((k) => T[k]);
  if (ak.length) await sheet("sheet-audit-behaviour.png", `665B ${ph} — Audit trail behaviour (Alice 1440×900 light): page 2 via Next, Actor filter kept on page 2, row expanded, Compliance group preset (sentinel → 0 rows + banner), page fetch in flight (RSC held 3 s), ?page=99`, ak.map(tile), 2, 720);
  if (T["audit__pending-title"] || T["audit__pending-pager"]) await sheet("crop-audit-pending.png", `665B ${ph} — Audit trail page fetch in flight (RSC request held 3 s, ~700 ms after Next): the only cue is "Loading…" in the Events card title; table at full opacity, no aria-busy, pager text/URL unchanged, Next still enabled`, [tile("audit__pending-title"), tile("audit__pending-pager")], 1);
  copy("audit__zero-results", "crop-audit-zero-results.png");
  copy("audit__out-of-range", "crop-audit-out-of-range.png");
  const dk = Object.keys(T).filter((k) => /^(dp__|save__|dlg__)/.test(k));
  if (dk.length) await sheet("sheet-dataprot-interactions.png", `665B ${ph} — Data protection / Data requests interactions (Alice): jurisdiction listbox, one save per reversible model (Information Officer ×2, retention row), dialogs opened and cancelled (Add / Edit activity, Log DSAR at 1440 and 1024)`, dk.map(tile), 2, 720);
  copy("defect__retention-delete-shown-as-anonymize", "crop-defect-retention-delete-as-anonymize.png");
  const rk = Object.keys(T).filter((k) => /^team__/.test(k));
  if (rk.length) await sheet("sheet-roles-assign.png", `665B ${ph} — Team › Carol's member panel per assignment (Alice 1440 light, through the UI): 665A OBS Oversight → 665A OBS No Oversight → restored to Member`, rk.map(tile), 3, 640);
  await browser.close();
  console.log("written", written.length, written.join(" "));
  console.log("console totals", JSON.stringify(cons.totals));
  console.log("overflow1024", JSON.stringify(meas.overflow1024));
})();
