// 666A OBS step 0: read-only discovery (GET only, each persona's own mock-IDP token). Records what the
// capture needs and the baseline: capabilities per persona (+ the status of the fee-note endpoints
// for each), the fee-note state (invoices by status, outstanding summary, billing runs + items,
// debtors, pending reminder gates, modules, default currency), unbilled summaries for the default
// period / USD / an empty period, org roles, members and their role/overrides, the Mailpit count.
// Creates nothing. Adapted from 665F/before/scripts/00-discover.cjs (not edited).
// Writes ../discover-<label>.json (single line).
// Usage: PHASE=before node 00-discover.cjs <label>   (e.g. pre / post)
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const ENDPOINTS = ["/api/invoices", "/api/invoices/outstanding-summary", "/api/billing-runs?size=100", "/api/collections/debtors?page=0&size=50", "/api/ai/gates?gateType=SEND_COLLECTION_REMINDER&status=PENDING&size=50", "/api/settings/modules"];

(async () => {
  const label = process.argv[2] || "discover";
  const out = { capturedAt: new Date().toISOString(), label, personas: {} };
  for (const who of ["alice", "carol"]) {
    const t = await L.idpToken(who);
    const caps = await L.apiGet(t, "/api/me/capabilities");
    out.personas[who] = { caps: caps.json || caps, endpointStatus: {} };
    for (const p of ENDPOINTS) out.personas[who].endpointStatus[p] = (await L.apiGet(t, p)).status;
  }
  const a = await L.idpToken("alice");
  out.fee = await L.feeState(a);
  // The unbilled page's default period is the last 24 months to today's SA date (unbilled/page.tsx).
  const today = new Date().toLocaleDateString("en-CA", { timeZone: "Africa/Johannesburg" });
  const [y, m, d] = today.split("-").map(Number);
  const from = new Date(Date.UTC(y - 2, m - 1, d)).toISOString().slice(0, 10);
  out.unbilled = {
    default: await L.unbilled(a, { periodFrom: from, periodTo: today, currency: "ZAR" }),
    usd: await L.unbilled(a, { periodFrom: from, periodTo: today, currency: "USD" }),
    emptyPeriod: await L.unbilled(a, { ...L.EMPTY_PERIOD, currency: "ZAR" }),
    run: await L.unbilled(a, { periodFrom: L.RUN.periodFrom, periodTo: L.RUN.periodTo, currency: "ZAR" }),
  };
  const clients = L.list((await L.apiGet(a, "/api/clients")).json);
  out.clients = [];
  for (const c of clients) {
    const d = (await L.apiGet(a, `/api/clients/${c.id}`)).json || {};
    const u = (await L.apiGet(a, `/api/clients/${c.id}/unbilled-summary`)).json || {};
    out.clients.push({ id: c.id, name: c.name, status: c.status, lifecycleStatus: d.lifecycleStatus, unbilledHours: u.totalHours, unbilledAmount: u.totalAmount });
  }
  const roles = (await L.apiGet(a, "/api/org-roles")).json || [];
  out.orgRoles = roles.map((r) => ({ id: r.id, name: r.name, slug: r.slug, isSystem: r.isSystem, memberCount: r.memberCount, capabilities: r.capabilities }));
  const members = (await L.apiGet(a, "/api/members")).json || [];
  out.members = [];
  for (const mm of members) {
    const c = await L.apiGet(a, `/api/members/${mm.id}/capabilities`);
    out.members.push({ id: mm.id, name: mm.name, email: mm.email, orgRole: mm.orgRole, memberCaps: c.json || c.status });
  }
  try { const mp = await (await fetch("http://localhost:8026/api/v1/messages?limit=1")).json(); out.mailpit = { total: mp.total, messagesCount: mp.messages_count }; } catch (e) { out.mailpit = { error: String(e) }; }
  fs.writeFileSync(path.join(L.OUT, `discover-${label}.json`), JSON.stringify(out) + "\n");
  const f = out.fee;
  console.log(JSON.stringify({
    personas: Object.fromEntries(Object.entries(out.personas).map(([k, v]) => [k, { role: v.caps.role, isAdmin: v.caps.isAdmin, isOwner: v.caps.isOwner, n: (v.caps.capabilities || []).length, status: v.endpointStatus }])),
    invoices: f.invoices.n, byStatus: f.invoices.byStatus, takeOn: f.invoices.rows.filter((r) => r.takeOnBatchId).length, summary: f.outstandingSummary.json,
    runs: f.billingRuns.rows.map((r) => `${r.id}:${r.name}:${r.status}:${r.totalClients}/${r.totalInvoices}`), debtors: f.debtors.rows.map((d) => `${d.clientName}:${d.outstandingTotal}:${d.oldestDaysOverdue}`), gates: f.reminderGates.page, modules: f.modules.modules, currency: f.defaultCurrency,
    unbilled: Object.fromEntries(Object.entries(out.unbilled).map(([k, v]) => [k, `${v.status}:${v.n}`])),
    clients: out.clients.map((c) => `${c.name}:${c.lifecycleStatus}:${c.unbilledAmount}`),
    orgRoles: out.orgRoles.map((r) => `${r.name}(${r.memberCount})`), members: out.members.map((x) => `${x.email}:${x.memberCaps.roleName}`), mailpit: out.mailpit,
  }, null, 1));
})();
