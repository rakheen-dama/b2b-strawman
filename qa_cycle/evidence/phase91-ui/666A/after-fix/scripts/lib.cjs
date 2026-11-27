// 666A observe-first shared helpers (copied from 665F/before/scripts/lib.cjs, adapted; that file is
// not edited). PHASE=before|after|after-fix (default before) picks the output dir (../../$PHASE/) and
// the raw-tile dir (/tmp/666A-$PHASE-raw/, outside the repo — raw per-viewport PNGs are never
// committed). Re-exports the 661B helpers (login, theme via localStorage `kazi-theme`, sidebar cookie,
// console watch, contact sheet). Playwright: the worktree's frontend/node_modules when installed, else
// the sibling main checkout's (../b2b-strawman/frontend/node_modules).
const path = require("path");
const fs = require("fs");
const Module = require("module");

const W = path.resolve(__dirname, "../../../../../..");
const candidates = [path.join(W, "frontend/node_modules/@playwright/test"), path.resolve(W, "../b2b-strawman/frontend/node_modules/@playwright/test")];
const pwPath = candidates.find((c) => fs.existsSync(c));
if (!pwPath) throw new Error("no @playwright/test in " + candidates.join(" or "));
const origResolve = Module._resolveFilename;
Module._resolveFilename = function (req, ...rest) {
  if (req === path.join(W, "frontend/node_modules/@playwright/test")) req = pwPath;
  return origResolve.call(this, req, ...rest);
};
const C = require("../../../661B/walk/scripts/common.cjs");
Module._resolveFilename = origResolve;

const PHASE = process.env.PHASE || "before";
if (!["before", "after", "after-fix"].includes(PHASE)) throw new Error(`PHASE must be before|after|after-fix, got ${PHASE}`);
const OUT = path.resolve(__dirname, "../..", PHASE);
const RAW = `/tmp/666A-${PHASE}-raw`;
fs.mkdirSync(OUT, { recursive: true });
fs.mkdirSync(RAW, { recursive: true });
const BASE = `${C.APP}/org/${C.ORG}`;
const EXTRA = { reducedMotion: "reduce", timezoneId: "Africa/Johannesburg" };
const CAROL_EMAIL = "carol@e2e-test.local";
// Data the OBS creates (all through the UI); ids are discovered by name / period at run time.
const NAMES = {
  roleInvoicing: "666A OBS Invoicing",
};
// The fee run the OBS creates through the wizard (03-fee-run.cjs). The configure step sends no name
// (K-65), so the run is found by this period + notes.
const RUN = { periodFrom: "2026-11-01", periodTo: "2026-11-27", notes: "666A OBS-before fee run" };
// A period with no unbilled work (empty state for /invoices/unbilled).
const EMPTY_PERIOD = { periodFrom: "2001-01-01", periodTo: "2001-01-31" };

// GET with a token; returns {status, json} and never throws on non-2xx.
async function apiGet(token, p) {
  const r = await fetch(`${C.API}${p}`, { headers: { Authorization: `Bearer ${token}` } });
  const text = await r.text();
  let json = null;
  try { json = JSON.parse(text); } catch (e) {}
  return { status: r.status, json, text: json ? undefined : text.slice(0, 200) };
}
const list = (j) => (Array.isArray(j) ? j : j && (j.content || j.items || j.data) ? j.content || j.items || j.data : []);
const pageInfo = (j) => (j && j.page ? { totalElements: j.page.totalElements, totalPages: j.page.totalPages, size: j.page.size } : j && j.totalElements !== undefined ? { totalElements: j.totalElements, totalPages: j.totalPages, size: j.size } : null);

// The fee-notes state, GET only (Alice's token).
async function feeState(t) {
  const inv = await apiGet(t, "/api/invoices");
  const sum = await apiGet(t, "/api/invoices/outstanding-summary");
  const runs = await apiGet(t, "/api/billing-runs?size=100&sort=createdAt,desc");
  const debt = await apiGet(t, "/api/collections/debtors?page=0&size=50");
  const gates = await apiGet(t, "/api/ai/gates?gateType=SEND_COLLECTION_REMINDER&status=PENDING&size=50");
  const mods = await apiGet(t, "/api/settings/modules");
  const settings = await apiGet(t, "/api/settings");
  const rows = list(inv.json);
  const byStatus = {};
  for (const i of rows) byStatus[i.status] = (byStatus[i.status] || 0) + 1;
  const runRows = list(runs.json);
  const items = {};
  for (const r of runRows) items[r.id] = ((await apiGet(t, `/api/billing-runs/${r.id}/items`)).json || []).map((x) => ({ id: x.id, clientName: x.clientName, status: x.status, invoiceId: x.invoiceId, totalUnbilledAmount: x.totalUnbilledAmount, failureReason: x.failureReason }));
  return {
    invoices: { status: inv.status, n: rows.length, byStatus, rows: rows.map((i) => ({ id: i.id, invoiceNumber: i.invoiceNumber, status: i.status, clientName: i.clientName, clientId: i.clientId, total: i.total, currency: i.currency, issueDate: i.issueDate, dueDate: i.dueDate, paidAt: i.paidAt, balanceDue: i.balanceDue, amountPaid: i.amountPaid, takeOnBatchId: i.takeOnBatchId, paymentUrl: !!i.paymentUrl })) },
    outstandingSummary: { status: sum.status, json: sum.json },
    billingRuns: { status: runs.status, page: pageInfo(runs.json), rows: runRows.map((r) => ({ id: r.id, name: r.name, status: r.status, periodFrom: r.periodFrom, periodTo: r.periodTo, currency: r.currency, totalClients: r.totalClients, totalInvoices: r.totalInvoices, totalSent: r.totalSent, totalFailed: r.totalFailed, totalAmount: r.totalAmount, notes: r.notes, createdAt: r.createdAt, completedAt: r.completedAt })), items },
    debtors: { status: debt.status, page: pageInfo(debt.json), rows: list(debt.json).map((d) => ({ clientId: d.clientId, clientName: d.clientName, currency: d.currency, outstandingTotal: d.outstandingTotal, invoiceCount: d.invoiceCount, oldestDaysOverdue: d.oldestDaysOverdue, buckets: d.buckets, signals: d.signals, collectionsExempt: d.collectionsExempt, lastActivity: d.lastActivity })) },
    reminderGates: { status: gates.status, page: pageInfo(gates.json), rows: list(gates.json).map((g) => ({ id: g.id, status: g.status, gateType: g.gateType, createdAt: g.createdAt })) },
    modules: { status: mods.status, modules: mods.json && (mods.json.modules || []).map((m) => ({ id: m.id, name: m.name, enabled: m.enabled })) },
    defaultCurrency: settings.json && settings.json.defaultCurrency,
  };
}

async function unbilled(t, q) {
  const qs = new URLSearchParams(q).toString();
  const r = await apiGet(t, `/api/invoices/unbilled-summary?${qs}`);
  return { status: r.status, query: q, n: list(r.json).length, rows: list(r.json).map((x) => ({ clientId: x.clientId, clientName: x.clientName, ...Object.fromEntries(Object.entries(x).filter(([k]) => /amount|total|hours|count|entries/i.test(k))) })) };
}

module.exports = { ...C, C, W, PHASE, OUT, RAW, BASE, EXTRA, CAROL_EMAIL, NAMES, RUN, EMPTY_PERIOD, apiGet, list, pageInfo, feeState, unbilled, pwPath };
