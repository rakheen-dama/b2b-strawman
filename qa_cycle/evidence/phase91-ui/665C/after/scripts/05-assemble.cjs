// 665C OBS step 5: merge the raw run files in $RAW (persona-*.json from 01, roles-*.json from 02,
// data.json from 03, saves.json from 04, naming-restore.json from 04b, overflow-probe.json from 06,
// spec-run.log from the spec run) into ../measurements.json, ../sel-compat.json, ../console.json,
// ../saves.json, ../data.json (single-line JSON) and build the contact sheets with the shared 661B
// sheet() helper.
// EVIDENCE-BUDGET: Alice — one sheet per tab / wizard / sub-page (cols 1024 light | 1024 dark | 1440
// light | 1440 dark, full page; checklists/[id] and request-templates/[id] put custom + pack in two
// rows, templates/[id] + templates/new likewise). Carol — one sheet for her two visible tabs (4
// variants), one for the forbidden deep links, one for the sub-pages (1024 light). Each custom role —
// one sheet. One sheet each for the data created, the saves, the role create/assign steps. Separate
// PNGs only for defects (digit-first checklist name, Templates / Checklists document overflow at 1024).
// Usage: PHASE=before STACK="<provenance>" node 05-assemble.cjs
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const STACK = process.env.STACK || "(STACK not given)";
const rd = (f) => (fs.existsSync(path.join(L.RAW, f)) ? JSON.parse(fs.readFileSync(path.join(L.RAW, f), "utf8")) : null);
const PERSONAS = [
  ["alice", "Alice (owner)"],
  ["carol", "Carol (member)"],
  ["carol-cr-holder", "Carol with custom role '665C OBS Take-on' (MANAGE_TAKE_ON only)"],
  ["carol-cr-lacker", "Carol with custom role '665C OBS No Take-on' (PROJECT_MANAGEMENT only, no MANAGE_TAKE_ON)"],
];
const VARIANTS = ["1024-light", "1024-dark", "1440-light", "1440-dark"];
const VCOLS = "cols 1024 light | 1024 dark | 1440 light | 1440 dark, full page";

(async () => {
  const P = {};
  for (const [k] of PERSONAS) P[k] = rd(`persona-${k}.json`);
  const roleFiles = fs.readdirSync(L.RAW).filter((f) => /^roles-.*\.json$/.test(f)).sort();
  const roles = Object.fromEntries(roleFiles.map((f) => [f.replace(/^roles-|\.json$/g, ""), rd(f)]));
  const data = rd("data.json");
  const saves = rd("saves.json");
  const nr = rd("naming-restore.json");
  const ov = rd("overflow-probe.json");
  const specLog = fs.existsSync(path.join(L.RAW, "spec-run.log")) ? fs.readFileSync(path.join(L.RAW, "spec-run.log"), "utf8") : "";
  const now = new Date().toISOString();

  // ---------- JSON ----------
  const meas = { capturedAt: now, stack: STACK, phase: L.PHASE, note: "per persona: caps, targets, surfaces (full probe at 1440 light), variants (compact probe per 1024/1440 × light/dark), legacyRoutes; at1024 = the per-tab summary the brief asks for (Alice 1024 light unless noted)", at1024: {}, overflowProbe: ov ? ov.tabs : null, personas: {}, customRoles: {} };
  const sel = { capturedAt: now, stack: STACK, note: "counts per selector at 1024×768 light per surface per persona (strict = the spec acts on it, so it must be exactly 1)", personas: {}, specRunBefore: null };
  const cons = { capturedAt: now, stack: STACK, totals: { pagesOpened: 0, errors: 0, warnings: 0, hydration: 0 }, byRun: {} };
  for (const [k] of PERSONAS) {
    const r = P[k];
    if (!r) continue;
    meas.personas[k] = { capturedAt: r.capturedAt, caps: r.caps, targets: r.targets, surfaces: r.surfaces, variants: r.variants, legacyRoutes: r.legacyRoutes };
    sel.personas[k] = { surfaces: r.selCompat, legacyRoutes: r.legacyRoutes };
    cons.byRun[`01-capture ${k}`] = { pagesOpened: r.console.pagesOpened, errors: r.console.errors, warnings: r.console.warnings, hydration: r.console.hydration, messages: r.console.messages };
    for (const x of ["pagesOpened", "errors", "warnings", "hydration"]) cons.totals[x] += r.console[x];
    for (const [sk, vv] of Object.entries(r.variants)) {
      const m = vv["1024-light"];
      if (!m) continue;
      (meas.at1024[sk] = meas.at1024[sk] || {})[k] = { url: m.url, selectedTab: m.selectedTab, notice: m.notice, tabs: m.tabs.join("|"), h1: m.h1, h1InMain: m.h1Count, mainIdCount: m.mainCount, mainElCount: m.mainElCount, mainWidth: m.mainBox && m.mainBox.width, contentWidth: m.contentBox && m.contentBox.width, tabStrip: m.tabStrip, docHOverflowPx: m.hOverflow, tables: m.tables.map((t) => ({ headers: t.headers.join("|"), rows: t.bodyRows, dataSlot: t.dataSlot, visible: t.scroller && t.scroller.clientWidth, scroll: t.scroller && t.scroller.scrollWidth, overflowPx: t.scroller && t.scroller.overflowPx })), emptyStates: m.emptyStates };
    }
  }
  for (const [k, r] of Object.entries(roles)) {
    meas.customRoles[k] = { at: r.at, cmd: r.cmd, arg: r.arg, steps: r.steps };
    cons.byRun[`02-roles ${k}`] = { messages: r.console };
  }
  if (data) cons.byRun["03-data"] = { messages: data.console };
  if (saves) cons.byRun["04-saves"] = { messages: saves.console };
  if (nr) cons.byRun["04b-naming-restore"] = { messages: nr.console };
  const allMsgs = Object.values(cons.byRun).flatMap((x) => x.messages || []);
  cons.totals.allMessages = allMsgs.length;
  cons.totals.allErrors = allMsgs.filter((m) => m.type === "error" || m.type === "pageerror").length;
  cons.totals.allHydration = allMsgs.filter((m) => /hydrat/i.test(m.text)).length;
  const specLines = specLog.split("\n").filter((l) => /✓|✘|passed|failed|skipped|flaky|Running/.test(l)).map((l) => l.trim());
  sel.specRunBefore = { command: "cd frontend && E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 MAILPIT_URL=http://localhost:8026 pnpm test:e2e e2e/tests/settings/custom-fields.spec.ts e2e/tests/settings/tags.spec.ts --reporter=list (main checkout @ f926ebf22, nvm node 20)", lines: specLines };
  fs.writeFileSync(path.join(L.OUT, "measurements.json"), JSON.stringify(meas) + "\n");
  fs.writeFileSync(path.join(L.OUT, "sel-compat.json"), JSON.stringify(sel) + "\n");
  fs.writeFileSync(path.join(L.OUT, "console.json"), JSON.stringify(cons) + "\n");
  if (saves) {
    const { tiles, console: c, ...rest } = saves;
    const x = nr ? { steps: nr.steps } : null;
    fs.writeFileSync(path.join(L.OUT, "saves.json"), JSON.stringify({ stack: STACK, ...rest, namingRestore04b: x }) + "\n");
  }
  if (data) {
    const brief = (v) => (v && v.id ? { id: v.id, name: v.name, source: v.source, status: v.status, dataset: v.dataset, totalRows: v.totalRows, validRows: v.validRows, errorRows: v.errorRows } : v);
    const t = Object.fromEntries(Object.entries(data.targets || {}).map(([k, v]) => [k, brief(v)]));
    fs.writeFileSync(path.join(L.OUT, "data.json"), JSON.stringify({ stack: STACK, at: data.at, steps: data.steps, targets: t }) + "\n");
  }

  // ---------- sheets ----------
  const T = {};
  for (const r of [...Object.values(P), ...Object.values(roles), data, saves, nr, ov].filter(Boolean)) for (const [k, f, c] of r.tiles || []) T[k] = { f, c };
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
  if (P.alice) {
    for (const sk of [...L.TABS, "take-on-batch"]) {
      const what = sk === "take-on-batch" ? `?tab=take-on&batch={id} (the mid-wizard batch, Validation step)` : `?tab=${sk}`;
      await sheet(`sheet-${sk}-alice.png`, `665C ${ph} — Settings › Matters ${what}, Alice (owner): ${VCOLS}`, vt(sk, "alice"), 4, 520);
    }
    const pairs = [
      ["templates-detail", "templates/{id} (row 1, seeded 'Collections (Debt Recovery)') and templates/new (row 2)", ["templates-id", "templates-new"]],
      ["checklists-new", "checklists/new", ["checklists-new"]],
      ["checklists-detail", "checklists/{id}: row 1 ORG_CUSTOM 'OBS 665C Checklist', row 2 PLATFORM 'Generic Client Onboarding'", ["checklists-id-custom", "checklists-id-pack"]],
      ["checklists-edit", "checklists/{id}/edit (ORG_CUSTOM 'OBS 665C Checklist')", ["checklists-edit-custom"]],
      ["request-templates-new", "request-templates/new", ["request-templates-new"]],
      ["request-templates-detail", "request-templates/{id}: row 1 CUSTOM '665C OBS Request Template', row 2 PLATFORM 'Liquidation and Distribution Account Pack'", ["request-templates-id-custom", "request-templates-id-pack"]],
    ];
    for (const [name, what, keys] of pairs) await sheet(`sheet-sub-${name}-alice.png`, `665C ${ph} — Settings › Matters › ${what}, Alice (owner): ${VCOLS}`, keys.flatMap((k) => vt(k, "alice")), 4, 520);
  }
  if (P.carol) {
    await sheet("sheet-tabs-visible-carol.png", `665C ${ph} — Carol (member): her two Matters tabs, row 1 ?tab=templates, row 2 ?tab=tags; ${VCOLS}`, [...vt("templates", "carol"), ...vt("tags", "carol")], 4, 520);
    await sheet("sheet-tabs-forbidden-carol.png", `665C ${ph} — Carol (member): forbidden deep links. Row 1 ?tab=take-on in ${VCOLS}; then 1024 light for ?tab=naming, custom-fields, checklists, request-templates, request-settings and ?tab=take-on&batch={id} — each falls back to Templates with the notice`, [...vt("take-on", "carol"), ...["naming", "custom-fields", "checklists", "request-templates", "request-settings", "take-on-batch"].map((s) => tile(`s__${s}-carol-1024-light`))], 4, 520);
    await sheet("sheet-sub-pages-carol.png", `665C ${ph} — Carol (member): the sub-page URLs at 1024×768 light (templates/{id} and /new redirect to ?tab=templates; checklists/{id}/edit redirects to checklists/{id}; request-templates/* show the non-admin text)`, ["templates-id", "templates-new", "checklists-new", "checklists-id-custom", "checklists-id-pack", "checklists-edit-custom", "request-templates-new", "request-templates-id-custom", "request-templates-id-pack"].map((s) => tile(`s__${s}-carol-1024-light`)), 3, 520);
  }
  for (const [k, label] of PERSONAS.slice(2)) {
    if (!P[k]) continue;
    await sheet(`sheet-custom-role-${k === "carol-cr-holder" ? "take-on" : "no-take-on"}.png`, `665C ${ph} — ${label}. Row 1 ?tab=take-on, row 2 ?tab=take-on&batch={id} (Alice's clients batch), ${VCOLS}; row 3 at 1024 light: ?tab=templates, ?tab=tags, ?tab=naming (forbidden)`, [...vt("take-on", k), ...vt("take-on-batch", k), ...["templates", "tags", "naming"].map((s) => tile(`s__${s}-${k}-1024-light`))], 4, 520);
  }
  const dk = ["data__checklist-new-filled", "data__take-on-upload-filled", "data__take-on-mapping", "data__take-on-validated"].filter((k) => T[k]);
  if (dk.length) await sheet("sheet-data-created.png", `665C ${ph} — data created through the UI as Alice (1440 light): checklists/new filled, Take-on › Clients upload filled, the wizard at Mapping right after Upload, the wizard at Validation after the dry run (left there for OBS-after)`, dk.map(tile), 2, 720);
  const sk = Object.keys(T).filter((k) => /^save__/.test(k));
  if (sk.length) await sheet("sheet-saves.png", `665C ${ph} — one save per save model (Alice 1440 light): Naming Save Settings (change, "restore", reload after — F-665C-3), Request settings Save Settings (change, restore — F-665C-4), Tag create / delete dialogs, Field group create dialog`, sk.map(tile), 2, 720);
  const rk = Object.keys(T).filter((k) => /^team__/.test(k));
  if (rk.length) await sheet("sheet-roles.png", `665C ${ph} — custom roles through the UI (Alice 1440 light): New Role "665C OBS Take-on" and "665C OBS No Take-on", then Team › Carol's member panel: Member → 665C OBS Take-on → 665C OBS No Take-on → Member`, rk.map(tile), 3, 640);
  copy("defect__checklist-digit-name-slug-error", "crop-defect-checklist-digit-name-slug-error.png");
  copy("defect__overflow-templates-1024", "crop-defect-templates-overflow-1024.png");
  copy("defect__overflow-checklists-1024", "crop-defect-checklists-overflow-1024.png");
  await browser.close();
  console.log("written", written.length, written.join(" "));
  console.log("console totals", JSON.stringify(cons.totals));
  console.log("spec", JSON.stringify(specLines.slice(-3)));
})();
