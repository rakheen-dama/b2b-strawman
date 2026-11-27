// F-668A-R1 fix — OBS through the UI (real Chromium via the Playwright library; never the
// founder's Playwright-MCP Chrome). E2E mock-auth stack: app :3001, backend :8081, IDP :8090.
// As Alice, on the current week's My Work › Timesheet, on one assigned task whose Mon/Tue/Wed
// cells start empty (on a fresh stack Alice first claims one open task on its matter's Tasks tab):
//   1. Mon 1h → Save            → 1 Mon entry (60)
//   2. reload, Tue 0.5h → Save  → still 1 Mon entry (60) + 1 Tue entry (30)  [the bug made Mon 2×60]
//   3. Mon 0.5h → Save          → the same Mon entry id, now 30 (updated, not re-created)
//   4. clear Tue → Save         → 0 Tue entries (deleted)
//   5. matter › Tasks › Log Time: a second Mon entry (1h) through the dialog
//   6. timesheet: Mon 1.5h → 1h (two entries: refused) and Wed 0.25h → Save
//      → alert names "Mon <d> · <task>" + the Time tab; Wed saved; Mon entries unchanged
//   7. reload: Save disabled (nothing to send)
// Counting uses read-only GET /api/my-work/time-entries (the endpoint the page uses). No SQL.
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
const MON = DAY(0), TUE = DAY(1), WED = DAY(2), SUN = DAY(6);
const MON_LABEL = `Mon ${Number(MON.slice(8))}`;

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
  const week = async (taskId) =>
    (await get(tok, `/api/my-work/time-entries?from=${MON}&to=${SUN}`))
      .filter((e) => !taskId || e.taskId === taskId)
      .map((e) => ({ id: e.id, date: e.date, minutes: e.durationMinutes, billable: e.billable, description: e.description ?? null }));
  out.entriesBeforeAllTasks = await week();

  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, timezoneId: "Africa/Johannesburg", reducedMotion: "reduce" });
  const page = await ctx.newPage();
  page.on("console", (m) => {
    if (m.type() === "error") out.console.push(m.text().slice(0, 300));
  });
  page.on("pageerror", (e) => out.console.push(String(e).slice(0, 300)));

  await page.goto(`${APP}/mock-login`, { waitUntil: "load" });
  await page.getByRole("button", { name: "Sign In", exact: true }).click();
  await page.waitForURL(/\/org\/.*\/dashboard/, { timeout: 60000 });

  const main = page.locator("#main-content");
  const open = async () => {
    await page.goto(`${BASE}/my-work/timesheet`, { waitUntil: "load" });
    await page.waitForLoadState("networkidle").catch(() => {});
    await main.locator('input[aria-label^="Hours for day"]').first().waitFor({ timeout: 20000 });
    await page.waitForTimeout(500);
  };
  // The matter's Tasks tab and one task's row there.
  const openTaskRow = async (matterId, title) => {
    await page.goto(`${BASE}/matters/${matterId}`, { waitUntil: "load" });
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
    const r = page.getByRole("tabpanel").getByRole("row").filter({ has: page.getByRole("button", { name: `Open task detail for ${title}`, exact: true }) }).first();
    await r.waitFor({ timeout: 15000 });
    return r;
  };

  // Setup through the UI: the grid lists assigned tasks only (timesheet/page.tsx), and Alice has
  // none on a fresh stack, so she claims one open unassigned task on its matter's Tasks tab.
  const before = await get(tok, "/api/my-work/tasks");
  out.assignedBefore = before.assigned.length;
  if (before.assigned.length === 0) {
    const pick = before.unassigned.find((t) => t.status === "OPEN");
    if (!pick) throw new Error("no OPEN unassigned task to claim");
    const r = await openTaskRow(pick.matterId, pick.title);
    await r.getByRole("button", { name: "Claim", exact: true }).click();
    await r.getByRole("button", { name: "Claim", exact: true }).waitFor({ state: "detached", timeout: 15000 });
    await page.screenshot({ path: path.join(OUT, "00-claimed-task.png") });
    out.steps.push({ step: `claimed "${pick.title}" on ${pick.matterName} via the Tasks tab Claim button` });
  }

  await open();

  // Pick the first task row whose Mon, Tue and Wed cells are empty.
  const rows = main.locator("tbody tr");
  const n = await rows.count();
  let rowIdx = -1;
  for (let i = 0; i < n - 1; i++) {
    const vals = await rows.nth(i).locator('input[aria-label^="Hours for day"]').evaluateAll((els) => els.map((e) => e.value));
    if (vals.length === 7 && !vals[0] && !vals[1] && !vals[2]) {
      rowIdx = i;
      break;
    }
  }
  if (rowIdx < 0) throw new Error("no task row with empty Mon/Tue/Wed");
  const title = (await rows.nth(rowIdx).locator("td").first().locator("p").first().innerText()).trim();
  const tasks = await get(tok, "/api/my-work/tasks");
  const task = [...tasks.assigned, ...tasks.unassigned].find((t) => t.title === title);
  if (!task) throw new Error(`task ${title} not in /api/my-work/tasks`);
  out.task = { id: task.id, title, matterId: task.matterId, matterName: task.matterName };

  const row = () => main.locator("tbody tr").filter({ hasText: title }).first();
  const cell = (d) => row().locator(`input[aria-label="Hours for day ${d}"]`);
  const values = () => row().locator('input[aria-label^="Hours for day"]').evaluateAll((els) => els.map((e) => e.value));
  const dailyTotal = async () => (await main.locator("tbody tr").last().innerText()).replace(/\s+/g, " ").trim();
  const setCell = async (d, v) => {
    await cell(d).click();
    await cell(d).press("ControlOrMeta+a");
    await page.keyboard.press("Backspace");
    if (v !== "") await page.keyboard.type(v);
    await page.keyboard.press("Tab"); // TimeCell commits on blur
  };
  const saveBtn = () => main.getByRole("button", { name: "Save", exact: true });
  const save = async (name) => {
    const enabled = !(await saveBtn().isDisabled());
    await saveBtn().click();
    await page.waitForLoadState("networkidle").catch(() => {});
    await page.waitForTimeout(2500);
    const toasts = (await page.locator("[data-sonner-toast]").allInnerTexts().catch(() => [])).map((t) => t.replace(/\s+/g, " ").trim());
    const alert = (await main.getByTestId("timesheet-save-problems").count()) ? (await main.getByTestId("timesheet-save-problems").innerText()).replace(/\s+/g, " ").trim() : null;
    await page.screenshot({ path: path.join(OUT, `${name}.png`) });
    const step = { step: `save ${name}`, saveEnabledBeforeClick: enabled, toasts, alert, valuesAfterSave: await values(), saveDisabledAfter: await saveBtn().isDisabled(), entries: await week(task.id) };
    out.steps.push(step);
    return step;
  };
  const reload = async (name) => {
    await open();
    const step = { step: `reload ${name}`, values: await values(), dailyTotal: await dailyTotal(), saveDisabled: await saveBtn().isDisabled() };
    out.steps.push(step);
    return step;
  };
  const on = (list, date) => list.filter((e) => e.date === date);

  // 1. Mon 1h
  await setCell(1, "1");
  const s1 = await save("01-mon-1h");
  check("1: one Mon entry of 60", on(s1.entries, MON).length === 1 && on(s1.entries, MON)[0].minutes === 60, on(s1.entries, MON));
  const monId = on(s1.entries, MON)[0]?.id;

  // 2. reload, Tue 0.5h (the original bug: this Save re-created Monday)
  await reload("after-01");
  await setCell(2, "0.5");
  const s2 = await save("02-tue-0.5h");
  check("2: still exactly one Mon entry of 60 (no duplicate)", on(s2.entries, MON).length === 1 && on(s2.entries, MON)[0].minutes === 60, on(s2.entries, MON));
  check("2: one Tue entry of 30", on(s2.entries, TUE).length === 1 && on(s2.entries, TUE)[0].minutes === 30, on(s2.entries, TUE));
  check("2: toast says 1 change", s2.toasts.some((t) => /Saved 1 change\b/.test(t)), s2.toasts);
  const r2 = await reload("after-02");
  check("2: reload shows Mon 1, Tue 0.5", r2.values[0] === "1" && r2.values[1] === "0.5", r2.values);

  // 3. Mon 1h → 0.5h (single entry → update)
  await setCell(1, "0.5");
  const s3 = await save("03-mon-to-0.5h");
  const mon3 = on(s3.entries, MON);
  check("3: Mon is the same entry id, now 30", mon3.length === 1 && mon3[0].id === monId && mon3[0].minutes === 30, mon3);

  // 4. clear Tue (single entry → delete)
  await reload("after-03");
  await setCell(2, "");
  const s4 = await save("04-clear-tue");
  check("4: Tue has no entries", on(s4.entries, TUE).length === 0, on(s4.entries, TUE));
  check("4: Mon unchanged (1 × 30)", on(s4.entries, MON).length === 1 && on(s4.entries, MON)[0].minutes === 30, on(s4.entries, MON));

  // 5. a second Mon entry through the task's Log Time dialog on the matter page
  const taskRow = await openTaskRow(task.matterId, title);
  await taskRow.getByRole("button", { name: "Log Time" }).click();
  const dialog = page.getByRole("dialog", { name: "Log Time" });
  await dialog.waitFor({ timeout: 10000 });
  await dialog.locator("#time-hours").fill("1");
  await dialog.locator("#time-minutes").fill("0");
  await dialog.locator("#time-date").fill(MON);
  await dialog.locator("#time-description").fill("OBS F-668A-R1 second Monday entry");
  await page.screenshot({ path: path.join(OUT, "05-log-time-dialog.png") });
  await dialog.getByRole("button", { name: "Log Time" }).click();
  await dialog.waitFor({ state: "hidden", timeout: 15000 });
  const e5 = await week(task.id);
  out.steps.push({ step: "log time dialog: second Mon entry 1h", entries: e5 });
  check("5: Mon now has 2 entries (30 + 60)", on(e5, MON).length === 2 && on(e5, MON).reduce((s, e) => s + e.minutes, 0) === 90, on(e5, MON));

  // 6. multi-entry reduce is refused; another cell still saves
  const r5 = await reload("after-05");
  check("6: timesheet shows Mon 1.5", r5.values[0] === "1.5", r5.values);
  await setCell(1, "1");
  await setCell(3, "0.25");
  const s6 = await save("06-multi-entry-refused");
  check("6: alert names the day and task and the Time tab", s6.alert && s6.alert.includes(`${MON_LABEL} · ${title}`) && s6.alert.includes("Time tab"), s6.alert);
  check("6: toast warns 1 saved, 1 not saved", s6.toasts.some((t) => /Saved 1 change, 1 not saved/.test(t)), s6.toasts);
  check("6: Mon entries unchanged (30 + 60)", on(s6.entries, MON).length === 2 && on(s6.entries, MON).reduce((s, e) => s + e.minutes, 0) === 90, on(s6.entries, MON));
  check("6: Wed saved (1 × 15)", on(s6.entries, WED).length === 1 && on(s6.entries, WED)[0].minutes === 15, on(s6.entries, WED));
  check("6: grid shows the server values (Mon 1.5, Wed 0.25)", s6.valuesAfterSave[0] === "1.5" && s6.valuesAfterSave[2] === "0.25", s6.valuesAfterSave);
  await main.getByTestId("timesheet-save-problems").screenshot({ path: path.join(OUT, "06-alert-crop.png") }).catch(() => {});

  // 7. reload: nothing to send
  const r7 = await reload("final");
  check("7: Save disabled after reload (no edits)", r7.saveDisabled, r7);
  await page.screenshot({ path: path.join(OUT, "07-final.png") });

  out.entriesFinal = await week(task.id);
  out.totals = {
    MON: on(out.entriesFinal, MON).reduce((s, e) => s + e.minutes, 0),
    TUE: on(out.entriesFinal, TUE).reduce((s, e) => s + e.minutes, 0),
    WED: on(out.entriesFinal, WED).reduce((s, e) => s + e.minutes, 0),
    entryCount: out.entriesFinal.length,
  };
  check("console: no errors", out.console.length === 0, out.console);
  out.pass = out.checks.every((c) => c.ok);
  await ctx.close();
  await browser.close();
  fs.writeFileSync(FILE, JSON.stringify(out, null, 2));
  console.log(out.pass ? "OBS PASS" : "OBS FAIL", JSON.stringify(out.checks.map((c) => `${c.ok ? "ok" : "FAIL"} ${c.name}`), null, 1), JSON.stringify(out.totals));
})().catch((e) => {
  console.error("OBS ERROR", e);
  fs.writeFileSync(FILE + ".failed", JSON.stringify({ ...out, error: String(e) }, null, 2));
  process.exit(1);
});
