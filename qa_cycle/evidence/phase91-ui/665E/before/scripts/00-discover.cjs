// 665E OBS step 0: read-only discovery (GET only, each persona's own mock-IDP token). Records what the
// capture needs and the baseline the saves must restore: capabilities per persona (+ the status of
// the Documents endpoints for each), the Documents state (branding colour / footer / logo present,
// acceptance expiry days, templates with source / active, clauses, clause categories, email stats,
// delivery log), org roles, members and their role/overrides. Creates nothing.
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
    for (const p of ["/api/settings", "/api/templates", "/api/clauses?includeInactive=true", "/api/clauses/categories", "/api/email/stats", "/api/email/delivery-log?page=0&size=20"]) out.personas[who].endpointStatus[p] = (await L.apiGet(t, p)).status;
  }
  const a = await L.idpToken("alice");
  out.docs = await L.docsState(a);
  const ct = await L.idpToken("carol");
  const custom = out.docs.templates.rows.find((r) => r.source === "ORG_CUSTOM" && r.active);
  const platform = out.docs.templates.rows.find((r) => r.source === "PLATFORM" && r.active);
  out.templateDetailStatus = {};
  for (const [k, r] of [["custom", custom], ["platform", platform]]) if (r) out.templateDetailStatus[k] = { id: r.id, name: r.name, alice: (await L.apiGet(a, `/api/templates/${r.id}`)).status, carol: (await L.apiGet(ct, `/api/templates/${r.id}`)).status };
  const roles = (await L.apiGet(a, "/api/org-roles")).json || [];
  out.orgRoles = roles.map((r) => ({ id: r.id, name: r.name, slug: r.slug, isSystem: r.isSystem, memberCount: r.memberCount, capabilities: r.capabilities }));
  const members = (await L.apiGet(a, "/api/members")).json || [];
  out.members = [];
  for (const m of members) {
    const c = await L.apiGet(a, `/api/members/${m.id}/capabilities`);
    out.members.push({ id: m.id, name: m.name, email: m.email, orgRole: m.orgRole, memberCaps: c.json || c.status });
  }
  fs.writeFileSync(path.join(L.OUT, `discover-${label}.json`), JSON.stringify(out) + "\n");
  const d = out.docs;
  console.log(JSON.stringify({ personas: Object.fromEntries(Object.entries(out.personas).map(([k, v]) => [k, { role: v.caps.role, isAdmin: v.caps.isAdmin, isOwner: v.caps.isOwner, n: (v.caps.capabilities || []).length, status: v.endpointStatus }])), branding: d.branding, acceptance: d.acceptance, templates: `${d.templates.status} n=${d.templates.rows.length} custom=${d.templates.rows.filter((r) => r.source === "ORG_CUSTOM").length} inactive=${d.templates.rows.filter((r) => !r.active).length}`, clauses: `${d.clauses.status} n=${d.clauses.n}`, categories: d.clauseCategories, emailStats: d.emailStats, deliveryLog: d.deliveryLog, detail: out.templateDetailStatus, orgRoles: out.orgRoles.map((r) => `${r.name}(${r.memberCount})`), members: out.members.map((m) => `${m.email}:${m.memberCaps.roleName}`) }, null, 1));
})();
