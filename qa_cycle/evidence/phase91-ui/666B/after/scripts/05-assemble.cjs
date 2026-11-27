// 666B OBS step 5: merge the raw run files in $RAW (persona-alice.json / persona-carol.json from 01,
// setup-02b.json from 02b (02 crashed before writing its json; its 5 PNGs are picked up by name),
// lifecycle.json from 03, crops.json from 08, spec-run.log from the spec run) into
// ../measurements.json, ../sel-compat.json, ../console.json, ../specs-baseline.json (single-line JSON)
// and build the contact sheets with the shared 661B sheet() helper. Adapted from
// 666A/before/scripts/05-assemble.cjs (not edited).
// EVIDENCE-BUDGET: one sheet per status target for Alice (cols 1024 light | 1024 dark | 1440 light |
// 1440 dark, full page), one for Carol, one for the lifecycle transitions, one for the two record
// payments (before / form / after save without reload / after reload), one for the UI setup; crops
// copied as separate PNGs.
// Usage: PHASE=before STACK="<provenance>" SPEC_CMD="<command>" SPEC_CWD="<dir>" SPEC_WINDOW="<window>" node 05-assemble.cjs
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const STACK = process.env.STACK || "(STACK not given)";
const rd = (f) => (fs.existsSync(path.join(L.RAW, f)) ? JSON.parse(fs.readFileSync(path.join(L.RAW, f), "utf8")) : null);
const VARIANTS = ["1024-light", "1024-dark", "1440-light", "1440-dark"];
const VCOLS = "cols 1024 light | 1024 dark | 1440 light | 1440 dark, full page";
const TARGETS = ["draft", "approved", "sent-overdue", "sent", "sent-takeon", "partial", "paid", "void"];

(async () => {
  const A = rd("persona-alice.json"), Cc = rd("persona-carol.json"), setup = rd("setup-02b.json"), lc = rd("lifecycle.json"), crops = rd("crops.json");
  const specLog = fs.existsSync(path.join(L.RAW, "spec-run.log")) ? fs.readFileSync(path.join(L.RAW, "spec-run.log"), "utf8").replace(/\u001b\[[0-9;]*[A-Za-z]/g, "") : "";
  const now = new Date().toISOString();

  // ---------- JSON ----------
  const slim = (m) => { const d = m.detail || {}; return { url: m.url, title: m.title, status: m.status, h1: m.h1, h1InMain: m.h1Count, h1Page: m.h1Page, mainIdCount: m.mainCount, mainElCount: m.mainElCount, docHeight: m.docHeight, docHOverflowPx: m.hOverflow, mainInnerWidth: d.mainInner, mainPadding: d.mainPadding, pageFrame: d.pageFrame, header: d.header, h1Detail: d.h1, badges: d.badges, meta: d.meta, buttons: d.buttons, backLink: d.backLink, generateDoc: d.generateDoc, alerts: d.alerts, totals: d.totals, testids: d.testids, placeholderLineDesc: d.placeholderLineDesc, indicators: d.indicators, paymentHistory: d.paymentHistory, lineTable: d.lineTable, readonly: d.readonly, draftForm: d.draftForm, customFields: d.customFields, fieldGroupSelector: d.fieldGroupSelector, generatedDocs: d.generatedDocs, collectionActivity: d.collectionActivity, audit: d.audit, auditTimelineItems: d.auditTimelineItems, sections: d.sections, clientSections: d.clientSections, cards: m.cards, lowContrast: m.lowContrast, notFound: m.notFound, bodyTextHead: m.h1.length && m.h1[0] !== "Page not found" ? undefined : m.bodyTextHead }; };
  const meas = { capturedAt: now, stack: STACK, phase: L.PHASE, note: "targets = fee notes per status (ids + API state at capture). variants[target][<w>-<theme>] = DETAIL + compact probe (header block, h1 + HelpTip, badges, meta row, action buttons with variant / classes / contrast, totals rows, testid counts, payment history, line table, readonly details / draft form, custom fields, generated documents, collection activity, audit trail, section boxes, low-contrast text). surfaces = full inventory at 1440 light. containerWidths = the content frame per width. lifecycle = UI transitions with DOM state right after each action (no reload) + API state + server-action / RSC requests + Mailpit delta. recordPayment = before / after save (no reload) / after reload for the partial and full payment.", targets: { ...(A ? A.targets : {}), ...(Cc ? Cc.targets : {}) }, caps: { alice: A && A.caps, carol: Cc && Cc.caps }, variants: {}, surfaces: { ...(A ? A.surfaces : {}), ...(Cc ? Cc.surfaces : {}) }, containerWidths: {}, setup: setup && { context: setup.context, steps: setup.steps, notes: setup.notes }, lifecycle: null, recordPayment: {}, draftEditors: lc && lc.draftEditors, crops: crops && { crops: crops.crops, paymentFormText: crops.paymentFormText, paymentFormCounts: crops.paymentFormCounts } };
  for (const R of [A, Cc].filter(Boolean))
    for (const [k, vv] of Object.entries(R.variants)) {
      meas.variants[k] = {};
      for (const [v, m] of Object.entries(vv)) {
        meas.variants[k][v] = slim(m);
        const d = m.detail || {};
        (meas.containerWidths[v] = meas.containerWidths[v] || {})[k] = { mainInner: d.mainInner, pageFrame: d.pageFrame && d.pageFrame.w, header: d.header && d.header.box, headerLeft: d.header && d.header.left && d.header.left.w, headerActions: d.header && d.header.actions && d.header.actions.w, totals: d.totals && d.totals.box && d.totals.box.w, lineTableScroller: d.lineTable && d.lineTable.measure && d.lineTable.measure.scroller && `${d.lineTable.measure.scroller.clientWidth}/${d.lineTable.measure.scroller.scrollWidth}`, paymentHistoryScroller: d.paymentHistory && d.paymentHistory.table && d.paymentHistory.table.scroller && `${d.paymentHistory.table.scroller.clientWidth}/${d.paymentHistory.table.scroller.scrollWidth}`, docHOverflowPx: m.hOverflow };
      }
    }
  if (lc) {
    meas.lifecycle = Object.fromEntries(Object.entries(lc.notes).map(([k, n]) => [k, { id: n.id, initial: n.initial, sendResult: n.sendResult, overrideText: n.overrideText, dueDateAfterSave: n.dueDateAfterSave, steps: n.steps }]));
    meas.lifecycle.nativeDialogs = lc.nativeDialogs;
    for (const k of ["partial", "paid"]) { const n = lc.notes[k]; if (n) meas.recordPayment[k] = { id: n.id, formDefaults: n.formDefaults, before: n.beforePayment, afterSaveNoReload: n.afterSaveNoReload, afterReload: n.afterReload, sameAfterReload: JSON.stringify(n.afterSaveNoReload && { ...n.afterSaveNoReload, audit: null, generatedDocs: null }) === JSON.stringify(n.afterReload && { ...n.afterReload, audit: null, generatedDocs: null }) }; }
  }
  const sel = { capturedAt: now, stack: STACK, note: "live DOM counts per selector at 1024×768 light per target (strict = the spec acts on it, so it must be exactly 1 where used). Spec file:line in each key; record-payment-form / payment-method-select are counted closed here and open in measurements.crops.paymentFormCounts; send-override-dialog never opened (see README).", alice: A && A.selCompat, carol: Cc && Cc.selCompat, paymentFormOpen: crops && crops.paymentFormCounts };
  const cons = { capturedAt: now, stack: STACK, totals: { pagesOpened: 0, errors: 0, warnings: 0, hydration: 0 }, byRun: {}, distinct: [] };
  for (const [nm, R] of [["01 alice", A], ["01 carol", Cc]]) if (R) { cons.byRun[nm] = R.console; for (const x of ["pagesOpened", "errors", "warnings", "hydration"]) cons.totals[x] += R.console[x]; }
  for (const [nm, x] of [["02b-setup", setup], ["03-lifecycle", lc], ["08-defect-crops", crops]]) if (x) cons.byRun[nm] = { messages: x.console };
  const allMsgs = Object.values(cons.byRun).flatMap((x) => x.messages || []);
  cons.totals.allMessages = allMsgs.length;
  cons.totals.allErrors = allMsgs.filter((m) => m.type === "error" || m.type === "pageerror").length;
  cons.totals.allWarnings = allMsgs.filter((m) => m.type === "warning").length;
  cons.totals.allHydration = allMsgs.filter((m) => /hydrat/i.test(m.text)).length;
  const dm = new Map();
  for (const m of allMsgs) { const k = `${m.type}|${m.text}`; const e = dm.get(k) || { type: m.type, text: m.text, count: 0, labels: new Set() }; e.count++; e.labels.add(m.label); dm.set(k, e); }
  cons.distinct = [...dm.values()].map((e) => ({ ...e, labels: [...e.labels] }));
  cons.note = "02-setup.cjs crashed before writing its json (no console record for its run: rate, task, 2 time entries, 2 drafts); 02b re-ran idempotently.";
  const lines = specLog.split(/\r?\n/).map((l) => l.trim());
  const started = lines.filter((l) => /^\[\d+\/\d+\] \[chromium\]/.test(l)).map((l) => l.replace(/^\[\d+\/\d+\] \[chromium\] › /, ""));
  const summ = (re) => { const m = lines.find((l) => re.test(l)); return m ? +m.match(/(\d+)/)[1] : 0; };
  const spec = { capturedAt: now, command: process.env.SPEC_CMD || "(SPEC_CMD not given)", cwd: process.env.SPEC_CWD || "(SPEC_CWD not given)", window: process.env.SPEC_WINDOW || null, passed: summ(/^\d+ passed/), failed: summ(/^\d+ failed/), skipped: summ(/^\d+ skipped/), flaky: summ(/^\d+ flaky/), summaryLine: lines.find((l) => /^\d+ (passed|failed)/.test(l)) || null, testsStarted: started, failures: lines.filter((l) => /^\d+\) \[chromium\]/.test(l)) };
  fs.writeFileSync(path.join(L.OUT, "measurements.json"), JSON.stringify(meas) + "\n");
  fs.writeFileSync(path.join(L.OUT, "sel-compat.json"), JSON.stringify(sel) + "\n");
  fs.writeFileSync(path.join(L.OUT, "console.json"), JSON.stringify(cons) + "\n");
  fs.writeFileSync(path.join(L.OUT, "specs-baseline.json"), JSON.stringify(spec) + "\n");

  // ---------- sheets ----------
  const T = {};
  for (const r of [A, Cc, setup, lc, crops].filter(Boolean)) for (const [k, f, c] of r.tiles || []) T[k] = { f, c };
  const SETUP02 = { "setup__rate-dialog": "02-setup: Settings › Fees › Tariffs › Add rate for Alice Owner — Billing Rate 2500 ZAR, effective today (before Create Rate)", "setup__task-dialog": "02-setup: Website Redesign (Acme Corp) › Work › Tasks › New Task \"666B OBS fee-note detail\" (before Create Task)", "setup__log-time": "02-setup: Log Time 30m \"666B OBS entry — approved\" with the rate preview (before Log Time)", "setup__gen-step2": "02-setup: Acme Corp › New fee note › Select Unbilled Items (1 entry)", "setup__gen-validated": "02-setup: after Validate & Create Draft — \"Create Draft (1 issues)\" (tax number missing)" };
  for (const [k, c] of Object.entries(SETUP02)) if (!T[k] && fs.existsSync(path.join(L.RAW, `${k}.png`))) T[k] = { f: path.join(L.RAW, `${k}.png`), c };
  const tile = (key) => [T[key] ? T[key].c : `${key} (not shot)`, T[key] ? T[key].f : null];
  const vt = (k, who) => VARIANTS.map((v) => tile(`s__${k}-${who}-${v}`));
  const browser = await L.chromium.launch();
  const written = [];
  const sheet = async (name, title, tiles, cols, tileW) => { await L.sheet(browser, path.join(L.OUT, name), title, tiles, cols, tileW); written.push(name); };
  const H = (s) => `666B ${L.PHASE} — ${s}`;
  const LBL = { draft: "DRAFT (seeded, h1 \"Draft fee note\")", approved: "APPROVED", "sent-overdue": "SENT overdue (due 1 Nov 2026 set on the draft)", sent: "SENT not overdue (seeded INV-0001)", "sent-takeon": "SENT take-on PS-1001 (overdue, Brought forward badge)", partial: "PARTIALLY_PAID", paid: "PAID", void: "VOID" };
  for (const k of TARGETS) if (A && A.targets[k]) await sheet(`sheet-${k}-alice.png`, H(`/invoices/[id] ${A.targets[k].invoiceNumber || "Draft"} — ${LBL[k]}, Alice (owner); ${VCOLS}`), vt(k, "alice"), 4, 420);
  if (Cc) await sheet("sheet-carol.png", H(`/invoices/[id] (INV-0008), Carol (Member, no INVOICING) → 404 "Page not found"; ${VCOLS}`), vt("carol-404", "carol"), 4, 420);
  const lk = Object.keys(T).filter((k) => /^lc__(approved|sent-overdue|void)-/.test(k) || /^lc__(partial|paid)-[12]-/.test(k)).sort();
  await sheet("sheet-lifecycle-alice.png", H("lifecycle through the detail page UI, Alice 1440 light, full page after each action (no reload): approved (Approve) · sent-overdue (Due Date 2026-11-01 + Save Changes, Approve, Send) · partial / paid (Approve, Send) · void (Approve, Void + confirm)"), lk.map(tile), 4, 360);
  const pk = (k) => Object.keys(T).filter((x) => x.startsWith(`lc__${k}-pay-0`) || x.startsWith(`lc__${k}-pay-1`) || new RegExp(`^lc__${k}-\\d+-(Record_Payment_opened|Confirm_Payment_no_reload_|after_reload)`).test(x)).sort((a, b) => { const o = (x) => (/pay-0/.test(x) ? 0 : /Record_Payment_opened/.test(x) ? 1 : /pay-1/.test(x) ? 2 : /Confirm/.test(x) ? 3 : 4); return o(a) - o(b); });
  await sheet("sheet-record-payment-alice.png", H("Record Payment on the detail page, Alice 1440 light, full page: row 1 INV-0010 partial R 1 000,00 (SENT → PARTIALLY_PAID), row 2 INV-0011 full balance (SENT → PAID) — before | form opened | form filled | after Confirm (no reload) | after reload"), [...pk("partial"), ...pk("paid")].map(tile), 5, 330);
  const dk = ["lc__draft-add-line-open", "lc__draft-edit-line-open"];
  const sk = ["setup__rate-dialog", "setup__task-dialog", "setup__log-time", "setup__gen-step2", "setup__gen-validated"];
  await sheet("sheet-setup.png", H("data created through the UI (Alice 1440 light): rate, task, time entry, New fee note generation (02 / 02b); and the seeded draft's inline line editors opened and cancelled (03)"), [...sk, ...dk].map(tile), 4, 420);
  for (const c of (crops && crops.crops) || []) { const src = path.join(L.RAW, `${c.key}.png`); if (fs.existsSync(src)) { const nm = `crop-${c.key.replace(/^defect__/, "defect-")}.png`; fs.copyFileSync(src, path.join(L.OUT, nm)); written.push(nm); } }
  await browser.close();
  console.log("written", written.length, written.join(" "));
  console.log("console totals", JSON.stringify(cons.totals), JSON.stringify(cons.distinct.map((d) => [d.type, d.text.slice(0, 80), d.count])));
  console.log("spec", JSON.stringify({ passed: spec.passed, failed: spec.failed, skipped: spec.skipped, started: spec.testsStarted.length }));
  console.log("containerWidths", JSON.stringify(Object.fromEntries(Object.entries(meas.containerWidths).map(([v, o]) => [v, Object.fromEntries(Object.entries(o).map(([k, x]) => [k, `${x.mainInner}/${x.pageFrame} hdr${x.header ? x.header.w + "x" + x.header.h : "-"}`]))]))));
  console.log("recordPayment sameAfterReload", JSON.stringify(Object.fromEntries(Object.entries(meas.recordPayment).map(([k, v]) => [k, v.sameAfterReload]))));
})();
