// 666B OBS-AFTER-FIX step 23 (new; 13-assemble-after.cjs is the OBS-after assembler, its sheet set and
// "@495c948c6" titles do not fit this pass). Merges $RAW persona-alice.json / persona-carol.json (01b),
// lines.json (21), line-counts.json (22) into ../measurements.json + ../console.json and builds 5
// contact sheets + 3 crops (EVIDENCE-BUDGET).
// Usage: PHASE=after-fix STACK="<provenance>" node 23-assemble-afterfix.cjs
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const STACK = process.env.STACK || "(STACK not given)";
const rd = (f) => (fs.existsSync(path.join(L.RAW, f)) ? JSON.parse(fs.readFileSync(path.join(L.RAW, f), "utf8")) : null);
const VARIANTS = ["1024-light", "1024-dark", "1440-light", "1440-dark"];
const TARGETS = ["draft", "approved", "sent-overdue", "sent", "sent-takeon", "partial", "paid", "void"];

(async () => {
  const A = rd("persona-alice.json"), Cc = rd("persona-carol.json"), LN = rd("lines.json"), LC = rd("line-counts.json");
  const now = new Date().toISOString();
  const lineSummary = {};
  for (const [k, vv] of Object.entries(LN.lines)) {
    lineSummary[k] = {};
    for (const [v, m] of Object.entries(vv)) {
      const c = LC.counts[k] && LC.counts[k][v];
      lineSummary[k][v] = { status: m.invoice.status, number: m.invoice.number, tableWidth: m.tableWidth, scrollerClientWidth: m.scroller.clientWidth, scrollerScrollWidth: m.scroller.scrollWidth, fits: m.fits, headers: m.headers.map((h) => ({ h: h.h, w: h.w, cutPx: h.cutPx, internalClip: h.internalClip })), rowButtonsVisible: `${m.rowButtons.filter((b) => b.visibleInScroller).length}/${m.rowButtons.length}`, descriptionLines: c ? c.rows.map((r) => r.DESCRIPTION.textLines) : null, taxLines: c ? c.rows.map((r) => r.TAX && r.TAX.textLines) : null, matterLines: c ? c.rows.map((r) => r.MATTER && r.MATTER.textLines) : null, headerLines: c && c.headerLines, sectionHeaders: c ? c.sectionHeaders : null, docHOverflow: m.docHOverflow };
    }
  }
  const variants = {};
  for (const R of [A, Cc]) for (const [k, vv] of Object.entries(R.variants)) { variants[k] = {}; for (const [v, m] of Object.entries(vv)) { const h = m.hero || {}; const ph = (m.tables || []).find((t) => t.heading === "Payment History"); variants[k][v] = { status: m.status, h1: m.h1, h1InMain: h.h1Count, h1Page: h.h1PageCount, docHOverflowPx: m.hOverflow, tiles: (h.tiles || []).map((x) => ({ label: x.label, value: x.value, hint: x.hint })), agree: h.agree, meta: h.meta, dueTileEqualsHeaderDue: (() => { const t = (h.tiles || []).find((x) => x.label === "Due"); const d = (h.meta || []).find((x) => /^Due:/.test(x)); return t ? !!d && t.value === d.replace(/^Due:\s*/, "") : null; })(), actions: (h.actions || []).map((a) => `${a.text}/${a.variant}`), counts: m.counts, paymentHistory: ph ? { tableWidth: ph.measure.tableWidth, cols: ph.measure.cols.map((c) => c.h), rows: ph.rowCount, frameClientWidth: ph.frame.clientWidth, frameScrollWidth: ph.frame.scrollWidth } : null, empties: m.empties, lowContrast: m.lowContrast }; } }
  const meas = { capturedAt: now, stack: STACK, phase: L.PHASE, note: "lines = 21-afterfix-lines (table vs scroller, headers cut px, row buttons in the visible scroller) + 22-line-counts (text-node line counts; 21's whole-cell Range count over-counts the Description by 1 because of the flex span box). variants = 01b-capture hero / counts / tables per target × variant (OBS-after probes, unchanged). draft = Add Line placeholder + keyboard focus on row Edit / Delete per variant. payment = R 50,00 on INV-0010.", targets: { ...A.targets, ...Cc.targets }, lines: lineSummary, sectionHeaderCandidates: LC.sectionHeaderCandidates, draft: LN.draft, payment: LN.payment, variants, selCompat: { alice: A.selCompat, carol: Cc.selCompat } };
  fs.writeFileSync(path.join(L.OUT, "measurements.json"), JSON.stringify(meas) + "\n");
  const runs = { "01b alice": A.console.messages, "01b carol": Cc.console.messages, "21-afterfix-lines": LN.console, "22-line-counts": LC.console };
  const all = Object.values(runs).flat();
  const cons = { capturedAt: now, stack: STACK, pagesOpened01b: A.console.pagesOpened + Cc.console.pagesOpened, totals: { messages: all.length, errors: all.filter((m) => m.type === "error" || m.type === "pageerror").length, warnings: all.filter((m) => m.type === "warning").length, hydration: all.filter((m) => /hydrat/i.test(m.text)).length }, byRun: Object.fromEntries(Object.entries(runs).map(([k, v]) => [k, { n: v.length, messages: v }])) };
  fs.writeFileSync(path.join(L.OUT, "console.json"), JSON.stringify(cons) + "\n");

  const T = {};
  for (const r of [A, Cc, LN]) for (const [k, f, c] of r.tiles || []) T[k] = { f, c };
  // 21's own lines__ captions read the cells by mixed-case header ("Description"), but innerText of the
  // CSS-uppercased <th> is "DESCRIPTION", so its caption line counts came out empty: rebuilt here from
  // lineSummary (22's text-node counts).
  for (const [k, vv] of Object.entries(lineSummary)) for (const [v, s] of Object.entries(vv)) { const key = `lines__${k}-${v}`; if (T[key]) T[key].c = `${s.number || "Draft"} (${s.status}) ${v.replace("-", " ")} — table ${s.tableWidth} / scroller ${s.scrollerClientWidth} (scrollW ${s.scrollerScrollWidth}); cut headers: ${s.headers.filter((h) => h.cutPx).map((h) => h.h).join(",") || "none"}; Description ${s.descriptionLines.join("/")} lines, Tax ${s.taxLines.join("/")}${s.rowButtonsVisible !== "0/0" ? `; row Edit/Delete in view ${s.rowButtonsVisible}` : ""}`; }
  const tile = (key) => [T[key] ? T[key].c : `${key} (not shot)`, T[key] ? T[key].f : null];
  const browser = await L.chromium.launch();
  const written = [];
  const sheet = async (name, title, tiles, cols, tileW) => { await L.sheet(browser, path.join(L.OUT, name), title, tiles, cols, tileW); written.push(name); };
  const H = (s) => `666B after-fix @b8a2e2032 — ${s}`;
  await sheet("sheet-line-tables-alice.png", H("Line Items section; rows = DRAFT de6ac48f, APPROVED INV-0008, PARTIALLY_PAID INV-0010, INV-0009 (PARTIALLY_PAID); cols 1024 light | 1024 dark | 1440 light | 1440 dark"), ["draft", "approved", "partial", "inv0009"].flatMap((k) => VARIANTS.map((v) => tile(`lines__${k}-${v}`))), 4, 520);
  await sheet("sheet-draft-editors-alice.png", H("DRAFT de6ac48f: row 1 = after Add Line (placeholder shown; cancelled, nothing saved); row 2 = row Edit focused by keyboard (Tab from h1); cols 1024 light | 1024 dark | 1440 light | 1440 dark"), ["draft-addline", "draft-focus-edit"].flatMap((k) => VARIANTS.map((v) => tile(`${k}__${v}`))), 4, 520);
  await sheet("sheet-hero-alice.png", H("DetailHero card per status, Alice; rows = draft, approved, INV-0009 (PARTIALLY_PAID, overdue), INV-0001 sent, PS-1001 take-on, INV-0010 partial (before the R 50 payment), paid, void; cols 1024 light | 1024 dark | 1440 light | 1440 dark"), TARGETS.flatMap((k) => VARIANTS.map((v) => tile(`hero__${k}-alice-${v}`))), 4, 520);
  await sheet("sheet-record-payment-alice.png", H("Record Payment R 50,00 on INV-0010 (PARTIALLY_PAID), 1440 light, full page: before | after Confirm (NO reload, sentinel kept) | after reload"), ["pay-0-before", "pay-1-after-no-reload", "pay-2-after-reload"].map(tile), 3, 440);
  await sheet("sheet-carol.png", H("/invoices/[id] (INV-0008), Carol (Member, no INVOICING) → 404; cols 1024 light | 1024 dark | 1440 light | 1440 dark"), VARIANTS.map((v) => tile(`s__carol-404-carol-${v}`)), 4, 420);
  for (const [k, nm] of [["lines__draft-1024-light", "crop-draft-lines-1024-light.png"], ["lines__draft-1024-dark", "crop-draft-lines-1024-dark.png"], ["draft-focus-edit__1024-light", "crop-draft-focus-edit-1024-light.png"]]) { fs.copyFileSync(path.join(L.RAW, `${k}.png`), path.join(L.OUT, nm)); written.push(nm); }
  await browser.close();
  console.log("written", written.length, written.join(" "));
  console.log("console", JSON.stringify(cons.totals), JSON.stringify(all.map((m) => `${m.type} ${m.label} ${m.text.slice(0, 60)}`)));
})();
