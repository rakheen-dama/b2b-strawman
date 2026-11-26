// 665E OBS AFTER-FIX copy of ../../after/scripts/lib.cjs (that file is not edited). One change: with
// PHASE=after-fix the raw-tile dir is /tmp/665E-afterfix-raw (the brief's path) instead of
// /tmp/665E-after-fix-raw. Everything else is byte-identical.
// 665E observe-first shared helpers (copied from 665D/before/scripts/lib.cjs, adapted; that file is
// not edited). PHASE=before|after|after-fix (default before) picks the output dir (../../$PHASE/) and
// the raw-tile dir (/tmp/665E-$PHASE-raw/, outside the repo — raw per-viewport PNGs are never
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
const RAW = PHASE === "after-fix" ? "/tmp/665E-afterfix-raw" : `/tmp/665E-${PHASE}-raw`;
fs.mkdirSync(OUT, { recursive: true });
fs.mkdirSync(RAW, { recursive: true });
const BASE = `${C.APP}/org/${C.ORG}`;
const EXTRA = { reducedMotion: "reduce", timezoneId: "Africa/Johannesburg" };
// Documents family tab ids, in lib/settings-nav.ts order (tablist aria-label "Documents settings").
const TABS = ["templates", "clauses", "acceptance", "email"];
const LABELS = { templates: "Templates", clauses: "Clauses", acceptance: "Acceptance", email: "Email" };
const TABLIST = "Documents settings";
const CAROL_EMAIL = "carol@e2e-test.local";

// GET with a token; returns {status, json} and never throws on non-2xx.
async function apiGet(token, p) {
  const r = await fetch(`${C.API}${p}`, { headers: { Authorization: `Bearer ${token}` } });
  const text = await r.text();
  let json = null;
  try { json = JSON.parse(text); } catch (e) {}
  return { status: r.status, json, text: json ? undefined : text.slice(0, 200) };
}
const list = (j) => (Array.isArray(j) ? j : j && (j.content || j.items || j.data) ? j.content || j.items || j.data : []);

// The Documents state the saves must restore, GET only (Alice's token).
async function docsState(t) {
  const s = (await apiGet(t, "/api/settings")).json || {};
  const pick = (keys) => Object.fromEntries(keys.map((k) => [k, s[k] ?? null]));
  const tpl = await apiGet(t, "/api/templates");
  const cl = await apiGet(t, "/api/clauses?includeInactive=true");
  const stats = await apiGet(t, "/api/email/stats");
  const log = await apiGet(t, "/api/email/delivery-log?page=0&size=20");
  return {
    branding: { ...pick(["brandColor", "documentFooterText"]), logoUrlPresent: !!s.logoUrl },
    acceptance: pick(["acceptanceExpiryDays"]),
    templates: { status: tpl.status, rows: list(tpl.json).map((x) => ({ id: x.id, name: x.name, category: x.category, format: x.format, source: x.source, active: x.active, primaryEntityType: x.primaryEntityType, sourceTemplateId: x.sourceTemplateId || null })) },
    clauses: { status: cl.status, n: list(cl.json).length, rows: list(cl.json).map((x) => ({ id: x.id, title: x.title, category: x.category, source: x.source, active: x.active })) },
    clauseCategories: (await apiGet(t, "/api/clauses/categories")).json,
    emailStats: { status: stats.status, json: stats.json },
    deliveryLog: { status: log.status, totalElements: log.json && (log.json.totalElements ?? (log.json.page && log.json.page.totalElements)), n: list(log.json).length, first: list(log.json).slice(0, 3).map((e) => ({ recipientEmail: e.recipientEmail, templateName: e.templateName, status: e.status })) },
  };
}

module.exports = { ...C, C, W, PHASE, OUT, RAW, BASE, EXTRA, TABS, LABELS, TABLIST, CAROL_EMAIL, apiGet, list, docsState, pwPath };
