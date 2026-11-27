// 668A step 1: capture (read-only on the data — no court date, task, time entry or notification is
// written; menus/dialogs are opened and cancelled; timesheet keystrokes are typed but NEVER saved;
// notification items are never clicked — opening one marks it read). Reads the ids from
// before/seed.json (00-seed.cjs, run once). Re-run UNEDITED with PHASE=after for the OBS-after.
// Personas through /mock-login: alice (owner), carol (member, on no matter). Variants 1024×768 /
// 1440×900 × light / dark (theme via localStorage `kazi-theme`, rail expanded, DPR 1, reduced motion,
// Africa/Johannesburg). Every diary / timesheet URL carries an explicit date, so a later replay loads
// the same windows. Raw PNGs → /tmp/668A-$PHASE-raw/ (never committed); JSON → $RAW/capture.json,
// assembled by 02-assemble.cjs.
// Usage: PHASE=before|after node 01-capture.cjs
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");
const { probe } = require("./probe.cjs");

const S = L.readSeed();
const ID = S.ids;
const B = L.BASE;
const CD = ID.courtDates; // { "POSTPONED-2026-11-26": id, ... }
const V4 = ["1024-light", "1024-dark", "1440-light", "1440-dark"];
const L1 = ["1440-light"];
const L2 = ["1440-light", "1024-light"];
// key, url, {persona: variants}, after-load action
const TARGETS = [
  ["diary-week", `${B}/diary?from=2026-11-23`, { alice: V4, carol: V4 }],
  ["diary-day", `${B}/diary?view=day&from=2026-11-26`, { alice: V4, carol: L1 }],
  ["diary-month", `${B}/diary?view=month&from=2026-11-01`, { alice: V4, carol: L1 }],
  ["diary-list", `${B}/diary?view=list&from=2026-11-01`, { alice: V4, carol: L1 }],
  ["diary-bare", `${B}/diary`, { alice: L1 }],
  ["diary-url-types", `${B}/diary?view=list&from=2026-11-01&types=COURT_DATE`, { alice: L1 }],
  ["diary-url-mine", `${B}/diary?view=list&from=2026-11-01&feeEarner=mine`, { alice: L1, carol: L1 }],
  ["diary-url-matter", `${B}/diary?view=list&from=2026-11-01&matter=${ID.khumaloMatterId}`, { alice: L1 }],
  ["diary-url-midmonth", `${B}/diary?view=month&from=2026-11-18`, { alice: L1 }],
  ["court-dates", `${B}/matters/${ID.khumaloMatterId}`, { alice: V4, carol: L1 }, "courtTab"],
  ["my-work", `${B}/my-work?from=2026-11-23&to=2026-11-29`, { alice: V4, carol: V4 }],
  ["my-work-bare", `${B}/my-work`, { alice: L2, carol: L1 }],
  ["my-work-month", `${B}/my-work?from=2026-11-01&to=2026-11-30`, { alice: L1 }],
  ["timesheet", `${B}/my-work/timesheet?week=2026-11-23`, { alice: V4, carol: V4 }],
  ["timesheet-bare", `${B}/my-work/timesheet`, { alice: L1 }],
  ["notifications", `${B}/notifications`, { alice: V4, carol: V4 }],
  ["notification-prefs", `${B}/settings/notifications`, { alice: L1 }],
];

const cons = [];
const out = { phase: L.PHASE, at: new Date().toISOString(), seedIds: ID, caps: {}, pages: {}, crops: {}, interactions: {} };
const rel = (page) => { const u = new URL(page.url()); return u.pathname.replace(`/org/${L.ORG}`, "") + u.search; };
async function settle(page, action) {
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.locator("#main-content h1").first().waitFor({ timeout: 20000 }).catch(() => {});
  if (action === "courtTab") {
    await page.getByTestId("tab-group-schedule").click().catch(() => {});
    await page.getByTestId("court-date-list").waitFor({ timeout: 15000 }).catch(() => {});
  }
  if (/\/diary/.test(page.url())) await page.getByTestId("diary-range").waitFor({ timeout: 15000 }).catch(() => {});
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.waitForTimeout(700);
}
async function crop(page, name, sel) {
  try {
    const loc = page.locator(sel).first();
    if (!(await loc.count())) { out.crops[name] = { missing: true, sel }; return null; }
    const f = path.join(L.RAW, `crop-${name}.png`);
    await loc.scrollIntoViewIfNeeded();
    await loc.screenshot({ path: f });
    out.crops[name] = { file: f, sel };
    return f;
  } catch (e) { out.crops[name] = { error: e.message.split("\n")[0], sel }; return null; }
}
// K-09 card: the bordered container of the Available Tasks table (before: div.rounded-lg; after: whatever wraps it)
const AVAILABLE = 'xpath=//*[normalize-space(text())="Available Tasks"]/ancestor::*[.//table][1]';

async function capture(browser, who, state) {
  const t = await L.idpToken(who);
  const cap = (await L.apiGet(t, "/api/me/capabilities")).json;
  out.caps[who] = cap && { role: cap.role, isAdmin: cap.isAdmin, isOwner: cap.isOwner, capabilities: cap.capabilities };
  const variants = [...new Set(TARGETS.flatMap(([, , p]) => p[who] || []))];
  for (const v of variants) {
    const [w, theme] = v.split("-");
    const ctx = await L.newCtx(browser, state, { width: +w, height: w === "1024" ? 768 : 900, theme, extra: L.EXTRA });
    const page = await ctx.newPage();
    const lbl = { label: "" };
    L.watch(page, cons, lbl);
    for (const [key, url, per, action] of TARGETS) {
      if (!(per[who] || []).includes(v)) continue;
      lbl.label = `${who} ${key} ${v}`;
      const resp = await page.goto(url, { waitUntil: "load" });
      await settle(page, action);
      const m = await page.evaluate(probe);
      m.httpStatus = resp ? resp.status() : null;
      m.finalUrl = rel(page);
      (out.pages[key] = out.pages[key] || {})[`${who}/${v}`] = m;
      await page.screenshot({ path: path.join(L.RAW, `${key}--${who}--${v}.png`), fullPage: true });
      if (who === "alice" && key === "my-work" && v.endsWith("light")) {
        await crop(page, `k09-available-${w}`, AVAILABLE);
        await crop(page, `k96-header-${w}`, "#main-content h1 >> xpath=ancestor::div[contains(@class,'space-y-')][1]/*[1]");
      }
      if (key === "my-work" && v.endsWith("light")) await crop(page, `k39-agenda-${who}-${w}`, '[data-testid="todays-diary"] >> xpath=ancestor::div[.//*[@data-testid="next-deadline"]][1]');
      if (who === "alice" && key === "my-work" && v.startsWith("1440")) await crop(page, `k31-bell-${theme}`, 'button[aria-label="Notifications"]');
      if (who === "alice" && key === "court-dates" && v.startsWith("1440")) await crop(page, `court-dates-${theme}`, '[data-testid="project-court-dates-tab"]');
      if (who === "alice" && key === "diary-list" && v === "1440-light") await crop(page, "diary-list-1440-light", '[data-testid="diary-list"]');
    }
    await ctx.close();
  }
}

async function interact(browser, state, who, v) {
  const [w, theme] = v.split("-");
  const ctx = await L.newCtx(browser, state, { width: +w, height: w === "1024" ? 768 : 900, theme, extra: L.EXTRA });
  const page = await ctx.newPage();
  const lbl = { label: `${who} interactions ${v}` };
  L.watch(page, cons, lbl);
  const I = (out.interactions[`${who}/${v}`] = {});
  const go = async (u, action) => { await page.goto(u, { waitUntil: "load" }); await settle(page, action); };
  const waitUrlChange = async (from, ms = 12000) => { const t0 = Date.now(); while (Date.now() - t0 < ms) { if (rel(page) !== from) break; await page.waitForTimeout(150); } await page.waitForLoadState("networkidle").catch(() => {}); await page.waitForTimeout(600); return rel(page); };
  const safe = async (k, fn) => { try { I[k] = await fn(); } catch (e) { I[k] = { ...(I[k] || {}), error: e.message.split("\n")[0] }; } };
  const shot = async (name, full = false) => { const f = path.join(L.RAW, `int-${name}--${who}--${v}.png`); await page.screenshot({ path: f, fullPage: full }); return f; };
  const main = page.locator("#main-content");
  const full = v === "1440-light";

  // Diary: view tabs and range navigation (URL before → after); read-only
  await safe("diaryViews", async () => {
    await go(`${B}/diary?from=2026-11-23`);
    const r = { start: rel(page) };
    for (const tab of ["Day", "Month", "List", "Week"]) {
      const from = rel(page);
      await main.getByRole("tab", { name: tab, exact: true }).click();
      r[`to${tab}`] = await waitUrlChange(from);
      await page.getByTestId("diary-range").waitFor({ timeout: 15000 }).catch(() => {});
      r[`range${tab}`] = (await page.getByTestId("diary-range").innerText().catch(() => "")).trim();
    }
    let from = rel(page);
    await main.getByRole("button", { name: /^Next/ }).first().click();
    r.next = await waitUrlChange(from);
    from = rel(page);
    await main.getByRole("button", { name: /^Previous/ }).first().click();
    r.previous = await waitUrlChange(from);
    return r;
  });
  // Diary filters on the list view: toggle the Court dates chip off and on (client-only, replaceState)
  await safe("diaryFilters", async () => {
    await go(`${B}/diary?view=list&from=2026-11-01`);
    const chip = page.getByTestId("diary-filter-row").getByRole("button", { name: /Court dates/ });
    const r = { start: rel(page), chipPressed: await chip.getAttribute("aria-pressed"), rows: await page.locator('[data-testid^="diary-list-row-"]').count() };
    await chip.click(); await page.waitForTimeout(800);
    r.afterOff = { url: rel(page), pressed: await chip.getAttribute("aria-pressed"), rows: await page.locator('[data-testid^="diary-list-row-"]').count() };
    await chip.click(); await page.waitForTimeout(800);
    r.afterOn = { url: rel(page), pressed: await chip.getAttribute("aria-pressed"), rows: await page.locator('[data-testid^="diary-list-row-"]').count() };
    const combos = page.getByTestId("diary-filter-row").getByRole("combobox");
    r.combos = await combos.allInnerTexts();
    if (await combos.count()) {
      await combos.first().click(); await page.waitForTimeout(400);
      r.feeEarnerOptions = (await page.getByRole("option").allInnerTexts()).map((s) => s.trim());
      if (full) r.feeEarnerFile = await shot("diary-fee-earner-open");
      await page.keyboard.press("Escape");
    }
    return r;
  });
  // Diary row actions on the POSTPONED court date (open → read → Escape)
  if (who === "alice") await safe("diaryPostponedRowMenu", async () => {
    await go(`${B}/diary?view=list&from=2026-11-01`);
    const id = CD["POSTPONED-2026-11-26"];
    const trig = page.getByTestId(`diary-row-actions-${id}`);
    const r = { present: await trig.count(), rowText: ((await page.getByTestId(`diary-list-row-${id}`).innerText().catch(() => "")) || "").replace(/\s+/g, " ").trim() };
    if (!r.present) return r;
    await trig.click(); await page.waitForTimeout(400);
    r.items = (await page.getByRole("menuitem").allInnerTexts()).map((s) => s.trim());
    r.file = await shot("diary-postponed-menu");
    await page.keyboard.press("Escape");
    return r;
  });
  // Court dates tab: the Actions menu of each non-terminal row (open → read → Escape)
  if (who === "alice" && full) await safe("courtDateMenus", async () => {
    await go(`${B}/matters/${ID.khumaloMatterId}`, "courtTab");
    const rows = page.getByTestId("court-date-list").locator("tbody tr");
    const n = await rows.count(); const r = { rows: n, menus: [] };
    for (let i = 0; i < n; i++) {
      const row = rows.nth(i); const txt = (await row.innerText()).replace(/\s+/g, " ").trim();
      const btn = row.getByRole("button", { name: "Actions" });
      if (!(await btn.count())) { r.menus.push({ row: txt, actions: null }); continue; }
      await btn.click(); await page.waitForTimeout(300);
      r.menus.push({ row: txt, actions: (await page.getByRole("menuitem").allInnerTexts()).map((s) => s.trim()) });
      await page.keyboard.press("Escape"); await page.waitForTimeout(200);
    }
    return r;
  });
  // My Work: date range presets (router.push ?from&to), TASK saved views, Save View dialog (cancelled),
  // Available Tasks collapse / expand
  await safe("myWorkDateRange", async () => {
    await go(`${B}/my-work?from=2026-11-23&to=2026-11-29`);
    const r = { start: rel(page), presets: (await main.locator("button").allInnerTexts()).map((s) => s.trim()).filter((s) => ["This Week", "This Month", "Last 30 Days", "This Quarter", "Custom"].includes(s)) };
    let from = rel(page);
    await main.getByRole("button", { name: "This Month", exact: true }).click();
    r.toThisMonth = await waitUrlChange(from);
    from = rel(page);
    await main.getByRole("button", { name: "This Week", exact: true }).click();
    r.toThisWeek = await waitUrlChange(from);
    if (full) r.file = await shot("my-work-this-week");
    return r;
  });
  await safe("myWorkViews", async () => {
    await go(`${B}/my-work?from=2026-11-23&to=2026-11-29`);
    const sel = page.getByTestId("saved-view-selector");
    const r = { selector: await sel.count(), tabs: (await sel.getByRole("tab").allInnerTexts().catch(() => [])).map((s) => s.trim()) };
    const save = main.getByRole("button", { name: /Save View/i });
    r.saveViewButton = await save.count();
    if (r.saveViewButton && full) {
      await save.first().click();
      const dlg = page.getByRole("dialog"); await dlg.waitFor({ timeout: 8000 });
      r.dialogTitle = (await dlg.getByRole("heading").first().innerText()).trim();
      r.file = await shot("my-work-save-view-dialog");
      await page.keyboard.press("Escape"); await dlg.waitFor({ state: "hidden" }).catch(() => {});
    }
    const toggle = main.getByRole("button", { name: /Available Tasks/ });
    if (await toggle.count()) {
      const h0 = await page.evaluate(() => document.documentElement.scrollHeight);
      await toggle.click(); await page.waitForTimeout(400);
      const h1 = await page.evaluate(() => document.documentElement.scrollHeight);
      await toggle.click(); await page.waitForTimeout(400);
      r.availableCollapse = { docHeightOpen: h0, docHeightCollapsed: h1, docHeightReopened: await page.evaluate(() => document.documentElement.scrollHeight) };
    }
    return r;
  });
  // Timesheet: keyboard entry across the week (typed, NEVER saved), the Add-task search
  await safe("timesheetKeyboard", async () => {
    await go(`${B}/my-work/timesheet?week=2026-11-23`);
    const cells = main.locator('input[aria-label^="Hours for day"]');
    const r = { cells: await cells.count(), rows: await main.locator("tbody tr").count(), saveDisabledBefore: await main.getByRole("button", { name: "Save", exact: true }).isDisabled().catch(() => null) };
    if (r.cells) {
      await cells.first().click();
      const seq = [];
      const typed = { 0: "2.5", 1: "1", 3: "0.75" };
      for (let d = 0; d < 7; d++) {
        const lab = await page.evaluate(() => document.activeElement && (document.activeElement.getAttribute("aria-label") || document.activeElement.innerText || document.activeElement.tagName));
        if (typed[d]) await page.keyboard.type(typed[d]);
        if (d === 2) { await page.keyboard.press("ArrowUp"); await page.keyboard.press("ArrowUp"); }
        seq.push({ focus: lab, typed: typed[d] || (d === 2 ? "ArrowUp×2" : null), value: await page.evaluate(() => document.activeElement && document.activeElement.value) });
        await page.keyboard.press("Tab");
      }
      r.tabSequence = seq;
      r.afterLastTab = await page.evaluate(() => { const a = document.activeElement; return a && `${a.tagName.toLowerCase()} ${(a.getAttribute("aria-label") || a.innerText || a.getAttribute("placeholder") || "").slice(0, 40)}`; });
      await page.waitForTimeout(300);
      const m = await page.evaluate(probe);
      const grid = m.tables[0];
      r.rowsAfter = grid && grid.rows.map((x) => x.cells.join(" | "));
      r.values = await cells.evaluateAll((els) => els.map((e) => e.value));
      r.saveDisabledAfter = await main.getByRole("button", { name: "Save", exact: true }).isDisabled().catch(() => null);
      r.file = await shot("timesheet-typed");
      await crop(page, `timesheet-typed-${who}-${v}`, "#main-content table");
    }
    const add = main.getByPlaceholder("Add task...");
    if (await add.count()) {
      await add.fill("Estate"); await page.waitForTimeout(400);
      r.addTaskEstateOptions = await add.locator("xpath=following-sibling::div[1]//button").count();
      await add.fill("Khumalo"); await page.waitForTimeout(400);
      r.addTaskKhumaloOptions = await add.locator("xpath=following-sibling::div[1]//button").count();
      await add.fill("");
    }
    return r; // nothing saved: the page is left with the edits unsaved (no beforeunload guard)
  });
  // Notifications: All / Unread toggle (client fetch), empty state; never click an item
  await safe("notificationsToggle", async () => {
    await go(`${B}/notifications`);
    const r = { start: { items: (await page.evaluate(probe)).notifications } };
    await main.getByRole("button", { name: "Unread", exact: true }).click(); await page.waitForTimeout(1200);
    r.unread = { url: rel(page), ...(await page.evaluate(probe)).notifications, empties: (await page.evaluate(probe)).empties.map((e) => e.text) };
    r.unreadFile = await shot("notifications-unread", true);
    await main.getByRole("button", { name: "All", exact: true }).click(); await page.waitForTimeout(1200);
    r.all = { url: rel(page), ...(await page.evaluate(probe)).notifications };
    return r;
  });
  // Header bell popover: open → read → Escape (opening marks nothing read)
  await safe("bellPopover", async () => {
    await go(`${B}/my-work?from=2026-11-23&to=2026-11-29`);
    const bell = page.getByRole("button", { name: "Notifications", exact: true });
    const r = { bellCount: await bell.count() };
    await bell.first().click();
    const dlg = page.getByRole("dialog", { name: "Notifications" });
    await dlg.waitFor({ timeout: 8000 }); await page.waitForTimeout(1200);
    r.viewAll = await dlg.getByRole("link", { name: "View all notifications" }).count();
    r.markAllRead = await dlg.getByRole("button", { name: /Mark all read/ }).count();
    r.items = await dlg.locator('[aria-label="Unread"]').count();
    r.text = (await dlg.innerText()).replace(/\s+/g, " ").trim().slice(0, 200);
    r.file = await shot("bell-popover");
    await page.keyboard.press("Escape"); await dlg.waitFor({ state: "hidden" }).catch(() => {});
    r.closedByEscape = (await page.getByRole("dialog", { name: "Notifications" }).count()) === 0;
    return r;
  });
  // SEL-COMPAT: the selectors notification-preferences.spec.ts uses (:53-103), read-only
  if (who === "alice" && full) await safe("notificationPrefsSel", async () => {
    await go(`${B}/settings/notifications`);
    await page.getByRole("tabpanel").first().waitFor({ timeout: 15000 }).catch(() => {});
    await page.waitForTimeout(800);
    const panel = page.getByRole("tabpanel");
    return {
      url: rel(page),
      redirectedToConnectionsNotifications: /\/settings\/connections\?tab=notifications/.test(page.url()),
      h1NotificationsPrefix: await page.getByRole("heading", { level: 1, name: /^Notifications/ }).count(),
      tabpanel: await panel.count(),
      categories: Object.fromEntries(await Promise.all(["Tasks", "Matters", "Billing", "Time Tracking"].map(async (cat) => [cat, await panel.getByText(cat, { exact: true }).count()]))),
      switchTimeReminders: await page.getByRole("switch", { name: "Time Reminders" }).count(),
      switchTimeRemindersChecked: await page.getByRole("switch", { name: "Time Reminders" }).first().getAttribute("aria-checked").catch(() => null),
      emailTimeReminder: await page.locator("#email-TIME_REMINDER").count(),
      emailTimeReminderChecked: await page.locator("#email-TIME_REMINDER").getAttribute("aria-checked").catch(() => null),
      savePreferences: await page.getByRole("button", { name: "Save preferences" }).count(),
    };
  });
  await ctx.close();
}

(async () => {
  const browser = await L.chromium.launch();
  for (const who of ["alice", "carol"]) {
    const st = await L.loginState(browser, who);
    await capture(browser, who, st);
    await interact(browser, st, who, "1440-light");
    if (who === "alice") await interact(browser, st, who, "1024-dark");
    console.log("captured", who);
  }
  // API cross-check: the diary feed the page reads (getDiary → GET /api/diary?from&to, no types) vs
  // every court date on the matter (GET /api/court-dates), the timesheet week, notification counts
  out.api = {};
  for (const who of ["alice", "carol"]) {
    const t = await L.idpToken(who);
    const d = await L.apiGet(t, "/api/diary?from=2026-11-01&to=2026-11-30");
    const w = await L.apiGet(t, "/api/diary?from=2026-11-23&to=2026-11-29");
    const cds = L.list((await L.apiGet(t, "/api/court-dates?size=100")).json);
    const e = (await L.apiGet(t, "/api/my-work/time-entries?from=2026-11-23&to=2026-11-29")).json || [];
    const tasks = (await L.apiGet(t, "/api/my-work/tasks")).json || {};
    const n = (await L.apiGet(t, "/api/notifications?size=100")).json || {};
    const unread = (await L.apiGet(t, "/api/notifications/unread-count")).json;
    out.api[who] = {
      diaryNov: { status: d.status, types: d.headers["x-diary-types"], events: (d.json || []).map((x) => ({ id: x.id, kind: x.kind, date: x.date, status: x.status, overdue: x.overdue ?? x.isOverdue, title: x.title })) },
      diaryWeek: { status: w.status, events: (w.json || []).map((x) => `${x.kind} ${x.date} ${x.status}`) },
      courtDates: cds.map((c) => ({ id: c.id, date: c.scheduledDate, status: c.status, ref: c.courtReference })),
      courtDatesNotInDiary: cds.filter((c) => !(d.json || []).some((x) => x.id === c.id)).map((c) => `${c.scheduledDate} ${c.status}`),
      weekEntries: { count: e.length, minutes: e.reduce((s, x) => s + (x.durationMinutes || 0), 0), byDay: e.map((x) => `${x.date} ${x.durationMinutes}m task ${x.taskId}`) },
      myWorkTasks: { assigned: (tasks.assigned || []).length, unassigned: (tasks.unassigned || []).length },
      notifications: { total: n.page && n.page.totalElements, unreadCount: unread && unread.count },
    };
  }
  out.console = cons;
  await browser.close();
  fs.writeFileSync(path.join(L.RAW, "capture.json"), JSON.stringify(out));
  console.log("pages", Object.values(out.pages).reduce((n, x) => n + Object.keys(x).length, 0), "console", cons.length);
  console.log("interactions", JSON.stringify(out.interactions).slice(0, 4000));
})().catch((e) => { console.error("CAPTURE FAIL", e); process.exit(1); });
