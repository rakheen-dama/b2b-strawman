// 669A step 0: seed — RUN ONCE (PHASE=before). Idempotent: the data request is found again by its
// description, so a re-run creates nothing and only appends an all-"already" entry to runs[].
// Always writes before/seed.json, whatever PHASE is. No SQL.
// (1) Read-only inventory (as Alice, the endpoints the pages call): data requests, conflict checks,
//     adverse parties, disbursements (+ each matter's status, K-66), tariff schedules, information
//     requests (+ reminderIntervalDays, K-67), clients (+ checklist counts), matters (+ statement
//     counts); Carol's capabilities and what her calls return.
// (2) Before the write: the naturally EMPTY /compliance/requests (0 data requests on this stack) is
//     probed and shot (Alice 1440 light + 1024 dark) — it cannot be observed after the seed.
// (3) The ONE write: a data request through the real "New Request" dialog on /compliance/requests
//     (CreateDataRequestDialog → createDataRequest): client Acme Corp, Access Request, description
//     "669A OBS seed — …". Without it the request table, the status-pill filter rows and the
//     /compliance/requests/[id] detail are unobservable. Its status stays RECEIVED (nothing advanced).
// Fixed ids for 01-capture (existing rows, chosen here) are written to seed.json › ids.
// Usage: node 00-seed.cjs
const fs = require("fs");
const path = require("path");
const L = require("./lib.cjs");
const { probe } = require("./probe.cjs");

const DESC = "669A OBS seed — the client asked for a copy of the personal information the firm holds (POPIA s23)";
const CLIENT = "Acme Corp";

async function inventory() {
  const ta = await L.idpToken("alice");
  const tc = await L.idpToken("carol");
  const g = async (p, t = ta) => (await L.apiGet(t, p)).json;
  const s = {};
  const matters = L.list(await g("/api/matters?status=ALL&size=200"));
  const active = L.list(await g("/api/matters?size=200"));
  s.matters = matters.map((m) => ({ id: m.id, name: m.name, status: m.status, clientId: m.customerId || m.clientId || null, inActiveList: active.some((a) => a.id === m.id) }));
  for (const m of s.matters) m.statements = L.list(await g(`/api/matters/${m.id}/statements?page=0&size=20`)).length;
  const clients = L.list(await g("/api/clients?size=200"));
  s.clients = [];
  for (const c of clients) { const ch = await g(`/api/clients/${c.id}/checklists`); s.clients.push({ id: c.id, name: c.name, lifecycle: c.lifecycleStatus, status: c.status, checklists: Array.isArray(ch) ? ch.length : null }); }
  s.dataRequests = L.list(await g("/api/data-requests")).map((r) => ({ id: r.id, client: r.customerName || r.clientName, type: r.requestType, status: r.status, requestedAt: r.requestedAt, deadline: r.deadline, description: r.description }));
  s.conflictChecks = L.list(await g("/api/conflict-checks?size=100")).map((c) => ({ id: c.id, name: c.checkedName, type: c.checkType, result: c.result, at: c.checkedAt || c.createdAt, resolution: c.resolution }));
  s.adverseParties = L.list(await g("/api/adverse-parties?size=100")).map((a) => ({ id: a.id, name: a.name, type: a.partyType, linkedMatters: a.linkedMatterCount }));
  s.disbursements = L.list(await g("/api/legal/disbursements?size=100")).map((d) => { const m = s.matters.find((x) => x.id === d.matterId); return { id: d.id, description: d.description, category: d.category, incurredDate: d.incurredDate, approval: d.approvalStatus, billing: d.billingStatus, source: d.paymentSource, trustTransactionId: d.trustTransactionId, matterId: d.matterId, matterStatus: m && m.status, matterInActiveList: m && m.inActiveList }; });
  s.tariffSchedules = L.list(await g("/api/tariff-schedules")).map((t) => ({ id: t.id, name: t.name, code: t.code === undefined ? "(absent)" : t.code, category: t.category, effectiveFrom: t.effectiveFrom, items: t.itemCount, active: t.isActive, system: t.isSystem }));
  s.informationRequests = [];
  for (const r of L.list(await g("/api/information-requests?size=100"))) { const d = await g(`/api/information-requests/${r.id}`); s.informationRequests.push({ id: r.id, number: r.requestNumber, status: r.status, client: r.clientName, matter: r.matterName, reminderIntervalDays: d.reminderIntervalDays, sentAt: d.sentAt, dueDate: d.dueDate, items: (d.items || []).length }); }
  const settings = await g("/api/settings");
  s.enabledModules = settings && settings.enabledModules;
  s.carol = { capabilities: await g("/api/me/capabilities", tc) };
  for (const p of ["/api/data-requests", "/api/information-requests?size=5", "/api/legal/disbursements?size=5", "/api/conflict-checks?size=5", "/api/adverse-parties?size=5", "/api/tariff-schedules", "/api/matters?size=50"]) {
    const r = await L.apiGet(tc, p);
    s.carol[p] = { status: r.status, count: r.json ? L.list(r.json).length : null };
  }
  return s;
}

async function main() {
  const prev = fs.existsSync(L.SEED_FILE) ? JSON.parse(fs.readFileSync(L.SEED_FILE, "utf8")) : null;
  const run = { at: new Date().toISOString(), phase: L.PHASE, steps: [] };
  const before = await inventory();
  console.log("inventory", JSON.stringify(before).slice(0, 1500));
  const existing = before.dataRequests.find((r) => r.description === DESC);

  const browser = await L.chromium.launch();
  const st = await L.loginState(browser, "alice");
  const preSeed = prev && prev.preSeedEmpty ? prev.preSeedEmpty : {};
  if (!existing && before.dataRequests.length === 0) {
    for (const v of ["1440-light", "1024-dark"]) {
      const [w, theme] = v.split("-");
      const ctx = await L.newCtx(browser, st, { width: +w, height: w === "1024" ? 768 : 900, theme, extra: L.EXTRA });
      const page = await ctx.newPage();
      await page.goto(`${L.BASE}/compliance/requests`, { waitUntil: "load" });
      await page.waitForLoadState("networkidle").catch(() => {});
      await page.waitForTimeout(800);
      const m = await page.evaluate(probe);
      const file = path.join(L.RAW, `requests-preseed-empty--alice--${v}.png`);
      await page.screenshot({ path: file, fullPage: true });
      preSeed[`alice/${v}`] = { file, url: m.url, h1Main: m.h1Main, empties: m.empties, header: m.header, docHOverflowPx: m.docHOverflowPx, buttons: m.buttons };
      await ctx.close();
    }
    run.steps.push("probed + shot the empty /compliance/requests (0 data requests) at alice 1440-light and 1024-dark");
  }

  let dsarId = existing && existing.id;
  if (!existing) {
    const ctx = await L.newCtx(browser, st, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
    const page = await ctx.newPage();
    await page.goto(`${L.BASE}/compliance/requests`, { waitUntil: "load" });
    await page.waitForLoadState("networkidle").catch(() => {});
    await page.getByRole("button", { name: "New Request" }).click();
    const dlg = page.getByRole("dialog", { name: "New Data Request" });
    await dlg.waitFor();
    const pick = dlg.getByRole("button", { name: "Select a client..." });
    await pick.waitFor({ timeout: 15000 });
    await pick.click();
    await dlg.getByPlaceholder("Search clients...").fill(CLIENT);
    await dlg.getByRole("option", { name: new RegExp(`^${CLIENT}`) }).first().click();
    await dlg.locator("#request-type").selectOption("ACCESS");
    await dlg.locator("#description").fill(DESC);
    await dlg.getByRole("button", { name: "Create Request" }).click();
    await dlg.waitFor({ state: "hidden", timeout: 20000 });
    await page.waitForTimeout(1000);
    await ctx.close();
    const t = await L.idpToken("alice");
    const r = L.list((await L.apiGet(t, "/api/data-requests")).json).find((x) => x.description === DESC);
    if (!r) throw new Error("data request not found after the dialog closed");
    dsarId = r.id;
    run.steps.push(`UI: /compliance/requests › New Request › client "${CLIENT}", Access Request, description "${DESC}" → ${r.id} (${r.status}, requestedAt ${r.requestedAt}, deadline ${r.deadline})`);
  } else run.steps.push(`data request already ${existing.id}`);
  await browser.close();

  const after = await inventory();
  const D = (pred) => (after.disbursements.find(pred) || {}).id;
  const M = (pred) => (after.matters.find(pred) || {}).id;
  const C = (pred) => (after.clients.find(pred) || {}).id;
  const ids = prev && prev.ids ? prev.ids : {
    dsarId,
    disbursementTrust: D((d) => d.trustTransactionId),
    disbursementActiveMatter: D((d) => d.matterInActiveList),
    disbursementActiveMatterMatterId: (after.disbursements.find((d) => d.matterInActiveList) || {}).matterId,
    informationRequestNullReminder: (after.informationRequests.find((r) => r.reminderIntervalDays == null) || {}).id,
    informationRequestWithReminder: (after.informationRequests.find((r) => r.reminderIntervalDays != null) || {}).id,
    tariffScheduleId: (after.tariffSchedules[0] || {}).id,
    statementsEmptyMatterId: M((m) => m.status === "ACTIVE" && m.statements === 0 && m.clientId && /Khumalo/.test(m.name)) || M((m) => m.status === "ACTIVE" && m.statements === 0 && m.clientId),
    checklistEmptyClientId: C((c) => c.lifecycle === "ONBOARDING" && c.checklists === 0),
    conflictPrefillClientId: C((c) => /^Khumalo/.test(c.name)),
  };
  ids.dsarId = ids.dsarId || dsarId;
  const out = {
    note: "669A OBS seed record. dataBefore = the stack before any write; dataAfter = after the seed. ids = the rows 01-capture.cjs loads (existing rows except dsarId). preSeedEmpty = the probe of the empty /compliance/requests taken before the write.",
    dataBefore: prev ? prev.dataBefore : before,
    dataAfter: after,
    ids,
    preSeedEmpty: preSeed,
    writes: prev ? prev.writes : run.steps.filter((x) => x.startsWith("UI:")),
    runs: [...(prev ? prev.runs : []), run],
  };
  fs.writeFileSync(L.SEED_FILE, JSON.stringify(out));
  console.log("seed", JSON.stringify({ ids, steps: run.steps }));
}
main().catch((e) => { console.error("SEED FAIL", e); process.exit(1); });
