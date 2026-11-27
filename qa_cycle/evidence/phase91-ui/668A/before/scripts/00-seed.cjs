// 668A step 0: seed — RUN ONCE (PHASE=before). Idempotent: court dates are found again by their
// court reference ("668A-OBS-n"), tasks by title on their matter, so a re-run changes nothing and only
// appends an all-"already" entry to runs[]. Always writes before/seed.json, whatever PHASE is.
// No SQL. Writes, as Alice (owner):
//  (a) TASKS — the REST call the matter's "New task" dialog makes (createTask → createMatterTask:
//      POST /api/matters/{id}/tasks). ONE task assigned to Alice ("Khumalo — prepare the heads of
//      argument…", HIGH, due Mon 30 Nov): the only way to give the timesheet grid a row to type into
//      (rows = assigned tasks only, timesheet/page.tsx:51-70) and the My Tasks (urgency) list a row to
//      measure. Plus 24 UNASSIGNED tasks (12 per legal matter, no due date): with the 1 seeded task
//      already on the stack that is 25 "Available Tasks" — what K-97 (every row unpaged) needs to be
//      observable at all. Alice's existing 240-min entry (Fri 27 Nov) stays on an unassigned task, so
//      K-88 (entries outside the grid's rows) is untouched.
//  (b) COURT DATES — real UI clicks on the matter's Diary tab (tab-group-schedule → "Court Dates",
//      project-court-dates-tab.tsx): four "Schedule Court Date" dialogs on Khumalo — lease dispute
//      (24 Nov Hearing, 25 Nov Motion, 26 Nov Trial, 27 Nov 10:00 Pre-Trial), then from each row's
//      Actions menu: 24 Nov → Cancel ("Cancel Court Date"), 25 Nov → Record Outcome (HEARD), 26 Nov →
//      Postpone to 30 Nov (the backend keeps the 26 Nov row as POSTPONED and creates a new SCHEDULED
//      30 Nov row, CourtCalendarService.postponeCourtDate). 27 Nov stays SCHEDULED.
// Usage: node 00-seed.cjs
const fs = require("fs");
const path = require("path");
const L = require("./lib.cjs");

const COURT = "Gauteng Division, Johannesburg";
const CDS = [
  { ref: "668A-OBS-1", type: "HEARING", date: "2026-11-24", time: "", then: "cancel", reason: "Matter settled — hearing withdrawn by agreement" },
  { ref: "668A-OBS-2", type: "MOTION", date: "2026-11-25", time: "", then: "outcome", reason: "Heard — order granted as prayed, costs reserved" },
  { ref: "668A-OBS-3", type: "TRIAL", date: "2026-11-26", time: "09:30", then: "postpone", newDate: "2026-11-30", reason: "Court roll full — postponed by the registrar" },
  { ref: "668A-OBS-4", type: "PRE_TRIAL", date: "2026-11-27", time: "10:00", then: null },
];
const ASSIGNED = { matter: "Khumalo — lease dispute", title: "Khumalo — prepare the heads of argument for the lease dispute hearing", priority: "HIGH", dueDate: "2026-11-30" };
const UNASSIGNED_TITLES = [
  "Draft the letter of demand", "Review the lease agreement and annexures", "Prepare the founding affidavit",
  "Collate the payment history from the client", "Brief counsel on the opposed motion", "Draft the notice of motion",
  "Diarise the dies for the answering affidavit", "Prepare the bundle index", "Obtain the sheriff's return of service",
  "Draft the replying affidavit", "Prepare the practice note", "Update the client on the hearing outcome",
];
const MATTERS_FOR_UNASSIGNED = ["Khumalo — lease dispute", "Ndlovu — estate transfer"];

async function snapshot(tokens, matters) {
  const s = {};
  const t = tokens.alice;
  s.courtDates = L.list((await L.apiGet(t, "/api/court-dates?size=100")).json).map((c) => ({ id: c.id, date: c.scheduledDate, time: c.scheduledTime, status: c.status, type: c.dateType, ref: c.courtReference, matter: c.matterName }));
  s.diaryNov = {};
  s.myWorkTasks = {};
  s.weekEntries = {};
  s.notifications = {};
  for (const who of Object.keys(tokens)) {
    const d = await L.apiGet(tokens[who], "/api/diary?from=2026-11-01&to=2026-11-30");
    s.diaryNov[who] = { types: d.headers["x-diary-types"], events: (d.json || []).map((e) => `${e.kind} ${e.date} ${e.status} ${e.title}`) };
    const mw = (await L.apiGet(tokens[who], "/api/my-work/tasks")).json || {};
    s.myWorkTasks[who] = { assigned: (mw.assigned || []).length, unassigned: (mw.unassigned || []).length };
    const we = (await L.apiGet(tokens[who], "/api/my-work/time-entries?from=2026-11-23&to=2026-11-29")).json || [];
    s.weekEntries[who] = { count: we.length, minutes: we.reduce((n, e) => n + (e.durationMinutes || 0), 0), entries: we.map((e) => `${e.date} ${e.durationMinutes}m ${e.taskTitle} (${e.matterName})`) };
    const n = (await L.apiGet(tokens[who], "/api/notifications?size=100")).json || {};
    s.notifications[who] = { total: n.page && n.page.totalElements, unread: L.list(n).filter((x) => !x.isRead).length };
  }
  s.taskViews = L.list((await L.apiGet(t, "/api/views?entityType=TASK")).json).map((v) => ({ id: v.id, name: v.name, shared: v.shared }));
  s.matters = matters.map((m) => ({ id: m.id, name: m.name, client: m.customerName || m.clientName }));
  return s;
}

async function main() {
  const prev = fs.existsSync(L.SEED_FILE) ? JSON.parse(fs.readFileSync(L.SEED_FILE, "utf8")) : null;
  const res = { at: new Date().toISOString(), phase: L.PHASE, steps: [] };
  const tokens = { alice: await L.idpToken("alice"), bob: await L.idpToken("bob"), carol: await L.idpToken("carol") };
  const ta = tokens.alice;
  const matters = L.list((await L.apiGet(ta, "/api/matters?size=200")).json);
  const M = (name) => matters.find((m) => m.name === name);
  const members = (await L.apiGet(ta, "/api/members")).json;
  const alice = members.find((m) => m.email.startsWith("alice@"));
  res.dataBefore = await snapshot(tokens, matters);
  console.log("before", JSON.stringify(res.dataBefore).slice(0, 1200));

  // (a) tasks
  const tasksOn = async (m) => L.list((await L.apiGet(ta, `/api/matters/${m.id}/tasks?size=200`)).json);
  const want = [{ ...ASSIGNED, assigneeId: alice.id }, ...MATTERS_FOR_UNASSIGNED.flatMap((mn) => UNASSIGNED_TITLES.map((title) => ({ matter: mn, title, priority: "MEDIUM" })))];
  const taskIds = { assigned: null, unassigned: [] };
  const cache = {};
  for (const w of want) {
    const m = M(w.matter);
    cache[m.id] = cache[m.id] || (await tasksOn(m));
    let t = cache[m.id].find((x) => x.title === w.title);
    if (!t) {
      t = await L.apiWrite(ta, "POST", `/api/matters/${m.id}/tasks`, { title: w.title, priority: w.priority, dueDate: w.dueDate, assigneeId: w.assigneeId, description: "668A OBS seed" });
      res.steps.push(`task "${w.title}" on ${w.matter}${w.assigneeId ? " assigned Alice" : ""} → ${t.id}`);
    } else res.steps.push(`task "${w.title}" on ${w.matter} already ${t.id}`);
    if (w.assigneeId) taskIds.assigned = t.id;
    else taskIds.unassigned.push(t.id);
  }

  // (b) court dates through the UI
  const browser = await L.chromium.launch();
  const st = await L.loginState(browser, "alice");
  const ctx = await L.newCtx(browser, st, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
  const page = await ctx.newPage();
  const KM = M("Khumalo — lease dispute");
  const openTab = async () => {
    await page.goto(`${L.BASE}/matters/${KM.id}`, { waitUntil: "load" });
    await page.getByTestId("tab-group-schedule").click();
    await page.getByTestId("project-court-dates-tab").waitFor();
    await page.waitForLoadState("networkidle").catch(() => {});
    await page.waitForTimeout(800);
  };
  const cds = async () => L.list((await L.apiGet(ta, `/api/court-dates?matterId=${KM.id}&size=100`)).json);
  await openTab();
  for (const cd of CDS) {
    if ((await cds()).some((c) => c.courtReference === cd.ref && c.scheduledDate === cd.date)) { res.steps.push(`court date ${cd.ref} ${cd.date} already`); continue; }
    await page.getByTestId("project-court-dates-tab").getByTestId("create-court-date-trigger").click();
    const dlg = page.getByRole("dialog", { name: "Schedule Court Date" });
    await dlg.waitFor();
    await dlg.getByLabel("Matter").locator(`option[value="${KM.id}"]`).waitFor({ state: "attached", timeout: 15000 });
    await dlg.getByLabel("Matter").selectOption(KM.id);
    await dlg.getByLabel("Type").selectOption(cd.type);
    await dlg.getByLabel("Date", { exact: true }).fill(cd.date);
    if (cd.time) await dlg.getByLabel(/^Time/).fill(cd.time);
    await dlg.getByLabel("Court Name").fill(COURT);
    await dlg.getByLabel(/^Court Reference/).fill(cd.ref);
    await dlg.getByRole("button", { name: "Schedule Court Date" }).click();
    await dlg.waitFor({ state: "hidden", timeout: 15000 });
    res.steps.push(`UI: scheduled ${cd.ref} ${cd.type} ${cd.date}${cd.time ? " " + cd.time : ""} on ${KM.name}`);
    await page.waitForTimeout(800);
  }
  await openTab();
  const shots = [];
  shots.push(path.join(L.RAW, "seed-court-dates-created.png"));
  await page.getByTestId("project-court-dates-tab").screenshot({ path: shots[0] });
  for (const cd of CDS.filter((c) => c.then)) {
    const row = (await cds()).find((c) => c.courtReference === cd.ref && c.scheduledDate === cd.date);
    if (row.status !== "SCHEDULED") { res.steps.push(`court date ${cd.ref} ${cd.date} already ${row.status}`); continue; }
    const tr = page.getByTestId("court-date-list").getByRole("row").filter({ hasText: cd.date });
    await tr.getByRole("button", { name: "Actions" }).click();
    if (cd.then === "cancel") {
      await page.getByRole("menuitem", { name: "Cancel", exact: true }).click();
      const d = page.getByTestId("cancel-court-date-dialog");
      await d.getByLabel("Reason").fill(cd.reason);
      await d.getByRole("button", { name: "Cancel Court Date" }).click();
      await d.waitFor({ state: "hidden", timeout: 15000 });
    } else if (cd.then === "outcome") {
      await page.getByRole("menuitem", { name: "Record Outcome" }).click();
      const d = page.getByTestId("outcome-dialog");
      await d.getByLabel("Outcome").fill(cd.reason);
      await d.getByRole("button", { name: "Record Outcome" }).click();
      await d.waitFor({ state: "hidden", timeout: 15000 });
    } else if (cd.then === "postpone") {
      await page.getByRole("menuitem", { name: "Postpone" }).click();
      const d = page.getByTestId("postpone-dialog");
      await d.getByLabel("New Date").fill(cd.newDate);
      await d.getByLabel("Reason").fill(cd.reason);
      await d.getByRole("button", { name: "Postpone", exact: true }).click();
      await d.waitFor({ state: "hidden", timeout: 15000 });
    }
    res.steps.push(`UI: ${cd.then} ${cd.ref} ${cd.date}${cd.newDate ? " → " + cd.newDate : ""} ("${cd.reason}")`);
    await page.waitForTimeout(1000);
  }
  await openTab();
  shots.push(path.join(L.RAW, "seed-court-dates-final.png"));
  await page.getByTestId("project-court-dates-tab").screenshot({ path: shots[1] });
  await ctx.close();
  await browser.close();

  const fin = await cds();
  res.courtDates = fin.map((c) => ({ id: c.id, ref: c.courtReference, date: c.scheduledDate, time: c.scheduledTime, type: c.dateType, status: c.status, outcome: c.outcome, description: c.description }));
  res.ids = {
    khumaloMatterId: KM.id,
    ndlovuMatterId: M("Ndlovu — estate transfer").id,
    aliceMemberId: alice.id,
    assignedTaskId: taskIds.assigned,
    unassignedTaskIds: taskIds.unassigned,
    courtDates: Object.fromEntries(fin.map((c) => [`${c.status}-${c.scheduledDate}`, c.id])),
  };
  res.dataAfter = await snapshot(tokens, matters);
  res.shots = shots;
  if (prev) { res.dataBefore = prev.dataBefore; res.runs = [...(prev.runs || [{ at: prev.at, steps: prev.steps }]), { at: res.at, steps: res.steps }]; }
  else res.runs = [{ at: res.at, steps: res.steps }];
  fs.writeFileSync(L.SEED_FILE, JSON.stringify(res));
  console.log(JSON.stringify({ ids: res.ids, courtDates: res.courtDates, steps: res.steps.filter((s) => s.startsWith("UI")) }, null, 1));
}
main().catch((e) => { console.error("SEED FAIL", e); process.exit(1); });
