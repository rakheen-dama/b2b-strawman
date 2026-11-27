// 666B OBS-AFTER step 13 (new; 05-assemble.cjs is the OBS-before assembler and expects the setup /
// lifecycle / spec-run files OBS-after does not produce, so it is not run here). Merges $RAW
// persona-alice.json / persona-carol.json (01b), interact.json (11), focus-crops.json (12) into
// ../measurements.json, ../sel-compat.json, ../console.json (single-line JSON), builds the contact
// sheets with the shared 661B sheet() helper, and copies the measured crops.
// EVIDENCE-BUDGET: per status target one sheet (cols 1024 light | 1024 dark | 1440 light | 1440 dark,
// full page); one hero sheet (every target's hero card at the 4 variants); Carol; record payment;
// draft line table / editors; consumers; focus. Crops as separate PNGs.
// Usage: PHASE=after STACK="<provenance>" node 13-assemble-after.cjs
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const STACK = process.env.STACK || "(STACK not given)";
const rd = (f) => (fs.existsSync(path.join(L.RAW, f)) ? JSON.parse(fs.readFileSync(path.join(L.RAW, f), "utf8")) : null);
const VARIANTS = ["1024-light", "1024-dark", "1440-light", "1440-dark"];
const VCOLS = "cols 1024 light | 1024 dark | 1440 light | 1440 dark";
const TARGETS = ["draft", "approved", "sent-overdue", "sent", "sent-takeon", "partial", "paid", "void"];

(async () => {
  const A = rd("persona-alice.json"), Cc = rd("persona-carol.json"), IA = rd("interact.json"), FC = rd("focus-crops.json");
  const now = new Date().toISOString();
  const slim = (m) => { const d = m.detail || {}; return { url: m.url, title: m.title, status: m.status, h1: m.h1, h1InMain: m.h1Count, h1Page: m.h1Page, mainIdCount: m.mainCount, mainElCount: m.mainElCount, docHeight: m.docHeight, docHOverflowPx: m.hOverflow, mainInnerWidth: d.mainInner, pageFrame: d.pageFrame, hero: m.hero, counts: m.counts, tables: m.tables, empties: m.empties, headings: m.headings, customFieldsLine: m.customFieldsLine, totals: d.totals, testids: d.testids, indicators: d.indicators, readonly: d.readonly, draftForm: d.draftForm, generatedDocs: d.generatedDocs, collectionActivity: d.collectionActivity, audit: d.audit, sections: d.sections, backLink: d.backLink, generateDoc: d.generateDoc, lowContrast: m.lowContrast, notFound: m.notFound, bodyTextHead: m.h1.length && m.h1[0] !== "Page not found" ? undefined : m.bodyTextHead }; };
  const meas = { capturedAt: now, stack: STACK, phase: L.PHASE, note: "targets = the OBS-before fee notes (ids + API state at capture). variants[target][<w>-<theme>] = OBS-after HERO probe (h1, identifier chips, status slot, meta row + rows, actions, tile grid cols×rows, tiles with label / value / hint / overflow / contrast / danger icon, totals rows + tile↔totals agreement), single-instance counts, TABLES (table vs scroller, columns, row testids, line counts), EMPTIES, HEADINGS, and the OBS-before DETAIL fields that still apply. tileGrid = per variant per target. interact = draft line table cell lines (Range rects) + editors, record payment on INV-0009, keyboard walks, DetailHero consumers. focus = focus ring per Tab stop.", targets: { ...(A ? A.targets : {}), ...(Cc ? Cc.targets : {}) }, caps: { alice: A && A.caps, carol: Cc && Cc.caps }, variants: {}, tileGrid: {}, surfaces: { ...(A ? A.surfaces : {}), ...(Cc ? Cc.surfaces : {}) }, interact: IA && { draft: IA.draft, payment: IA.payment, keyboard: IA.keyboard, consumers: IA.consumers }, focus: FC && { focus: FC.focus, draftScroll: FC.draftScroll, crops: FC.crops } };
  for (const R of [A, Cc].filter(Boolean))
    for (const [k, vv] of Object.entries(R.variants)) {
      meas.variants[k] = {};
      for (const [v, m] of Object.entries(vv)) {
        meas.variants[k][v] = slim(m);
        const h = m.hero || {};
        (meas.tileGrid[v] = meas.tileGrid[v] || {})[k] = h.tileGrid ? { tiles: h.tiles.length, cols: h.tileGrid.cols, rows: h.tileGrid.rows, gridW: h.tileGrid.box.w, tileW: h.tiles.map((x) => x.box.w), hero: `${h.heroBox.w}x${h.heroBox.h}`, heroRowH: h.heroRow && h.heroRow.h, metaRows: h.metaRows, actionsRows: h.actionsRows, valueOverflow: h.tiles.filter((x) => x.valueScroll.scrollWidth > x.valueScroll.clientWidth || x.overflowEls.length).length, docHOverflowPx: m.hOverflow } : null;
      }
    }
  const sel = { capturedAt: now, stack: STACK, note: "live DOM counts per selector at 1024×768 light per target (the OBS-before SEL-COMPAT list, unchanged).", alice: A && A.selCompat, carol: Cc && Cc.selCompat };
  const cons = { capturedAt: now, stack: STACK, totals: { pagesOpened: 0, errors: 0, warnings: 0, hydration: 0 }, byRun: {}, distinct: [] };
  for (const [nm, R] of [["01b alice", A], ["01b carol", Cc]]) if (R) { cons.byRun[nm] = R.console; for (const x of ["pagesOpened", "errors", "warnings", "hydration"]) cons.totals[x] += R.console[x]; }
  for (const [nm, x] of [["11-interact", IA], ["12-focus-crops", FC]]) if (x) cons.byRun[nm] = { messages: x.console };
  const allMsgs = Object.values(cons.byRun).flatMap((x) => x.messages || []);
  cons.totals.allMessages = allMsgs.length;
  cons.totals.allErrors = allMsgs.filter((m) => m.type === "error" || m.type === "pageerror").length;
  cons.totals.allWarnings = allMsgs.filter((m) => m.type === "warning").length;
  cons.totals.allHydration = allMsgs.filter((m) => /hydrat/i.test(m.text)).length;
  const dm = new Map();
  for (const m of allMsgs) { const k = `${m.type}|${m.text}`; const e = dm.get(k) || { type: m.type, text: m.text, count: 0, labels: new Set() }; e.count++; e.labels.add(m.label); dm.set(k, e); }
  cons.distinct = [...dm.values()].map((e) => ({ ...e, labels: [...e.labels] }));
  fs.writeFileSync(path.join(L.OUT, "measurements.json"), JSON.stringify(meas) + "\n");
  fs.writeFileSync(path.join(L.OUT, "sel-compat.json"), JSON.stringify(sel) + "\n");
  fs.writeFileSync(path.join(L.OUT, "console.json"), JSON.stringify(cons) + "\n");

  const T = {};
  for (const r of [A, Cc, IA, FC].filter(Boolean)) for (const [k, f, c] of r.tiles || []) T[k] = { f, c };
  const tile = (key) => [T[key] ? T[key].c : `${key} (not shot)`, T[key] ? T[key].f : null];
  const browser = await L.chromium.launch();
  const written = [];
  const sheet = async (name, title, tiles, cols, tileW) => { await L.sheet(browser, path.join(L.OUT, name), title, tiles, cols, tileW); written.push(name); };
  const H = (s) => `666B ${L.PHASE} @495c948c6 — ${s}`;
  const LBL = { draft: "DRAFT (seeded)", approved: "APPROVED", "sent-overdue": "SENT overdue (due 1 Nov 2026)", sent: "SENT not overdue (INV-0001)", "sent-takeon": "SENT take-on PS-1001 (overdue, badge)", partial: "PARTIALLY_PAID", paid: "PAID", void: "VOID" };
  for (const k of TARGETS) if (A && A.targets[k]) await sheet(`sheet-${k}-alice.png`, H(`/invoices/[id] ${A.targets[k].invoiceNumber || "Draft"} — ${LBL[k]}, Alice (owner); ${VCOLS}, full page`), VARIANTS.map((v) => tile(`s__${k}-alice-${v}`)), 4, 420);
  if (A) await sheet("sheet-hero-alice.png", H(`the DetailHero card per status, Alice; rows = draft, approved, sent-overdue, sent, sent-takeon, partial, paid, void (before the INV-0009 payment); ${VCOLS}`), TARGETS.flatMap((k) => VARIANTS.map((v) => tile(`hero__${k}-alice-${v}`))), 4, 520);
  if (Cc) await sheet("sheet-carol.png", H(`/invoices/[id] (INV-0008), Carol (Member, no INVOICING) → 404 "Page not found"; ${VCOLS}`), VARIANTS.map((v) => tile(`s__carol-404-carol-${v}`)), 4, 420);
  if (IA) {
    await sheet("sheet-record-payment-alice.png", H("Record Payment on INV-0009 (SENT, overdue), Alice 1440 light, full page: before | form filled R 500,00 | after Confirm (NO reload) | after reload | hero after reload"), ["ia__pay-0-before", "ia__pay-1-filled", "ia__pay-2-after-no-reload", "ia__pay-3-after-reload", "ia__pay-hero-after-reload"].map(tile), 5, 330);
    await sheet("sheet-draft-lines-alice.png", H("seeded Draft line items: 1024 | 1024 Edit focused by keyboard | 1440; Add Line opened + Edit Line opened (both cancelled, nothing saved), 1440 light"), ["ia__draft-lines-1024", "ia__draft-lines-1024-focused-edit", "ia__draft-lines-1440", "ia__draft-add-line-open", "ia__draft-edit-line-open"].map(tile), 3, 520);
    await sheet("sheet-consumers-alice.png", H("DetailHero consumers at 1440 light (viewport): matter Website Redesign | client Acme Corp | dashboard"), ["ia__consumer-matter", "ia__consumer-client", "ia__consumer-dashboard"].map(tile), 3, 520);
  }
  if (FC) await sheet("sheet-focus-alice.png", H("keyboard focus on INV-0010 at 1440: Tab stops 0–3 (HelpTip, Preview, Record Payment, Payment History receipt link), light row then dark row"), ["light", "dark"].flatMap((t) => [0, 1, 2, 3].map((i) => tile(`focus__${t}-${i}`))), 4, 420);
  for (const c of (FC && FC.crops) || []) { if (!c.key.startsWith("crop__")) continue; const src = path.join(L.RAW, `${c.key}.png`); if (fs.existsSync(src)) { const nm = `${c.key.replace(/^crop__/, "crop-")}.png`; fs.copyFileSync(src, path.join(L.OUT, nm)); written.push(nm); } }
  for (const [k, nm] of [["hero__sent-overdue-alice-1440-light", "crop-hero-overdue-1440.png"], ["hero__sent-overdue-alice-1024-light", "crop-hero-overdue-1024.png"], ["hero__sent-takeon-alice-1440-dark", "crop-hero-takeon-1440-dark.png"], ["hero__draft-alice-1440-light", "crop-hero-draft-1440.png"]]) { const src = path.join(L.RAW, `${k}.png`); if (fs.existsSync(src)) { fs.copyFileSync(src, path.join(L.OUT, nm)); written.push(nm); } }
  await browser.close();
  console.log("written", written.length, written.join(" "));
  console.log("console totals", JSON.stringify(cons.totals), JSON.stringify(cons.distinct.map((d) => [d.type, d.text.slice(0, 80), d.count])));
  console.log("tileGrid", JSON.stringify(Object.fromEntries(Object.entries(meas.tileGrid).map(([v, o]) => [v, Object.fromEntries(Object.entries(o).filter(([, x]) => x).map(([k, x]) => [k, `${x.cols}x${x.rows} hero ${x.hero}`]))]))));
})();
