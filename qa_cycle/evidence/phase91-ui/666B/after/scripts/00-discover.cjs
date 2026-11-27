// 666B OBS step 0: GET-only snapshot of the fee notes on the stack (Alice's token) plus the bits the
// setup depends on (billing rates, the setup matter's tasks / time entries, Mailpit count, Carol's
// capabilities). Usage: PHASE=before node 00-discover.cjs <pre|post> → ../discover-<pre|post>.json
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const TAG = process.argv[2];
if (!["pre", "post"].includes(TAG)) throw new Error("usage: <pre|post>");

(async () => {
  const t = await L.idpToken("alice");
  const tc = await L.idpToken("carol");
  const inv = await L.invoices(t);
  const rates = await L.apiGet(t, "/api/billing-rates");
  const matters = L.list((await L.apiGet(t, "/api/matters?size=100")).json);
  const m = matters.find((x) => x.name === L.SETUP.matter);
  const tasks = m ? L.list((await L.apiGet(t, `/api/matters/${m.id}/tasks`)).json) : [];
  const task = tasks.find((x) => x.title === L.SETUP.task);
  const entries = task ? L.list((await L.apiGet(t, `/api/tasks/${task.id}/time-entries`)).json).map((e) => ({ id: e.id, description: e.description, durationMinutes: e.durationMinutes, billable: e.billable, billingRateSnapshot: e.billingRateSnapshot, invoiceId: e.invoiceId })) : [];
  const mp = await (await fetch("http://localhost:8026/api/v1/messages?limit=50")).json();
  const carolCaps = await L.apiGet(tc, "/api/me/capabilities");
  const carolInv = await L.apiGet(tc, inv.rows[0] ? `/api/invoices/${inv.rows[0].id}` : "/api/invoices");
  const fieldDefs = await L.apiGet(t, "/api/field-definitions?entityType=INVOICE");
  const fieldGroups = await L.apiGet(t, "/api/field-groups?entityType=INVOICE");
  const templates = await L.apiGet(t, "/api/templates?category=INVOICE");
  const out = {
    at: new Date().toISOString(), tag: TAG, invoices: inv,
    takeOn: { withBatchId: inv.rows.filter((i) => i.takeOnBatchId).map((i) => `${i.invoiceNumber} ${i.status} ${i.id}`) },
    billingRates: { status: rates.status, rows: L.list(rates.json).map((r) => ({ id: r.id, memberName: r.memberName, hourlyRate: r.hourlyRate, currency: r.currency, effectiveFrom: r.effectiveFrom, projectId: r.projectId, customerId: r.customerId })) },
    setupMatter: m ? { id: m.id, name: m.name, clientName: m.clientName, clientId: m.clientId, tasks: tasks.map((x) => ({ id: x.id, title: x.title, status: x.status })), entries } : null,
    mailpit: { total: mp.total, latest: (mp.messages || []).slice(0, 12).map((x) => ({ ID: x.ID, Subject: x.Subject, To: (x.To || []).map((y) => y.Address), Created: x.Created })) },
    carol: { caps: carolCaps.json && { role: carolCaps.json.role, capabilities: carolCaps.json.capabilities }, invoiceGetStatus: carolInv.status },
    invoiceFieldDefinitions: { status: fieldDefs.status, n: L.list(fieldDefs.json).length, slugs: L.list(fieldDefs.json).map((d) => d.slug) },
    invoiceFieldGroups: { status: fieldGroups.status, n: L.list(fieldGroups.json).length, names: L.list(fieldGroups.json).map((g) => g.name) },
    invoiceTemplates: { status: templates.status, n: L.list(templates.json).length },
  };
  fs.writeFileSync(path.join(L.OUT, `discover-${TAG}.json`), JSON.stringify(out) + "\n");
  console.log(JSON.stringify({ n: inv.n, byStatus: inv.byStatus, takeOn: out.takeOn.withBatchId.length, rates: out.billingRates.rows.length, matter: m && m.id, task: task && task.id, entries: entries.length, mailpit: mp.total, carol: out.carol, fieldDefs: out.invoiceFieldDefinitions, groups: out.invoiceFieldGroups, templates: out.invoiceTemplates }));
  for (const i of inv.rows) console.log(i.id, i.invoiceNumber, i.status, i.clientName, i.total, "due", i.dueDate, "paid", i.amountPaid, "bal", i.balanceDue, i.takeOnBatchId ? "TAKE-ON" : "", "pe", i.paymentEvents, "lines", i.lines.length);
})();
