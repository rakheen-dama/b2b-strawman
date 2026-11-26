// 665A OBS step 0: read-only discovery (GET only, each persona's own mock-IDP token). Records what
// the capture needs and the baseline the saves must restore: capabilities, org settings (General,
// letterhead, portal cadence, compliance + FICA review intervals), modules, org roles, members and
// their role/overrides, subscription. Creates nothing. Writes ../discover-<label>.json (single line).
// Usage: PHASE=before|after node 00-discover.cjs [label]   (label goes into the JSON, e.g. "pre-save")
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
  }
  const a = await L.idpToken("alice");
  const s = (await L.apiGet(a, "/api/settings")).json || {};
  out.settings = {
    defaultCurrency: s.defaultCurrency, brandColor: s.brandColor, documentFooterText: s.documentFooterText, logoUrl: s.logoUrl,
    taxRegistrationNumber: s.taxRegistrationNumber, taxLabel: s.taxLabel, taxInclusive: s.taxInclusive,
    letterhead: s.letterhead, portalDigestCadence: s.portalDigestCadence,
    legalMatterRetentionYears: s.legalMatterRetentionYears, dormancyThresholdDays: s.dormancyThresholdDays, dataRequestDeadlineDays: s.dataRequestDeadlineDays,
    ficaReviewMonthsLow: s.ficaReviewMonthsLow, ficaReviewMonthsMedium: s.ficaReviewMonthsMedium, ficaReviewMonthsHigh: s.ficaReviewMonthsHigh,
    enabledModules: s.enabledModules, compliancePackStatus: (s.compliancePackStatus || []).map((p) => `${p.packId || p.id}@${p.version}`),
  };
  const mods = (await L.apiGet(a, "/api/settings/modules")).json;
  out.modules = mods && mods.modules ? mods.modules.map((m) => `${m.id}:${m.enabled}`) : mods;
  const roles = (await L.apiGet(a, "/api/org-roles")).json || [];
  out.orgRoles = roles.map((r) => ({ id: r.id, name: r.name, slug: r.slug, isSystem: r.isSystem, memberCount: r.memberCount, capabilities: r.capabilities }));
  const members = (await L.apiGet(a, "/api/members")).json || [];
  out.members = [];
  for (const m of members) {
    const c = await L.apiGet(a, `/api/members/${m.id}/capabilities`);
    out.members.push({ id: m.id, name: m.name, email: m.email, role: m.role || m.orgRole, orgRoleId: m.orgRoleId, memberCaps: c.json || c.status });
  }
  out.retentionPolicies = ((await L.apiGet(a, "/api/retention-policies")).json || []).map((p) => `${p.recordType}/${p.triggerEvent}:${p.retentionDays}:${p.action}:${p.active}`);
  const sub = (await L.apiGet(a, "/api/billing/subscription")).json;
  out.subscription = sub ? { status: sub.status, billingMethod: sub.billingMethod } : null;
  out.orgDocuments = ((await L.apiGet(a, "/api/documents?scope=ORG")).json || []).length;
  fs.writeFileSync(path.join(L.OUT, `discover-${label}.json`), JSON.stringify(out) + "\n");
  console.log(JSON.stringify(out, null, 1).slice(0, 6000));
})();
