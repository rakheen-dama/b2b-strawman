// 665C OBS step 0: read-only discovery (GET only, each persona's own mock-IDP token). Records what the
// capture needs and the baseline the saves must restore: capabilities + take-on reachability per
// persona, the Matters-family data (matter templates, checklist templates, request templates, tags,
// field definitions / groups per entity type, naming pattern, request-reminder days, take-on
// datasets / batches / go-live checklist), org roles, members and their role/overrides.
// Creates nothing. Writes ../discover-<label>.json (single line).
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
    out.personas[who] = { caps: caps.json || caps };
    out.personas[who].takeOnDatasetsStatus = (await L.apiGet(t, "/api/take-on/datasets")).status;
    out.personas[who].fieldDefinitionsStatus = (await L.apiGet(t, "/api/field-definitions?entityType=PROJECT")).status;
    out.personas[who].checklistTemplatesStatus = (await L.apiGet(t, "/api/checklist-templates")).status;
  }
  const a = await L.idpToken("alice");
  const s = (await L.apiGet(a, "/api/settings")).json || {};
  out.settings = { projectNamingPattern: s.projectNamingPattern ?? null, defaultRequestReminderDays: s.defaultRequestReminderDays ?? null };
  const brief = (x) => ({ id: x.id, name: x.name, source: x.source, active: x.active, packId: x.packId, slug: x.slug, updatedAt: x.updatedAt });
  out.matterTemplates = L.list((await L.apiGet(a, "/api/matter-templates")).json).map(brief);
  out.checklistTemplates = L.list((await L.apiGet(a, "/api/checklist-templates")).json).map(brief);
  out.requestTemplates = L.list((await L.apiGet(a, "/api/request-templates")).json).map(brief);
  out.tags = L.list((await L.apiGet(a, "/api/tags")).json).map((t) => ({ id: t.id, name: t.name, color: t.color, slug: t.slug }));
  out.fields = {};
  for (const et of ["PROJECT", "TASK", "CUSTOMER", "INVOICE"]) {
    const defs = L.list((await L.apiGet(a, `/api/field-definitions?entityType=${et}`)).json);
    const groups = L.list((await L.apiGet(a, `/api/field-groups?entityType=${et}`)).json);
    out.fields[et] = { definitions: defs.length, activeDefinitions: defs.filter((d) => d.active).length, definitionNames: defs.map((d) => d.name), groups: groups.map((g) => ({ id: g.id, name: g.name, slug: g.slug, active: g.active, fields: (g.fieldDefinitionIds || []).length })) };
  }
  out.takeOn = {
    datasets: ((await L.apiGet(a, "/api/take-on/datasets")).json || []).map((d) => ({ dataset: d.dataset, completedBatches: d.completedBatches, sections: (d.sections || []).map((x) => x.section) })),
    batches: L.list((await L.apiGet(a, "/api/take-on/batches?page=0&size=50")).json).map((b) => ({ id: b.id, name: b.name, dataset: b.dataset, status: b.status, createdAt: b.createdAt })),
    goLive: ((await L.apiGet(a, "/api/take-on/go-live-checklist")).json || []).map((g) => ({ itemKey: g.itemKey, acknowledged: g.acknowledged, acknowledgedAt: g.acknowledgedAt })),
  };
  const roles = (await L.apiGet(a, "/api/org-roles")).json || [];
  out.orgRoles = roles.map((r) => ({ id: r.id, name: r.name, slug: r.slug, isSystem: r.isSystem, memberCount: r.memberCount, capabilities: r.capabilities }));
  const members = (await L.apiGet(a, "/api/members")).json || [];
  out.members = [];
  for (const m of members) {
    const c = await L.apiGet(a, `/api/members/${m.id}/capabilities`);
    out.members.push({ id: m.id, name: m.name, email: m.email, role: m.role || m.orgRole, memberCaps: c.json || c.status });
  }
  fs.writeFileSync(path.join(L.OUT, `discover-${label}.json`), JSON.stringify(out) + "\n");
  console.log(JSON.stringify({ personas: Object.fromEntries(Object.entries(out.personas).map(([k, v]) => [k, { role: v.caps.role, n: (v.caps.capabilities || []).length, takeOn: v.takeOnDatasetsStatus, fields: v.fieldDefinitionsStatus, checklists: v.checklistTemplatesStatus }])), settings: out.settings, counts: { matterTemplates: out.matterTemplates.length, checklists: out.checklistTemplates.length, requestTemplates: out.requestTemplates.length, tags: out.tags.map((t) => t.name) }, fields: Object.fromEntries(Object.entries(out.fields).map(([k, v]) => [k, `${v.definitions} defs / ${v.groups.length} groups`])), takeOn: out.takeOn, orgRoles: out.orgRoles.map((r) => `${r.name}(${r.memberCount})`), members: out.members.map((m) => `${m.email}:${m.memberCaps.roleName}`) }, null, 1));
})();
