// 663C OBS-before step 0: read-only discovery (GET only, each persona's own mock-IDP token) of the
// ids the capture script needs: a fee-note id, a report slug, Carol's capabilities. Creates nothing.
const C = require("../../../661B/walk/scripts/common.cjs");
const safe = async (f) => { try { return await f(); } catch (e) { return "ERR " + e.message; } };
(async () => {
  for (const who of ["alice", "carol"]) {
    const t = await C.idpToken(who);
    const out = { who };
    out.caps = await safe(async () => { const j = await C.api(t, "/api/me/capabilities"); return { isAdmin: j.isAdmin, isOwner: j.isOwner, capabilities: j.capabilities }; });
    out.invoices = await safe(async () => C.list(await C.api(t, "/api/invoices")).slice(0, 5).map((i) => `${i.id} ${i.status} ${i.invoiceNumber || ""}`));
    out.reports = await safe(async () => { const j = await C.api(t, "/api/report-definitions"); const arr = Array.isArray(j) ? j : j.categories ? j.categories.flatMap((c) => c.reports || []) : C.list(j); return arr.slice(0, 8).map((r) => r.slug); });
    out.auditRecent = await safe(async () => (await C.api(t, "/api/audit-events?severities=WARNING&severities=CRITICAL&size=5")).content?.length);
    console.log(JSON.stringify(out));
  }
})();
