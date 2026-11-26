// 665D OBS step 0: read-only discovery (GET only, each persona's own mock-IDP token). Records what the
// capture needs and the baseline the saves must restore: capabilities per persona (+ the status of
// the Fees endpoints for each), the Fees & Trust state (default currency, tax settings, time
// recording, fee runs, collections, horizontal modules, enabled modules, tax rates, billing / cost
// rates, trust accounts), org roles, members and their role/overrides. Creates nothing.
// Writes ../discover-<label>.json (single line).
// Usage: PHASE=before node 00-discover.cjs <label>   (e.g. pre / post)
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

(async () => {
  const label = process.argv[2] || "discover";
  const out = { capturedAt: new Date().toISOString(), label, personas: {} };
  for (const who of ["alice", "carol"]) {
    const t = await L.idpToken(who);
    const caps = await L.apiGet(t, "/api/me/capabilities");
    out.personas[who] = { caps: caps.json || caps, endpointStatus: {} };
    for (const p of ["/api/settings", "/api/billing-rates", "/api/cost-rates", "/api/tax-rates", "/api/settings/collections", "/api/settings/modules", "/api/trust-accounts"]) out.personas[who].endpointStatus[p] = (await L.apiGet(t, p)).status;
  }
  const a = await L.idpToken("alice");
  out.fees = await L.feesState(a);
  const roles = (await L.apiGet(a, "/api/org-roles")).json || [];
  out.orgRoles = roles.map((r) => ({ id: r.id, name: r.name, slug: r.slug, isSystem: r.isSystem, memberCount: r.memberCount, capabilities: r.capabilities }));
  const members = (await L.apiGet(a, "/api/members")).json || [];
  out.members = [];
  for (const m of members) {
    const c = await L.apiGet(a, `/api/members/${m.id}/capabilities`);
    out.members.push({ id: m.id, name: m.name, email: m.email, orgRole: m.orgRole, memberCaps: c.json || c.status });
  }
  fs.writeFileSync(path.join(L.OUT, `discover-${label}.json`), JSON.stringify(out) + "\n");
  const f = out.fees;
  console.log(JSON.stringify({ personas: Object.fromEntries(Object.entries(out.personas).map(([k, v]) => [k, { role: v.caps.role, n: (v.caps.capabilities || []).length, status: v.endpointStatus }])), currency: f.currency, tax: f.tax, timeRecording: f.timeRecording, feeRuns: f.feeRuns, collections: f.collections, horizontal: (f.horizontalModules || []).map((m) => `${m.id}:${m.enabled}`), enabledModules: f.enabledModules, taxRates: f.taxRates.map((r) => `${r.name}:${r.active}`), billingRates: f.billingRates.length, costRates: f.costRates.length, trust: f.trustAccounts.map((x) => x.accountName), orgRoles: out.orgRoles.map((r) => `${r.name}(${r.memberCount})`), members: out.members.map((m) => `${m.email}:${m.memberCaps.roleName}`) }, null, 1));
})();
