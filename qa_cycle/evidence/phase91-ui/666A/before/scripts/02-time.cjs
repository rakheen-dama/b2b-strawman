// 666A OBS step 2a: create unbilled work through the UI (as Alice, 1440×900 light) so that the
// Unbilled work page and the fee-run wizard have data. Why: on this stack GET
// /api/invoices/unbilled-summary is [] for every period — the only unbilled time belongs to 3
// ONBOARDING clients, and the summary / billing-run preview count lifecycle-ACTIVE clients only
// (backend UnbilledTimeService.getUnbilledSummary `WHERE c.lifecycle_status = 'ACTIVE'`). So one
// billable entry is logged on a task of a matter of each of two lifecycle-ACTIVE clients:
//   Matter → Work › Tasks → task row "Log Time" → dialog Log Time (hours, minutes, description; date
//   = today, Billable checked by default) → "Log Time".
// Idempotent: skips an entry whose description already exists on the task (GET).
// Writes $RAW/time.json + tiles; 05-assemble merges them.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const ENTRIES = [
  { matterPrefix: "Khumalo Property Holdings — Erf 1234", task: "Receive instruction & conflict check", hours: "1", minutes: "30", description: "666A OBS-before unbilled entry (Khumalo)" },
  { matterPrefix: "Ndlovu Property Dispute", task: "Initial consultation & case assessment", hours: "0", minutes: "45", description: "666A OBS-before unbilled entry (Ndlovu)" },
];
const out = { at: new Date().toISOString(), steps: [] };
const tiles = [];
const consoleSink = [];
const lab = { label: "" };

(async () => {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const t = await L.idpToken("alice");
  const matters = L.list((await L.apiGet(t, "/api/matters")).json);
  const ctx = await L.newCtx(browser, S, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
  const p = await ctx.newPage();
  L.watch(p, consoleSink, lab);
  for (const e of ENTRIES) {
    const m = matters.find((x) => x.name.startsWith(e.matterPrefix));
    const task = L.list((await L.apiGet(t, `/api/matters/${m.id}/tasks`)).json).find((x) => x.title === e.task);
    const existing = L.list((await L.apiGet(t, `/api/tasks/${task.id}/time-entries`)).json).find((x) => x.description === e.description);
    if (existing) { out.steps.push({ matter: m.name, task: e.task, result: "exists — skipped", entryId: existing.id }); continue; }
    lab.label = `time|${m.name}`;
    await p.goto(`${L.BASE}/matters/${m.id}`, { waitUntil: "load" });
    await p.waitForLoadState("networkidle").catch(() => {});
    const group = p.getByTestId("tab-group-work").filter({ visible: true });
    const item = p.getByTestId("tab-item-tasks").filter({ visible: true });
    await group.waitFor({ timeout: 15000 });
    for (let i = 0; i < 3; i++) { await group.click(); if (await item.waitFor({ state: "visible", timeout: 2000 }).then(() => true, () => false)) { await item.click(); break; } }
    const row = p.getByRole("tabpanel").getByRole("row").filter({ has: p.getByRole("button", { name: `Open task detail for ${e.task}`, exact: true }) });
    await row.waitFor({ timeout: 15000 });
    await row.getByRole("button", { name: "Log Time" }).click();
    const dlg = p.getByRole("dialog", { name: "Log Time" });
    await dlg.waitFor();
    await dlg.locator("#time-hours").fill(e.hours);
    await dlg.locator("#time-minutes").fill(e.minutes);
    await dlg.getByRole("textbox", { name: /^Description/ }).fill(e.description);
    await p.waitForTimeout(800);
    const billable = await dlg.getByRole("checkbox", { name: "Billable" }).isChecked();
    const rate = (await dlg.getByTestId("rate-preview").innerText().catch(() => "")).replace(/\s+/g, " ").trim();
    const date = await dlg.locator("#time-date").inputValue();
    const f = path.join(L.RAW, `time__${e.matterPrefix.replace(/\W+/g, "_")}.png`);
    await p.screenshot({ path: f });
    tiles.push([`time__${e.matterPrefix.replace(/\W+/g, "_")}`, f, `Alice 1440 light — ${m.name} › Work › Tasks › "${e.task}" › Log Time: ${e.hours}h ${e.minutes}m, ${date}, billable ${billable}; ${rate} (before Log Time)`]);
    await dlg.getByRole("button", { name: "Log Time" }).click();
    await dlg.waitFor({ state: "hidden", timeout: 15000 });
    const created = L.list((await L.apiGet(t, `/api/tasks/${task.id}/time-entries`)).json).find((x) => x.description === e.description);
    out.steps.push({ matter: m.name, matterId: m.id, task: e.task, taskId: task.id, date, billable, ratePreview: rate, entryId: created && created.id, api: created && { durationMinutes: created.durationMinutes, billable: created.billable, billingRateSnapshot: created.billingRateSnapshot, billingRateCurrency: created.billingRateCurrency, invoiceId: created.invoiceId } });
  }
  await ctx.close();
  await browser.close();
  out.console = consoleSink;
  out.tiles = tiles;
  fs.writeFileSync(path.join(L.RAW, "time.json"), JSON.stringify(out));
  console.log(JSON.stringify(out.steps), "console", consoleSink.length);
})();
