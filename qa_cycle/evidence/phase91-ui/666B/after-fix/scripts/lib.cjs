// [after-fix copy: only the RAW dir for PHASE=after-fix changed]
// 666B observe-first shared helpers (copied from 666A/before/scripts/lib.cjs, adapted; that file is
// not edited). PHASE=before|after|after-fix (default before) picks the output dir (../../$PHASE/) and
// the raw-tile dir (/tmp/666B-$PHASE-raw/, outside the repo — raw per-viewport PNGs are never
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
const RAW = PHASE === "after-fix" ? "/tmp/666B-afterfix-raw" : `/tmp/666B-${PHASE}-raw`; // after-fix copy: raw dir name per the after-fix brief
fs.mkdirSync(OUT, { recursive: true });
fs.mkdirSync(RAW, { recursive: true });
const BASE = `${C.APP}/org/${C.ORG}`;
const EXTRA = { reducedMotion: "reduce", timezoneId: "Africa/Johannesburg" };

// Data the OBS creates through the UI (02-setup.cjs). Found again by these names at run time.
const SETUP = {
  client: "Acme Corp",
  matter: "Website Redesign",
  task: "666B OBS fee-note detail",
  rate: { hourly: "2500", currency: "ZAR" },
  // One billable time entry per fee note; each generation bills only the newest entry.
  notes: [
    { key: "approved", minutes: "30", description: "666B OBS entry — approved" },
    { key: "sent-overdue", minutes: "45", description: "666B OBS entry — sent overdue", dueDate: "2026-11-01" },
    { key: "partial", minutes: "60", description: "666B OBS entry — partially paid" },
    { key: "paid", minutes: "15", description: "666B OBS entry — paid" },
    { key: "void", minutes: "20", description: "666B OBS entry — void" },
  ],
};

// GET with a token; returns {status, json} and never throws on non-2xx.
async function apiGet(token, p) {
  const r = await fetch(`${C.API}${p}`, { headers: { Authorization: `Bearer ${token}` } });
  const text = await r.text();
  let json = null;
  try { json = JSON.parse(text); } catch (e) {}
  return { status: r.status, json, text: json ? undefined : text.slice(0, 200) };
}
const list = (j) => (Array.isArray(j) ? j : j && (j.content || j.items || j.data) ? j.content || j.items || j.data : []);

// Fee notes, GET only (Alice's token): the list plus each one's detail essentials.
async function invoices(t) {
  const inv = await apiGet(t, "/api/invoices");
  const rows = list(inv.json);
  const out = [];
  for (const i of rows) {
    const d = (await apiGet(t, `/api/invoices/${i.id}`)).json || {};
    const pe = ["SENT", "PARTIALLY_PAID", "PAID"].includes(i.status) ? (await apiGet(t, `/api/invoices/${i.id}/payment-events`)).json : null;
    out.push({ id: i.id, invoiceNumber: i.invoiceNumber, status: i.status, clientName: i.clientName, clientId: i.clientId, total: i.total, subtotal: d.subtotal, taxAmount: d.taxAmount, currency: i.currency, issueDate: i.issueDate, dueDate: i.dueDate, paidAt: d.paidAt, amountPaid: d.amountPaid, balanceDue: d.balanceDue, takeOnBatchId: d.takeOnBatchId, notes: d.notes, lines: (d.lines || []).map((l) => ({ description: l.description, quantity: l.quantity, unitPrice: l.unitPrice, amount: l.amount, lineType: l.lineType })), payments: (d.payments || []).length, paymentEvents: pe ? list(pe).length : null, appliedFieldGroups: (d.appliedFieldGroups || []).length, createdAt: d.createdAt, updatedAt: d.updatedAt });
  }
  const byStatus = {};
  for (const i of out) byStatus[i.status] = (byStatus[i.status] || 0) + 1;
  return { status: inv.status, n: out.length, byStatus, rows: out };
}

module.exports = { ...C, C, W, PHASE, OUT, RAW, BASE, EXTRA, SETUP, apiGet, list, invoices, pwPath };
