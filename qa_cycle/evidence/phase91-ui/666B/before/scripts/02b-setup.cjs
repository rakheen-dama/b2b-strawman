// 666B OBS step 2b = 02-setup.cjs (not edited) with one fix: 02 typed SETUP.notes minutes straight
// into #time-minutes, and "60" (the "partial" entry) fails the field's max=59 constraint, so the Log
// Time dialog stayed open (02 stopped after 2 drafts). 02b splits minutes into hours + minutes.
// Everything else is idempotent, so 02b resumes where 02 stopped.
// Original header of 02: create the fee notes the detail-page OBS needs, through the UI only (Alice,
// 1440×900 light). Why: the stack (fresh seed) has 1 DRAFT + 7 SENT fee notes (6 of them take-on),
// no APPROVED / PARTIALLY_PAID / PAID / VOID, no billing rates, no tasks and no unbilled time, and
// the only UI path to a fee note is the client's "New fee note" generation dialog, which needs
// unbilled items with a rate. So:
//   1. Settings › Fees › Tariffs › member rates row "billing-rate-owner" › "Add rate for Alice Owner"
//      → Billing Rate R 2 500/h ZAR, effective today → "Create Rate" (skipped if Alice has a rate).
//   2. Matter "Website Redesign" (Acme Corp) › Work › Tasks › "New Task" → "666B OBS fee-note
//      detail" → "Create Task" (skipped if it exists).
//   3. Per SETUP.notes entry: task row "Log Time" (billable, today) → client Acme Corp ?tab=invoices
//      → "New fee note" → "Fetch Unbilled Time" (only this entry is unbilled) → "Validate & Create
//      Draft" → "Create Draft" → the new DRAFT is found by the entry's invoiceId (GET).
// Idempotent per step (GET checks). Writes $RAW/setup.json (key → fee-note id) + tiles.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const out = { at: new Date().toISOString(), steps: [], notes: {} };
const tiles = [];
const consoleSink = [];
const lab = { label: "" };
const settle = async (p) => { await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(600); };
const shot = async (p, key, caption, opt = {}) => { const f = path.join(L.RAW, `${key}.png`); await p.screenshot({ path: f, ...opt }); tiles.push([key, f, caption]); };

async function openTasks(p, matterId) {
  await p.goto(`${L.BASE}/matters/${matterId}`, { waitUntil: "load" });
  await settle(p);
  const group = p.getByTestId("tab-group-work").filter({ visible: true });
  const item = p.getByTestId("tab-item-tasks").filter({ visible: true });
  await group.waitFor({ timeout: 15000 });
  for (let i = 0; i < 3; i++) { await group.click(); if (await item.waitFor({ state: "visible", timeout: 2000 }).then(() => true, () => false)) { await item.click(); break; } }
  await settle(p);
}

(async () => {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const t = await L.idpToken("alice");
  const me = (await L.apiGet(t, "/api/me/capabilities")).json;
  const members = L.list((await L.apiGet(t, "/api/members")).json);
  const alice = members.find((m) => m.name === "Alice Owner");
  const matter = L.list((await L.apiGet(t, "/api/matters?size=100")).json).find((x) => x.name === L.SETUP.matter);
  const clientId = matter.clientId;
  out.context = { role: me.role, aliceMemberId: alice.id, matterId: matter.id, clientId, clientName: matter.clientName };
  const ctx = await L.newCtx(browser, S, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
  const p = await ctx.newPage();
  L.watch(p, consoleSink, lab);
  p.on("dialog", (d) => { out.steps.push({ unexpectedNativeDialog: d.message() }); d.dismiss(); });

  // 1. Billing rate for Alice.
  const rates = L.list((await L.apiGet(t, "/api/billing-rates")).json).filter((r) => r.memberId === alice.id);
  if (rates.length) out.steps.push({ step: "rate", result: "exists — skipped", rateId: rates[0].id });
  else {
    lab.label = "setup|rate";
    await p.goto(`${L.BASE}/settings/fees?tab=tariffs`, { waitUntil: "load" });
    await settle(p);
    await p.getByTestId("billing-rate-owner").getByRole("button", { name: "Add rate for Alice Owner" }).click();
    const dlg = p.getByRole("dialog", { name: "Add Rate" });
    await dlg.waitFor();
    await dlg.locator("#add-hourly-rate").fill(L.SETUP.rate.hourly);
    const from = await dlg.locator("#add-effective-from").inputValue();
    const cur = (await dlg.getByRole("combobox").first().innerText().catch(() => "")).trim();
    await shot(p, "setup__rate-dialog", `Settings › Fees › Tariffs › "Add rate for Alice Owner": Billing Rate ${L.SETUP.rate.hourly}, currency "${cur}", effective from ${from} (before Create Rate)`);
    await dlg.getByRole("button", { name: "Create Rate" }).click();
    await dlg.waitFor({ state: "hidden", timeout: 15000 });
    const r = L.list((await L.apiGet(t, "/api/billing-rates")).json).filter((x) => x.memberId === alice.id);
    out.steps.push({ step: "rate", result: "created", effectiveFrom: from, currencyShown: cur, api: r.map((x) => ({ id: x.id, hourlyRate: x.hourlyRate, currency: x.currency, effectiveFrom: x.effectiveFrom })) });
  }

  // 2. Task on the matter.
  let task = L.list((await L.apiGet(t, `/api/matters/${matter.id}/tasks`)).json).find((x) => x.title === L.SETUP.task);
  if (task) out.steps.push({ step: "task", result: "exists — skipped", taskId: task.id });
  else {
    lab.label = "setup|task";
    await openTasks(p, matter.id);
    await p.getByRole("tabpanel").getByRole("button", { name: "New Task" }).first().click();
    const dlg = p.getByRole("dialog", { name: "Create Task" });
    await dlg.waitFor();
    await dlg.locator("#task-title").fill(L.SETUP.task);
    await shot(p, "setup__task-dialog", `Matter "${matter.name}" › Work › Tasks › New Task: "${L.SETUP.task}" (before Create Task)`);
    await dlg.getByRole("button", { name: "Create Task" }).click();
    await dlg.waitFor({ state: "hidden", timeout: 15000 });
    task = L.list((await L.apiGet(t, `/api/matters/${matter.id}/tasks`)).json).find((x) => x.title === L.SETUP.task);
    out.steps.push({ step: "task", result: "created", taskId: task && task.id });
  }

  // 3. One time entry + one generated draft per note.
  for (const [i, n] of L.SETUP.notes.entries()) {
    let entry = L.list((await L.apiGet(t, `/api/tasks/${task.id}/time-entries`)).json).find((x) => x.description === n.description);
    if (!entry) {
      lab.label = `setup|time ${n.key}`;
      await openTasks(p, matter.id);
      const row = p.getByRole("tabpanel").getByRole("row").filter({ has: p.getByRole("button", { name: `Open task detail for ${L.SETUP.task}`, exact: true }) });
      await row.waitFor({ timeout: 15000 });
      await row.getByRole("button", { name: "Log Time" }).click();
      const dlg = p.getByRole("dialog", { name: "Log Time" });
      await dlg.waitFor();
      await dlg.locator("#time-hours").fill(String(Math.floor(+n.minutes / 60)));
      await dlg.locator("#time-minutes").fill(String(+n.minutes % 60));
      await dlg.getByRole("textbox", { name: /^Description/ }).fill(n.description);
      await p.waitForTimeout(1000);
      const rate = (await dlg.getByTestId("rate-preview").innerText().catch(() => "")).replace(/\s+/g, " ").trim();
      if (i === 0) await shot(p, "setup__log-time", `Task "${L.SETUP.task}" › Log Time: ${n.minutes}m "${n.description}"; ${rate} (before Log Time)`);
      await dlg.getByRole("button", { name: "Log Time" }).click();
      await dlg.waitFor({ state: "hidden", timeout: 15000 });
      entry = L.list((await L.apiGet(t, `/api/tasks/${task.id}/time-entries`)).json).find((x) => x.description === n.description);
      out.steps.push({ step: `time ${n.key}`, result: "logged", entryId: entry && entry.id, ratePreview: rate, billingRateSnapshot: entry && entry.billingRateSnapshot });
    } else out.steps.push({ step: `time ${n.key}`, result: "exists — skipped", entryId: entry.id, invoiceId: entry.invoiceId });
    if (entry.invoiceId) { out.notes[n.key] = entry.invoiceId; continue; }

    lab.label = `setup|generate ${n.key}`;
    await p.goto(`${L.BASE}/clients/${clientId}?tab=invoices`, { waitUntil: "load" });
    await settle(p);
    await p.locator("#main-content").getByRole("button", { name: "New fee note", exact: true }).click();
    const dlg = p.getByRole("dialog");
    await dlg.waitFor({ timeout: 15000 });
    const title = (await dlg.getByRole("heading").first().innerText()).trim();
    if (title !== "Generate fee note") { await shot(p, `setup__gen-blocked-${n.key}`, `New fee note opened "${title}" instead of the generation dialog`); throw new Error(`generation dialog title "${title}"`); }
    const currency = await dlg.locator("#invoice-currency").inputValue();
    await dlg.getByRole("button", { name: "Fetch Unbilled Time" }).click();
    await dlg.getByRole("heading", { name: "Select Unbilled Items" }).waitFor({ timeout: 15000 });
    await p.waitForTimeout(500);
    const step2 = (await dlg.innerText()).replace(/\s+/g, " ").slice(0, 600);
    if (i === 0) await shot(p, "setup__gen-step2", `Acme Corp › Fee notes › New fee note › Select Unbilled Items (currency ${currency}; only "${n.description}" unbilled)`);
    await dlg.getByRole("button", { name: "Validate & Create Draft" }).click();
    const create = dlg.getByRole("button", { name: /^Create Draft/ });
    await create.waitFor({ timeout: 15000 });
    const createLabel = (await create.innerText()).trim();
    const checks = (await dlg.innerText()).replace(/\s+/g, " ").slice(0, 900);
    if (i === 0) await shot(p, "setup__gen-validated", `after "Validate & Create Draft": button "${createLabel}"`);
    await create.click();
    await dlg.waitFor({ state: "hidden", timeout: 20000 });
    await settle(p);
    const banner = (await p.locator("#main-content").getByRole("alert").allInnerTexts().catch(() => [])).map((x) => x.replace(/\s+/g, " ").slice(0, 200));
    entry = L.list((await L.apiGet(t, `/api/tasks/${task.id}/time-entries`)).json).find((x) => x.description === n.description);
    out.notes[n.key] = entry.invoiceId;
    out.steps.push({ step: `generate ${n.key}`, currency, step2, createLabel, checks, postCreateAlerts: banner, invoiceId: entry.invoiceId });
  }
  await ctx.close();
  await browser.close();
  out.console = consoleSink;
  out.tiles = tiles;
  fs.writeFileSync(path.join(L.RAW, "setup-02b.json"), JSON.stringify(out));
  console.log(JSON.stringify(out.steps, null, 0).slice(0, 4000));
  console.log("notes", JSON.stringify(out.notes), "console", consoleSink.length, JSON.stringify(consoleSink.slice(0, 5)));
})();
