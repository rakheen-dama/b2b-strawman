// K-88 review round 1 — OBS (a NEW file; obs.cjs is the executed round-0 script, unchanged).
// Real Chromium via the Playwright library (never the founder's Playwright-MCP Chrome), E2E
// mock-auth stack (app :3001, backend :8081, IDP :8090), as Alice.
//   Setup (UI): Alice claims open tasks on their matters' Tasks tabs until she has 6+ assigned tasks
//   with no time this week, so one assigned task H has no grid row (the page pre-lists 5) and is
//   offered by "Add task...". 1h is logged on H on last week's Tue via H's Log Time dialog.
//   M1:   type H's title in "Add task..." (option offered), make a real edit (B-L1: Save enabled),
//         then Copy Previous Week with its server action delayed 4 s: Save, Add task and the cells
//         are disabled, H's option is gone, Save/Add-task attempts do nothing. The copy gives H ONE
//         row (Tue 1). Save (delayed; Copy disabled during it) posts H's Tue 60 once and the edit's
//         30 once — no (task, date) gains more than one entry.
// Read-only GETs + DOM only. No SQL. Writes ../r1/obs-r1.json and ../r1/*.png.
const path = require("path");
const fs = require("fs");

const W = path.resolve(__dirname, "../../../..");
const { chromium } = require(path.join(W, "frontend/node_modules/@playwright/test"));
const APP = "http://localhost:3001";
const API = "http://localhost:8081";
const IDP = "http://localhost:8090";
const ORG = "e2e-test-org";
const BASE = `${APP}/org/${ORG}`;
const OUT = path.resolve(__dirname, "../r1");
fs.mkdirSync(OUT, { recursive: true });
const FILE = path.join(OUT, "obs-r1.json");

const todaySA = new Date(new Date().toLocaleString("en-US", { timeZone: "Africa/Johannesburg" }));
const monday = new Date(todaySA);
monday.setDate(todaySA.getDate() - ((todaySA.getDay() + 6) % 7));
const DAY = (i) => {
  const d = new Date(monday);
  d.setDate(monday.getDate() + i);
  return d.toLocaleDateString("en-CA");
};
const MON = DAY(0), TUE = DAY(1), SUN = DAY(6);
const PREV_MON = DAY(-7), PREV_TUE = DAY(-6), PREV_SUN = DAY(-1);

const out = { at: new Date().toISOString(), week: { MON, SUN }, steps: [], console: [], checks: [] };
const check = (name, ok, detail) => out.checks.push({ name, ok: !!ok, detail });

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
    (await get(tok, `/api/my-work/time-entries?from=${from}&to=${to}`)).map((e) => ({ id: e.id, taskId: e.taskId, date: e.date, minutes: e.durationMinutes }));
  const thisWeek = () => entriesIn(MON, SUN);

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

  const openTaskRow = async (t) => {
    await page.goto(`${BASE}/matters/${t.matterId}`, { waitUntil: "load" });
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
    const r = page.getByRole("tabpanel").getByRole("row").filter({ has: page.getByRole("button", { name: `Open task detail for ${t.title}`, exact: true }) }).first();
    await r.waitFor({ timeout: 15000 });
    return r;
  };

  // --- Setup: 6+ assigned tasks without time this week (claimed through the Tasks tab) ---
  const weekNow = await thisWeek();
  const withTime = new Set(weekNow.map((e) => e.taskId));
  let tasks = await get(tok, "/api/my-work/tasks");
  const emptyAssigned = () => tasks.assigned.filter((t) => !withTime.has(t.id));
  const taken = new Set();
  const key = (t) => `${t.title}|${t.matterName}`;
  for (const t of tasks.assigned) taken.add(key(t));
  const claimed = [];
  while (emptyAssigned().length < 6) {
    const pick = tasks.unassigned.find((t) => t.status === "OPEN" && !taken.has(key(t)));
    if (!pick) throw new Error("not enough OPEN unassigned tasks to claim");
    taken.add(key(pick));
    const r = await openTaskRow(pick);
    await r.getByRole("button", { name: "Claim", exact: true }).click();
    await r.getByRole("button", { name: "Claim", exact: true }).waitFor({ state: "detached", timeout: 15000 });
    claimed.push({ id: pick.id, title: pick.title, matterName: pick.matterName });
    tasks = await get(tok, "/api/my-work/tasks");
  }
  out.claimed = claimed;
  await page.screenshot({ path: path.join(OUT, "00-claimed.png") });

  // H: an assigned task without a grid row.
  const openTimesheet = async () => {
    await page.goto(`${BASE}/my-work/timesheet`, { waitUntil: "load" });
    await page.waitForLoadState("networkidle").catch(() => {});
    await main.getByText("Daily Total").waitFor({ timeout: 20000 });
    await page.waitForTimeout(500);
  };
  const rowOf = (t) => main.locator("tbody tr").filter({ hasText: t.title }).filter({ hasText: t.matterName });
  await openTimesheet();
  let H = null;
  for (const t of emptyAssigned()) {
    if ((await rowOf(t).count()) === 0) {
      H = t;
      break;
    }
  }
  if (!H) throw new Error("every assigned task has a row; no hidden task for Add task");
  out.hidden = { id: H.id, title: H.title, matterId: H.matterId, matterName: H.matterName };

  // 1h on H last Tuesday, through H's Log Time dialog.
  {
    const r = await openTaskRow(H);
    await r.getByRole("button", { name: "Log Time" }).click();
    const dialog = page.getByRole("dialog", { name: "Log Time" });
    await dialog.waitFor({ timeout: 10000 });
    await dialog.locator("#time-hours").fill("1");
    await dialog.locator("#time-minutes").fill("0");
    await dialog.locator("#time-date").fill(PREV_TUE);
    await dialog.locator("#time-description").fill("OBS K-88 r1 previous-week entry");
    await page.screenshot({ path: path.join(OUT, "01-log-time-prev-week.png") });
    await dialog.getByRole("button", { name: "Log Time" }).click();
    await dialog.waitFor({ state: "hidden", timeout: 15000 });
  }
  const prev = await entriesIn(PREV_MON, PREV_SUN);
  out.prevWeekEntries = prev;

  // --- M1 + B-L1 ---
  await openTimesheet();
  const before = await thisWeek();
  check("setup: H has no row on the timesheet", (await rowOf(H).count()) === 0, out.hidden);

  // B-L1: a real edit first, so Save is genuinely enabled before the copy.
  const editRow = main.locator("tbody tr").first();
  const editTitle = (await editRow.locator("td").first().locator("p").first().innerText()).trim();
  const editMatter = (await editRow.locator("td").first().locator("p").nth(1).innerText()).trim();
  const rawWeek = await get(tok, `/api/my-work/time-entries?from=${MON}&to=${SUN}`);
  const editTaskId =
    tasks.assigned.find((t) => t.title === editTitle && t.matterName === editMatter)?.id ??
    rawWeek.find((e) => e.taskTitle === editTitle && e.matterName === editMatter)?.taskId;
  const monCell = editRow.locator('input[aria-label="Hours for day 1"]');
  const monBefore = Number((await monCell.inputValue()) || 0);
  await monCell.click();
  await monCell.press("ControlOrMeta+a");
  await page.keyboard.press("Backspace");
  await page.keyboard.type(String(monBefore + 0.5));
  await page.keyboard.press("Tab");
  out.edit = { title: editTitle, matter: editMatter, taskId: editTaskId, monBefore, monAfter: monBefore + 0.5 };

  const saveBtn = () => main.getByRole("button", { name: /^Sav(e|ing\.\.\.)$/ });
  const copyBtn = () => main.getByRole("button", { name: /Copy Previous Week|Copying\.\.\./ });
  const addInput = main.getByPlaceholder("Add task...");
  const hOption = () => main.locator("button").filter({ hasText: H.title }).filter({ hasText: H.matterName });

  await addInput.fill(H.title);
  await page.waitForTimeout(300);
  const beforeCopy = { saveEnabled: !(await saveBtn().isDisabled()), addEnabled: !(await addInput.isDisabled()), hOptionOffered: (await hOption().count()) > 0 };
  await page.screenshot({ path: path.join(OUT, "02-before-copy.png") });
  check("B-L1: Save is enabled before the copy (real edit made)", beforeCopy.saveEnabled, beforeCopy);
  check("M1: Add task offers H before the copy", beforeCopy.addEnabled && beforeCopy.hOptionOffered, beforeCopy);

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
    saveDisabled: await saveBtn().isDisabled(),
    addDisabled: await addInput.isDisabled(),
    hOptionVisible: (await hOption().count()) > 0,
    cellDisabled: await monCell.isDisabled(),
    nextDisabled: await main.getByRole("button", { name: "Next week", exact: true }).isDisabled(),
  };
  let saveClickBlocked = false;
  await saveBtn().click({ timeout: 1500 }).catch(() => (saveClickBlocked = true));
  let addTypeBlocked = false;
  await addInput.fill("x", { timeout: 1500 }).catch(() => (addTypeBlocked = true));
  await page.screenshot({ path: path.join(OUT, "03-copy-in-flight.png") });
  check("B-L1: during the copy Save is disabled (it was enabled) and a click does nothing", duringCopy.copyLabel === "Copying..." && duringCopy.saveDisabled && saveClickBlocked, { duringCopy, saveClickBlocked });
  check("M1: during the copy Add task is disabled and H's option is gone", duringCopy.addDisabled && !duringCopy.hOptionVisible && addTypeBlocked, { duringCopy, addTypeBlocked });
  check("A-L1: during the copy the cells and week nav are disabled", duringCopy.cellDisabled && duringCopy.nextDisabled, duringCopy);

  await main.getByRole("button", { name: "Copy Previous Week" }).waitFor({ timeout: 30000 });
  await page.waitForTimeout(800);
  const hRows = await rowOf(H).count();
  const hValues = hRows ? await rowOf(H).first().locator('input[aria-label^="Hours for day"]').evaluateAll((els) => els.map((e) => e.value)) : null;
  await page.screenshot({ path: path.join(OUT, "04-copied.png"), fullPage: true });
  check("M1: after the copy H has exactly one row, Tue 1", hRows === 1 && hValues && hValues[1] === "1", { hRows, hValues });
  check("B-L1: the edit survived the copy", (await monCell.inputValue()) === String(monBefore + 0.5), await monCell.inputValue());

  await saveBtn().click();
  await page.waitForTimeout(800);
  const duringSave = { saveLabel: (await saveBtn().innerText()).trim(), copyDisabled: await copyBtn().isDisabled(), addDisabled: await addInput.isDisabled() };
  await page.screenshot({ path: path.join(OUT, "05-save-in-flight.png") });
  await main.getByRole("button", { name: "Save", exact: true }).waitFor({ timeout: 60000 });
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.waitForTimeout(2000);
  await page.unroute("**/my-work/timesheet**");
  const toasts = (await page.locator("[data-sonner-toast]").allInnerTexts().catch(() => [])).map((t) => t.replace(/\s+/g, " ").trim());
  const alert = (await main.getByTestId("timesheet-save-problems").count()) ? (await main.getByTestId("timesheet-save-problems").innerText()).replace(/\s+/g, " ").trim() : null;
  await page.screenshot({ path: path.join(OUT, "06-after-save.png"), fullPage: true });
  const after = await thisWeek();
  const count = (list, taskId, date) => list.filter((e) => e.taskId === taskId && e.date === date).length;
  const sum = (list, taskId, date) => list.filter((e) => e.taskId === taskId && e.date === date).reduce((s, e) => s + e.minutes, 0);
  const pairs = new Set([...before, ...after].map((e) => `${e.taskId}|${e.date}`));
  const grew = [...pairs].map((p) => {
    const [t, d] = p.split("|");
    return { taskId: t, date: d, before: count(before, t, d), after: count(after, t, d) };
  }).filter((g) => g.after !== g.before);
  out.saveResult = { delayedActionPosts: delayed, duringSave, toasts, alert, grew, entriesBefore: before.length, entriesAfter: after.length };
  check("both server actions were delayed (copy fetch + save)", delayed >= 2, delayed);
  check("during the save: Saving..., Copy and Add task disabled", duringSave.saveLabel === "Saving..." && duringSave.copyDisabled && duringSave.addDisabled, duringSave);
  check("M1: H's Tue posted once (1 entry, 60 min)", count(after, H.id, TUE) === 1 && sum(after, H.id, TUE) === 60, after.filter((e) => e.taskId === H.id));
  check("B-L1: the edit posted once (+1 entry of 30 on Mon)", editTaskId && count(after, editTaskId, MON) === count(before, editTaskId, MON) + 1 && sum(after, editTaskId, MON) === sum(before, editTaskId, MON) + 30, { editTaskId, before: before.filter((e) => e.taskId === editTaskId), after: after.filter((e) => e.taskId === editTaskId) });
  check("no (task, date) gained more than one entry", grew.every((g) => g.after - g.before <= 1), grew);
  check("toast: Saved one change per new entry, no alert", toasts.some((t) => t.includes(`Saved ${grew.length} changes`)) && alert === null, { toasts, alert, newEntryCells: grew.length });
  check("console: no errors", out.console.length === 0, out.console);

  out.pass = out.checks.every((c) => c.ok);
  await ctx.close();
  await browser.close();
  fs.writeFileSync(FILE, JSON.stringify(out, null, 2));
  console.log(out.pass ? "OBS-R1 PASS" : "OBS-R1 FAIL", JSON.stringify(out.checks.map((c) => `${c.ok ? "ok" : "FAIL"} ${c.name}`), null, 1));
})().catch((e) => {
  console.error("OBS-R1 ERROR", e);
  fs.writeFileSync(FILE + ".failed", JSON.stringify({ ...out, error: String(e) }, null, 2));
  process.exit(1);
});
