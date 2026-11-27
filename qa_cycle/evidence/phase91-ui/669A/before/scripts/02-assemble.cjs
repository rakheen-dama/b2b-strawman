// 669A step 2: merge $RAW/capture.json (01-capture) into ../measurements.json, ../sel-compat.json,
// ../console.json, ../discover.json (single-line JSON) and build the contact sheets with the shared
// 661B sheet() helper. Measured-kink crops are copied as separate PNGs.
// EVIDENCE-BUDGET: one sheet per surface × persona (cols 1024 light | 1024 dark | 1440 light | 1440 dark,
// full page; some sheets append variant / interaction tiles), one shared sheet for the two 1440-light
// extras (statements empty, checklist empty), + 8 kink crops (Alice 1440 light).
// Usage: PHASE=before|after STACK="<provenance>" node 02-assemble.cjs
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const STACK = process.env.STACK || "(STACK not given)";
const c = JSON.parse(fs.readFileSync(path.join(L.RAW, "capture.json"), "utf8"));
const seed = L.readSeed();
const V4 = ["1024-light", "1024-dark", "1440-light", "1440-dark"];
const raw = (f) => path.join(L.RAW, f);
const pg = (key, pv) => (c.pages[key] || {})[pv];
const tile = (key, who, v) => [`${key} · ${who} · ${v} · ${(pg(key, `${who}/${v}`) || {}).finalUrl || ""}`, raw(`${key}--${who}--${v}.png`)];
const intTile = (cap, k, who, v, field = "file") => { const i = (c.interactions[`${who}/${v}`] || {})[k]; return [cap, i && i[field] ? i[field] : null]; };

(async () => {
  const now = new Date().toISOString();
  // ---------- measurements ----------
  const slim = (m, pv) => { const { testids, ...rest } = m; if (pv.endsWith("dark")) rest.tables = m.tables.map((t) => ({ ...t, rows: t.rows.slice(0, 4) })); return rest; };
  const pages = {};
  for (const [k, vv] of Object.entries(c.pages)) { pages[k] = {}; for (const [pv, m] of Object.entries(vv)) pages[k][pv] = slim(m, pv); }
  const tbl = (t) => ({ wrapper: t.wrapperTestid, testid: t.testid, tableWidth: t.width, tableScrollWidth: t.tableScrollWidth, scrollerClientWidth: t.scroller && t.scroller.clientWidth, scrollerScrollWidth: t.scroller && t.scroller.scrollWidth, overflowPx: t.scroller && t.scroller.overflowPx, containerWidth: t.container.clientWidth, rows: t.rowCount, columnsInView: t.columnsInView, headers: t.headers.map((h) => `${h.text}:${h.w}${h.shown ? "" : " (hidden)"}`) });
  const each = (key, fn) => Object.fromEntries(Object.entries(c.pages[key] || {}).map(([pv, m]) => [pv, fn(m, pv)]));
  const summary = {};
  for (const [k, vv] of Object.entries(c.pages)) summary[k] = Object.fromEntries(Object.entries(vv).map(([pv, m]) => [pv, { url: m.finalUrl, http: m.httpStatus, h1: m.h1Main, level1InMain: m.level1Main, mainId: m.mainIdCount, mainEl: m.mainElCount, notFound: m.notFound, hOverflow: m.docHOverflowPx, header: m.header && `${m.header.text} · ${m.header.fontSize}/${m.header.fontWeight} ${m.header.fontFamily} · ${m.header.fg} on ${m.header.bg} ${m.header.ratio}${m.header.subtitle ? " · sub: " + m.header.subtitle : ""}`, breadcrumb: m.breadcrumb, empties: m.empties.map((e) => `${e.slot === "empty-state" ? "EmptyState/" + e.variant : e.tag}${e.testid ? "#" + e.testid : ""}: ${e.text} (h ${e.h}, w ${e.w}${e.heading ? ", " + e.heading : ""}${e.hasIcon ? ", icon" : ""})`), tables: m.tables.map(tbl) }]));
  const kinks = {
    note: "headline numbers per register kink, copied from pages[] (Alice unless the key says carol).",
    "K-37": { informationRequest: each("information-request", (m) => m.links.filter((l) => /clients/.test(l.href)).map((l) => `${l.text}: ${l.fg} on ${l.bg} ${l.ratio} (${l.cls})`)), disbursementDetail: each("disbursement-detail", (m) => m.links.filter((l) => /^[0-9a-f-]{36}$|trust/i.test(l.text + l.href)).map((l) => `${l.text}: ${l.fg} on ${l.bg} ${l.ratio} ${l.fontFamily}`)) },
    "K-60": { conflictInputs: each("conflict-check", (m) => m.inputs.map((i) => `${i.name}: w ${i.w}`)), headers: Object.fromEntries(["conflict-check", "adverse-parties"].map((k) => [k, pg(k, "alice/1440-light") && pg(k, "alice/1440-light").header])) },
    "K-66": each("disbursements", (m) => { const t = m.tables.find((x) => x.rows.some((r) => /^disbursement-row-/.test(r.testid))); return t && { rows: t.rowCount, matterCells: t.rows.map((r) => r.cells[1]), dashes: t.rows.filter((r) => r.cells[1] === "—").length, matterColumnShown: (t.headers.find((h) => h.text === "Matter") || {}).shown }; }),
    "K-66-api": (seed.dataAfter || seed.dataBefore).disbursements.map((d) => `${d.description}: matter ${d.matterStatus}, in /api/matters (ACTIVE list) ${d.matterInActiveList}`),
    "K-67": Object.fromEntries(["information-request", "information-request-2"].flatMap((k) => Object.entries(c.pages[k] || {}).filter(([pv]) => pv.startsWith("alice/")).map(([pv, m]) => [`${k} ${pv}`, m.irMeta ? (m.irMeta.find((x) => /Reminder/.test(x.text)) || {}).text : null]))),
    "K-68": { dsar: each("request-detail", (m) => m.dsar), api: c.api.alice && c.api.alice.dsar },
    "K-79": Object.fromEntries(["disbursements", "disbursement-detail", "tariffs"].map((k) => [k, each(k, (m) => m.isoDates.map((d) => `${d.text}${d.mono ? " (mono)" : ""}`))])),
    "K-79-tariffsExpanded": Object.fromEntries(Object.entries(c.interactions).map(([pv, i]) => [pv, i.tariffBrowse && i.tariffBrowse.expanded && i.tariffBrowse.expanded.isoDates])),
    "K-80": each("disbursement-detail", (m) => ({ h1: m.header && `${m.header.text} (${m.header.fontSize})`, breadcrumb: m.breadcrumb, monoLinks: m.links.filter((l) => /mono/i.test(l.fontFamily)).map((l) => l.text) })),
    "K-81": each("tariffs", (m) => ({ emptyBadges: m.emptyBadges, badges: m.badges.map((b) => `${b.text || "(empty)"} w${b.w}`) })),
    "K-82": each("request-detail", (m) => m.header && `${m.header.text}: ${m.header.fontSize} weight ${m.header.fontWeight} (${m.header.cls})`),
    "K-84": each("compliance", (m) => ({ h1: m.h1Main, breadcrumb: m.breadcrumb, navActive: m.navActive })),
    "K-85": Object.fromEntries(Object.entries(c.pages["information-request"] || {}).filter(([pv]) => pv.startsWith("alice/")).map(([pv, m]) => [pv, m.irMeta && m.irMeta.map((x) => x.label && `${x.label.text}: ${x.label.fg} on ${x.label.bg} ${x.label.ratio}`)])),
    lowContrast: Object.fromEntries(Object.entries(c.pages).flatMap(([k, vv]) => Object.entries(vv).filter(([, m]) => m.lowContrast.length).map(([pv, m]) => [`${k} ${pv}`, [...new Set(m.lowContrast.map((x) => `${x.text}: ${x.fg} on ${x.bg} ${x.ratio}`))]]))),
  };
  const meas = {
    capturedAt: now, captureRunAt: c.at, stack: STACK, phase: L.PHASE,
    note: "summary[target][persona/variant] = headline facts; pages[] = the full probe (scripts/probe.cjs; dark keeps 4 table rows). kinks = register-kink numbers. interactions = URL before → after per action; menus/dialogs Escaped; searches/filters client-only; writes = the one conflict-check submit per run. api = endpoint cross-checks.",
    seed: { ids: seed.ids, writes: seed.writes, preSeedEmpty: seed.preSeedEmpty },
    writes: c.writes, carolStatementsMatter: c.carolStatementsMatter, caps: c.caps, summary, kinks, interactions: c.interactions, api: c.api, crops: c.crops, pages,
  };
  fs.writeFileSync(path.join(L.OUT, "measurements.json"), JSON.stringify(meas));
  // ---------- discover ----------
  const disc = { capturedAt: now, stack: STACK, note: "every data-testid on the page (UUIDs → <id>) with its count, per target × persona × variant" };
  for (const [k, vv] of Object.entries(c.pages)) { disc[k] = {}; for (const [pv, m] of Object.entries(vv)) disc[k][pv] = m.testids; }
  fs.writeFileSync(path.join(L.OUT, "discover.json"), JSON.stringify(disc));
  // ---------- sel-compat ----------
  const SEL = {
    'h1 "Compliance" exact (day-90-review:351, compliance-overview:23)': (m) => m.sel.h1Compliance,
    'h1 name "Compliance" as Playwright matches it (substring, case-insensitive)': (m) => m.sel.h1ComplianceSubstring,
    'h1 "Data Requests" (compliance-overview:47)': (m) => m.sel.h1DataRequests,
    'h1 "Disbursements" (day-05:149)': (m) => m.sel.h1Disbursements,
    'any heading in main containing "Disbursements" (day-05:428 substring rule)': (m) => m.sel.headingContainsDisbursements.join(" ; "),
    "testid adverse-party-table": (m) => m.sel.adversePartyTable,
    "testid disbursement-row-<id>": (m) => m.sel.disbursementRows,
    "testid disbursements-list-page": (m) => m.sel.disbursementsListPage,
    "testid statements-empty (day-30-45:265)": (m) => m.sel.statementsEmpty,
    "testid tariff-item-search": (m) => m.sel.tariffItemSearch,
    "#main-content level-1 headings": (m) => m.level1Main,
    "main#main-content count": (m) => m.mainIdCount,
    "h2 sections in main": (m) => m.sel.h2Sections.join(" | "),
    "testids (non-generic) on the page": (m) => Object.keys(m.testids).filter((k) => !/^(sidebar|nav-|breadcrumb|command|user-|org-|notification|theme|tab-item|tab-group)/.test(k)).join(","),
  };
  const sel = { capturedAt: now, stack: STACK, note: "live DOM counts per selector, per target/persona at 1440 light (plus 1024 light for list pages). Only non-zero/non-empty values kept; a selector absent everywhere is listed as {}.", counts: {} };
  const at = [];
  for (const k of Object.keys(c.pages)) for (const who of ["alice", "carol"]) for (const v of ["1440-light", "1024-light"]) if (pg(k, `${who}/${v}`) && (v === "1440-light" || ["disbursements", "adverse-parties", "requests"].includes(k))) at.push([k, who, v]);
  for (const [name, fn] of Object.entries(SEL)) {
    sel.counts[name] = {};
    for (const [k, who, v] of at) { const val = fn(pg(k, `${who}/${v}`)); if (val && val !== "0") sel.counts[name][`${k}/${who}/${v}`] = val; }
  }
  sel.interactions = Object.fromEntries(Object.entries(c.interactions).map(([pv, i]) => [pv, {
    adverseNoMatch: i.adverseSearch && i.adverseSearch.none && { adversePartyTable: i.adverseSearch.none.adversePartyTable, empties: i.adverseSearch.none.empties.map((e) => e.text) },
    disbursementsDateEmpty: i.disbursementFilters && i.disbursementFilters.dateFilterEmpty && { rows: i.disbursementFilters.dateFilterEmpty.rows, headingContainsDisbursements: i.disbursementFilters.dateFilterEmpty.headingContainsDisbursements, empties: i.disbursementFilters.dateFilterEmpty.empties.map((e) => `${e.text} (${e.heading || e.tag})`) },
    tariffExpanded: i.tariffBrowse && i.tariffBrowse.expanded && { tariffItemSearch: i.tariffBrowse.expanded.tariffItemSearch },
  }]));
  fs.writeFileSync(path.join(L.OUT, "sel-compat.json"), JSON.stringify(sel));
  // ---------- console ----------
  const msgs = c.console;
  const loads = Object.values(c.pages).reduce((n, vv) => n + Object.keys(vv).length, 0);
  fs.writeFileSync(path.join(L.OUT, "console.json"), JSON.stringify({ capturedAt: now, stack: STACK, totals: { capturePageLoads: loads, messages: msgs.length, errors: msgs.filter((m) => m.type === "error" || m.type === "pageerror").length, warnings: msgs.filter((m) => m.type === "warning").length, hydration: msgs.filter((m) => /hydrat/i.test(m.text)).length }, messages: msgs }));
  // ---------- sheets ----------
  const b = await L.chromium.launch();
  const S = (f, title, tiles, cols, w) => L.sheet(b, path.join(L.OUT, f), title, tiles, cols, w);
  const head = (s) => `669A ${L.PHASE} — ${s} — ${STACK}`;
  const four = "cols 1024 light | 1024 dark | 1440 light | 1440 dark, full page";
  const v4 = (key, who) => V4.map((v) => tile(key, who, v));
  const pre = seed.preSeedEmpty || {};
  await S("sheet-compliance-alice.png", head(`/compliance · Alice (owner) · ${four} + AI Audit tab (1440 light, 1024 dark), ?tab=ai-audit`), [...v4("compliance", "alice"), intTile("AI Audit tab clicked · 1440 light", "complianceTabs", "alice", "1440-light"), intTile("AI Audit tab clicked · 1024 dark", "complianceTabs", "alice", "1024-dark"), tile("compliance-tab-param", "alice", "1440-light")], 4, 480);
  await S("sheet-compliance-carol.png", head(`/compliance · Carol (member) · ${four}`), v4("compliance", "carol"), 4, 480);
  await S("sheet-requests-alice.png", head(`/compliance/requests · Alice · ${four} + pre-seed empty (before only) + status pills`), [...v4("requests", "alice"), ["pre-seed empty (0 requests) · 1440 light", (pre["alice/1440-light"] || {}).file || null], ["pre-seed empty · 1024 dark", (pre["alice/1024-dark"] || {}).file || null], tile("requests-RECEIVED", "alice", "1440-light"), tile("requests-IN_PROGRESS", "alice", "1440-light"), tile("requests-IN_PROGRESS", "alice", "1024-dark"), tile("requests-COMPLETED", "alice", "1440-light"), tile("requests-REJECTED", "alice", "1440-light")], 4, 480);
  await S("sheet-requests-carol.png", head(`/compliance/requests · Carol · ${four}`), v4("requests", "carol"), 4, 480);
  await S("sheet-request-detail-alice.png", head(`/compliance/requests/[id] (the seeded DSAR) · Alice · ${four}`), v4("request-detail", "alice"), 4, 520);
  await S("sheet-request-detail-carol.png", head(`/compliance/requests/[id] · Carol · ${four}`), v4("request-detail", "carol"), 4, 480);
  const ia = (pv, k) => (c.interactions[pv] || {})[k] || {};
  const cr = (k) => [`after Run Conflict Check (Pillay Holdings) · ${k}`, (ia("alice/1440-light", "conflictRun").files || {})[k] || null];
  const resolveFile = (ia("alice/1440-light", "conflictHistory").resolveDialog || {}).file || null;
  await S("sheet-conflict-check-alice.png", head(`/conflict-check · Alice · ${four} + prefill URLs, the run result (1440/1024 × light/dark), History, History 1024 dark, Resolve dialog`), [...v4("conflict-check", "alice"), tile("conflict-prefill-client", "alice", "1440-light"), tile("conflict-prefill-explicit", "alice", "1440-light"), tile("conflict-prefill-badtype", "alice", "1440-light"), cr("1440-light"), cr("1024-light"), cr("1024-dark"), cr("1440-dark"), intTile("History tab · 1440 light", "conflictHistory", "alice", "1440-light"), intTile("History tab · 1024 dark", "conflictHistory", "alice", "1024-dark"), ["Resolve dialog (Escaped) · 1440 light", resolveFile]], 4, 480);
  await S("sheet-conflict-check-carol.png", head(`/conflict-check · Carol · ${four} + ?customerId prefill, History (1440 light)`), [...v4("conflict-check", "carol"), tile("conflict-prefill-client", "carol", "1440-light"), intTile("History tab · 1440 light", "conflictHistory", "carol", "1440-light")], 3, 480);
  await S("sheet-adverse-parties-alice.png", head(`/legal/adverse-parties · Alice · ${four} + search no match (1440 light, 1024 dark), search "Pillay"`), [...v4("adverse-parties", "alice"), intTile("search no match · 1440 light", "adverseSearch", "alice", "1440-light", "noneFile"), intTile("search no match · 1024 dark", "adverseSearch", "alice", "1024-dark", "noneFile"), intTile('search "Pillay" · 1440 light', "adverseSearch", "alice", "1440-light", "pillayFile")], 4, 480);
  await S("sheet-adverse-parties-carol.png", head(`/legal/adverse-parties · Carol · ${four} + search no match`), [...v4("adverse-parties", "carol"), intTile("search no match · 1440 light", "adverseSearch", "carol", "1440-light", "noneFile")], 3, 480);
  await S("sheet-disbursements-alice.png", head(`/legal/disbursements · Alice · ${four} + Matter filter (unbilled strip), From 2030-01-01 (empty) 1440 light / 1024 dark, matter Disbursements tab (rows / empty)`), [...v4("disbursements", "alice"), intTile("Matter filter → unbilled strip · 1440 light", "disbursementFilters", "alice", "1440-light", "matterFilterFile"), intTile("From 2030-01-01 → empty · 1440 light", "disbursementFilters", "alice", "1440-light", "dateFilterFile"), intTile("From 2030-01-01 → empty · 1024 dark", "disbursementFilters", "alice", "1024-dark", "dateFilterFile"), tile("matter-disbursements-tab", "alice", "1440-light"), tile("matter-disbursements-tab-empty", "alice", "1440-light")], 4, 480);
  await S("sheet-disbursements-carol.png", head(`/legal/disbursements · Carol · ${four} + From 2030-01-01 (empty)`), [...v4("disbursements", "carol"), intTile("From 2030-01-01 → empty · 1440 light", "disbursementFilters", "carol", "1440-light", "dateFilterFile")], 3, 480);
  await S("sheet-disbursement-detail-alice.png", head(`/legal/disbursements/[id] (trust-paid, closed matter) · Alice · ${four} + office-paid detail 1440 light`), [...v4("disbursement-detail", "alice"), tile("disbursement-detail-office", "alice", "1440-light")], 5, 440);
  await S("sheet-disbursement-detail-carol.png", head(`/legal/disbursements/[id] · Carol · ${four}`), v4("disbursement-detail", "carol"), 4, 480);
  await S("sheet-tariffs-alice.png", head(`/legal/tariffs · Alice · ${four} + schedule expanded, search "summons", search no match (1440 light), expanded + no match 1024 dark`), [...v4("tariffs", "alice"), intTile("expanded · 1440 light", "tariffBrowse", "alice", "1440-light", "expandedFile"), intTile('search "summons" · 1440 light', "tariffBrowse", "alice", "1440-light", "summonsFile"), intTile("search no match · 1440 light", "tariffBrowse", "alice", "1440-light", "noneFile"), intTile("expanded · 1024 dark", "tariffBrowse", "alice", "1024-dark", "expandedFile"), intTile("search no match · 1024 dark", "tariffBrowse", "alice", "1024-dark", "noneFile")], 4, 480);
  await S("sheet-tariffs-carol.png", head(`/legal/tariffs · Carol · ${four} + expanded, search no match`), [...v4("tariffs", "carol"), intTile("expanded · 1440 light", "tariffBrowse", "carol", "1440-light", "expandedFile"), intTile("search no match · 1440 light", "tariffBrowse", "carol", "1440-light", "noneFile")], 3, 480);
  await S("sheet-information-request-alice.png", head(`/information-requests/[id] REQ-0001 (reminder null) · Alice · ${four} + REQ-0002 (reminder 5) 1440 light`), [...v4("information-request", "alice"), tile("information-request-2", "alice", "1440-light")], 5, 440);
  await S("sheet-information-request-carol.png", head(`/information-requests/[id] · Carol (non-admin branch) · ${four}`), v4("information-request", "carol"), 4, 480);
  await S("sheet-statements-checklist.png", head("1440 light · matter Statements tab empty (Alice; Carol on the same matter; Carol on her own matter) · client Onboarding tab, no checklists (Alice admin, Carol)"), [tile("statements-empty", "alice", "1440-light"), tile("statements-empty", "carol", "1440-light"), tile("statements-empty-carol-matter", "carol", "1440-light"), tile("checklist-empty", "alice", "1440-light"), tile("checklist-empty", "carol", "1440-light")], 5, 440);
  const crops = { "k37-ir-header": "crop-k37-ir-header-1440-light.png", "k67-k85-ir-meta": "crop-k67-k85-ir-meta-1440-light.png", "k66-k79-disbursement-list": "crop-k66-k79-disbursement-list-1440-light.png", "k80-disbursement-detail-header": "crop-k80-disbursement-detail-header-1440-light.png", "k81-tariff-schedule-row": "crop-k81-tariff-schedule-row-1440-light.png", "k82-dsar-header": "crop-k82-dsar-header-1440-light.png", "k68-dsar-timeline": "crop-k68-dsar-timeline-1440-light.png", "k60-conflict-form": "crop-k60-conflict-form-1440-light.png" };
  for (const [k, f] of Object.entries(crops)) if (c.crops[k] && c.crops[k].file && fs.existsSync(c.crops[k].file)) fs.copyFileSync(c.crops[k].file, path.join(L.OUT, f));
  await b.close();
  console.log("assembled", fs.readdirSync(L.OUT).length, fs.readdirSync(L.OUT).join(" "));
})().catch((e) => { console.error("ASSEMBLE FAIL", e); process.exit(1); });
