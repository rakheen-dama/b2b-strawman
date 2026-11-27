// 668A step 2: merge $RAW/capture.json (01-capture) into ../measurements.json, ../sel-compat.json,
// ../console.json, ../discover.json (single-line JSON) and build the contact sheets with the shared
// 661B sheet() helper. Measured-kink crops are copied as separate PNGs.
// EVIDENCE-BUDGET: one sheet per surface × persona (cols 1024 light | 1024 dark | 1440 light | 1440 dark,
// full page; some sheets append variant / interaction tiles) + 7 crops (K-09 Available Tasks card at
// 1024, K-96 header at 1024, K-39 member agenda at 1440, K-31 bell light + dark, the court-date list,
// the timesheet grid after keyboard entry).
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
const PVS = (who) => V4.map((v) => `${who}/${v}`);

(async () => {
  const now = new Date().toISOString();
  // ---------- measurements ----------
  const slim = (m, pv) => { const { testids, ...rest } = m; if (pv.endsWith("dark")) rest.tables = m.tables.map((t) => ({ ...t, rows: t.rows.slice(0, 6) })); return rest; };
  const pages = {};
  for (const [k, vv] of Object.entries(c.pages)) { pages[k] = {}; for (const [pv, m] of Object.entries(vv)) pages[k][pv] = slim(m, pv); }
  const tbl = (t) => ({ section: t.section, tableWidth: t.width, tableScrollWidth: t.tableScrollWidth, scrollerClientWidth: t.scroller && t.scroller.clientWidth, scrollerScrollWidth: t.scroller && t.scroller.scrollWidth, overflowPx: t.scroller && t.scroller.overflowPx, cardClientWidth: t.card && t.card.clientWidth, rows: t.rowCount, columnsInView: t.columnsInView, columnsFullyInView: t.columnsFullyInView, headers: t.headers.map((h) => `${h.sr}:${h.w}@${h.x}${h.shown ? "" : " (hidden)"}`) });
  const myWorkPvs = [...PVS("alice"), ...PVS("carol")];
  const kinks = {
    note: "headline numbers per kink, copied from pages[]. K-09: per My Work task table — table scrollWidth vs its scroller/bordered-card clientWidth and the columns in view. K-96: title-block width, h1/subtitle line counts, Timesheet link right edge vs the 1024 viewport. K-97: Available Tasks rows + document height. K-39: composite contrast of every `text-slate-400` element on My Work. K-31: the header bell badge. K-88: timesheet rows vs the week's API entries.",
    k09: Object.fromEntries(myWorkPvs.map((pv) => { const m = pg("my-work", pv); return [pv, m && (m.tables.length ? m.tables.map(tbl) : { tables: 0, empties: m.empties.map((e) => e.text) })]; })),
    k96: Object.fromEntries(myWorkPvs.map((pv) => { const m = pg("my-work", pv); const h = m && m.header; return [pv, h && { titleBlockWidth: h.titleBlock && h.titleBlock.box.w, h1Lines: h.h1.lines, h1FontSize: h.h1.fontSize, subtitleLines: h.subtitle && h.subtitle.lines, headerRow: h.row, viewport: m.docClientWidth, docHOverflowPx: m.docHOverflowPx, timesheetLink: (h.controls.find((x) => x.text === "Timesheet") || null), controls: h.controls.map((x) => `${x.text} x${x.box.x}..${x.box.right}`) }]; })),
    k97: Object.fromEntries(myWorkPvs.map((pv) => { const m = pg("my-work", pv); const t = m && m.tables.find((x) => /^Available Tasks/.test(x.section || "")); return [pv, m && { availableRows: t ? t.rowCount : 0, docHeight: m.docHeight, availableSection: t ? t.section : null }]; })),
    k97Collapse: Object.fromEntries(Object.entries(c.interactions).map(([pv, i]) => [pv, i.myWorkViews && i.myWorkViews.availableCollapse])),
    k39: Object.fromEntries(myWorkPvs.map((pv) => { const m = pg("my-work", pv); return [pv, m && m.slate400.map((s) => `${s.text} — ${s.fg} on ${s.bg} ${s.ratio}`)]; })),
    k31: Object.fromEntries(Object.entries(c.pages).flatMap(([k, vv]) => Object.entries(vv).filter(([pv]) => pv.startsWith("alice/") && ["my-work", "notifications", "diary-week"].includes(k)).map(([pv, m]) => [`${k} ${pv}`, m.bell && m.bell.badge && `${m.bell.badge.text}: ${m.bell.badge.fg} on ${m.bell.badge.bg} ${m.bell.badge.ratio} (${m.bell.badge.box.w}×${m.bell.badge.box.h})`]))),
    k88: {
      apiWeekEntries: Object.fromEntries(Object.entries(c.api).map(([w, a]) => [w, a.weekEntries])),
      grid: Object.fromEntries(Object.entries(c.pages.timesheet || {}).map(([pv, m]) => [pv, m.tables[0] && { rows: m.tables[0].rows.map((r) => r.cells.join(" | ")), scroller: m.tables[0].scroller, inputs: m.timeInputs.length }])),
      keyboard: Object.fromEntries(Object.entries(c.interactions).map(([pv, i]) => [pv, i.timesheetKeyboard])),
    },
    diaryFeed: Object.fromEntries(Object.entries(c.api).map(([w, a]) => [w, { diaryNov: a.diaryNov, diaryWeek: a.diaryWeek, courtDates: a.courtDates, courtDatesNotInDiary: a.courtDatesNotInDiary }])),
    diaryUi: Object.fromEntries(Object.entries(c.pages).filter(([k]) => k.startsWith("diary")).flatMap(([k, vv]) => Object.entries(vv).filter(([pv]) => !pv.endsWith("dark")).map(([pv, m]) => [`${k} ${pv}`, { url: m.finalUrl, tabs: m.diary.tabs, range: m.diary.range, chips: m.diary.filterChips, events: m.diary.events.map((e) => `${e.text}${e.badges.length ? " [" + e.badges.join(", ") + "]" : ""}`), listRows: m.diary.listRows.map((r) => `${r.kind}: ${r.cells.join(" | ")}`), empties: m.empties.map((e) => e.text) }]))),
    courtDateBadges: Object.fromEntries(Object.entries(c.pages["court-dates"] || {}).map(([pv, m]) => { const t = m.tables.find((x) => x.wrapperTestid === "court-date-list"); return [pv, t ? { table: tbl(t), rows: t.rows.map((r) => `${r.cells[0]} ${r.cells[6]}: ${r.badges.filter((b) => /Scheduled|Postponed|Heard|Cancelled/.test(b.text)).map((b) => `${b.variant} ${b.fg} on ${b.bg} ${b.ratio}`).join("")}`) } : { bodyHead: m.bodyHead }]; })),
    notifications: Object.fromEntries(Object.entries(c.pages.notifications || {}).map(([pv, m]) => [pv, { ...m.notifications, empties: m.empties.map((e) => `${e.text} ${e.fg} on ${e.bg} ${e.ratio}`), h1: m.header && m.header.h1.fontSize }])),
    shell: Object.fromEntries(Object.entries(c.pages).map(([k, vv]) => [k, Object.fromEntries(Object.entries(vv).map(([pv, m]) => [pv, `main#main-content ${m.mainIdCount} · <main> ${m.mainElCount} · h1 in main ${m.h1Main.length} · h1 page ${m.h1Page} · hOverflow ${m.docHOverflowPx}`]))])),
  };
  const meas = {
    capturedAt: now, captureRunAt: c.at, stack: STACK, phase: L.PHASE,
    note: "pages[target][persona/variant] = probe of the page (see scripts/probe.cjs). Dark variants keep the first 6 table rows. kinks = headline numbers. interactions = URL before → after per action; dialogs/menus opened and cancelled; timesheet typed, never saved. api = the endpoints the pages read.",
    seed: { ids: seed.ids, courtDates: seed.courtDates, steps: seed.runs, dataBefore: seed.dataBefore, dataAfter: seed.dataAfter },
    caps: c.caps, kinks, interactions: c.interactions, api: c.api, crops: c.crops, pages,
  };
  fs.writeFileSync(path.join(L.OUT, "measurements.json"), JSON.stringify(meas));
  // ---------- discover ----------
  const disc = { capturedAt: now, stack: STACK, note: "every data-testid on the page (UUIDs → <id>) with its count, per target × persona × variant" };
  for (const [k, vv] of Object.entries(c.pages)) { disc[k] = {}; for (const [pv, m] of Object.entries(vv)) disc[k][pv] = m.testids; }
  fs.writeFileSync(path.join(L.OUT, "discover.json"), JSON.stringify(disc));
  // ---------- sel-compat ----------
  const T = (id) => (m) => m.testids[id] || 0;
  const SEL = {
    'h1 "My Work" (day-07-14:57,170; project-time:244)': (m) => m.sel.h1MyWork,
    'heading "By Task" (project-time:145 — on the matter Time tab, not My Work)': (m) => m.sel.headingByTask,
    'link "Timesheet Report" (day-60:483-484 — a reports-page link)': (m) => m.sel.linkTimesheetReport,
    'h1 "Notifications" (notification-bell:112,138)': (m) => m.sel.h1Notifications,
    'h1 "Diary" / "Timesheet"': (m) => `${m.sel.h1Diary}/${m.sel.h1Timesheet}`,
    "#main-content level-1 headings (sidebar-navigation:38-39 uses .first())": (m) => m.sel.mainH1,
    "main#main-content count": (m) => m.mainIdCount,
    'button "Notifications" exact (bell)': (m) => m.sel.bellButtonsExact,
    'link "Timesheet" on My Work': (m) => m.sel.linkTimesheet.join(","),
    "testid court-date-list (day-45:141, day-90:369) / rows": (m) => `${m.testids["court-date-list"] || 0}/${m.sel.courtDateListRows}`,
    "testid project-court-dates-tab / tab-group-schedule": (m) => `${m.testids["project-court-dates-tab"] || 0}/${m.testids["tab-group-schedule"] || 0}`,
    "testid create-court-date-trigger": T("create-court-date-trigger"),
    "testid prescription-tracker-list": T("prescription-tracker-list"),
    "testid diary-range / diary-filter-row": (m) => `${m.testids["diary-range"] || 0}/${m.testids["diary-filter-row"] || 0}`,
    "testid diary-week-grid / diary-day-<iso>": (m) => `${m.testids["diary-week-grid"] || 0}/${Object.entries(m.testids).filter(([k]) => /^diary-day-\d/.test(k)).reduce((n, [, v]) => n + v, 0)}`,
    "testid diary-day-view / diary-month-grid / diary-list": (m) => ["diary-day-view", "diary-month-grid", "diary-list"].map((k) => m.testids[k] || 0).join("/"),
    "testid diary-event-<id> / diary-list-row-<id> / diary-row-actions-<id>": (m) => ["diary-event-<id>", "diary-list-row-<id>", "diary-row-actions-<id>"].map((k) => m.testids[k] || 0).join("/"),
    "testid tasks-panel / saved-view-selector / extended-widgets": (m) => ["tasks-panel", "saved-view-selector", "extended-widgets"].map((k) => m.testids[k] || 0).join("/"),
    "testid todays-diary / todays-diary-overdue / time-progress-today / next-deadline": (m) => ["todays-diary", "todays-diary-overdue", "time-progress-today", "next-deadline"].map((k) => m.testids[k] || 0).join("/"),
    "testid time-activity-panel": T("time-activity-panel"),
    'input aria-label "Hours for day N" (timesheet)': (m) => m.timeInputs.length,
    'notifications page "Unread" dots / "Mark all as read" / "Load more"': (m) => `${m.notifications.unreadDots}/${m.notifications.markAll}/${m.notifications.loadMore}`,
    "main tbody tr": (m) => m.sel.mainTbodyTr,
  };
  const at = [["diary-week", "1440-light"], ["diary-day", "1440-light"], ["diary-month", "1440-light"], ["diary-list", "1440-light"], ["court-dates", "1440-light"], ["my-work", "1440-light"], ["my-work", "1024-light"], ["timesheet", "1440-light"], ["notifications", "1440-light"], ["notification-prefs", "1440-light"]];
  const sel = { capturedAt: now, stack: STACK, note: "live DOM counts per selector, per target/persona/variant (only non-zero entries kept; a selector whose every entry is 0 is listed with {} — absent everywhere). Strings carry text.", counts: {} };
  for (const [name, fn] of Object.entries(SEL)) {
    sel.counts[name] = {};
    for (const [k, v] of at) for (const who of ["alice", "carol"]) { const m = pg(k, `${who}/${v}`); if (!m) continue; const val = fn(m); if (val && !/^0(\/0)+$/.test(String(val))) sel.counts[name][`${k}/${who}/${v}`] = val; }
  }
  sel.bellPopover = Object.fromEntries(Object.entries(c.interactions).map(([pv, i]) => [pv, i.bellPopover && { viewAllNotifications: i.bellPopover.viewAll, markAllRead: i.bellPopover.markAllRead, unreadItems: i.bellPopover.items, closedByEscape: i.bellPopover.closedByEscape }]));
  sel.notificationPreferencesSpec = c.interactions["alice/1440-light"] && c.interactions["alice/1440-light"].notificationPrefsSel;
  fs.writeFileSync(path.join(L.OUT, "sel-compat.json"), JSON.stringify(sel));
  // ---------- console ----------
  const msgs = c.console;
  const loads = Object.values(c.pages).reduce((n, vv) => n + Object.keys(vv).length, 0);
  fs.writeFileSync(path.join(L.OUT, "console.json"), JSON.stringify({ capturedAt: now, stack: STACK, totals: { capturePageLoads: loads, messages: msgs.length, errors: msgs.filter((m) => m.type === "error" || m.type === "pageerror").length, warnings: msgs.filter((m) => m.type === "warning").length, hydration: msgs.filter((m) => /hydrat/i.test(m.text)).length }, messages: msgs }));
  // ---------- sheets ----------
  const b = await L.chromium.launch();
  const S = (f, title, tiles, cols, w) => L.sheet(b, path.join(L.OUT, f), title, tiles, cols, w);
  const head = (s) => `668A ${L.PHASE} — ${s} — ${STACK}`;
  const four = "cols 1024 light | 1024 dark | 1440 light | 1440 dark, full page";
  const v4 = (key, who) => V4.map((v) => tile(key, who, v));
  await S("sheet-diary-week-alice.png", head(`Diary week 23–29 Nov · Alice (owner) · ${four}`), v4("diary-week", "alice"), 4, 520);
  await S("sheet-diary-day-alice.png", head(`Diary day Thu 26 Nov (the POSTPONED court date) · Alice · ${four}`), v4("diary-day", "alice"), 4, 520);
  await S("sheet-diary-month-alice.png", head(`Diary month November · Alice · ${four}`), v4("diary-month", "alice"), 4, 520);
  await S("sheet-diary-list-alice.png", head(`Diary list November · Alice · ${four}`), v4("diary-list", "alice"), 4, 520);
  await S("sheet-diary-url-alice.png", head("Diary URL state · Alice 1440 light · bare /diary, ?types=COURT_DATE, ?feeEarner=mine, ?matter=<Khumalo>, month ?from=2026-11-18 (snaps to 1 Nov); fee-earner select open; POSTPONED row menu (1440 light, 1024 dark)"), [tile("diary-bare", "alice", "1440-light"), tile("diary-url-types", "alice", "1440-light"), tile("diary-url-mine", "alice", "1440-light"), tile("diary-url-matter", "alice", "1440-light"), tile("diary-url-midmonth", "alice", "1440-light"), intTile("fee-earner select open", "diaryFilters", "alice", "1440-light", "feeEarnerFile"), intTile("POSTPONED row menu · 1440 light", "diaryPostponedRowMenu", "alice", "1440-light"), intTile("POSTPONED row menu · 1024 dark", "diaryPostponedRowMenu", "alice", "1024-dark")], 4, 480);
  await S("sheet-diary-carol.png", head("Diary · Carol (member, on no matter) · week × 4 + day, month, list, ?feeEarner=mine (1440 light)"), [...v4("diary-week", "carol"), tile("diary-day", "carol", "1440-light"), tile("diary-month", "carol", "1440-light"), tile("diary-list", "carol", "1440-light"), tile("diary-url-mine", "carol", "1440-light")], 4, 480);
  await S("sheet-court-dates.png", head("Matter › Diary tab › Court Dates (court-date-list-view.tsx status badges) · Alice × 4 + Carol 1440 light (not on the matter)"), [...v4("court-dates", "alice"), tile("court-dates", "carol", "1440-light")], 5, 440);
  await S("sheet-my-work-alice.png", head(`My Work ?from=2026-11-23&to=2026-11-29 · Alice · ${four}`), v4("my-work", "alice"), 4, 520);
  await S("sheet-my-work-carol.png", head(`My Work ?from=2026-11-23&to=2026-11-29 · Carol (no tasks) · ${four}`), v4("my-work", "carol"), 4, 520);
  await S("sheet-my-work-variants.png", head("My Work variants · bare /my-work Alice 1440 + 1024, Carol 1440; ?from=2026-11-01&to=2026-11-30 (This Month) Alice; Save View dialog (opened, Escaped)"), [tile("my-work-bare", "alice", "1440-light"), tile("my-work-bare", "alice", "1024-light"), tile("my-work-bare", "carol", "1440-light"), tile("my-work-month", "alice", "1440-light"), intTile("Save View dialog · 1440 light", "myWorkViews", "alice", "1440-light")], 5, 440);
  await S("sheet-timesheet-alice.png", head(`Timesheet ?week=2026-11-23 · Alice · ${four} + bare, typed (not saved) 1440 light + 1024 dark`), [...v4("timesheet", "alice"), tile("timesheet-bare", "alice", "1440-light"), intTile("typed 2.5 / 1 / ↑↑ / 0.75 (not saved) · 1440 light", "timesheetKeyboard", "alice", "1440-light"), intTile("typed (not saved) · 1024 dark", "timesheetKeyboard", "alice", "1024-dark")], 4, 480);
  await S("sheet-timesheet-carol.png", head(`Timesheet ?week=2026-11-23 · Carol (no assigned tasks) · ${four}`), v4("timesheet", "carol"), 4, 520);
  await S("sheet-notifications-alice.png", head(`Notifications · Alice (21 unread) · ${four} + Unread toggle, bell popover (1440 light, 1024 dark), Settings › Notifications prefs`), [...v4("notifications", "alice"), intTile("Unread toggle · 1440 light", "notificationsToggle", "alice", "1440-light", "unreadFile"), intTile("bell popover · 1440 light", "bellPopover", "alice", "1440-light"), intTile("bell popover · 1024 dark", "bellPopover", "alice", "1024-dark"), tile("notification-prefs", "alice", "1440-light")], 4, 480);
  await S("sheet-notifications-carol.png", head(`Notifications · Carol (0 — the empty state) · ${four} + Unread toggle, bell popover (1440 light)`), [...v4("notifications", "carol"), intTile("Unread toggle · 1440 light", "notificationsToggle", "carol", "1440-light", "unreadFile"), intTile("bell popover · 1440 light", "bellPopover", "carol", "1440-light")], 3, 520);
  const crops = { "k09-available-1024": "crop-k09-available-tasks-1024-light.png", "k96-header-1024": "crop-k96-header-1024-light.png", "k39-agenda-carol-1440": "crop-k39-agenda-carol-1440-light.png", "k31-bell-light": "crop-k31-bell-1440-light.png", "k31-bell-dark": "crop-k31-bell-1440-dark.png", "court-dates-light": "crop-court-dates-1440-light.png", "timesheet-typed-alice-1440-light": "crop-timesheet-typed-1440-light.png" };
  for (const [k, f] of Object.entries(crops)) if (c.crops[k] && c.crops[k].file && fs.existsSync(c.crops[k].file)) fs.copyFileSync(c.crops[k].file, path.join(L.OUT, f));
  await b.close();
  console.log("assembled", fs.readdirSync(L.OUT).join(" "));
})().catch((e) => { console.error("ASSEMBLE FAIL", e); process.exit(1); });
