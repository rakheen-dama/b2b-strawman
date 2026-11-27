// 665F observe-first shared helpers (copied from 665E/before/scripts/lib.cjs and 665D's NAMES, adapted;
// those files are not edited). PHASE=before|after|after-fix (default before) picks the output dir
// (../../$PHASE/) and the raw-tile dir (/tmp/665F-$PHASE-raw/, outside the repo — raw per-viewport PNGs
// are never committed). Re-exports the 661B helpers (login, theme via localStorage `kazi-theme`,
// sidebar cookie, console watch, contact sheet). Playwright: the worktree's frontend/node_modules
// when installed, else the sibling main checkout's (../b2b-strawman/frontend/node_modules).
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
const RAW = `/tmp/665F-${PHASE}-raw`;
fs.mkdirSync(OUT, { recursive: true });
fs.mkdirSync(RAW, { recursive: true });
const BASE = `${C.APP}/org/${C.ORG}`;
const EXTRA = { reducedMotion: "reduce", timezoneId: "Africa/Johannesburg" };
// Connections family tab ids, in lib/settings-nav.ts order (tablist aria-label "Connections settings").
const TABS = ["xero", "mcp", "ai", "notifications", "packs", "automations"];
const LABELS = { xero: "Integrations", mcp: "MCP", ai: "AI", notifications: "Notifications", packs: "Packs", automations: "Automations" };
const TABLIST = "Connections settings";
// The three log routes that stay sub-pages of the family.
const SUBPAGES = { "sync-log": "/settings/connections/xero/sync-log", "ai-history": "/settings/connections/ai/history", executions: "/settings/connections/automations/executions" };
const CAROL_EMAIL = "carol@e2e-test.local";
// Data the OBS creates (all through the UI); ids are discovered by name at run time.
const NAMES = {
  roleHolder: "665F OBS AI Automations",
  roleLacker: "665F OBS No Integration",
};
// The instant switches the OBS toggles and reverts (03-interactions).
const TOGGLE_RULE = "Investment Maturity Reminder";
const TOGGLE_PREF = { type: "TASK_ASSIGNED", label: "Task Assigned" };

// GET with a token; returns {status, json} and never throws on non-2xx.
async function apiGet(token, p) {
  const r = await fetch(`${C.API}${p}`, { headers: { Authorization: `Bearer ${token}` } });
  const text = await r.text();
  let json = null;
  try { json = JSON.parse(text); } catch (e) {}
  return { status: r.status, json, text: json ? undefined : text.slice(0, 200) };
}
const list = (j) => (Array.isArray(j) ? j : j && (j.content || j.items || j.data) ? j.content || j.items || j.data : []);
const pageInfo = (j) => (j && j.page ? { totalElements: j.page.totalElements, totalPages: j.page.totalPages, size: j.page.size } : null);

// The Connections state, GET only (Alice's token).
async function connState(t) {
  const integ = await apiGet(t, "/api/integrations");
  const xero = await apiGet(t, "/api/integrations/xero/connection");
  const mcp = await apiGet(t, "/api/integrations/mcp/status");
  const prof = await apiGet(t, "/api/ai/profile");
  const prefs = await apiGet(t, "/api/notifications/preferences");
  const catalog = await apiGet(t, "/api/packs/catalog");
  const installed = await apiGet(t, "/api/packs/installed");
  const rules = await apiGet(t, "/api/automation-rules");
  const ex = await apiGet(t, "/api/automation-executions?size=20");
  const sync = await apiGet(t, "/api/integrations/sync/entries?size=20");
  const aiEx = await apiGet(t, "/api/ai/executions?size=20");
  const checks = {};
  for (const p of list(installed.json)) checks[p.packId] = (await apiGet(t, `/api/packs/${encodeURIComponent(p.packId)}/uninstall-check`)).json;
  return {
    integrations: { status: integ.status, rows: list(integ.json).map((i) => ({ domain: i.domain, providerSlug: i.providerSlug, enabled: i.enabled, keySuffix: i.keySuffix, configJson: i.configJson })) },
    providers: (await apiGet(t, "/api/integrations/providers")).json,
    xeroConnection: { status: xero.status, json: xero.json },
    mcpStatus: { status: mcp.status, json: mcp.json },
    aiProfile: { status: prof.status, coldStartCompleted: prof.json && prof.json.coldStartCompleted, preferredModel: prof.json && prof.json.preferredModel, riskCalibration: prof.json && prof.json.riskCalibration, jurisdiction: prof.json && prof.json.jurisdiction, monthlyBudgetCents: prof.json ? prof.json.monthlyBudgetCents : undefined, profileVersion: prof.json && prof.json.profileVersion, updatedAt: prof.json && prof.json.updatedAt },
    aiCostSummary: (await apiGet(t, "/api/ai/cost-summary")).json,
    notificationPreferences: { status: prefs.status, n: (prefs.json && prefs.json.preferences || []).length, rows: (prefs.json && prefs.json.preferences) || [] },
    packsCatalog: { status: catalog.status, rows: list(catalog.json).map((p) => ({ packId: p.packId, name: p.name, description: p.description, type: p.type, version: p.version, itemCount: p.itemCount, installed: p.installed })) },
    packsInstalled: { status: installed.status, rows: list(installed.json).map((p) => ({ packId: p.packId, name: p.name, installedAt: p.installedAt })) },
    uninstallChecks: checks,
    rules: { status: rules.status, rows: list(rules.json).map((r) => ({ id: r.id, name: r.name, enabled: r.enabled, triggerType: r.triggerType, source: r.source, actions: (r.actions || []).length, updatedAt: r.updatedAt })) },
    automationExecutions: { status: ex.status, page: pageInfo(ex.json), first: list(ex.json).slice(0, 5).map((e) => ({ id: e.id, ruleName: e.ruleName, triggerEventType: e.triggerEventType, status: e.status, startedAt: e.startedAt })), triggerEventTypes: [...new Set(list(ex.json).map((e) => e.triggerEventType))] },
    syncEntries: { status: sync.status, page: pageInfo(sync.json) },
    aiExecutions: { status: aiEx.status, page: pageInfo(aiEx.json) },
  };
}

module.exports = { ...C, C, W, PHASE, OUT, RAW, BASE, EXTRA, TABS, LABELS, TABLIST, SUBPAGES, CAROL_EMAIL, NAMES, TOGGLE_RULE, TOGGLE_PREF, apiGet, list, pageInfo, connState, pwPath };
