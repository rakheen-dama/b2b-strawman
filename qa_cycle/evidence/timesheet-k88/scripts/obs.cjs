// K-88 (FU-6) — OBS through the UI (real Chromium via the Playwright library; never the founder's
// Playwright-MCP Chrome). E2E mock-auth stack: app :3001, backend :8081, IDP :8090. As Alice:
//   1. Log 1h on a task NOT assigned to her (matter › Tasks tab › Log Time), on this week's Wed.
//      Timesheet lists the task as a row; daily + weekly totals match the week's entries and My
//      Work's "this week".
//   2. That row's Wed 1h → 1.5h → Save: exactly one new 30-min entry; the 60-min entry is untouched.
//   3. Previous, then This Week (client-side navigation): the row is back and totals still match.
//   4. A 1h entry on the same task on last week's Tue (Log Time dialog), then on this week: Copy
//      Previous Week with its server action delayed 4 s (page.route) — Save and week nav are
//      disabled and a Save click does nothing; then Save (delayed) — Copy is disabled. Every copied
//      cell ends on the server or in the "Not saved" alert: none is lost silently.
// Counting uses read-only GETs (/api/my-work/time-entries, /api/my-work/tasks). No SQL.
// Writes ../obs.json and ../*.png. Usage: node obs.cjs
const path = require("path");
const fs = require("fs");

const W = path.resolve(__dirname, "../../../..");
const { chromium } = require(path.join(W, "frontend/node_modules/@playwright/test"));
const APP = "http://localhost:3001";
const API = "http://localhost:8081";
const IDP = "http://localhost:8090";
const ORG = "e2e-test-org";
const BASE = `${APP}/org/${ORG}`;
const OUT = path.resolve(__dirname, "..");
fs.mkdirSync(OUT, { recursive: true });
const FILE = path.join(OUT, "obs.json");

// Current SA week, Monday first.
const todaySA = new Date(new Date().toLocaleString("en-US", { timeZone: "Africa/Johannesburg" }));
const monday = new Date(todaySA);
monday.setDate(todaySA.getDate() - ((todaySA.getDay() + 6) % 7));
const DAY = (i) => {
  const d = new Date(monday);
  d.setDate(monday.getDate() + i);
  return d.toLocaleDateString("en-CA");
};
const MON = DAY(0), WED = DAY(2), SUN = DAY(6);
const PREV_MON = DAY(-7), PREV_TUE = DAY(-6), PREV_SUN = DAY(-1);

const out = { at: new Date().toISOString(), week: { MON, SUN }, prevWeek: { PREV_MON, PREV_SUN }, steps: [], console: [], checks: [] };
const check = (name, ok, detail) => out.checks.push({ name, ok: !!ok, detail });
const hours = (t) => (t === "—" || t === "" ? 0 : Number(t.replace("h", "")));
const near = (a, b) => Math.abs(a - b) < 0.001;

async function token() {
  const r = await fetch(`${IDP}/token`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ userId: "user_e2e_alice", orgId: "org_e2e_test", orgSlug: ORG, orgRole: "owner" }),
  });
  return (await r.json()).access_token;
}
async function get(tok, p) {
  const r = await fetch(`${API}${p}`, { headers: { Authorization: `Bearer ${tok}` } });
  if (!r.ok) throw new Error(`GET ${p} -> ${r.status}`);
  return r.json();
}

(async () => {
  const tok = await token();
  const entriesIn = async (from, to) =>
    (await get(tok, `/api/my-work/time-entries?from=${from}&to=${to}`)).map((e) => ({
      id: e.id, taskId: e.taskId, date: e.date, minutes: e.durationMinutes,
    }));
  const thisWeek = () => entriesIn(MON, SUN);
  // Expected Daily Total (h) per day Mon..Sun from the API, plus the week total.
  const apiTotals = (entries, mondayIso) => {
    const days = Array.from({ length: 7 }, (_, i) => {
      const d = new Date(mondayIso);
      d.setDate(d.getDate() + i);
      return d.toISOString().slice(0, 10);
    });
    const perDay = days.map((d) => entries.filter((e) => e.date === d).reduce((s, e) => s + e.minutes, 0) / 60);
    return { perDay, week: perDay.reduce((s, h) => s + h, 0) };
  };

  // A task in one of Alice's matters that is NOT assigned to her.
  const tasks = await get(tok, "/api/my-work/tasks");
  const task = tasks.unassigned.find((t) => t.status === "OPEN");
  if (!task) throw new Error("no OPEN unassigned task");
  if (tasks.assigned.some((t) => t.id === task.id)) throw new Error("picked task is assigned");
  out.task = { id: task.id, title: task.title, matterId: task.matterId, matterName: task.matterName };
  out.assignedTaskCount = tasks.assigned.length;
  out.entriesBefore = await thisWeek();

  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, timezoneId: "Africa/Johannesburg", reducedMotion: "reduce" });
  const page = await ctx.newPage();
  page.on("console", (m) => {
    if (m.type() === "error") out.console.push(m.text().slice(0, 300));
  });
  page.on("pageerror", (e) => out.console.push(String(e).slice(0, 300)));
  page.on("dialog", (d) => {
    out.steps.push({ step: "browser dialog accepted", message: d.message() });
    d.accept();
  });

  await page.goto(`${APP}/mock-login`, { waitUntil: "load" });
  await page.getByRole("button", { name: "Sign In", exact: true }).click();
  await page.waitForURL(/\/org\/.*\/dashboard/, { timeout: 60000 });

  const main = page.locator("#main-content");

  const logTime = async (date, desc, shot) => {
    await page.goto(`${BASE}/matters/${task.matterId}`, { waitUntil: "load" });
    const group = page.getByTestId("tab-group-work").filter({ visible: true });
    const item = page.getByTestId("tab-item-tasks").filter({ visible: true });
    await group.waitFor({ timeout: 20000 });
    for (let i = 0; i < 3; i++) {
      await group.click();
      if (await item.waitFor({ state: "visible", timeout: 2000 }).then(() => true, () => false)) {
        await item.click();
        break;
      }
    }
    const row = page.getByRole("tabpanel").getByRole("row").filter({ has: page.getByRole("button", { name: `Open task detail for ${task.title}`, exact: true }) }).first();
    await row.waitFor({ timeout: 15000 });
    out.steps.push({ step: `tasks tab row for "${task.title}"`, rowText: (await row.innerText()).replace(/\s+/g, " ").trim() });
    await row.getByRole("button", { name: "Log Time" }).click();
    const dialog = page.getByRole("dialog", { name: "Log Time" });
    await dialog.waitFor({ timeout: 10000 });
    await dialog.locator("#time-hours").fill("1");
    await dialog.locator("#time-minutes").fill("0");
    await dialog.locator("#time-date").fill(date);
    await dialog.locator("#time-description").fill(desc);
    await page.screenshot({ path: path.join(OUT, `${shot}.png`) });
    await dialog.getByRole("button", { name: "Log Time" }).click();
    await dialog.waitFor({ state: "hidden", timeout: 15000 });
  };

  const openTimesheet = async () => {
    await page.goto(`${BASE}/my-work/timesheet`, { waitUntil: "load" });
    await page.waitForLoadState("networkidle").catch(() => {});
    await main.getByText("Daily Total").waitFor({ timeout: 20000 });
    await page.waitForTimeout(500);
  };
  const myWorkThisWeek = async () => {
    await page.goto(`${BASE}/my-work`, { waitUntil: "load" });
    const strip = page.getByTestId("weekly-rhythm-strip").first();
    await strip.waitFor({ timeout: 20000 });
    const text = (await strip.innerText()).replace(/\s+/g, " ").trim();
    const m = text.match(/([\d.]+)h this week/);
    return { text, hours: m ? Number(m[1]) : null };
  };

  const taskRow = () => main.locator("tbody tr").filter({ hasText: task.title });
  const cell = (d) => taskRow().first().locator(`input[aria-label="Hours for day ${d}"]`);
  const values = async () =>
    (await taskRow().count()) ? taskRow().first().locator('input[aria-label^="Hours for day"]').evaluateAll((els) => els.map((e) => e.value)) : null;
  const gridTotals = async () => {
    const cells = await main.locator("tbody tr").filter({ hasText: "Daily Total" }).locator("td").allInnerTexts();
    const t = cells.slice(1).map((c) => hours(c.trim()));
    return { perDay: t.slice(0, 7), week: t[7] };
  };
  const label = async () => (await main.locator("span").filter({ hasText: /–/ }).first().innerText()).trim();
  const rowTitles = () => main.locator("tbody tr td:first-child p:first-child").allInnerTexts();
  const setCell = async (d, v) => {
    await cell(d).click();
    await cell(d).press("ControlOrMeta+a");
    await page.keyboard.press("Backspace");
    if (v !== "") await page.keyboard.type(v);
    await page.keyboard.press("Tab"); // TimeCell commits on blur
  };
  const saveBtn = () => main.getByRole("button", { name: /^Sav(e|ing\.\.\.)$/ });
  const copyBtn = () => main.getByRole("button", { name: /Copy Previous Week|Copying\.\.\./ });
  const totalsMatch = (g, a) => g.perDay.every((h, i) => near(h, a.perDay[i])) && near(g.week, a.week);
  const snapshot = async (name, entries, mondayIso, extra = {}) => {
    const g = await gridTotals();
    const a = apiTotals(entries, mondayIso);
    const s = { step: name, label: await label(), rows: await rowTitles(), taskRowCount: await taskRow().count(), taskValues: await values(), gridTotals: g, apiTotals: a, ...extra };
    out.steps.push(s);
    await page.screenshot({ path: path.join(OUT, `${name}.png`), fullPage: true });
    return s;
  };

  // --- 1. Log time on a non-assigned task, then open the timesheet ---
  await logTime(WED, "OBS K-88 non-assigned task", "01a-log-time-dialog");
  const e1 = await thisWeek();
  const mine1 = e1.filter((e) => e.taskId === task.id && e.date === WED);
  check("1: the Log Time dialog created one 60-min Wed entry", mine1.length === 1 && mine1[0].minutes === 60, mine1);
  const firstId = mine1[0]?.id;
  const mw1 = await myWorkThisWeek();
  await page.screenshot({ path: path.join(OUT, "01b-my-work.png") });
  await openTimesheet();
  const s1 = await snapshot("01c-timesheet-row", e1, MON, { myWork: mw1 });
  check("1: the non-assigned task has exactly one row", s1.taskRowCount === 1, s1.rows);
  check("1: its Wed cell shows 1", s1.taskValues && s1.taskValues[2] === "1", s1.taskValues);
  check("1: daily + weekly totals equal the week's entries (API)", totalsMatch(s1.gridTotals, s1.apiTotals), { grid: s1.gridTotals, api: s1.apiTotals });
  check("1: weekly total equals My Work 'this week'", mw1.hours !== null && near(Number(s1.gridTotals.week.toFixed(1)), mw1.hours), { grid: s1.gridTotals.week, myWork: mw1 });

  // --- 2. Increase the row and save ---
  await setCell(3, "1.5");
  await saveBtn().click();
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.waitForTimeout(2500);
  const toasts2 = (await page.locator("[data-sonner-toast]").allInnerTexts().catch(() => [])).map((t) => t.replace(/\s+/g, " ").trim());
  const e2 = await thisWeek();
  const mine2 = e2.filter((e) => e.taskId === task.id && e.date === WED);
  const s2 = await snapshot("02-increase-saved", e2, MON, { toasts: toasts2, wedEntries: mine2 });
  check("2: Wed now has two entries: the original 60 (same id) + one new 30", mine2.length === 2 && mine2.some((e) => e.id === firstId && e.minutes === 60) && mine2.some((e) => e.id !== firstId && e.minutes === 30), mine2);
  check("2: exactly one entry was added this week", e2.length === e1.length + 1, { before: e1.length, after: e2.length });
  check("2: toast says 1 change", toasts2.some((t) => /Saved 1 change\b/.test(t)), toasts2);
  check("2: grid shows Wed 1.5 and Save is disabled", s2.taskValues && s2.taskValues[2] === "1.5" && (await saveBtn().isDisabled()), s2.taskValues);
  check("2: totals equal the API", totalsMatch(s2.gridTotals, s2.apiTotals), { grid: s2.gridTotals, api: s2.apiTotals });

  // --- 3. Previous, then This Week ---
  const mw3 = await myWorkThisWeek();
  await openTimesheet();
  await main.getByRole("button", { name: "Previous week", exact: true }).click();
  await page.waitForFunction(() => !document.body.innerText.includes("Loading..."), null, { timeout: 20000 });
  await page.waitForTimeout(1500);
  const p3 = await entriesIn(PREV_MON, PREV_SUN);
  const s3a = await snapshot("03a-previous-week", p3, PREV_MON);
  check("3: previous week's totals equal its entries (API)", totalsMatch(s3a.gridTotals, s3a.apiTotals), { grid: s3a.gridTotals, api: s3a.apiTotals });
  check("3: previous week: the task has no row (no time there yet)", s3a.taskRowCount === 0, s3a.rows);
  await main.getByRole("button", { name: "This Week", exact: true }).click();
  await page.waitForFunction(() => !document.body.innerText.includes("Loading..."), null, { timeout: 20000 });
  await taskRow().first().waitFor({ timeout: 15000 });
  await page.waitForTimeout(1000);
  const e3 = await thisWeek();
  const s3b = await snapshot("03b-this-week-again", e3, MON, { myWork: mw3 });
  check("3: This Week: the row is back once with Wed 1.5", s3b.taskRowCount === 1 && s3b.taskValues[2] === "1.5", s3b.taskValues);
  check("3: This Week: totals equal the API", totalsMatch(s3b.gridTotals, s3b.apiTotals), { grid: s3b.gridTotals, api: s3b.apiTotals });
  check("3: This Week: weekly total equals My Work 'this week'", mw3.hours !== null && near(Number(s3b.gridTotals.week.toFixed(1)), mw3.hours), { grid: s3b.gridTotals.week, myWork: mw3 });

  // --- 4. Copy Previous Week during a delayed server action, then a delayed Save ---
  await logTime(PREV_TUE, "OBS K-88 previous-week entry", "04a-log-time-prev-week");
  const prev = await entriesIn(PREV_MON, PREV_SUN);
  out.prevWeekEntries = prev;
  await openTimesheet();
  const before4 = await thisWeek();
  const labelBefore = await label();
  let delayed = 0;
  await page.route("**/my-work/timesheet**", async (route) => {
    const req = route.request();
    if (req.method() === "POST" && req.headers()["next-action"]) {
      delayed++;
      await new Promise((r) => setTimeout(r, 4000));
    }
    await route.continue();
  });
  await copyBtn().click();
  await page.waitForTimeout(800);
  const duringCopy = {
    copyLabel: (await copyBtn().innerText()).trim(),
    copyDisabled: await copyBtn().isDisabled(),
    saveDisabled: await saveBtn().isDisabled(),
    nextDisabled: await main.getByRole("button", { name: "Next week", exact: true }).isDisabled(),
    prevDisabled: await main.getByRole("button", { name: "Previous week", exact: true }).isDisabled(),
    thisWeekDisabled: await main.getByRole("button", { name: "This Week", exact: true }).isDisabled(),
  };
  let saveClickBlocked = false;
  await saveBtn().click({ timeout: 1500 }).catch(() => (saveClickBlocked = true));
  await page.screenshot({ path: path.join(OUT, "04b-copy-in-flight.png") });
  await main.getByRole("button", { name: "Copy Previous Week" }).waitFor({ timeout: 30000 });
  await page.waitForTimeout(800);
  const afterCopy = { values: await values(), label: await label(), saveDisabled: await saveBtn().isDisabled() };
  await page.screenshot({ path: path.join(OUT, "04c-copied.png"), fullPage: true });
  // The cells the copy wrote: previous-week sums per (task, weekday), shifted +7 days.
  const copied = {};
  for (const e of prev) {
    const d = new Date(e.date);
    d.setDate(d.getDate() + 7);
    const k = `${e.taskId}|${d.toISOString().slice(0, 10)}`;
    copied[k] = (copied[k] ?? 0) + e.minutes;
  }
  await saveBtn().click();
  await page.waitForTimeout(800);
  const duringSave = {
    saveLabel: (await saveBtn().innerText()).trim(),
    copyDisabled: await copyBtn().isDisabled(),
    nextDisabled: await main.getByRole("button", { name: "Next week", exact: true }).isDisabled(),
  };
  await page.screenshot({ path: path.join(OUT, "04d-save-in-flight.png") });
  await main.getByRole("button", { name: "Save", exact: true }).waitFor({ timeout: 60000 });
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.waitForTimeout(2000);
  await page.unroute("**/my-work/timesheet**");
  const alert = (await main.getByTestId("timesheet-save-problems").count()) ? (await main.getByTestId("timesheet-save-problems").innerText()).replace(/\s+/g, " ").trim() : null;
  const toasts4 = (await page.locator("[data-sonner-toast]").allInnerTexts().catch(() => [])).map((t) => t.replace(/\s+/g, " ").trim());
  const e4 = await thisWeek();
  const sumOn = (taskId, date) => e4.filter((e) => e.taskId === taskId && e.date === date).reduce((s, e) => s + e.minutes, 0);
  const copiedOutcome = Object.entries(copied).map(([k, minutes]) => {
    const [taskId, date] = k.split("|");
    const server = sumOn(taskId, date);
    return { taskId, date, copiedMinutes: minutes, serverMinutes: server, onServer: server === minutes };
  });
  const s4 = await snapshot("04e-after-save", e4, MON, { duringCopy, saveClickBlocked, afterCopy, duringSave, delayedActionPosts: delayed, alert, toasts: toasts4, copiedOutcome, labelBefore });
  check("4: both server actions were delayed (copy fetch + save)", delayed >= 2, delayed);
  check("4: during the copy: Copying..., Copy + Save + week nav disabled", duringCopy.copyLabel === "Copying..." && duringCopy.copyDisabled && duringCopy.saveDisabled && duringCopy.nextDisabled && duringCopy.prevDisabled && duringCopy.thisWeekDisabled, duringCopy);
  check("4: clicking Save during the copy does nothing", saveClickBlocked, saveClickBlocked);
  check("4: the copy landed on this week: the task's Tue shows 1, Save enabled", afterCopy.label === labelBefore && afterCopy.values && afterCopy.values[1] === "1" && !afterCopy.saveDisabled, afterCopy);
  check("4: during the save: Saving..., Copy disabled", duringSave.saveLabel === "Saving..." && duringSave.copyDisabled && duringSave.nextDisabled, duringSave);
  check("4: no copied value lost silently (each copied cell is on the server or in the alert)", copiedOutcome.every((c) => c.onServer || (alert && alert.length > 0)), { copiedOutcome, alert });
  const ourTue = copiedOutcome.find((c) => c.taskId === task.id && c.date === DAY(1));
  check("4: the task's copied Tue 60 is on the server once", ourTue && ourTue.onServer && e4.filter((e) => e.taskId === task.id && e.date === DAY(1)).length === 1, ourTue);
  check("4: the task's Wed is unchanged (60 + 30)", sumOn(task.id, WED) === 90 && e4.filter((e) => e.taskId === task.id && e.date === WED).length === 2, e4.filter((e) => e.taskId === task.id && e.date === WED));
  check("4: totals equal the API after the save", totalsMatch(s4.gridTotals, s4.apiTotals), { grid: s4.gridTotals, api: s4.apiTotals });
  out.entriesBeforeStep4 = before4.length;

  const mw4 = await myWorkThisWeek();
  check("4: weekly total equals My Work 'this week' after the save", mw4.hours !== null && near(Number(s4.gridTotals.week.toFixed(1)), mw4.hours), { grid: s4.gridTotals.week, myWork: mw4 });

  out.totalsByStep = {
    step1: { grid: s1.gridTotals, myWork: mw1.hours },
    step2: { grid: s2.gridTotals },
    step3: { previous: s3a.gridTotals, thisWeek: s3b.gridTotals, myWork: mw3.hours },
    step4: { grid: s4.gridTotals, myWork: mw4.hours },
  };
  check("console: no errors", out.console.length === 0, out.console);
  out.pass = out.checks.every((c) => c.ok);
  await ctx.close();
  await browser.close();
  fs.writeFileSync(FILE, JSON.stringify(out, null, 2));
  console.log(out.pass ? "OBS PASS" : "OBS FAIL", JSON.stringify(out.checks.map((c) => `${c.ok ? "ok" : "FAIL"} ${c.name}`), null, 1), JSON.stringify(out.totalsByStep));
})().catch((e) => {
  console.error("OBS ERROR", e);
  fs.writeFileSync(FILE + ".failed", JSON.stringify({ ...out, error: String(e) }, null, 2));
  process.exit(1);
});
