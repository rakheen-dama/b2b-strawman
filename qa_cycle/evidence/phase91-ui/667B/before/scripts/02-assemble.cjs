// 667B step 2: merge $RAW/capture.json (01-capture) into ../measurements.json, ../sel-compat.json,
// ../console.json, ../discover.json (single-line JSON) and build the contact sheets with the shared
// 661B sheet() helper. Measured-kink crops are copied as separate PNGs.
// EVIDENCE-BUDGET (18 PNGs): one sheet per surface × persona (cols 1024 light | 1024 dark | 1440 light
// | 1440 dark, full page; some sheets append variant tiles), two variants sheets (reconciliation,
// investments) and 5 crops (K-91 register at 1024, K-76 step badges 1440 + 1024, K-36 report card,
// the reconciliation card on page 2 at 1024).
// Usage: PHASE=before|after STACK="<provenance>" node 02-assemble.cjs
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const STACK = process.env.STACK || "(STACK not given)";
const c = JSON.parse(fs.readFileSync(path.join(L.RAW, "capture.json"), "utf8"));
const seed = L.readSeed();
const V4 = ["1024-light", "1024-dark", "1440-light", "1440-dark"];
const raw = (f) => path.join(L.RAW, f);
const tile = (key, who, v) => [`${key} · ${who} · ${v} · ${(c.pages[key] && c.pages[key][`${who}/${v}`] || {}).url || ""}`, raw(`${key}--${who}--${v}.png`)];
const intTile = (cap, k, who, v) => { const i = (c.interactions[`${who}/${v}`] || {})[k]; return [cap, i && i.file ? i.file : null]; };

(async () => {
  const now = new Date().toISOString();
  // ---------- measurements ----------
  const slim = (m, v) => {
    const light = v.endsWith("light");
    const { testids, ...rest } = m;
    return { ...rest, tables: m.tables.map((t) => ({ ...t, rows: t.rows.map((r) => (light ? r : { testid: r.testid, cells: r.cells, h: r.h, badges: r.badges.map((b) => ({ text: b.text, fg: b.fg, bg: b.bg, ratio: b.ratio })), lastCellRightMinusScroller: r.lastCellRightMinusScroller })) })) };
  };
  const pages = {};
  for (const [k, vv] of Object.entries(c.pages)) { pages[k] = {}; for (const [pv, m] of Object.entries(vv)) pages[k][pv] = slim(m, pv); }
  const tbl = (k, pv, id) => { const m = c.pages[k] && c.pages[k][pv]; return m && m.tables.find((t) => t.testid === id); };
  const pick = (t) => t && { width: t.width, scroller: t.scroller && { clientWidth: t.scroller.clientWidth, scrollWidth: t.scroller.scrollWidth, overflowPx: t.scroller.overflowPx }, card: t.card, headers: t.headers.map((h) => `${h.text || h.sr}:${h.w}@${h.x}`), lastCellRight: t.rows.map((r) => r.lastCellRightMinusScroller) };
  const kinks = {
    note: "headline numbers per kink, copied from pages[] (alice unless stated). overflowPx = scroller scrollWidth − clientWidth; lastCellRight = last cell's right edge − scroller's right edge per row (> 0 = out of view).",
    k91: Object.fromEntries(["alice/1024-light", "alice/1024-dark", "alice/1440-light", "carol/1024-light", "carol/1440-light"].map((pv) => [pv, pick(tbl("inv", pv, "investments-table"))])),
    k91FundNote: Object.fromEntries(Object.entries(c.pages.inv || {}).map(([pv, m]) => [pv, m.sel.fundNoteLines])),
    k36: Object.fromEntries(["alice/1440-light", "alice/1440-dark", "alice/1024-light"].map((pv) => { const m = c.pages.reports && c.pages.reports[pv]; return [pv, m && { runReport: m.reportCards.map((r) => r.run && `${r.run.fg} on ${r.run.bg} ${r.run.ratio}`).filter((x, i, a) => a.indexOf(x) === i), investments5pct: ((c.pages.inv[pv] || {}).teal || []).filter((t) => /statutory/.test(t.text)).map((t) => `${t.fg} on ${t.bg} ${t.ratio}`).filter((x, i, a) => a.indexOf(x) === i) }]; })),
    k76: Object.fromEntries(Object.entries(c.pages["recon-new"] || {}).map(([pv, m]) => [pv, m.steps && { indicator: m.stepIndicator, badges: m.steps.map((s) => `${s.num} ${s.label}: ${s.fg} on ${s.bg} ${s.ratio} (${s.box && s.box.w}×${s.box && s.box.h})`) }])),
    k95: {
      reconNewCarol: c.interactions["carol/1440-light"] && c.interactions["carol/1440-light"].wizardStep1,
      reconNewCarolButtons: c.pages["recon-new"] && c.pages["recon-new"]["carol/1440-light"] && c.pages["recon-new"]["carol/1440-light"].buttons,
      reconListCarolButtons: c.pages.recon && c.pages.recon["carol/1440-light"] && c.pages.recon["carol/1440-light"].buttons,
      reconOutOfRangeCarol: c.pages["recon-oor"] && c.pages["recon-oor"]["carol/1440-light"] && { found: c.pages["recon-oor"]["carol/1440-light"].found, empties: c.pages["recon-oor"]["carol/1440-light"].empties.map((e) => e.text) },
      landingCarolLinks: c.pages.landing && c.pages.landing["carol/1440-light"] && c.pages.landing["carol/1440-light"].header,
    },
    reconTable: Object.fromEntries(["recon", "recon-p2"].flatMap((k) => ["alice/1024-light", "alice/1440-light", "carol/1024-light"].map((pv) => [`${k} ${pv}`, pick(tbl(k, pv, "reconciliations-table"))]))),
    openStatements: Object.fromEntries(["alice/1024-light", "alice/1440-light", "carol/1440-light"].map((pv) => [pv, pick(tbl("recon", pv, "open-statements-table"))])),
    otherCreditorsFundLedger: Object.fromEntries(["alice/1024-light", "alice/1440-light"].map((pv) => [pv, pick(tbl("oc", pv, "fund-ledger-table"))])),
  };
  const meas = {
    capturedAt: now, captureRunAt: c.at, stack: STACK, phase: L.PHASE,
    note: "pages[target][persona/variant] = probe of the page: shell counts (h1 / main), header block + page-root blocks (vertical rhythm), doc overflow, every table (scroller clientWidth/scrollWidth, card width, header widths + x, rows with cells, cell boxes vs the scroller, badges with composite fg/bg/contrast, links), empties, pager, 'N found', report cards (Run Report contrast), teal utilities with contrast, the wizard step indicator + badges, buttons, inputs, headings, cards, sel (spec-selector counts). Dark variants keep a slim row form. kinks = headline numbers. interactions = URL before → after per action, dialogs opened and cancelled. api = the endpoint's order and totals.",
    seed: { account: seed.account, accounts: seed.accounts, ids: seed.ids, counts: seed.counts, steps: seed.steps, dataBefore: seed.dataBefore, csv: seed.csv },
    caps: c.caps, kinks, interactions: c.interactions, api: c.api, crops: c.crops, pages,
  };
  fs.writeFileSync(path.join(L.OUT, "measurements.json"), JSON.stringify(meas));
  // ---------- discover ----------
  const disc = { capturedAt: now, stack: STACK, note: "every data-testid on the page (UUIDs → <id>) with its count, per target × persona × variant" };
  for (const [k, vv] of Object.entries(c.pages)) { disc[k] = {}; for (const [pv, m] of Object.entries(vv)) disc[k][pv] = m.testids; }
  fs.writeFileSync(path.join(L.OUT, "discover.json"), JSON.stringify(disc));
  // ---------- sel-compat ----------
  const T = (id) => (m) => m.testids[id] || 0;
  const H1 = (s) => (m) => m.h1Main.filter((h) => h === s).length;
  const SEL = {
    'h1 "Reconciliation" (day-45:44)': H1("Reconciliation"),
    'h1 "New Reconciliation" (day-45:58)': H1("New Reconciliation"),
    'h1 "Investments" (day-60:52)': H1("Investments"),
    'h1 "Trust Reports"': H1("Trust Reports"),
    "testid new-reconciliation-btn (day-45:48,57)": T("new-reconciliation-btn"),
    "testid reconciliations-table": T("reconciliations-table"),
    "testid reconciliation-row-<id>": T("reconciliation-row-<id>"),
    "testid list-locked-<id> (trust-reconciliation.test:1875)": T("list-locked-<id>"),
    "testid open-statements / open-statements-table": (m) => `${m.testids["open-statements"] || 0}/${m.testids["open-statements-table"] || 0}`,
    "testid statement-row-* (prefix; 667.6 target name)": (m) => m.sel.statementRowPrefix,
    "testid open-statement-row-* (prefix; today's name)": (m) => m.sel.openStatementRowPrefix,
    "testid discard-statement-<id>": T("discard-statement-<id>"),
    "testid step-indicator / step-select / account-select / period-end-input / step-next-btn": (m) => ["step-indicator", "step-select", "account-select", "period-end-input", "step-next-btn"].map((k) => m.testids[k] || 0).join("/"),
    "testid reconciliation-split-pane (step 4 only)": T("reconciliation-split-pane"),
    "testid investments-page": T("investments-page"),
    "testid investments-table": T("investments-table"),
    "testid investment-row-* (prefix)": (m) => m.sel.investmentRowPrefix,
    "testid maturity-alert": T("maturity-alert"),
    "testid empty-investments (day-60:247)": T("empty-investments"),
    "testid investment-basis-filter": T("investment-basis-filter"),
    'button "Place Investment" (day-60:246)': (m) => Object.entries(m.buttons).filter(([k]) => k.startsWith("Place Investment")).reduce((n, [, v]) => n + v, 0),
    "investments columnheaders (exact)": (m) => { const t = m.tables.find((x) => x.testid === "investments-table"); return t ? t.headers.map((h) => h.sr).join(" | ") : 0; },
    "testid trust-reports-page": T("trust-reports-page"),
    "testid report-card-* (day-90:137 = 9)": (m) => m.sel.reportCardPrefix,
    "testid trust-reports-financial-visibility-required": T("trust-reports-financial-visibility-required"),
    'text "Section 35" (leaf elements)': (m) => m.sel.textSection35,
    'text "Investment Register" (leaf elements)': (m) => m.sel.textInvestmentRegister,
    "testid monthly-list-page": T("monthly-list-page"),
    "testid monthly-list-creditors": T("monthly-list-creditors"),
    "testid monthly-list-matter / monthly-list-flags / monthly-list-after-lock / monthly-list-brought-forward": (m) => ["monthly-list-matter", "monthly-list-flags", "monthly-list-after-lock", "monthly-list-brought-forward"].map((k) => m.testids[k] || 0).join("/"),
    "testid other-creditors-page": T("other-creditors-page"),
    "testid fund-ledger-section / fund-ledger-table / unidentified-deposits-section": (m) => ["fund-ledger-section", "fund-ledger-table", "unidentified-deposits-section"].map((k) => m.testids[k] || 0).join("/"),
    'a "Interest" exact, whole document (day-00:279)': (m) => m.sel.navInterestExactAll,
    'th/columnheader "LPFF Rate" (day-90:244)': (m) => m.sel.lpffRateHeaders,
    "main tbody tr": (m) => m.sel.mainTbodyTr,
  };
  const at = [["recon", "1440-light"], ["recon", "1024-light"], ["recon-p2", "1440-light"], ["recon-oor", "1440-light"], ["recon-new", "1440-light"], ["mlist", "1440-light"], ["inv", "1440-light"], ["inv", "1024-light"], ["inv-firm", "1440-light"], ["reports", "1440-light"], ["oc", "1440-light"], ["landing", "1440-light"]];
  const sel = { capturedAt: now, stack: STACK, note: "live DOM counts per selector, per target/persona/variant (only non-zero entries kept; a selector whose every entry is 0 is listed with {} — absent everywhere). Strings carry text.", counts: {} };
  for (const [name, fn] of Object.entries(SEL)) {
    sel.counts[name] = {};
    for (const [k, v] of at) for (const who of ["alice", "carol"]) { const m = c.pages[k] && c.pages[k][`${who}/${v}`]; if (!m) continue; const val = fn(m); if (val && val !== "0/0" && !/^0(\/0)+$/.test(String(val))) sel.counts[name][`${k}/${who}/${v}`] = val; }
  }
  sel.investmentRegisterReportHeaders = c.interactions["alice/1440-light"] && c.interactions["alice/1440-light"].investmentRegisterRun;
  sel.section35CardClick = c.interactions["alice/1440-light"] && c.interactions["alice/1440-light"].reportCardSection35;
  fs.writeFileSync(path.join(L.OUT, "sel-compat.json"), JSON.stringify(sel));
  // ---------- console ----------
  const msgs = c.console;
  const pagesOpened = Object.values(c.pages).reduce((n, vv) => n + Object.keys(vv).length, 0);
  const cons = { capturedAt: now, stack: STACK, totals: { capturePageLoads: pagesOpened, messages: msgs.length, errors: msgs.filter((m) => m.type === "error" || m.type === "pageerror").length, warnings: msgs.filter((m) => m.type === "warning").length, hydration: msgs.filter((m) => /hydrat/i.test(m.text)).length }, messages: msgs };
  fs.writeFileSync(path.join(L.OUT, "console.json"), JSON.stringify(cons));
  // ---------- sheets ----------
  const b = await L.chromium.launch();
  const S = (f, title, tiles, cols, w) => L.sheet(b, path.join(L.OUT, f), title, tiles, cols, w);
  const head = (s) => `667B ${L.PHASE} — ${s} — ${STACK}`;
  const four = "cols 1024 light | 1024 dark | 1440 light | 1440 dark, full page";
  await S("sheet-reconciliation-alice.png", head(`Reconciliation list page 1 · Alice (owner) · ${four}`), V4.map((v) => tile("recon", "alice", v)), 4, 520);
  await S("sheet-reconciliation-carol.png", head("Reconciliation · Carol (member, VIEW_TRUST) · 4 variants of page 1 + page 2, ?page=9, trust landing (K-95) at 1440 light"), [...V4.map((v) => tile("recon", "carol", v)), tile("recon-p2", "carol", "1440-light"), tile("recon-oor", "carol", "1440-light"), tile("landing", "carol", "1440-light")], 4, 480);
  await S("sheet-reconciliation-variants-alice.png", head("Reconciliation variants · Alice · page 2 (?page=1) × 4, ?page=9 (out of range), Discard dialog opened then cancelled (1440 light, 1024 dark)"), [...V4.map((v) => tile("recon-p2", "alice", v)), tile("recon-oor", "alice", "1440-light"), intTile("Discard dialog · 1440 light (Cancel)", "discardDialog", "alice", "1440-light"), intTile("Discard dialog · 1024 dark (Cancel)", "discardDialog", "alice", "1024-dark")], 4, 480);
  await S("sheet-recon-new-alice.png", head(`New Reconciliation wizard, step 1 · Alice · ${four}`), V4.map((v) => tile("recon-new", "alice", v)), 4, 520);
  await S("sheet-recon-new-carol.png", head(`New Reconciliation wizard, step 1 · Carol (K-95a) · ${four}`), V4.map((v) => tile("recon-new", "carol", v)), 4, 520);
  await S("sheet-monthly-list-alice.png", head(`Monthly list (31 Oct 2026, from the List locked link) · Alice · ${four}`), V4.map((v) => tile("mlist", "alice", v)), 4, 520);
  await S("sheet-monthly-list-carol.png", head(`Monthly list · Carol · ${four}`), V4.map((v) => tile("mlist", "carol", v)), 4, 520);
  await S("sheet-investments-alice.png", head(`Investments · Alice (MANAGE_TRUST: Place Investment + Actions) · ${four}`), V4.map((v) => tile("inv", "alice", v)), 4, 520);
  await S("sheet-investments-carol.png", head("Investments · Carol (no Actions) · 4 variants + empty register ?investmentBasis=FIRM_DISCRETION (1440 light)"), [...V4.map((v) => tile("inv", "carol", v)), tile("inv-firm", "carol", "1440-light")], 5, 440);
  await S("sheet-investments-variants-alice.png", head("Investments variants · Alice · empty register ?investmentBasis=FIRM_DISCRETION × 4, ?investmentBasis=CLIENT_INSTRUCTION, basis filter open (1440 light, 1024 dark)"), [...V4.map((v) => tile("inv-firm", "alice", v)), tile("inv-client", "alice", "1440-light"), intTile("basis filter open · 1440 light", "basisFilter", "alice", "1440-light"), intTile("basis filter open · 1024 dark", "basisFilter", "alice", "1024-dark")], 4, 480);
  await S("sheet-reports-alice.png", head(`Trust Reports · Alice · ${four} + the Investment Register report run (card → Run Report, 1440 light)`), [...V4.map((v) => tile("reports", "alice", v)), intTile("report-card-investment-register → Run Report · 1440 light", "investmentRegisterRun", "alice", "1440-light")], 5, 440);
  await S("sheet-reports-carol.png", head(`Trust Reports · Carol (no FINANCIAL_VISIBILITY) · ${four}`), V4.map((v) => tile("reports", "carol", v)), 4, 520);
  await S("sheet-other-creditors.png", head("Other trust creditors (as migrated in 664B) · Alice × 4 + Carol 1440 light"), [...V4.map((v) => tile("oc", "alice", v)), tile("oc", "carol", "1440-light")], 5, 440);
  const crops = { "k91-1024-light": "crop-k91-investments-1024-light.png", "k76-alice-1440-light": "crop-k76-steps-1440-light.png", "k76-alice-1024-light": "crop-k76-steps-1024-light.png", "k36-1440-light": "crop-k36-report-card-1440-light.png", "recon-p2-1024-light": "crop-recon-p2-1024-light.png" };
  for (const [k, f] of Object.entries(crops)) if (c.crops[k] && c.crops[k].file && fs.existsSync(c.crops[k].file)) fs.copyFileSync(c.crops[k].file, path.join(L.OUT, f));
  await b.close();
  console.log("assembled", fs.readdirSync(L.OUT).join(" "));
})().catch((e) => { console.error("ASSEMBLE FAIL", e); process.exit(1); });
