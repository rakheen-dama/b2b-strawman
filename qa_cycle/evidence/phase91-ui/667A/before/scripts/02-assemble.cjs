// 667A step 2: merge $RAW/capture.json (01-capture) into ../measurements.json, ../sel-compat.json,
// ../console.json, ../discover.json (single-line JSON) and build the contact sheets with the shared
// 661B sheet() helper. Crops (K-10) are copied as separate PNGs.
// EVIDENCE-BUDGET (14 PNGs): one sheet per surface × persona (cols 1024 light | 1024 dark | 1440 light
// | 1440 dark, full page; the Carol sheets append her page-2 / empty / awaiting tiles), Bob's
// second-approver sheet, one variants sheet per surface (Alice), approval rows, reversal, 2 K-10 crops.
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

(async () => {
  const now = new Date().toISOString();
  // ---------- measurements ----------
  const slim = (m, v) => ({
    url: m.url, httpStatus: m.httpStatus, title: m.title, h1Main: m.h1Main, h1Page: m.h1Page, mainIdCount: m.mainIdCount, mainElCount: m.mainElCount, docHOverflowPx: m.docHOverflowPx, docHeight: m.docHeight, mainInnerWidth: m.mainInnerWidth, notFound: m.notFound, h1Style: m.h1Style,
    tables: m.tables.map((t) => ({ testid: t.testid, width: t.width, scroller: t.scroller, card: t.card, headers: t.headers, rowCount: t.rowCount, rowTestidPrefixes: t.rowTestidPrefixes, rows: v.endsWith("light") ? t.rows : undefined })),
    pager: m.pager, found: m.found, statusLinks: m.statusLinks, typeLinks: m.typeLinks, nonZeroLink: m.nonZeroLink, inputs: m.inputs, buttons: m.buttons, ledgerTotals: m.ledgerTotals, clientSummary: m.clientSummary, printStatement: m.printStatement, genStatementTitle: m.genStatementTitle, headings: m.headings, k10: m.k10 && m.k10.length ? m.k10 : undefined, k10SpacedOnly: m.k10SpacedOnly, bodyHead: m.notFound ? m.bodyHead : undefined,
  });
  const pages = {};
  for (const [k, vv] of Object.entries(c.pages)) { pages[k] = {}; for (const [pv, m] of Object.entries(vv)) pages[k][pv] = slim(m, pv); }
  const k10 = {};
  for (const [pv, m] of Object.entries(c.pages.tx || {})) if (pv.startsWith("alice")) k10[pv] = { rows: m.k10, spacedOnly: m.k10SpacedOnly };
  const meas = {
    capturedAt: now, captureRunAt: c.at, stack: STACK, phase: L.PHASE,
    note: "pages[target][persona/variant] = probe of the page (shell counts, doc overflow, every table: scroller clientWidth/scrollWidth, header widths, rows with cells/buttons on light variants, pager, filter-link hrefs, inputs, buttons, ledger totals, summary). k10 = the two 150-char reference rows (refText = the text's own line boxes; dateCell = the Date column) + spacedOnly = same probe with the unbroken-token row display:none. interactions = URL before → after for each filter/pager/search action (Alice 1440 light). api = ledger card vs history page 0 running balances (X2) + transaction totals.",
    seed: { account: seed.account, clients: seed.clients, matters: seed.matters, members: seed.members, ids: seed.ids, final: seed.final, ui: seed.ui && { approve: seed.ui.approve, reversal: seed.ui.reversal } },
    caps: c.caps, k10, interactions: c.interactions, api: c.api, pages,
  };
  fs.writeFileSync(path.join(L.OUT, "measurements.json"), JSON.stringify(meas));
  // ---------- discover (all testids per page, normalised) ----------
  const disc = { capturedAt: now, stack: STACK, note: "every data-testid on the page (UUIDs → <id>) with its count, per target × persona × variant" };
  for (const [k, vv] of Object.entries(c.pages)) { disc[k] = {}; for (const [pv, m] of Object.entries(vv)) disc[k][pv] = m.testids; }
  fs.writeFileSync(path.join(L.OUT, "discover.json"), JSON.stringify(disc));
  // ---------- sel-compat ----------
  const I = seed.ids;
  const SEL = {
    'h1 "Transactions" (helpers/legal-lifecycle.ts:322, day-90:268, trust-balanced-month:309, day-05:91)': (m) => m.h1Main.filter((h) => h === "Transactions").length,
    "testid transactions-table": (m) => m.testids["transactions-table"] || 0,
    "testid transaction-row-<id> (rows on page; :396)": (m) => m.testids["transaction-row-<id>"] || 0,
    'button "Record Transaction" (#record-transaction-button)': (m) => m.sel.recordTxButton,
    'link "Record Transaction" (helpers:303, day-00:255, day-90:461,478, day-05:205 — landing page)': (m) => m.sel.recordTxLink,
    "testid approval-actions-<id>": (m) => m.testids["approval-actions-<id>"] || 0,
    "testid approve-button / reject-button / reverse-button": (m) => `${m.testids["approve-button"] || 0}/${m.testids["reject-button"] || 0}/${m.testids["reverse-button"] || 0}`,
    "testid approval-progress-<id>": (m) => m.testids["approval-progress-<id>"] || 0,
    "main tbody tr (trustTransactionRow helpers:329)": (m) => m.sel.mainTbodyTr,
    "testid client-ledgers-page": (m) => m.testids["client-ledgers-page"] || 0,
    "testid client-ledgers-table": (m) => m.testids["client-ledgers-table"] || 0,
    "testid ledger-row-<id>": (m) => m.testids["ledger-row-<id>"] || 0,
    "testid empty-state (client-ledgers/page.tsx:192)": (m) => m.testids["empty-state"] || 0,
    "testid client-search": (m) => m.testids["client-search"] || 0,
    'button "Search" exact (day-07-14:226, day-30:240)': (m) => m.sel.searchButtonExact,
    "testid non-zero-filter": (m) => m.testids["non-zero-filter"] || 0,
    'link "Client Ledgers" (day-07-14:193 — landing page)': (m) => m.sel.clientLedgersLink,
    "testid client-detail-page (trust-balanced-month:507)": (m) => m.testids["client-detail-page"] || 0,
    "testid client-summary (day-60:362)": (m) => m.testids["client-summary"] || 0,
    "testid history-table": (m) => m.testids["history-table"] || 0,
    "testid history-row-<id>": (m) => m.testids["history-row-<id>"] || 0,
    "history-row td count (trust-balanced-month:1398 td.nth(3) = Amount)": (m) => { const t = m.tables.find((x) => x.testid === "history-table"); return t && t.rows[0] ? `${t.rows[0].tdCount} (td[3]=${t.rows[0].cells[3]})` : 0; },
    "testid ledger-totals-breakdown": (m) => m.testids["ledger-totals-breakdown"] || 0,
    "testid ledger-total-balance › dd": (m) => { const x = (m.ledgerTotals || []).find((r) => r.testid === "ledger-total-balance"); return x ? `1 "${x.dd}"${x.inSummary ? " in client-summary" : ""}` : 0; },
    "testid ledger-total-held › dd": (m) => { const x = (m.ledgerTotals || []).find((r) => r.testid === "ledger-total-held"); return x ? `1 "${x.dd}"` : 0; },
    "testid ledger-total-invested › dd": (m) => { const x = (m.ledgerTotals || []).find((r) => r.testid === "ledger-total-invested"); return x ? `1 "${x.dd}"` : 0; },
    "testid print-statement / print-statement-btn": (m) => `${m.testids["print-statement"] || 0}/${m.testids["print-statement-btn"] || 0}`,
    'text "Generate Statement" (card title)': (m) => m.genStatementTitle.length,
    'nav link "Interest" exact (day-00:279; Trust group collapsed in DOM here)': (m) => m.sel.navInterestLinkExact,
    'columnheader "LPFF Rate" (day-90:244 — reports page)': (m) => m.sel.lpffRateHeaders,
  };
  const at = [["tx", "1024-light"], ["tx-await", "1440-light"], ["tx-p2", "1440-light"], ["cl", "1024-light"], ["cl-empty", "1440-light"], ["hist", "1024-light"], ["hist-ndlovu", "1440-light"], ["hist-p2", "1440-light"], ["landing", "1440-light"]];
  const sel = { capturedAt: now, stack: STACK, note: "live DOM counts per selector, per target/persona (variant in the key). Ids: transaction-row/history-row counts are rows on that page; string values carry the text.", counts: {} };
  for (const [name, fn] of Object.entries(SEL)) {
    sel.counts[name] = {};
    for (const [k, v] of at) for (const who of ["alice", "carol", "bob"]) { const m = c.pages[k] && c.pages[k][`${who}/${v}`]; if (m) sel.counts[name][`${k}/${who}/${v}`] = fn(m); }
  }
  sel.recordTxMenu = c.interactions.recordTxMenu;
  fs.writeFileSync(path.join(L.OUT, "sel-compat.json"), JSON.stringify(sel));
  // ---------- console ----------
  const msgs = c.console.concat(L.PHASE === "before" && seed.ui ? seed.ui.console.map((m) => ({ ...m, label: `00-seed ${m.label}` })) : []);
  const pagesOpened = Object.values(c.pages).reduce((n, vv) => n + Object.keys(vv).length, 0);
  const cons = { capturedAt: now, stack: STACK, totals: { capturePageLoads: pagesOpened, messages: msgs.length, errors: msgs.filter((m) => m.type === "error" || m.type === "pageerror").length, warnings: msgs.filter((m) => m.type === "warning").length, hydration: msgs.filter((m) => /hydrat/i.test(m.text)).length }, messages: msgs };
  fs.writeFileSync(path.join(L.OUT, "console.json"), JSON.stringify(cons));
  // ---------- sheets ----------
  const b = await L.chromium.launch();
  const S = (f, title, tiles, cols, w) => L.sheet(b, path.join(L.OUT, f), title, tiles, cols, w);
  const head = (s) => `667A ${L.PHASE} — ${s} — ${STACK}`;
  await S("sheet-transactions-alice.png", head("Transactions · Alice (owner) · cols 1024 light | 1024 dark | 1440 light | 1440 dark, full page"), V4.map((v) => tile("tx", "alice", v)), 4, 560);
  await S("sheet-transactions-carol.png", head("Transactions · Carol (member, VIEW_TRUST) · 4 variants + page 2 + Awaiting Approval (1440 light)"), [...V4.map((v) => tile("tx", "carol", v)), tile("tx-p2", "carol", "1440-light"), tile("tx-await", "carol", "1440-light")], 3, 560);
  await S("sheet-transactions-bob.png", head("Transactions ?status=AWAITING_APPROVAL · Bob (admin, the second approver) · 4 variants + default list 1440 light"), [...V4.map((v) => tile("tx-await", "bob", v)), tile("tx", "bob", "1440-light")], 5, 480);
  await S("sheet-transactions-variants-alice.png", head("Transactions variants · Alice · page 2 (1440, 1024), page 3, Awaiting (1440, 1024), type+page 2, date/client/matter + page 2"), [tile("tx-p2", "alice", "1440-light"), tile("tx-p2", "alice", "1024-light"), tile("tx-p3", "alice", "1440-light"), tile("tx-await", "alice", "1440-light"), tile("tx-await", "alice", "1024-light"), tile("tx-type-p1", "alice", "1440-light"), tile("tx-adv", "alice", "1440-light")], 4, 520);
  await S("sheet-client-ledgers-alice.png", head("Client Ledgers · Alice · 4 variants"), V4.map((v) => tile("cl", "alice", v)), 4, 560);
  await S("sheet-client-ledgers-carol.png", head("Client Ledgers · Carol · 4 variants + empty search (1440 light)"), [...V4.map((v) => tile("cl", "carol", v)), tile("cl-empty", "carol", "1440-light")], 5, 480);
  await S("sheet-client-ledgers-variants-alice.png", head("Client Ledgers variants · Alice 1440 light · nonZeroOnly, search, empty-state, page=1, nonZeroOnly+search"), ["cl-nonzero", "cl-search", "cl-empty", "cl-p1", "cl-nonzero-search"].map((k) => tile(k, "alice", "1440-light")), 5, 480);
  await S("sheet-client-history-alice.png", head("Client history (Khumalo Holdings) · Alice · 4 variants"), V4.map((v) => tile("hist", "alice", v)), 4, 560);
  await S("sheet-client-history-carol.png", head("Client history (Khumalo Holdings) · Carol · 4 variants + page 2 (1440 light)"), [...V4.map((v) => tile("hist", "carol", v)), tile("hist-p2", "carol", "1440-light")], 5, 480);
  await S("sheet-client-history-variants-alice.png", head("Client history variants · Alice · page 2 (1440, 1024), type+page 2, status=REVERSED, dates, Ndlovu (awaiting rows, no invested), Acme (zero balance)"), [tile("hist-p2", "alice", "1440-light"), tile("hist-p2", "alice", "1024-light"), ...["hist-type-p1", "hist-status", "hist-dates", "hist-ndlovu", "hist-acme"].map((k) => tile(k, "alice", "1440-light"))], 4, 520);
  const rows = [];
  for (const k of ["await0", "await1"]) for (const who of ["alice", "bob", "carol"]) rows.push([`${k === "await0" ? "667A-AWAIT-0 (0 approvals)" : "667A-AWAIT-1 (1st approval by Alice)"} · ${who} · 1440 light`, raw(`row-${k}--${who}.png`)]);
  if (L.PHASE === "before") rows.push(["00-seed: AWAIT-1 before Alice's first approval (Alice)", raw("seed-approve-1-before.png")], ["00-seed: right after the click, no reload (toast)", raw("seed-approve-2-after-noreload.png")], ["00-seed: after reload", raw("seed-approve-3-after-reload.png")]);
  await S("sheet-approval-rows.png", head("Dual approval rows (?status=AWAITING_APPROVAL, 1440 light) · first approver (Alice) / second approver (Bob) / member (Carol)"), rows, 3, 700);
  const rv = [["01-capture: Reverse dialog on an eligible deposit (667A-H-22), cancelled", raw("reverse-dialog-probe.png")]];
  if (L.PHASE === "before") rv.unshift(["00-seed: 667A-REV-PAY row before (APPROVED, Reverse)", raw("seed-reverse-1-before.png")], ["00-seed: Reverse Transaction dialog, reason filled", raw("seed-reverse-2-dialog.png")], ["00-seed: after confirm, no reload (no toast)", raw("seed-reverse-3-after-noreload.png")], ["00-seed: after reload", raw("seed-reverse-4-after-reload.png")]);
  await S("sheet-reversal.png", head("Reversal · Alice 1440 light"), rv, 3, 700);
  for (const v of ["1024-light", "1440-light"]) if (c.k10Crops[v] && fs.existsSync(c.k10Crops[v].file)) fs.copyFileSync(c.k10Crops[v].file, path.join(L.OUT, `crop-k10-${v}.png`));
  await b.close();
  console.log("assembled", fs.readdirSync(L.OUT).join(" "));
})().catch((e) => { console.error("ASSEMBLE FAIL", e); process.exit(1); });
