// 665E OBS-AFTER copy of ../../before/scripts/05-assemble.cjs (that file is not edited). Changes:
//   (1) specs-baseline.json is written only when $RAW/spec-run.log exists (OBS-after ran no spec: the
//       orchestrator's suite runs on this build are recorded in the README instead);
//   (2) the Alice editor sheet gets a third row: the editable editor at 390 light (full page) and the
//       1024 / 390 light VIEWPORT shots (K-118);
//   (3) the K-117 alignment crops `crop-k117-align-{1024,1440}.png` are copied; the crop captions are
//       the after captions from 08;
//   (4) measurements.json also embeds $RAW/upload.json (the Word template uploaded by 02) and
//       $RAW/docx-editor.json (10); the 10 shot is copied as crop-defect-docx-editor-1440.png.
// 665E OBS step 5: merge the raw run files in $RAW (persona-*.json from 01, interactions.json from 03,
// saves.json from 04, custom-editor.json from 06, message-contrast.json from 07, crops.json from 08,
// spec-run.log from the spec run) into ../measurements.json, ../sel-compat.json, ../console.json,
// ../saves.json, ../specs-baseline.json (single-line JSON) and build the contact sheets with the
// shared 661B sheet() helper.
// EVIDENCE-BUDGET: one sheet per surface × persona (cols 1024 light | 1024 dark | 1440 light |
// 1440 dark, full page; the Email sheet has a second row for the Delivery Log inner tab; the editor
// sheet has a second row for the editable ORG_CUSTOM editor). One sheet each for the open-and-cancel
// interactions and the saves. Separate PNGs only for defects (crops).
// Usage: PHASE=before STACK="<provenance>" SPEC_CMD="<command>" node 05-assemble.cjs
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const STACK = process.env.STACK || "(STACK not given)";
const rd = (f) => (fs.existsSync(path.join(L.RAW, f)) ? JSON.parse(fs.readFileSync(path.join(L.RAW, f), "utf8")) : null);
const PERSONAS = [["alice", "Alice (owner)"], ["carol", "Carol (member)"]];
const VARIANTS = ["1024-light", "1024-dark", "1440-light", "1440-dark"];
const VCOLS = "cols 1024 light | 1024 dark | 1440 light | 1440 dark, full page";
const SURFACES = ["templates", "clauses", "acceptance", "email", "email-delivery-log", "tpl-new", "tpl-edit-platform", "no-tab", "legacy-redirect"];

(async () => {
  const P = {};
  for (const [k] of PERSONAS) P[k] = rd(`persona-${k}.json`);
  const inter = rd("interactions.json");
  const saves = rd("saves.json");
  const ce = rd("custom-editor.json");
  const mc = rd("message-contrast.json");
  const crops = rd("crops.json");
  const specLog = fs.existsSync(path.join(L.RAW, "spec-run.log")) ? fs.readFileSync(path.join(L.RAW, "spec-run.log"), "utf8") : "";
  const now = new Date().toISOString();

  // ---------- JSON ----------
  const meas = { capturedAt: now, stack: STACK, phase: L.PHASE, note: "per persona: caps, surfaces (full probe at 1440 light), variants (compact probe per 1024/1440 × light/dark incl. lowContrast < AA); at1024 = per-surface summary per persona (h1, docHOverflowPx light/dark, tables with per-column cut px at scroll 0); interactions = 03; customEditor = 06 (editable ORG_CUSTOM editor, post-spec); messageContrast = 07", at1024: {}, at1440: {}, personas: {}, interactions: inter ? inter.steps : null, customEditor: ce ? { template: ce.template, runs: ce.runs } : null, messageContrast: mc ? mc.results : null, upload: rd("upload.json"), docxEditor: (() => { const d = rd("docx-editor.json"); if (!d) return null; const { tiles, ...r } = d; return r; })() };
  const sel = { capturedAt: now, stack: STACK, note: "live DOM counts per selector at 1024×768 light per surface per persona (strict = the spec acts on it, so it must be exactly 1). Spec file:line in each key. document-generation.spec.ts acts only on matter / client pages (not this family) — not counted here; it passed in the baseline.", personas: {}, customEditor: ce ? ce.runs.map((r) => ({ who: r.who, viewport: r.viewport, ...r.selCompat })) : null };
  const cons = { capturedAt: now, stack: STACK, totals: { pagesOpened: 0, errors: 0, warnings: 0, hydration: 0 }, byRun: {}, distinct: [] };
  for (const [k] of PERSONAS) {
    const r = P[k];
    if (!r) continue;
    meas.personas[k] = { capturedAt: r.capturedAt, caps: r.caps, editTemplate: r.editTemplate, surfaces: r.surfaces, variants: r.variants };
    sel.personas[k] = r.selCompat;
    cons.byRun[`01-capture ${k}`] = { pagesOpened: r.console.pagesOpened, errors: r.console.errors, warnings: r.console.warnings, hydration: r.console.hydration, messages: r.console.messages };
    for (const x of ["pagesOpened", "errors", "warnings", "hydration"]) cons.totals[x] += r.console[x];
    for (const [sk, vv] of Object.entries(r.variants))
      for (const [vk, dst] of [["1024", meas.at1024], ["1440", meas.at1440]]) {
        const m = vv[`${vk}-light`];
        if (!m) continue;
        const dk = vv[`${vk}-dark`];
        (dst[sk] = dst[sk] || {})[k] = { url: m.url, title: m.title, status: m.status, selectedTab: m.selectedTab, notice: m.notice, tabs: m.tabs.join("|"), rail: m.rail && m.rail.join("|"), h1: m.h1, h1InMain: m.h1Count, mainIdCount: m.mainCount, mainElCount: m.mainElCount, mainWidth: m.mainBox && m.mainBox.width, contentWidth: m.contentBox && m.contentBox.width, docHOverflowPx: m.hOverflow, docHOverflowPxDark: dk ? dk.hOverflow : null, tables: m.tables.map((t) => ({ headers: t.headers.join("|"), rows: t.bodyRows, visible: t.scroller && t.scroller.clientWidth, scroll: t.scroller && t.scroller.scrollWidth, cut: t.cols.filter((c) => c.cutPx).map((c) => `${c.h}=${c.cutPx}${c.hidden ? "(hidden)" : ""}`).join(", "), colWidths: t.cols.map((c) => `${c.h}:${c.w}`).join(" ") })), emptyStates: m.emptyStates, lowContrastLight: m.lowContrast.map((x) => `${x.text} ${x.ratio}`), lowContrastDark: dk ? dk.lowContrast.map((x) => `${x.text} ${x.ratio}`) : null };
      }
  }
  for (const [name, r] of [["03-interactions", inter], ["04-saves", saves], ["06-custom-editor", ce]]) if (r) cons.byRun[name] = { messages: r.console };
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
  const spec = { capturedAt: now, command: process.env.SPEC_CMD || "(SPEC_CMD not given)", cwd: "main checkout frontend/ @ 18b95bcec", passed: summ(/^\d+ passed/), failed: summ(/^\d+ failed/), skipped: summ(/^\d+ skipped/), flaky: summ(/^\d+ flaky/), summaryLine: lines.find((l) => /^\d+ (passed|failed)/.test(l)) || null, tests, failures: lines.filter((l) => /^\d+\) \[/.test(l) || /^Error:/.test(l)).slice(0, 20) };
  fs.writeFileSync(path.join(L.OUT, "measurements.json"), JSON.stringify(meas) + "\n");
  fs.writeFileSync(path.join(L.OUT, "sel-compat.json"), JSON.stringify(sel) + "\n");
  fs.writeFileSync(path.join(L.OUT, "console.json"), JSON.stringify(cons) + "\n");
  if (specLog) fs.writeFileSync(path.join(L.OUT, "specs-baseline.json"), JSON.stringify(spec) + "\n");
  if (saves) { const { tiles, console: c, ...rest } = saves; fs.writeFileSync(path.join(L.OUT, "saves.json"), JSON.stringify({ stack: STACK, ...rest }) + "\n"); }

  // ---------- sheets ----------
  const T = {};
  for (const r of [...Object.values(P), inter, saves, ce, crops].filter(Boolean)) for (const [k, f, c] of r.tiles || []) T[k] = { f, c };
  const tile = (key) => [T[key] ? T[key].c : `${key} (not shot)`, T[key] ? T[key].f : null];
  const vt = (sk, k) => VARIANTS.map((v) => tile(`s__${sk}-${k}-${v}`));
  const browser = await L.chromium.launch();
  const written = [];
  const sheet = async (name, title, tiles, cols, tileW) => { await L.sheet(browser, path.join(L.OUT, name), title, tiles, cols, tileW); written.push(name); };
  const copy = (key, name) => { if (T[key]) { fs.copyFileSync(T[key].f, path.join(L.OUT, name)); written.push(name); } };
  const ph = L.PHASE;
  for (const [k, label] of PERSONAS) {
    if (!P[k]) continue;
    await sheet(`sheet-templates-${k}.png`, `665E ${ph} — Settings › Documents ?tab=templates, ${label}: ${VCOLS}`, vt("templates", k), 4, 520);
    await sheet(`sheet-clauses-${k}.png`, `665E ${ph} — Settings › Documents ?tab=clauses, ${label}: ${VCOLS}`, vt("clauses", k), 4, 520);
    await sheet(`sheet-acceptance-${k}.png`, `665E ${ph} — Settings › Documents ?tab=acceptance, ${label}${k === "carol" ? " (adminOnly → falls back to Templates + notice)" : ""}: ${VCOLS}`, vt("acceptance", k), 4, 520);
    await sheet(`sheet-email-${k}.png`, `665E ${ph} — Settings › Documents ?tab=email, ${label}${k === "carol" ? " (adminOnly → falls back to Templates + notice)" : ""}: row 1 Overview, row 2 after clicking the inner tab "Delivery Log"; ${VCOLS}`, [...vt("email", k), ...vt("email-delivery-log", k)], 4, 520);
    await sheet(`sheet-template-new-${k}.png`, `665E ${ph} — Settings › Documents › templates/new ("New Template"), ${label}${k === "carol" ? " (no gate: the full create form renders)" : ""}: ${VCOLS}`, vt("tpl-new", k), 4, 520);
    const ceT = k === "alice" ? [...VARIANTS.map((v) => tile(`s__tpl-edit-custom-alice-${v}`)), tile("s__tpl-edit-custom-alice-390-light"), tile("s__tpl-edit-custom-alice-1024-light-viewport"), tile("s__tpl-edit-custom-alice-390-light-viewport")] : [tile("s__tpl-edit-custom-carol-1024-light")];
    await sheet(`sheet-template-edit-${k}.png`, `665E ${ph} — Settings › Documents › templates/[id]/edit, ${label}: row 1 PLATFORM "${P[k].editTemplate.name}" (read-only, h1 = name); row 2 the ORG_CUSTOM clone the spec run created (${k === "alice" ? "editable: name input, no h1" : "read-only for a member"}; ${ce ? ce.template.name : "?"}, inactive); ${VCOLS}${k === "alice" ? "; row 3 = editable editor 390 light full page, then 1024 / 390 light viewport shots (K-118)" : ""}`, [...vt("tpl-edit-platform", k), ...ceT], 4, 520);
  }
  const ik = Object.keys(T).filter((k) => /^int__/.test(k));
  if (ik.length) await sheet("sheet-interactions.png", `665E ${ph} — open-and-cancel (Alice, light): Upload Word Template dialog → Cancel; template actions menu (View | Clone & Customize | Deactivate — no confirmation, not clicked) → Escape at 1440 and 1024; Branding card (no logo → no delete X); New Clause sheet → Close (1440, 1024); clause actions → View sheet → Close; Clone Clause alertdialog → Cancel; clause expanded; Delivery Log status filter → Escape`, ik.map(tile), 2, 720);
  const sk = Object.keys(T).filter((k) => /^save__/.test(k));
  if (sk.length) await sheet("sheet-saves.png", `665E ${ph} — one save per save model (Alice 1440 light, displayed values): Branding "Save Branding" (per-card) and Acceptance "Save Settings"`, sk.map(tile), 2, 720);
  copy("defect__templates-table-1024", "crop-defect-templates-table-1024.png");
  copy("defect__delivery-log-table-1024", "crop-defect-delivery-log-table-1024.png");
  copy("defect__editor-topbar-1024", "crop-defect-editor-topbar-1024.png");
  copy("defect__editor-topbar-1024-viewport", "crop-defect-editor-topbar-1024-viewport.png");
  copy("defect__k117-align-1024", "crop-k117-align-1024.png");
  copy("defect__k117-align-1440", "crop-k117-align-1440.png");
  { const d = rd("docx-editor.json"); if (d) { fs.copyFileSync(d.tiles[0][1], path.join(L.OUT, "crop-defect-docx-editor-1440.png")); written.push("crop-defect-docx-editor-1440.png"); } }
  await browser.close();
  console.log("written", written.length, written.join(" "));
  console.log("console totals", JSON.stringify(cons.totals), JSON.stringify(cons.distinct.map((d) => [d.type, d.text.slice(0, 80), d.count, d.surfaces])));
  console.log("spec", JSON.stringify({ passed: spec.passed, failed: spec.failed, skipped: spec.skipped, n: spec.tests.length }));
})();
