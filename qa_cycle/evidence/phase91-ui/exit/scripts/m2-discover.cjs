// 670A M2 id discovery: READ-ONLY API GETs only (slice precedent, brief S7 gotcha 13: "read-only API
// GET for id discovery only"), with Alice's / Carol's mock-IDP tokens. Nothing is written to the
// stack. Result merged into ../ids.json under the key "m2" (M1's keys untouched).
// Usage: node m2-discover.cjs
const fs = require("fs");
const path = require("path");
const L = require("./lib.cjs");

(async () => {
  const ta = await L.idpToken("alice");
  const tc = await L.idpToken("carol");
  const g = async (t, p) => { try { return await L.api(t, p); } catch (e) { return { ERR: e.message }; } };
  const m2 = { discoveredAt: new Date().toISOString(), by: "read-only API GET (alice; carol for her matters / my-work)", how: {} };
  // Fee notes: first id per status (list order of GET /api/invoices), plus whether any SENT is overdue.
  const inv = L.list(await g(ta, "/api/invoices?size=200"));
  const today = new Date().toISOString().slice(0, 10);
  const byStatus = {};
  for (const i of inv) (byStatus[i.status] = byStatus[i.status] || []).push({ id: i.id, number: i.invoiceNumber || null, dueDate: i.dueDate || null, total: i.total });
  m2.invoiceCounts = Object.fromEntries(Object.entries(byStatus).map(([k, v]) => [k, v.length]));
  m2.invoices = {};
  for (const s of ["DRAFT", "APPROVED", "SENT", "PARTIALLY_PAID", "PAID", "VOID"]) m2.invoices[s] = (byStatus[s] || [])[0] || null;
  // SENT, not overdue = a SENT note due today or later; SENT overdue = due before today.
  const sent = byStatus.SENT || [];
  m2.invoices.SENT = sent.find((x) => x.dueDate && x.dueDate >= today) || m2.invoices.SENT;
  m2.invoices.SENT_OVERDUE = sent.find((x) => x.dueDate && x.dueDate < today) || null;
  m2.how.invoices = `GET /api/invoices?size=200 (${inv.length} rows; today ${today})`;
  // Billing runs.
  const br = L.list(await g(ta, "/api/billing-runs?size=20"));
  m2.billingRunId = br[0] ? br[0].id : null;
  m2.how.billingRuns = `GET /api/billing-runs?size=20 (${br.length} rows)`;
  // Trust: primary GENERAL account, its client ledgers, reconciliations, monthly lists.
  const accts = L.list(await g(ta, "/api/trust-accounts"));
  const gen = accts.find((a) => a.accountType === "GENERAL") || accts[0];
  m2.trustAccountId = gen ? gen.id : null;
  const led = gen ? L.list(await g(ta, `/api/trust-accounts/${gen.id}/client-ledgers`)) : [];
  m2.trustLedgerClientId = led[0] ? led[0].customerId || led[0].clientId : null;
  m2.trustLedgerClientName = led[0] ? led[0].customerName || led[0].clientName : null;
  m2.reconciliations = {};
  for (const a of accts) m2.reconciliations[a.id] = L.list(await g(ta, `/api/trust-accounts/${a.id}/reconciliations`)).map((r) => ({ id: r.id, status: r.status, monthlyListId: r.monthlyListId || null }));
  m2.monthlyLists = gen ? L.list(await g(ta, `/api/trust-accounts/${gen.id}/monthly-lists`)).map((x) => x.id) : [];
  m2.monthlyListId = m2.monthlyLists[0] || Object.values(m2.reconciliations).flat().map((r) => r.monthlyListId).find(Boolean) || null;
  m2.how.trust = `GET /api/trust-accounts (${accts.length}), …/client-ledgers (${led.length}), …/reconciliations, …/monthly-lists`;
  // Section 9 ids.
  const dr = L.list(await g(ta, "/api/data-requests?size=5"));
  m2.dataRequestId = dr[0] ? dr[0].id : null;
  const ds = L.list(await g(ta, "/api/legal/disbursements?size=5"));
  m2.disbursementId = ds[0] ? ds[0].id : null;
  const ir = L.list(await g(ta, "/api/information-requests?size=5"));
  m2.informationRequestId = ir[0] ? ir[0].id : null;
  m2.informationRequestNumber = ir[0] ? ir[0].requestNumber : null;
  // 663A/663B: M1's matter (Carol is a member: GET /api/matters as Carol) and its first task.
  const ids = L.readIds();
  const cm = L.list(await g(tc, "/api/matters"));
  m2.carolMatterIds = cm.map((m) => m.id);
  m2.matterId = ids.matterId;
  m2.carolOnMatter = m2.carolMatterIds.includes(ids.matterId);
  const tasks = L.list(await g(ta, `/api/matters/${ids.matterId}/tasks`));
  m2.taskId = tasks[0] ? tasks[0].id : null;
  m2.taskTitle = tasks[0] ? tasks[0].title : null;
  m2.clientId = ids.clientId;
  const mwA = await g(ta, "/api/my-work/tasks");
  const mwC = await g(tc, "/api/my-work/tasks");
  m2.myWork = { alice: { assigned: (mwA.assigned || []).length, unassigned: (mwA.unassigned || []).length }, carol: { assigned: (mwC.assigned || []).length, unassigned: (mwC.unassigned || []).length } };
  const f = path.join(L.EXIT, "ids.json");
  const cur = JSON.parse(fs.readFileSync(f, "utf8"));
  cur.m2 = { ...(cur.m2 || {}), ...m2, created: (cur.m2 && cur.m2.created) || [] };
  fs.writeFileSync(f, JSON.stringify(cur) + "\n");
  console.log(JSON.stringify(m2, null, 1).slice(0, 3000));
})().catch((e) => { console.error("DISCOVER FAIL", e); process.exit(1); });
