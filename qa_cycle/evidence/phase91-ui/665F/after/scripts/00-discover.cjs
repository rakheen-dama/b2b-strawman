// 665F OBS step 0: read-only discovery (GET only, each persona's own mock-IDP token). Records what the
// capture needs and the baseline the interactions must restore: capabilities per persona (+ the
// status of the Connections endpoints for each), the Connections state (integrations, providers, Xero
// connection, MCP status, AI profile / cost summary, notification preferences, pack catalog /
// installed / uninstall checks, automation rules, automation executions, sync entries, AI
// executions), org roles, members and their role/overrides. Creates nothing.
// Writes ../discover-<label>.json (single line).
// Usage: PHASE=before node 00-discover.cjs <label>   (e.g. pre / post)
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const ENDPOINTS = ["/api/integrations", "/api/integrations/xero/connection", "/api/integrations/mcp/status", "/api/integrations/sync/entries?size=20", "/api/ai/profile", "/api/ai/cost-summary", "/api/ai/executions?size=20", "/api/notifications/preferences", "/api/packs/catalog", "/api/packs/installed", "/api/automation-rules", "/api/automation-executions?size=20"];

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
  out.connections = await L.connState(a);
  const roles = (await L.apiGet(a, "/api/org-roles")).json || [];
  out.orgRoles = roles.map((r) => ({ id: r.id, name: r.name, slug: r.slug, isSystem: r.isSystem, memberCount: r.memberCount, capabilities: r.capabilities }));
  const members = (await L.apiGet(a, "/api/members")).json || [];
  out.members = [];
  for (const m of members) {
    const c = await L.apiGet(a, `/api/members/${m.id}/capabilities`);
    out.members.push({ id: m.id, name: m.name, email: m.email, orgRole: m.orgRole, memberCaps: c.json || c.status });
  }
  fs.writeFileSync(path.join(L.OUT, `discover-${label}.json`), JSON.stringify(out) + "\n");
  const d = out.connections;
  console.log(JSON.stringify({
    personas: Object.fromEntries(Object.entries(out.personas).map(([k, v]) => [k, { role: v.caps.role, isAdmin: v.caps.isAdmin, isOwner: v.caps.isOwner, n: (v.caps.capabilities || []).length, status: v.endpointStatus }])),
    integrations: d.integrations.rows.map((r) => `${r.domain}:${r.providerSlug}:${r.enabled}`),
    xero: d.xeroConnection.status, mcp: d.mcpStatus.json, ai: d.aiProfile, cost: d.aiCostSummary,
    prefs: d.notificationPreferences.n, unknownPrefTypes: d.notificationPreferences.rows.map((r) => r.notificationType),
    catalog: d.packsCatalog.rows.map((p) => `${p.packId}|${p.name}|${p.description === null ? "null" : "desc"}|${p.installed}`), checks: d.uninstallChecks,
    rules: d.rules.rows.map((r) => `${r.name}:${r.enabled}:${r.triggerType}`), executions: d.automationExecutions.page, triggerEventTypes: d.automationExecutions.triggerEventTypes, sync: d.syncEntries.page, aiEx: d.aiExecutions.page,
    orgRoles: out.orgRoles.map((r) => `${r.name}(${r.memberCount})`), members: out.members.map((m) => `${m.email}:${m.memberCaps.roleName}`),
  }, null, 1));
})();
