// 668A OBS-after — F-668A-R1 reproduction (HIGH, pre-existing): timesheet Save re-creates the
// entries it pre-filled. DELIBERATE WRITE (authorised by the orchestrator brief): as Alice, through
// the UI only, on the current week's /my-work/timesheet: Monday 1h on the assigned task → Save →
// reload → Tuesday 0.5h on the same row → Save. Then reads the week's entries through the API the
// page uses (GET /api/my-work/time-entries?from&to, timesheet/page.tsx) and counts Monday entries
// for the task (2 = reproduced). Run ONCE. Writes ../seed-r1.json. Usage: PHASE=after node 11-r1-timesheet-duplicate.cjs
const path = require("path");
const fs = require("fs");
const L = require("../../before/scripts/lib.cjs");

const S = L.readSeed();
const TASK = S.ids.assignedTaskId; // 4743c754-… "Khumalo — prepare the heads of argument …"
const OUT = path.resolve(__dirname, "..");
const FILE = path.join(OUT, "seed-r1.json");
if (fs.existsSync(FILE)) throw new Error("seed-r1.json exists — R1 reproduction already ran; do not re-run");
const MON = "2026-11-23", TUE = "2026-11-24", SUN = "2026-11-29";
const out = { at: new Date().toISOString(), what: "F-668A-R1 reproduction (deliberate write, UI only)", taskId: TASK, steps: [], console: [] };

async function entries(token) {
  const r = await L.apiGet(token, `/api/my-work/time-entries?from=${MON}&to=${SUN}`);
  return (r.json || []).map((e) => ({ id: e.id, taskId: e.taskId, date: e.date, minutes: e.durationMinutes, billable: e.billable, description: e.description ?? null }));
}

(async () => {
  const token = await L.idpToken("alice");
  out.entriesBefore = await entries(token);
  const browser = await L.chromium.launch();
  const st = await L.loginState(browser, "alice");
  const ctx = await L.newCtx(browser, st, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
  const page = await ctx.newPage();
  const lbl = { label: "r1" };
  L.watch(page, out.console, lbl);
  const main = page.locator("#main-content");
  const open = async () => {
    await page.goto(`${L.BASE}/my-work/timesheet`, { waitUntil: "load" });
    await page.waitForLoadState("networkidle").catch(() => {});
    await main.locator('input[aria-label^="Hours for day"]').first().waitFor({ timeout: 20000 });
    await page.waitForTimeout(600);
  };
  const row = () => main.locator("tbody tr").filter({ hasText: "Khumalo — prepare the heads of argument" }).first();
  const cell = (d) => row().locator(`input[aria-label="Hours for day ${d}"]`);
  const save = async (name) => {
    const btn = main.getByRole("button", { name: "Save", exact: true });
    const enabled = !(await btn.isDisabled());
    await btn.click();
    await page.waitForLoadState("networkidle").catch(() => {});
    await page.waitForTimeout(2000);
    const toasts = (await page.locator("[data-sonner-toast]").allInnerTexts().catch(() => [])).map((t) => t.replace(/\s+/g, " ").trim());
    const f = path.join(L.RAW, `r1-${name}.png`);
    await page.screenshot({ path: f });
    out.steps.push({ step: `save ${name}`, saveEnabledBeforeClick: enabled, toasts, values: await row().locator('input[aria-label^="Hours for day"]').evaluateAll((els) => els.map((e) => e.value)) });
  };

  await open();
  out.steps.push({ step: "open", url: page.url().replace(L.BASE, ""), weekLabel: (await main.innerText()).match(/\d{1,2} \w{3}[^\n]*\d{4}/)?.[0] || null, rows: await main.locator("tbody tr").count(), values: await row().locator('input[aria-label^="Hours for day"]').evaluateAll((els) => els.map((e) => e.value)) });
  await cell(1).click();
  await page.keyboard.type("1"); await page.keyboard.press("Tab"); // keystrokes + Tab: TimeCell commits on blur (fill() alone left Save disabled)
  await save("1-monday-1h");
  out.entriesAfterSave1 = await entries(token);

  await open(); // reload
  out.steps.push({ step: "reload", values: await row().locator('input[aria-label^="Hours for day"]').evaluateAll((els) => els.map((e) => e.value)) });
  await cell(2).click();
  await page.keyboard.type("0.5"); await page.keyboard.press("Tab");
  await save("2-tuesday-0.5h");
  out.entriesAfterSave2 = await entries(token);

  await open(); // what the user now sees
  out.steps.push({ step: "reload after save 2", values: await row().locator('input[aria-label^="Hours for day"]').evaluateAll((els) => els.map((e) => e.value)), dailyTotal: (await main.locator("tbody tr").last().innerText()).replace(/\s+/g, " ").trim() });
  await page.screenshot({ path: path.join(OUT, "crop-r1-timesheet-after-second-save.png"), clip: { x: 260, y: 60, width: 1180, height: 420 } });
  await ctx.close();
  await browser.close();

  const mine = (list) => list.filter((e) => e.taskId === TASK);
  const created = mine(out.entriesAfterSave2).filter((e) => !out.entriesBefore.some((b) => b.id === e.id));
  out.result = {
    mondayEntriesForTask: mine(out.entriesAfterSave2).filter((e) => e.date === MON).length,
    tuesdayEntriesForTask: mine(out.entriesAfterSave2).filter((e) => e.date === TUE).length,
    createdEntryIds: created.map((e) => `${e.id} ${e.date} ${e.minutes}m billable=${e.billable}`),
    reproduced: mine(out.entriesAfterSave2).filter((e) => e.date === MON).length >= 2,
  };
  fs.writeFileSync(FILE, JSON.stringify(out));
  console.log(JSON.stringify(out.result), "console", out.console.length);
})().catch((e) => { console.error("R1 FAIL", e); fs.writeFileSync(FILE + ".failed", JSON.stringify({ ...out, error: String(e) })); process.exit(1); });
