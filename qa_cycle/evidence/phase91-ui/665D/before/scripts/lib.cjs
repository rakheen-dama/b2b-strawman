// 665D observe-first shared helpers (copied from 665C/before/scripts/lib.cjs, adapted; that file is
// not edited). PHASE=before|after|after-fix (default before) picks the output dir (../../$PHASE/) and
// the raw-tile dir (/tmp/665D-$PHASE-raw/, outside the repo — raw per-viewport PNGs are never
// committed). Re-exports the 661B helpers (login, theme via localStorage `kazi-theme`, sidebar
// cookie, console watch, contact sheet). Playwright: the worktree's frontend/node_modules when
// installed, else the sibling main checkout's (../b2b-strawman/frontend/node_modules).
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
const RAW = `/tmp/665D-${PHASE}-raw`;
fs.mkdirSync(OUT, { recursive: true });
fs.mkdirSync(RAW, { recursive: true });
const BASE = `${C.APP}/org/${C.ORG}`;
const EXTRA = { reducedMotion: "reduce", timezoneId: "Africa/Johannesburg" };
// Fees & Trust family tab ids, in lib/settings-nav.ts order (tablist aria-label "Fees & Trust settings").
const TABS = ["tariffs", "tax", "time-recording", "fee-runs", "collections", "trust-accounting"];
const LABELS = { tariffs: "Rates", tax: "Tax", "time-recording": "Time recording", "fee-runs": "Fee runs", collections: "Collections", "trust-accounting": "Trust accounting" };
const TABLIST = "Fees & Trust settings";
const CAROL_EMAIL = "carol@e2e-test.local";
// Data the OBS creates (all through the UI); ids are discovered by name at run time.
const NAMES = {
  roleHolder: "665D OBS Financial",
  roleLacker: "665D OBS No Financial",
  taxRate: "OBS 665D Rate",
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

// The Fees & Trust state the saves must restore, GET only (Alice's token).
async function feesState(t) {
  const s = (await apiGet(t, "/api/settings")).json || {};
  const pick = (keys) => Object.fromEntries(keys.map((k) => [k, s[k] ?? null]));
  return {
    currency: pick(["defaultCurrency"]),
    tax: pick(["taxRegistrationNumber", "taxRegistrationLabel", "taxLabel", "taxInclusive"]),
    timeRecording: pick(["timeReminderEnabled", "timeReminderDays", "timeReminderTime", "timeReminderMinHours", "defaultExpenseMarkupPercent"]),
    feeRuns: pick(["billingBatchAsyncThreshold", "billingEmailRateLimit", "defaultBillingRunCurrency", "defaultPaymentTermsDays"]),
    enabledModules: s.enabledModules || null,
    collections: (await apiGet(t, "/api/settings/collections")).json,
    horizontalModules: ((await apiGet(t, "/api/settings/modules")).json || {}).modules || null,
    taxRates: list((await apiGet(t, "/api/tax-rates?includeInactive=true")).json).map((r) => ({ id: r.id, name: r.name, rate: r.rate, isDefault: r.isDefault, isExempt: r.isExempt, active: r.active })),
    billingRates: list((await apiGet(t, "/api/billing-rates")).json).map((r) => ({ id: r.id, memberId: r.memberId, hourlyRate: r.hourlyRate, currency: r.currency, effectiveFrom: r.effectiveFrom, effectiveTo: r.effectiveTo })),
    costRates: list((await apiGet(t, "/api/cost-rates")).json).map((r) => ({ id: r.id, memberId: r.memberId, hourlyCost: r.hourlyCost, currency: r.currency, effectiveFrom: r.effectiveFrom })),
    trustAccounts: list((await apiGet(t, "/api/trust-accounts")).json).map((a) => ({ id: a.id, accountName: a.accountName, status: a.status, isPrimary: a.isPrimary, section: a.section })),
  };
}

module.exports = { ...C, C, W, PHASE, OUT, RAW, BASE, EXTRA, TABS, LABELS, TABLIST, CAROL_EMAIL, NAMES, apiGet, list, feesState, pwPath };
