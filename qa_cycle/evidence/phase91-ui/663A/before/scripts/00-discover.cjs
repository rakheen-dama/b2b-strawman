// 663A OBS-before step 0: read-only discovery (GET only, each persona's own mock-IDP token) of what
// the 662A-walked stack holds for the empty-state surfaces. Creates nothing. Prints a summary.
const C = require("../../../661B/walk/scripts/common.cjs");
const safe = async (f) => { try { return await f(); } catch (e) { return "ERR " + e.message; } };
(async () => {
  for (const who of ["alice", "carol"]) {
    const t = await C.idpToken(who);
    const out = { who };
    for (const et of ["TASK", "PROJECT", "CUSTOMER", "INVOICE"]) {
      out["fd_" + et] = await safe(async () => C.list(await C.api(t, `/api/field-definitions?entityType=${et}`)).length);
      out["fg_" + et] = await safe(async () => C.list(await C.api(t, `/api/field-groups?entityType=${et}`)).map((g) => g.name + (g.autoApply ? "*" : "")));
    }
    out.myWork = await safe(async () => { const j = await C.api(t, "/api/my-work/tasks"); return { assigned: (j.assigned || []).length, unassigned: (j.unassigned || []).length }; });
    out.gatesPending = await safe(async () => C.list(await C.api(t, "/api/ai/gates?status=PENDING&size=50")).length);
    out.gatesAll = await safe(async () => C.list(await C.api(t, "/api/ai/gates?size=50")).length);
    const matters = await safe(async () => C.list(await C.api(t, "/api/matters")));
    out.matters = Array.isArray(matters) ? matters.slice(0, 40).map((m) => `${m.id.slice(0, 8)} ${m.status} ${m.name}`) : matters;
    out.invoices = await safe(async () => C.list(await C.api(t, "/api/invoices")).slice(0, 10).map((i) => `${i.id.slice(0, 8)} ${i.status} ${i.invoiceNumber || ""} ${i.customerName || ""}`));
    console.log(JSON.stringify(out, null, 1));
  }
})();
