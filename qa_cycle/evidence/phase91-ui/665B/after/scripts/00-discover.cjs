// 665B OBS step 0: read-only discovery (GET only, each persona's own mock-IDP token). Records what the
// capture needs and the baseline the saves must restore: capabilities, data-protection settings
// (jurisdiction, information officer, financial retention), retention policies, processing
// activities, DSAR requests, audit-event totals + facets (all-time), org roles, members and their
// role/overrides. Creates nothing. Writes ../discover-<label>.json (single line).
// Usage: PHASE=before|after node 00-discover.cjs <label>   (e.g. pre / post)
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
    // Carol's own view of the audit list: 403 unless she holds TEAM_OVERSIGHT.
    out.personas[who].auditEventsStatus = (await L.apiGet(t, "/api/audit-events?page=0&size=1")).status;
    out.personas[who].dataRequestsStatus = (await L.apiGet(t, "/api/data-requests")).status;
  }
  const a = await L.idpToken("alice");
  const s = (await L.apiGet(a, "/api/settings")).json || {};
  out.settings = {
    dataProtectionJurisdiction: s.dataProtectionJurisdiction, informationOfficerName: s.informationOfficerName, informationOfficerEmail: s.informationOfficerEmail,
    financialRetentionMonths: s.financialRetentionMonths, dataRequestDeadlineDays: s.dataRequestDeadlineDays, dormancyThresholdDays: s.dormancyThresholdDays,
  };
  out.retentionPolicies = ((await L.apiGet(a, "/api/settings/retention-policies")).json || []).map((p) => ({ id: p.id, v: `${p.recordType}/${p.triggerEvent}:${p.retentionDays}:${p.action}:${p.active}`, lastEvaluatedAt: p.lastEvaluatedAt, description: p.description }));
  const pa = (await L.apiGet(a, "/api/settings/processing-activities?size=100")).json;
  out.processingActivities = (pa && pa.content ? pa.content : []).map((x) => ({ id: x.id, category: x.category, legalBasis: x.legalBasis, dataSubjects: x.dataSubjects, retentionPeriod: x.retentionPeriod, recipients: x.recipients, updatedAt: x.updatedAt }));
  out.dataRequests = ((await L.apiGet(a, "/api/data-requests")).json || []).map((r) => ({ id: r.id, clientName: r.clientName, subjectName: r.subjectName, requestType: r.requestType, status: r.status, deadline: r.deadline, deadlineStatus: r.deadlineStatus }));
  const ev = (await L.apiGet(a, "/api/audit-events?page=0&size=50")).json;
  out.auditEvents = ev && ev.page ? { page: ev.page, newest: (ev.content || []).slice(0, 3).map((e) => `${e.occurredAt} ${e.eventType}`) } : ev;
  const all = "?from=1970-01-01T00:00:00Z";
  out.auditFacets = {
    actors: (await L.apiGet(a, `/api/audit-events/facets/actors${all}`)).json,
    entityTypes: (await L.apiGet(a, `/api/audit-events/facets/entity-types${all}`)).json,
    eventTypes: ((await L.apiGet(a, `/api/audit-events/facets/event-types${all}`)).json || []).map((f) => `${f.eventType}:${f.severity}:${f.count}`),
  };
  for (const sev of ["INFO", "NOTICE", "WARNING", "CRITICAL"]) {
    const r = (await L.apiGet(a, `/api/audit-events?page=0&size=1&severities=${sev}`)).json;
    (out.auditBySeverity = out.auditBySeverity || {})[sev] = r && r.page ? r.page.totalElements : r;
  }
  const roles = (await L.apiGet(a, "/api/org-roles")).json || [];
  out.orgRoles = roles.map((r) => ({ id: r.id, name: r.name, slug: r.slug, isSystem: r.isSystem, memberCount: r.memberCount, capabilities: r.capabilities }));
  const members = (await L.apiGet(a, "/api/members")).json || [];
  out.members = [];
  for (const m of members) {
    const c = await L.apiGet(a, `/api/members/${m.id}/capabilities`);
    out.members.push({ id: m.id, name: m.name, email: m.email, role: m.role || m.orgRole, memberCaps: c.json || c.status });
  }
  fs.writeFileSync(path.join(L.OUT, `discover-${label}.json`), JSON.stringify(out) + "\n");
  console.log(JSON.stringify({ ...out, auditFacets: { actors: out.auditFacets.actors, entityTypes: (out.auditFacets.entityTypes || []).length, eventTypes: out.auditFacets.eventTypes.length } }, null, 1).slice(0, 5000));
})();
