// 665F OBS step 5: merge the raw run files in $RAW (persona-*.json from 01, roles-*.json from 02,
// interactions.json from 03, crops.json from 08, spec-run.log from the spec run) into
// ../measurements.json, ../sel-compat.json, ../console.json, ../specs-baseline.json (single-line JSON)
// and build the contact sheets with the shared 661B sheet() helper. Adapted from
// 665E/before/scripts/05-assemble.cjs (not edited).
// EVIDENCE-BUDGET (≲ 40 committed files): Alice gets one sheet per surface (cols 1024 light | 1024 dark
// | 1440 light | 1440 dark, full page; the Packs sheet has a second row for the inner "Installed"
// tab). Carol and the two custom-role personas get two sheets each — the 6 tabs (one row per tab) and
// the 3 log sub-pages (one row each) — because most of their surfaces are the same fallback. One
// sheet for the open-and-cancel / toggle-and-revert interactions, one for the role UI. Separate PNGs
// only for defects (crops).
// Usage: PHASE=before STACK="<provenance>" SPEC_CMD="<command>" SPEC_CWD="<dir>" node 05-assemble.cjs
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const STACK = process.env.STACK || "(STACK not given)";
const rd = (f) => (fs.existsSync(path.join(L.RAW, f)) ? JSON.parse(fs.readFileSync(path.join(L.RAW, f), "utf8")) : null);
const PERSONAS = [["alice", "Alice (owner)"], ["carol", "Carol (member)"], ["carol-cr-holder", `Carol as custom role "${L.NAMES.roleHolder}" (AI_MANAGE + AUTOMATIONS)`], ["carol-cr-lacker", `Carol as custom role "${L.NAMES.roleLacker}" (CUSTOMER_MANAGEMENT)`]];
const VARIANTS = ["1024-light", "1024-dark", "1440-light", "1440-dark"];
const VCOLS = "cols 1024 light | 1024 dark | 1440 light | 1440 dark, full page";
const TABROWS = ["xero", "mcp", "ai", "notifications", "packs", "automations"];
const SUBROWS = ["sync-log", "ai-history", "executions"];

(async () => {
  const P = {};
  for (const [k] of PERSONAS) P[k] = rd(`persona-${k}.json`);
  const inter = rd("interactions.json");
  const crops = rd("crops.json");
  const roleRuns = fs.readdirSync(L.RAW).filter((f) => /^roles-.*\.json$/.test(f)).sort().map((f) => ({ file: f, ...rd(f) }));
  const specLog = fs.existsSync(path.join(L.RAW, "spec-run.log")) ? fs.readFileSync(path.join(L.RAW, "spec-run.log"), "utf8") : "";
  const now = new Date().toISOString();

  // ---------- JSON ----------
  const meas = { capturedAt: now, stack: STACK, phase: L.PHASE, note: "per persona: caps, surfaces (full probe at 1440 light incl. `specific`: notification groups, pack cards, table cells, pager, comboboxes), variants (compact probe per 1024/1440 × light/dark: h1, tabs, widths, tables with per-column cut, cards/grids overflow, targeted contrast, lowContrast < AA), legacy (redirects at 1024 light); at1024 / at1440 = per-surface summary per persona; interactions = 03; customRoles = 02; crops = 08", at1024: {}, at1440: {}, personas: {}, interactions: inter ? { steps: inter.steps, api: inter.api } : null, customRoles: roleRuns.map((r) => ({ cmd: r.cmd, arg: r.arg, at: r.at, steps: r.steps })), crops: crops ? { crops: crops.crops, carolAlert: crops.carolAlert } : null };
  const sel = { capturedAt: now, stack: STACK, note: "live DOM counts per selector at 1024×768 light per surface per persona (strict = the spec acts on it, so it must be exactly 1). Spec file:line in each key.", personas: {} };
  const cons = { capturedAt: now, stack: STACK, totals: { pagesOpened: 0, errors: 0, warnings: 0, hydration: 0 }, byRun: {}, distinct: [] };
  for (const [k] of PERSONAS) {
    const r = P[k];
    if (!r) continue;
    meas.personas[k] = { capturedAt: r.capturedAt, caps: r.caps, surfaces: r.surfaces, variants: r.variants, legacy: r.legacy };
    sel.personas[k] = r.selCompat;
    cons.byRun[`01-capture ${k}`] = { pagesOpened: r.console.pagesOpened, errors: r.console.errors, warnings: r.console.warnings, hydration: r.console.hydration, messages: r.console.messages };
    for (const x of ["pagesOpened", "errors", "warnings", "hydration"]) cons.totals[x] += r.console[x];
    for (const [sk, vv] of Object.entries(r.variants))
      for (const [vk, dst] of [["1024", meas.at1024], ["1440", meas.at1440]]) {
        const m = vv[`${vk}-light`];
        if (!m) continue;
        const dk = vv[`${vk}-dark`];
        const cd = m.cards || {};
        const tg = m.targets || {};
        (dst[sk] = dst[sk] || {})[k] = { url: m.url, title: m.title, status: m.status, selectedTab: m.selectedTab, notice: m.notice, tabs: m.tabs.join("|"), rail: m.rail && m.rail.join("|"), h1: m.h1, h1InMain: m.h1Count, mainIdCount: m.mainCount, mainElCount: m.mainElCount, mainWidth: m.mainBox && m.mainBox.width, contentWidth: m.contentBox && m.contentBox.width, column: cd.column, docHOverflowPx: m.hOverflow, docHOverflowPxDark: dk ? dk.hOverflow : null, grids: (cd.grids || []).filter((g) => g.cols > 1).map((g) => `${g.cols}col ${g.clientWidth}/${g.scrollWidth}`), cardsSpilling: (cd.cards || []).filter((c) => c.spillCount || c.overflowPx).map((c) => ({ head: c.head, width: c.width, overflowPx: c.overflowPx, spillMaxPx: c.spillMaxPx, spill: c.spill[0] && `${c.spill[0].tag}:${c.spill[0].text}` })), tables: m.tables.map((t) => ({ headers: t.headers.join("|"), rows: t.bodyRows, tableWidth: t.tableWidth, visible: t.scroller && t.scroller.clientWidth, scroll: t.scroller && t.scroller.scrollWidth, cut: t.cols.filter((c) => c.cutPx).map((c) => `${c.h}=${c.cutPx}${c.hidden ? "(hidden)" : ""}`).join(", ") })), contrast: { links: (tg.links || []).map((x) => `${x.text} ${x.fg} on ${x.bg} ${x.ratio}`), linksDark: dk && dk.targets ? (dk.targets.links || []).map((x) => `${x.text} ${x.fg} on ${x.bg} ${x.ratio}`) : null, badges: (tg.badges || []).map((x) => `${x.text}[${x.variant}] ${x.fg} on ${x.bg} ${x.ratio}`), packTitles: (tg.packTitles || []).map((x) => `${x.text} ${x.fg} on ${x.bg} ${x.ratio}`), notifCategoryHeaders: (tg.notifCategoryHeaders || []).map((x) => `${x.sample} ${x.fg} on ${x.bg} ${x.ratio}`), notifCategoryHeadersDark: dk && dk.targets ? (dk.targets.notifCategoryHeaders || []).map((x) => `${x.sample} ${x.fg} on ${x.bg} ${x.ratio}`) : null }, lowContrastLight: m.lowContrast.map((x) => `${x.text} ${x.ratio}`), lowContrastDark: dk ? dk.lowContrast.map((x) => `${x.text} ${x.ratio}`) : null, emptyStates: m.emptyStates.map((e) => e.text) };
      }
  }
  if (inter) cons.byRun["03-interactions"] = { messages: inter.console };
  for (const r of roleRuns) cons.byRun[`02-roles ${r.cmd} ${r.arg || ""}`.trim()] = { messages: r.console };
  const allMsgs = Object.values(cons.byRun).flatMap((x) => x.messages || []);
  cons.totals.allMessages = allMsgs.length;
  cons.totals.allErrors = allMsgs.filter((m) => m.type === "error" || m.type === "pageerror").length;
  cons.totals.allWarnings = allMsgs.filter((m) => m.type === "warning").length;
  cons.totals.allHydration = allMsgs.filter((m) => /hydrat/i.test(m.text)).length;
  const dm = new Map();
  for (const m of allMsgs) { const k = `${m.type}|${m.text}`; const e = dm.get(k) || { type: m.type, text: m.text, count: 0, surfaces: new Set() }; e.count++; e.surfaces.add(m.label.split("|")[1]); dm.set(k, e); }
  cons.distinct = [...dm.values()].map((e) => ({ ...e, surfaces: [...e.surfaces] }));
  const lines = specLog.split("\n").map((l) => l.trim());
  const tests = lines.filter((l) => /^[✓✘-]\s+\d+ \[/.test(l)).map((l) => ({ status: l.startsWith("✓") ? "passed" : l.startsWith("✘") ? "failed" : "skipped", title: l.replace(/^[✓✘-]\s+\d+ \[chromium\] › /, "").replace(/ \(\d+(\.\d+)?m?s\)$/, "") }));
  const summ = (re) => { const m = lines.find((l) => re.test(l)); return m ? +m.match(/^(\d+)/)[1] : 0; };
  const spec = { capturedAt: now, command: process.env.SPEC_CMD || "(SPEC_CMD not given)", cwd: process.env.SPEC_CWD || "(SPEC_CWD not given)", passed: summ(/^\d+ passed/), failed: summ(/^\d+ failed/), skipped: summ(/^\d+ skipped/), flaky: summ(/^\d+ flaky/), summaryLine: lines.find((l) => /^\d+ (passed|failed)/.test(l)) || null, tests, failures: lines.filter((l) => /^\d+\) \[/.test(l) || /^Error:/.test(l)).slice(0, 20) };
  fs.writeFileSync(path.join(L.OUT, "measurements.json"), JSON.stringify(meas) + "\n");
  fs.writeFileSync(path.join(L.OUT, "sel-compat.json"), JSON.stringify(sel) + "\n");
  fs.writeFileSync(path.join(L.OUT, "console.json"), JSON.stringify(cons) + "\n");
  fs.writeFileSync(path.join(L.OUT, "specs-baseline.json"), JSON.stringify(spec) + "\n");

  // ---------- sheets ----------
  const T = {};
  for (const r of [...Object.values(P), inter, crops, ...roleRuns].filter(Boolean)) for (const [k, f, c] of r.tiles || []) T[k] = { f, c };
  const tile = (key) => [T[key] ? T[key].c : `${key} (not shot)`, T[key] ? T[key].f : null];
  const vt = (sk, k) => VARIANTS.map((v) => tile(`s__${sk}-${k}-${v}`));
  const browser = await L.chromium.launch();
  const written = [];
  const sheet = async (name, title, tiles, cols, tileW) => { await L.sheet(browser, path.join(L.OUT, name), title, tiles, cols, tileW); written.push(name); };
  const copy = (key, name) => { if (T[key]) { fs.copyFileSync(T[key].f, path.join(L.OUT, name)); written.push(name); } };
  const ph = L.PHASE;
  const T665 = (s) => `665F ${ph} — Settings › Connections ${s}`;
  if (P.alice) {
    const a = "Alice (owner)";
    await sheet("sheet-xero-alice.png", T665(`?tab=xero ("Integrations"), ${a}: ${VCOLS}`), vt("xero", "alice"), 4, 520);
    await sheet("sheet-mcp-alice.png", T665(`?tab=mcp, ${a}: ${VCOLS}`), vt("mcp", "alice"), 4, 520);
    await sheet("sheet-ai-alice.png", T665(`?tab=ai, ${a} (coldStartCompleted=false → h1 "Set Up AI"): ${VCOLS}`), vt("ai", "alice"), 4, 520);
    await sheet("sheet-notifications-alice.png", T665(`?tab=notifications, ${a}: ${VCOLS}`), vt("notifications", "alice"), 4, 520);
    await sheet("sheet-packs-alice.png", T665(`?tab=packs, ${a}: row 1 inner tab Available, row 2 after clicking "Installed (5)"; ${VCOLS}`), [...vt("packs", "alice"), ...vt("packs-installed", "alice")], 4, 520);
    await sheet("sheet-automations-alice.png", T665(`?tab=automations, ${a}: ${VCOLS}`), vt("automations", "alice"), 4, 520);
    await sheet("sheet-sync-log-alice.png", T665(`› xero/sync-log ("Sync Log"), ${a} (0 entries → empty state, no pager): ${VCOLS}`), vt("sync-log", "alice"), 4, 520);
    await sheet("sheet-ai-history-alice.png", T665(`› ai/history ("Execution History"), ${a} (0 executions → empty state, no pager): ${VCOLS}`), vt("ai-history", "alice"), 4, 520);
    await sheet("sheet-executions-alice.png", T665(`› automations/executions ("Execution Log"), ${a} (10 rows, 1 page, no pager): ${VCOLS}`), vt("executions", "alice"), 4, 520);
  }
  for (const [k, label] of PERSONAS.slice(1)) {
    if (!P[k]) continue;
    await sheet(`sheet-tabs-${k}.png`, T665(`6 tabs, ${label}: one row per requested tab — xero, mcp, ai, notifications, packs, automations (gated tabs fall back to Integrations with a notice); ${VCOLS}`), TABROWS.flatMap((sk) => vt(sk, k)), 4, 520);
    await sheet(`sheet-subpages-${k}.png`, T665(`log sub-pages, ${label}: rows xero/sync-log, ai/history, automations/executions (redirects to ?tab=automations for non-admins); ${VCOLS}`), SUBROWS.flatMap((sk) => vt(sk, k)), 4, 520);
  }
  const ik = Object.keys(T).filter((k) => /^int__/.test(k));
  if (ik.length) await sheet("sheet-interactions.png", `665F ${ph} — open-and-cancel / toggle-and-revert (Alice, light): KYC Configure dialog → Escape; Payment / AI provider comboboxes → Escape; MCP "Enabled" switch → POPIA consent dialog → Cancel; Packs Uninstall "Common Templates" → alertdialog → Cancel; "SA Legal Templates" Uninstall (disabled) tooltip; Automations rule switch OFF (then back ON); Notifications "Task Assigned" In-App OFF + Save (then back ON + Save); Execution Log / Sync Log filters → Escape; 1024 rule switch scrolled into view`, ik.map(tile), 2, 720);
  const rk = Object.keys(T).filter((k) => /^team__/.test(k));
  if (rk.length) await sheet("sheet-roles.png", `665F ${ph} — custom roles through the UI (Alice 1440 light): Settings › Firm › Roles › New Role for "${L.NAMES.roleHolder}" and "${L.NAMES.roleLacker}"; Team › Carol's member panel for each assignment and the restore to Member`, rk.map(tile), 3, 520);
  for (const c of (crops && crops.crops) || []) if (!c.missing) copy(c.key, `crop-${c.key.replace(/^defect__/, "defect-")}.png`);
  await browser.close();
  console.log("written", written.length, written.join(" "));
  console.log("console totals", JSON.stringify(cons.totals), JSON.stringify(cons.distinct.map((d) => [d.type, d.text.slice(0, 80), d.count, d.surfaces])));
  console.log("spec", JSON.stringify({ passed: spec.passed, failed: spec.failed, skipped: spec.skipped, n: spec.tests.length }));
})();
