// 666A OBS-after step 5b: 05-assemble.cjs (copied from before/, executed once in after/ and failed
// at the tiles line: the kit StatCard value carries no text-<colour>-<n> class, so .match() is null;
// 05 is not edited) copied with four changes: (1) that .match() is null-safe; (2) OBS-after has no
// `pre` / `preview` state (the OBS run is COMPLETED and the data from OBS-before is still there), so
// the Alice sheets keep only their post rows; (3) the billing-runs / detail sheet captions say so; (4) no sheet-wizard-alice.png (the wizard is
// in 07's sheet-wizard-walk.png), no specs-baseline.json (no spec run in OBS-after), setup caption.
// 666A OBS step 5: merge the raw run files in $RAW (persona-*-<state>.json from 01 / 01b, time.json
// from 02-time, roles-*.json from 02-roles, fee-run.json from 03b, module-off.json from 04,
// crops.json from 08, spec-run.log from the spec run) into ../measurements.json, ../sel-compat.json,
// ../console.json, ../specs-baseline.json (single-line JSON) and build the contact sheets with the
// shared 661B sheet() helper. Adapted from 665F/before/scripts/05-assemble.cjs (not edited).
// EVIDENCE-BUDGET: one sheet per surface family for Alice (rows = data states, cols 1024 light |
// 1024 dark | 1440 light | 1440 dark, full page); one sheet per other persona (one row per surface);
// one for the wizard (one row per step), one for the status-filter clicks, one for the module-off
// branch, one for the UI setup (time entries + role). Separate PNGs only for defects (crops).
// Usage: PHASE=before STACK="<provenance>" SPEC_CMD="<command>" SPEC_CWD="<dir>" node 05-assemble.cjs
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const STACK = process.env.STACK || "(STACK not given)";
const rd = (f) => (fs.existsSync(path.join(L.RAW, f)) ? JSON.parse(fs.readFileSync(path.join(L.RAW, f), "utf8")) : null);
const RUNS = [["alice", "pre"], ["alice", "preview"], ["alice", "post"], ["carol-cr-invoicing", "post"], ["carol", "post"]];
const VARIANTS = ["1024-light", "1024-dark", "1440-light", "1440-dark"];
const VCOLS = "cols 1024 light | 1024 dark | 1440 light | 1440 dark, full page";

(async () => {
  const P = {};
  for (const [k, s] of RUNS) P[`${k}-${s}`] = rd(`persona-${k}-${s}.json`);
  const time = rd("time.json"), fee = rd("fee-run.json"), mod = rd("module-off.json"), crops = rd("crops.json");
  const roleRuns = fs.readdirSync(L.RAW).filter((f) => /^roles-.*\.json$/.test(f)).sort().map((f) => ({ file: f, ...rd(f) }));
  const specLog = fs.existsSync(path.join(L.RAW, "spec-run.log")) ? fs.readFileSync(path.join(L.RAW, "spec-run.log"), "utf8").replace(/\u001b\[[0-9;]*[A-Za-z]/g, "") : "";
  const now = new Date().toISOString();

  // ---------- JSON ----------
  const meas = { capturedAt: now, stack: STACK, phase: L.PHASE, note: "runs = <persona>-<state> (state pre: before the OBS fee run; preview: the run in PREVIEW; post: run COMPLETED + 2 fee notes APPROVED). Per run: caps, surfaces (full probe at 1440 light), variants (compact probe per 1024/1440 × light/dark incl. `fee`: main inner width, Fee notes|Fee runs links, count pill, tiles + tile grid, run stat cards + grid, filter chips, tables with scroller vs table width, row heights, first-cell line counts, links, wizard step badges, empties), filters (status-filter clicks + URL-only states). at1024 / at1440 = per-surface summary per run. tileContainer = the tile grid's measured width per run/viewport. feeRun = 03b; moduleOff = 04; time = 02-time; customRoles = 02-roles; crops = 08.", at1024: {}, at1440: {}, tileContainer: {}, runs: {}, feeRun: fee ? { runId: fee.runId, steps: fee.steps.map((s) => ({ step: s.step, caption: s.caption, at: s.at, variants: s.variants, full: s.full })), validation: fee.validation, sheet: fee.sheet, confirm: fee.confirm, approveError: fee.approveError || null, api: fee.api } : null, moduleOff: mod ? { initial: mod.initial, toggles: mod.toggles, final: mod.final, surfaces: mod.surfaces, rail: mod.rail } : null, time: time ? time.steps : null, customRoles: roleRuns.map((r) => ({ cmd: r.cmd, arg: r.arg, at: r.at, steps: r.steps })), crops: crops ? crops.crops : null };
  const sel = { capturedAt: now, stack: STACK, note: "live DOM counts per selector at 1024×768 light per surface per run (strict = the spec acts on it, so it must be exactly 1). Spec file:line in each key.", runs: {} };
  const cons = { capturedAt: now, stack: STACK, totals: { pagesOpened: 0, errors: 0, warnings: 0, hydration: 0 }, byRun: {}, distinct: [] };
  for (const [rk, r] of Object.entries(P)) {
    if (!r) continue;
    meas.runs[rk] = { capturedAt: r.capturedAt, caps: r.caps, runId: r.runId, runStatus: r.runStatus || null, surfaces: r.surfaces, variants: r.variants, filters: r.filters };
    sel.runs[rk] = r.selCompat;
    cons.byRun[`01 ${rk}`] = { pagesOpened: r.console.pagesOpened, errors: r.console.errors, warnings: r.console.warnings, hydration: r.console.hydration, messages: r.console.messages };
    for (const x of ["pagesOpened", "errors", "warnings", "hydration"]) cons.totals[x] += r.console[x];
    for (const [sk, vv] of Object.entries(r.variants)) {
      for (const [vk, dst] of [["1024", meas.at1024], ["1440", meas.at1440]]) {
        const m = vv[`${vk}-light`];
        if (!m) continue;
        const dk = vv[`${vk}-dark`];
        const f = m.fee || {};
        (dst[sk] = dst[sk] || {})[rk] = { url: m.url, title: m.title, status: m.status, h1: m.h1, h1InMain: m.h1Count, h1Page: m.h1Page, mainIdCount: m.mainCount, mainElCount: m.mainElCount, mainInnerWidth: f.mainInner, mainPadding: f.mainPadding, docHOverflowPx: m.hOverflow, docHOverflowPxDark: dk ? dk.hOverflow : null, tabLinks: (f.tabLinks || []).map((x) => `${x.text} aria-current=${x.ariaCurrent} [${x.cls}]`), pill: (f.pill || []).map((x) => x.text), tiles: (f.tiles || []).map((x) => `${x.label}=${x.value} (${x.testid}; ${(x.valueCls.match(/text-\w+-\d+/g) || []).join(" ")}; ${x.contrast}:1)`), tileGrid: f.tileGrid, stats: (f.stats || []).map((x) => `${x.label}=${x.value}`), statGrid: f.statGrid, filters: f.filters, tables: (f.tables || []).map((t) => ({ headers: t.headers.join("|"), rows: t.bodyRows, tableWidth: t.tableWidth, scrollerClient: t.scroller && t.scroller.clientWidth, scrollerScroll: t.scroller && t.scroller.scrollWidth, horizontalOverflowPx: t.scroller ? t.scroller.overflowPx : null, cut: t.cols.filter((c) => c.cutPx).map((c) => `${c.h}=${c.cutPx}${c.hidden ? "(hidden)" : ""}`).join(", "), rowHeightMin: t.rowHeightMin, rowHeightMax: t.rowHeightMax, rowsAboveFold: t.rowsAboveFold, firstCellLines: t.firstCellLines, headLines: t.headLines, links: t.links.map((l) => `${l.text} → ${l.href} (${l.lines} line(s), ${l.contrast}:1)`) })), empties: (f.empties || []).map((e) => e.text), emptyStates: m.emptyStates.map((e) => e.text), lowContrastLight: m.lowContrast.map((x) => `${x.text} ${x.ratio}`), lowContrastDark: dk ? dk.lowContrast.map((x) => `${x.text} ${x.ratio}`) : null, notFound: m.notFound, bodyTextHead: m.h1.length && m.h1[0] ? undefined : m.bodyTextHead };
        if (f.tileGrid) (meas.tileContainer[rk] = meas.tileContainer[rk] || {})[`${sk}@${vk}`] = { width: f.tileGrid.width, cols: f.tileGrid.cols, template: f.tileGrid.template, cls: f.tileGrid.cls, parentWidth: f.tileGrid.parentWidth, mainInner: f.mainInner, atLeast768: f.tileGrid.width >= 768 };
      }
    }
  }
  for (const [nm, x] of [["02-time", time], ["03b-fee-run-resume", fee], ["04-module-off", mod]]) if (x) cons.byRun[nm] = { messages: x.console };
  for (const r of roleRuns) cons.byRun[`02-roles ${r.cmd} ${r.arg || ""}`.trim()] = { messages: r.console };
  const allMsgs = Object.values(cons.byRun).flatMap((x) => x.messages || []);
  cons.totals.allMessages = allMsgs.length;
  cons.totals.allErrors = allMsgs.filter((m) => m.type === "error" || m.type === "pageerror").length;
  cons.totals.allWarnings = allMsgs.filter((m) => m.type === "warning").length;
  cons.totals.allHydration = allMsgs.filter((m) => /hydrat/i.test(m.text)).length;
  const dm = new Map();
  for (const m of allMsgs) { const k = `${m.type}|${m.text}`; const e = dm.get(k) || { type: m.type, text: m.text, count: 0, labels: new Set() }; e.count++; e.labels.add(m.label.split("|").slice(0, 2).join("|")); dm.set(k, e); }
  cons.distinct = [...dm.values()].map((e) => ({ ...e, labels: [...e.labels] }));
  // Line reporter: "[n/N] [chromium] › file:line:col › title" per test start, then "N passed (Xs)".
  const lines = specLog.split(/\r?\n/).map((l) => l.trim());
  const started = lines.filter((l) => /^\[\d+\/\d+\] \[chromium\]/.test(l)).map((l) => l.replace(/^\[\d+\/\d+\] \[chromium\] › /, ""));
  const summ = (re) => { const m = lines.find((l) => re.test(l)); return m ? +m.match(/(\d+)/)[1] : 0; };
  const spec = { capturedAt: now, command: process.env.SPEC_CMD || "(SPEC_CMD not given)", cwd: process.env.SPEC_CWD || "(SPEC_CWD not given)", window: process.env.SPEC_WINDOW || null, passed: summ(/^\d+ passed/), failed: summ(/^\d+ failed/), skipped: summ(/^\d+ skipped/), flaky: summ(/^\d+ flaky/), summaryLine: lines.find((l) => /^\d+ (passed|failed)/.test(l)) || null, testsStarted: started, failures: lines.filter((l) => /^\d+\) \[/.test(l) || /^Error:/.test(l)).slice(0, 20), reporter: "line (per-test status is not printed for passes; any failure prints a numbered block)" };
  fs.writeFileSync(path.join(L.OUT, "measurements.json"), JSON.stringify(meas) + "\n");
  fs.writeFileSync(path.join(L.OUT, "sel-compat.json"), JSON.stringify(sel) + "\n");
  fs.writeFileSync(path.join(L.OUT, "console.json"), JSON.stringify(cons) + "\n");

  // ---------- sheets ----------
  const T = {};
  for (const r of [...Object.values(P), time, fee, mod, crops, ...roleRuns].filter(Boolean)) for (const [k, f, c] of r.tiles || []) T[k] = { f, c };
  const tile = (key) => [T[key] ? T[key].c : `${key} (not shot)`, T[key] ? T[key].f : null];
  const vt = (sk, k, s) => VARIANTS.map((v) => tile(`s__${sk}-${k}-${s}-${v}`));
  const browser = await L.chromium.launch();
  const written = [];
  const sheet = async (name, title, tiles, cols, tileW) => { await L.sheet(browser, path.join(L.OUT, name), title, tiles, cols, tileW); written.push(name); };
  const copy = (key, name) => { if (T[key]) { fs.copyFileSync(T[key].f, path.join(L.OUT, name)); written.push(name); } };
  const H = (s) => `666A ${L.PHASE} — ${s}`;
  const a = "Alice (owner)";
  await sheet("sheet-list-alice.png", H(`/invoices, ${a}: state post only (23 fee notes; OBS-after has no pre state); ${VCOLS}`), vt("list", "alice", "post"), 4, 480);
  const fk = Object.keys(T).filter((k) => /^f__.*-alice-post$/.test(k));
  await sheet("sheet-filters-alice.png", H(`/invoices status filter links clicked one by one, ${a} (state post, 1440×900 light, full page) — caption = resulting URL`), fk.map(tile), 4, 420);
  await sheet("sheet-billing-runs-alice.png", H(`/invoices/billing-runs, ${a}: state post only (the OBS run COMPLETED; the PREVIEW banner is in sheet-wizard-walk.png); ${VCOLS}`), vt("billing-runs", "alice", "post"), 4, 480);
  await sheet("sheet-run-detail-alice.png", H(`/invoices/billing-runs/[id] (the OBS run), ${a}: COMPLETED (state post only; a PREVIEW detail is in sheet-wizard-walk.png); ${VCOLS}`), vt("billing-run-detail", "alice", "post"), 4, 480);
  await sheet("sheet-collections-alice.png", H(`/invoices/collections ("Debtors"), ${a}: state post (4 debtors; 0 pending reminder gates → "No reminders awaiting approval"); ${VCOLS}`), vt("collections", "alice", "post"), 4, 480);
  await sheet("sheet-unbilled-alice.png", H(`/invoices/unbilled, ${a}: row 1 post default period (1 client: InvLife, from the OBS-before spec run), row 2 post ?periodFrom=2001-01-01&periodTo=2001-01-31&currency=ZAR (EmptyState); ${VCOLS}`), [...vt("unbilled", "alice", "post"), ...vt("unbilled-empty", "alice", "post")], 4, 480);
  const SURF7 = ["list", "billing-runs", "billing-runs-new", "billing-run-detail", "collections", "unbilled", "unbilled-empty"];
  await sheet("sheet-carol-cr-invoicing.png", H(`Carol holding custom role "${L.NAMES.roleInvoicing}" (exactly INVOICING; no FINANCIAL_VISIBILITY, no AI_REVIEW), state post: one row per route — /invoices, /billing-runs, /new, /[id], /collections, /unbilled, /unbilled empty period; ${VCOLS}`), SURF7.flatMap((sk) => vt(sk, "carol-cr-invoicing", "post")), 4, 400);
  await sheet("sheet-carol.png", H(`Carol (Member), state post: one row per route — /invoices, /billing-runs, /new, /[id], /collections, /unbilled, /unbilled empty period; ${VCOLS}`), SURF7.flatMap((sk) => vt(sk, "carol", "post")), 4, 400);
  const mk = Object.keys(T).filter((k) => /^mo__/.test(k)).sort();
  const fe = ["m__features-before-off", "m__features-after-off", "m__features-before-on", "m__features-after-on"];
  await sheet("sheet-module-off-alice.png", H(`bulk_billing OFF (Settings › Firm › Features switch, then back ON), ${a}: Features before/after OFF and before/after ON; /invoices/billing-runs, /new, /[id] at 1024/1440 × light/dark (ModuleDisabledFallback, no h1); /invoices with the module off`), [...fe.map(tile), ...mk.map(tile)], 4, 440);
  const sk = Object.keys(T).filter((k) => /^(time__|team__)/.test(k));
  await sheet("sheet-setup.png", H(`role assignment through the Team UI (Alice 1440 light): Carol Member → "${L.NAMES.roleInvoicing}" → back to Member (the role already existed: 02-roles create skipped it; no time entries were made in OBS-after)`), sk.map(tile), 3, 520);
  for (const c of (crops && crops.crops) || []) if (!c.missing) copy(c.key, `crop-${c.key.replace(/^defect__/, "defect-")}.png`);
  await browser.close();
  console.log("written", written.length, written.join(" "));
  console.log("console totals", JSON.stringify(cons.totals), JSON.stringify(cons.distinct.map((d) => [d.type, d.text.slice(0, 80), d.count, d.labels.slice(0, 4)])));
  console.log("spec", JSON.stringify({ passed: spec.passed, failed: spec.failed, skipped: spec.skipped, started: spec.testsStarted.length }));
  console.log("tileContainer", JSON.stringify(meas.tileContainer));
})();
