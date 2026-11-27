// 669B step 2: merge $RAW/capture.json (01-capture) + $RAW/suites.json (03-sel-compat.sh, optional)
// into ../measurements.json, ../sel-compat.json, ../console.json, ../discover.json (single-line
// JSON) and build the contact sheets with the shared 661B sheet() helper; defect crops are cut from
// the raw full-page tiles.
// EVIDENCE-BUDGET: one sheet per surface × persona (/org/ pages: cols 1024 light | 1024 dark |
// 1440 light | 1440 dark; platform-admin: light only, cols 1024 | 1440) + 3 defect crops.
// Usage: PHASE=before|after STACK="<provenance>" node 02-assemble.cjs
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const STACK = process.env.STACK || "(STACK not given)";
const c = JSON.parse(fs.readFileSync(path.join(L.RAW, "capture.json"), "utf8"));
const suitesFile = path.join(L.RAW, "suites.json");
const suites = fs.existsSync(suitesFile) ? JSON.parse(fs.readFileSync(suitesFile, "utf8")) : null;
const seed = L.readSeed();
const V4 = ["1024-light", "1024-dark", "1440-light", "1440-dark"];
const PL = ["1024-light", "1440-light"];
const raw = (f) => path.join(L.RAW, f);
const pg = (key, pv) => (c.pages[key] || {})[pv];
const tile = (key, who, v) => [`${key} · ${who} · ${v} · HTTP ${(pg(key, `${who}/${v}`) || {}).httpStatus || "?"} · ${(pg(key, `${who}/${v}`) || {}).finalUrl || ""}`, raw(`${key}--${who}--${v}.png`)];
const ia = (pv, k) => (c.interactions[pv] || {})[k] || {};

async function cropPng(browser, src, dst, [x, y, w, h]) {
  if (!fs.existsSync(src)) return false;
  const p = await browser.newPage({ viewport: { width: w, height: h }, deviceScaleFactor: 1 });
  await p.setContent(`<html><body style="margin:0;overflow:hidden"><img id="i" style="position:absolute;left:${-x}px;top:${-y}px" src="data:image/png;base64,${fs.readFileSync(src).toString("base64")}"></body></html>`, { waitUntil: "load" });
  await p.screenshot({ path: dst, clip: { x: 0, y: 0, width: w, height: h } });
  await p.close();
  return true;
}

(async () => {
  const now = new Date().toISOString();
  // ---------- measurements ----------
  const slim = (m, pv) => { const { testids, ...rest } = m; if (pv.endsWith("dark")) rest.tables = m.tables.map((t) => ({ ...t, rows: t.rows.slice(0, 4) })); return rest; };
  const pages = {};
  for (const [k, vv] of Object.entries(c.pages)) { pages[k] = {}; for (const [pv, m] of Object.entries(vv)) pages[k][pv] = slim(m, pv); }
  const tbl = (t) => `${t.inDataTable ? "DataTable " : ""}table ${t.width} in scroller ${t.scroller ? `${t.scroller.clientWidth} (overflow ${t.scroller.overflowPx})` : "-"} · rows ${t.rowCount} · in view: ${t.columnsInView.join(", ")}`;
  const summary = {};
  for (const [k, vv] of Object.entries(c.pages)) summary[k] = Object.fromEntries(Object.entries(vv).map(([pv, m]) => [pv, {
    url: m.finalUrl, http: m.httpStatus, h1: m.h1Main, level1: m.level1Main, mainId: m.mainIdCount, mainEl: m.mainElCount, hOverflow: m.docHOverflowPx,
    header: m.header && `${m.header.text} · ${m.header.fontSize}/${m.header.fontWeight} ${m.header.fontFamily} · PageHeader ${m.header.pageHeader} · ${m.header.fg} on ${m.header.bg} ${m.header.ratio}${m.header.subtitle ? " · sub: " + m.header.subtitle : ""}${m.header.actions ? " · actions: " + m.header.actions : ""}`,
    empties: m.empties.map((e) => `${e.slot === "empty-state" ? "EmptyState " + e.variant : e.tag}: "${e.text.slice(0, 70)}" ${e.w}×${e.h}${e.titleTag ? " title<" + e.titleTag + ">" : ""}`),
    tables: m.tables.map(tbl),
    tabs: m.tabs,
    pa: m.pa && { width: m.pa.contentRoot && `${m.pa.contentRoot.maxW} ${m.pa.contentRoot.w} (inner ${m.pa.contentRoot.innerW})`, ariaCurrent: m.pa.links.map((l) => `${l.text}=${l.ariaCurrent || "-"}`).join(" "), underline: m.pa.links.map((l) => `${l.text}: ::after ${l.afterContent === "none" ? "none" : l.afterHeight + " " + l.afterBg}, border ${l.borderBottom}, decoration ${l.textDecoration}`).join(" | ") },
    team: m.team.memberRows || m.team.inviteCard || m.team.memberPill ? m.team : undefined,
    dialogs: m.dialogs.length ? m.dialogs : undefined,
    lowContrast: m.lowContrast.length ? [...new Set(m.lowContrast.map((x) => `${x.text}: ${x.fg} on ${x.bg} ${x.ratio}`))] : undefined,
  }]));
  const each = (key, fn) => Object.fromEntries(Object.entries(c.pages[key] || {}).map(([pv, m]) => [pv, fn(m, pv)]));
  const paKeys = Object.keys(c.pages).filter((k) => k.startsWith("pa-"));
  const kinks = {
    note: "headline numbers per register kink / slice task, copied from pages[].",
    "K-60 team header": each("team", (m) => m.header && { h1: `${m.header.text} ${m.header.fontSize}/${m.header.fontWeight} ${m.header.fontFamily}`, pageHeader: m.header.pageHeader, helpTipInH1: m.header.buttonsInH1, memberPill: m.team.memberPill }),
    "AI reviews header (both branches)": Object.fromEntries(["ai-reviews", "ai-reviews-history"].flatMap((k) => Object.entries(c.pages[k] || {}).map(([pv, m]) => [`${k} ${pv}`, m.header && `${m.header.text} ${m.header.fontSize}/${m.header.fontWeight} ${m.header.fontFamily} PageHeader ${m.header.pageHeader} · sub "${m.header.subtitle}"`]))),
    "K-98 main + aria-current": Object.fromEntries(paKeys.flatMap((k) => Object.entries(c.pages[k]).filter(([pv]) => pv.startsWith("alice/")).map(([pv, m]) => [`${k} ${pv}`, { mainId: m.mainIdCount, mainEl: m.mainElCount, h1InsideMain: m.pa && m.pa.h1InsideMain, ariaCurrent: m.pa && m.pa.links.filter((l) => l.ariaCurrent).map((l) => l.text), navInsideMain: m.pa && m.pa.nav && m.pa.nav.insideMain }]))),
    "K-99 width + h1 style": Object.fromEntries(["pa-access", "pa-billing", "pa-demo"].flatMap((k) => PL.map((v) => { const m = pg(k, `alice/${v}`); return [`${k} ${v}`, m && { width: m.pa && m.pa.contentRoot && `${m.pa.contentRoot.maxW} ${m.pa.contentRoot.w}/${m.pa.contentRoot.innerW}`, h1: m.header && `${m.header.fontSize}/${m.header.fontWeight} ${m.header.fontFamily} (${m.header.cls})` }]; }))),
    "K-01 + 669.5 EmptyState variants": Object.fromEntries(["team-invitations", "ai-reviews", "ai-reviews-history", "pa-access-Approved", "pa-billing-nomatch", "pa-demo"].flatMap((k) => Object.entries(c.pages[k] || {}).map(([pv, m]) => [`${k} ${pv}`, m.empties.map((e) => `${e.variant || e.tag} ${e.w}×${e.h}`)]))),
    "K-01 pre-seed access-request empties": Object.fromEntries(Object.entries(seed.preSeedEmpty || {}).map(([k, m]) => [k, m.empties.map((e) => `${e.variant || e.tag} ${e.w}×${e.h} "${e.text.slice(0, 60)}"`)])),
    "admin row role=button": each("team", (m) => ({ trRoleButton: m.team.trRoleButton, trTabindex: m.team.trTabindex, tbodyButtons: m.team.tbodyButtons, rowCursor: m.tables[0] && m.tables[0].rows[0] && m.tables[0].rows[0].cursor })),
    "table overflow": Object.fromEntries(Object.entries(c.pages).flatMap(([k, vv]) => Object.entries(vv).filter(([, m]) => m.tables.length).map(([pv, m]) => [`${k} ${pv}`, m.tables.map(tbl)]))),
    "team row hover (Bob's row, pointer on Email)": Object.fromEntries(Object.entries(c.interactions).map(([pv, i]) => [pv, i.rowHover && { rowBg: i.rowHover.rowBg, cells: i.rowHover.cells, error: i.rowHover.error }])),
    "team contrast": each("team", (m) => m.tables[0] && { header: m.tables[0].headerColour, firstRow: m.tables[0].firstRowCellColours }),
    lowContrast: Object.fromEntries(Object.entries(c.pages).flatMap(([k, vv]) => Object.entries(vv).filter(([, m]) => m.lowContrast.length).map(([pv, m]) => [`${k} ${pv}`, [...new Set(m.lowContrast.map((x) => `${x.text}: ${x.fg} on ${x.bg} ${x.ratio} (${x.cls})`))]]))),
  };
  const meas = {
    capturedAt: now, captureRunAt: c.at, stack: STACK, phase: L.PHASE,
    note: "summary[target][persona/variant] = headline facts; pages[] = the full probe (scripts/probe.cjs; dark keeps 4 table rows). kinks = register-kink numbers. interactions = the member row → MemberDetailPanel by click and by keyboard. Platform-admin pages have no #main-content before K-98, so their probe scope is <body>. api = endpoint cross-checks.",
    seed: { ids: seed.ids, writes: seed.writes, preSeedEmpty: seed.preSeedEmpty },
    caps: c.caps, acts: c.acts, summary, kinks, interactions: c.interactions, api: c.api, pages,
  };
  fs.writeFileSync(path.join(L.OUT, "measurements.json"), JSON.stringify(meas));
  // ---------- discover ----------
  const disc = { capturedAt: now, stack: STACK, note: "every data-testid on the page (UUIDs → <id>) with its count, per target × persona × variant" };
  for (const [k, vv] of Object.entries(c.pages)) { disc[k] = {}; for (const [pv, m] of Object.entries(vv)) disc[k][pv] = m.testids; }
  fs.writeFileSync(path.join(L.OUT, "discover.json"), JSON.stringify(disc));
  // ---------- sel-compat ----------
  const tid = (m, re) => Object.entries(m.testids).filter(([k]) => re.test(k)).reduce((n, [, v]) => n + v, 0);
  const SEL = {
    "level-1 headings in #main-content (sidebar-navigation:22,35-42 — exactly one)": (m) => m.level1Main,
    'h1 text (sidebar-navigation Team; ai-surfaces-without-assistant "AI Reviews")': (m) => m.h1Main.join(" | "),
    "main#main-content count": (m) => m.mainIdCount,
    '"Alice" / "Bob" / "Carol" in the member table (day-00-firm-setup:192-197)': (m) => ["Alice", "Bob", "Carol"].filter((n) => m.tables.some((t) => t.rows.some((r) => r.cells.some((x) => x.includes(n))))).join(","),
    "testid member-row-<email>": (m) => tid(m, /^member-row-/),
    "testid member-role-badge": (m) => tid(m, /^member-role-badge$/),
    "testid override-count": (m) => tid(m, /^override-count$/),
    "testid pending-invite-row": (m) => tid(m, /^pending-invite-row$/),
    "testid request-row-<org> (onboarding.spec:69, KC-only)": (m) => tid(m, /^request-row-/),
    "testid approve-btn (onboarding.spec:70, KC-only)": (m) => tid(m, /^approve-btn$/),
    "testid pending-tab": (m) => tid(m, /^pending-tab$/),
    "testid access-requests-page": (m) => tid(m, /^access-requests-page$/),
    "testid billing-tenants-page": (m) => tid(m, /^billing-tenants-page$/),
    "testid demo-tenants-table": (m) => tid(m, /^demo-tenants-table$/),
    "testid demo-tenant-row-<name>": (m) => tid(m, /^demo-tenant-row-/),
    "testid sheet-org-name (billing sheet)": (m) => tid(m, /^sheet-org-name$/),
    "testid ai-reviews-client": (m) => tid(m, /^ai-reviews-client$/),
  };
  const sel = { capturedAt: now, stack: STACK, note: "live DOM counts per selector, per target/persona at 1440 light (+ 1024 light for platform-admin). Only non-zero/non-empty values kept. suites = the SEL-COMPAT Playwright runs (03-sel-compat.sh), unchanged specs.", suites, counts: {} };
  const at = [];
  for (const k of Object.keys(c.pages)) for (const who of ["alice", "bob", "carol"]) for (const v of ["1440-light", "1024-light"]) if (pg(k, `${who}/${v}`) && (v === "1440-light" || k.startsWith("pa-"))) at.push([k, who, v]);
  for (const [name, fn] of Object.entries(SEL)) {
    sel.counts[name] = {};
    for (const [k, who, v] of at) { const val = fn(pg(k, `${who}/${v}`)); if (val && val !== "0") sel.counts[name][`${k}/${who}/${v}`] = val; }
  }
  fs.writeFileSync(path.join(L.OUT, "sel-compat.json"), JSON.stringify(sel));
  // ---------- console ----------
  const msgs = c.console;
  const loads = Object.values(c.pages).reduce((n, vv) => n + Object.keys(vv).length, 0);
  fs.writeFileSync(path.join(L.OUT, "console.json"), JSON.stringify({ capturedAt: now, stack: STACK, totals: { capturePageLoads: loads, messages: msgs.length, errors: msgs.filter((m) => m.type === "error" || m.type === "pageerror").length, warnings: msgs.filter((m) => m.type === "warning").length, hydration: msgs.filter((m) => /hydrat/i.test(m.text)).length }, messages: msgs }));
  // ---------- sheets ----------
  const b = await L.chromium.launch();
  const SH = (f, title, tiles, cols, w) => L.sheet(b, path.join(L.OUT, f), title, tiles, cols, w);
  const head = (s) => `669B ${L.PHASE} — ${s} — ${STACK}`;
  const four = "cols 1024 light | 1024 dark | 1440 light | 1440 dark, full page";
  const v4 = (key, who) => V4.map((v) => tile(key, who, v));
  const pl = (key, who) => PL.map((v) => tile(key, who, v));
  const it = (cap, pv, k, field = "file") => [cap, ia(pv, k)[field] || null];
  await SH("sheet-team-alice.png", head(`/team · Alice (owner) · ${four} · row 2: Pending Invitations tab (1440 light, 1024 dark), row → MemberDetailPanel by click (1440 light, 1024 dark) · row 3: keyboard focus on the row + Enter (1440 light, 1024 dark) · row 4: hover on Bob's row (table only; 1440 light, 1024 dark)`), [...v4("team", "alice"), tile("team-invitations", "alice", "1440-light"), tile("team-invitations", "alice", "1024-dark"), it("click Bob's row → panel · 1440 light", "alice/1440-light", "rowClick"), it("click Bob's row → panel · 1024 dark", "alice/1024-dark", "rowClick"), it("Tab ×2 from the Members tab → focus · 1440 light", "alice/1440-light", "rowKeyboard", "focusFile"), it("then Enter → panel · 1440 light", "alice/1440-light", "rowKeyboard"), it("Tab → focus · 1024 dark", "alice/1024-dark", "rowKeyboard", "focusFile"), it("then Enter → panel · 1024 dark", "alice/1024-dark", "rowKeyboard"), it("hover Bob's row (admin) · table · 1440 light", "alice/1440-light", "rowHover"), it("hover Bob's row (admin) · table · 1024 dark", "alice/1024-dark", "rowHover")], 4, 480);
  await SH("sheet-team-carol.png", head(`/team · Carol (member) · ${four} · row 2: Pending Invitations (1440 light, 1024 dark), click on Bob's row (no panel) 1440 light, hover on Bob's row 1024 dark (table only)`), [...v4("team", "carol"), tile("team-invitations", "carol", "1440-light"), tile("team-invitations", "carol", "1024-dark"), it("click Bob's row (member: nothing opens) · 1440 light", "carol/1440-light", "rowClick"), it("hover Bob's row (member) · table · 1024 dark", "carol/1024-dark", "rowHover")], 4, 480);
  await SH("sheet-ai-reviews-alice.png", head(`/ai/reviews · Alice (owner, AI_REVIEW) · row 1 Pending Review tab, row 2 History tab · ${four}`), [...v4("ai-reviews", "alice"), ...v4("ai-reviews-history", "alice")], 4, 480);
  await SH("sheet-ai-reviews-bob.png", head("/ai/reviews · Bob (admin, AI_REVIEW) · 1440 light · Pending Review | History"), [tile("ai-reviews", "bob", "1440-light"), tile("ai-reviews-history", "bob", "1440-light")], 2, 640);
  await SH("sheet-ai-reviews-carol.png", head(`/ai/reviews · Carol (member, no AI_REVIEW → denied branch) · ${four}`), v4("ai-reviews", "carol"), 4, 480);
  const pre = seed.preSeedEmpty || {};
  const preTiles = L.PHASE === "before" ? ["All", "Pending", "Approved", "Rejected"].flatMap((t) => PL.map((v) => [`PRE-SEED (0 requests) · ${t} tab · ${v}`, (pre[`${t}/alice/${v}`] || {}).file || null])) : [];
  const postTiles = [["pa-access-All", "All"], ["pa-access", "Pending (default)"], ["pa-access-Approved", "Approved"], ["pa-access-Rejected", "Rejected"]].flatMap(([k]) => pl(k, "alice"));
  await SH("sheet-pa-access-requests-alice.png", head(`/platform-admin/access-requests · Alice (platform-admin) · LIGHT ONLY · pairs 1024 | 1440 · ${L.PHASE === "before" ? "rows 1-2: before the seed (0 requests: All, Pending, Approved, Rejected); rows 3-4: after the seed (Alpha PENDING, Beta REJECTED)" : "All, Pending, Approved, Rejected"}`), [...preTiles, ...postTiles], 4, 480);
  await SH("sheet-pa-billing-alice.png", head("/platform-admin/billing · Alice · LIGHT ONLY · cols 1024 | 1440 · rows: tenant list, search no match, Open → BillingDetailSheet"), [...pl("pa-billing", "alice"), ...pl("pa-billing-nomatch", "alice"), ...pl("pa-billing-sheet", "alice")], 2, 640);
  await SH("sheet-pa-demo-alice.png", head("/platform-admin/demo · Alice · LIGHT ONLY · cols 1024 | 1440 (form + no demo tenants)"), pl("pa-demo", "alice"), 2, 640);
  await SH("sheet-pa-carol.png", head("/platform-admin/* · Carol (not a platform admin) · 1440 light · access-requests | billing | demo"), ["pa-access", "pa-billing", "pa-demo"].map((k) => tile(k, "carol", "1440-light")), 3, 480);
  // defect crops (measured): access-requests table at 1024 (Actions column out of view), the nav bar (K-98)
  const crops = {};
  const m1024 = pg("pa-access", "alice/1024-light");
  if (m1024) crops["crop-pa-access-table-1024-light.png"] = await cropPng(b, raw("pa-access--alice--1024-light.png"), path.join(L.OUT, "crop-pa-access-table-1024-light.png"), [0, 0, 1024, Math.min(520, m1024.docHeight)]);
  const hov = ia("alice/1024-dark", "rowHover").file;
  if (hov && fs.existsSync(hov)) { fs.copyFileSync(hov, path.join(L.OUT, "crop-team-admin-row-hover-1024-dark.png")); crops["crop-team-admin-row-hover-1024-dark.png"] = true; }
  crops["crop-pa-nav-1440-light.png"] = await cropPng(b, raw("pa-billing--alice--1440-light.png"), path.join(L.OUT, "crop-pa-nav-1440-light.png"), [0, 0, 1440, 140]);
  await b.close();
  console.log("assembled", JSON.stringify(crops), fs.readdirSync(L.OUT).length, fs.readdirSync(L.OUT).join(" "));
})().catch((e) => { console.error("ASSEMBLE FAIL", e); process.exit(1); });
